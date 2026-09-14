package com.frameflow.learning.product.service;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.frameflow.learning.shared.error.ApiException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 批次分析进度的 SSE 实时推送（F5+，替代前端固定间隔轮询）。
 *
 * 数据来源完全复用 {@link BatchService#progressOf}，因此订阅时与 REST 一样
 * 走"实时团队归属校验 + 缓存优先统计"，权限与进度语义不会出现两套实现。
 *
 * 线程模型（为什么这样做见 subscribe/start 的 ★ 注释）：
 * - 所有订阅共享一个 2 线程的有界守护线程池，按固定节拍扫描，不"一个连接一个线程"；
 * - 连接建立、断开、超时都只维护订阅表，不新起线程。
 */
@Service
public class BatchProgressStreamService {

    private static final Logger log = LoggerFactory.getLogger(BatchProgressStreamService.class);

    /** 连接最长存活 10 分钟；到点由容器触发 onTimeout 收尾。 */
    private static final long EMITTER_TIMEOUT_MS = Duration.ofMinutes(10).toMillis();
    /** 服务端扫描节拍：1s 检查一次计数是否变化。 */
    private static final long TICK_INTERVAL_MS = 1000L;
    /** 每 ~15 个节拍（约 15s）在无变化时发一条注释帧，防代理/负载均衡器掐掉空闲连接。 */
    private static final long HEARTBEAT_EVERY_TICKS = 15L;
    private static final String PROGRESS_EVENT = "progress";
    private static final String DONE_EVENT = "done";
    /** 分析流水线终态判定用到的状态键（与 CandidateMapper 的状态枚举一致）。 */
    private static final String ANALYZING = "ANALYZING";
    private static final String UPLOADED = "UPLOADED";

    private final BatchService batchService;
    private final Map<Long, CopyOnWriteArrayList<Subscription>> subscribers = new ConcurrentHashMap<>();
    private final AtomicLong tickCounter = new AtomicLong();
    private ScheduledExecutorService scheduler;

    public BatchProgressStreamService(BatchService batchService) {
        this.batchService = batchService;
    }

    @PostConstruct
    void start() {
        // ★ 核心：全局只用一个有界调度池，而不是每个订阅者各起一个定时线程。
        // 每个连接占一个线程的话，几百个并发订阅就会把线程数打爆（OOM / 线程耗尽）。
        // 守护线程保证 JVM 退出时不会被这个后台任务挂住。
        scheduler = Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "batch-progress-sse");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(this::tick, TICK_INTERVAL_MS, TICK_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    /**
     * 建立订阅：连接即推一帧当前进度，之后计数变化即推；进入终态推 done 并关闭。
     * 归属校验在建立连接时即完成——越权用户在这里就被 {@code progressOf} 拒绝。
     */
    public SseEmitter subscribe(long userId, long batchId) {
        // ★ 核心：复用 progressOf 做与 /progress 完全相同的授权与统计——
        // 若这里绕过 teamAccess，任何登录用户都能订阅他人批次进度（跨团队泄露）。
        Map<String, Integer> initial = batchService.progressOf(userId, batchId);

        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        Subscription subscription = new Subscription(userId, emitter,
                initial == null ? Map.of() : initial);
        subscribers.computeIfAbsent(batchId, key -> new CopyOnWriteArrayList<>()).add(subscription);

        // 客户端断开 / 超时 / 出错都要从订阅表移除，否则会残留引用并持续被扫描。
        emitter.onCompletion(() -> remove(batchId, subscription));
        emitter.onTimeout(() -> {
            emitter.complete();
            remove(batchId, subscription);
        });
        emitter.onError(error -> remove(batchId, subscription));

        if (initial == null) {
            // 批次不存在：与 /progress 的空响应契约保持一致，直接收尾不推数据。
            completeWithDone(batchId, subscription);
        } else {
            send(subscription, PROGRESS_EVENT, initial);
            if (isTerminal(initial)) {
                completeWithDone(batchId, subscription);
            }
        }
        return emitter;
    }

    /** 固定节拍扫描：逐订阅比对计数，变化即推；终态收尾；空闲发心跳。 */
    private void tick() {
        if (subscribers.isEmpty()) {
            return;
        }
        boolean heartbeatTick = tickCounter.incrementAndGet() % HEARTBEAT_EVERY_TICKS == 0;
        for (Map.Entry<Long, CopyOnWriteArrayList<Subscription>> entry : subscribers.entrySet()) {
            long batchId = entry.getKey();
            for (Subscription subscription : entry.getValue()) {
                if (subscription.closed.get()) {
                    continue;
                }
                pushIfActive(batchId, subscription, heartbeatTick);
            }
            // 兜底清理：写失败时可能只有 closed 标记而错过回调，这里统一摘除，避免引用泄漏。
            entry.getValue().removeIf(subscription -> subscription.closed.get());
            if (entry.getValue().isEmpty()) {
                subscribers.remove(batchId, entry.getValue());
            }
        }
    }

    private void pushIfActive(long batchId, Subscription subscription, boolean heartbeatTick) {
        try {
            Map<String, Integer> counts = batchService.progressOf(subscription.userId, batchId);
            if (counts == null) {
                completeWithDone(batchId, subscription);
                return;
            }
            if (!counts.equals(subscription.lastCounts)) {
                subscription.lastCounts = counts;
                send(subscription, PROGRESS_EVENT, counts);
                if (isTerminal(counts)) {
                    completeWithDone(batchId, subscription);
                }
                return;
            }
            if (heartbeatTick) {
                sendHeartbeat(subscription);
            }
        } catch (ApiException ex) {
            // 订阅期间被移出团队或批次被删：静默收尾，不把越权原因回给客户端。
            completeWithDone(batchId, subscription);
        } catch (Exception ex) {
            log.debug("SSE 进度扫描失败，移除订阅 batchId={}: {}", batchId, ex.getMessage());
            remove(batchId, subscription);
        }
    }

    /**
     * ★ 核心：分析终态 = 没有候选在分析（ANALYZING=0）且没有候选排队待派发（UPLOADED=0）。
     * 只看 ANALYZING 会在派发波次之间的空档误判为完成；只看 UPLOADED 会漏掉正在处理的候选。
     * 两者同时为 0，才代表这批进入流水线的候选都落到了终态（终态见前端 STATUS_CLASS）。
     */
    private static boolean isTerminal(Map<String, Integer> counts) {
        return counts.getOrDefault(ANALYZING, 0) == 0
                && counts.getOrDefault(UPLOADED, 0) == 0;
    }

    private void completeWithDone(long batchId, Subscription subscription) {
        // 幂等：tick 与 onCompletion 可能并发触发同一订阅的收尾。
        if (!subscription.closed.compareAndSet(false, true)) {
            return;
        }
        try {
            subscription.emitter.send(SseEmitter.event()
                    .name(DONE_EVENT).data(Map.of(), MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ignore) {
            // 客户端已断开，无需再推 done。
        }
        subscription.emitter.complete();
        remove(batchId, subscription);
    }

    private void send(Subscription subscription, String event, Map<String, Integer> data) {
        try {
            subscription.emitter.send(SseEmitter.event()
                    .name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ex) {
            // 写失败说明连接已断，标记停用；容器随后触发 onError/onCompletion 做清理。
            subscription.closed.set(true);
        }
    }

    private void sendHeartbeat(Subscription subscription) {
        try {
            // 注释帧（": ..."）客户端 EventSource 会忽略，只用于保活代理连接。
            subscription.emitter.send(SseEmitter.event().comment("hb"));
        } catch (IOException | IllegalStateException ex) {
            subscription.closed.set(true);
        }
    }

    private void remove(long batchId, Subscription subscription) {
        subscription.closed.set(true);
        List<Subscription> list = subscribers.get(batchId);
        if (list != null) {
            list.remove(subscription);
            if (list.isEmpty()) {
                subscribers.remove(batchId, list);
            }
        }
    }

    /** 单个订阅：记住发起用户（用于每次扫描重新校验归属）与上一次推送的计数。 */
    private static final class Subscription {
        private final long userId;
        private final SseEmitter emitter;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private volatile Map<String, Integer> lastCounts;

        private Subscription(long userId, SseEmitter emitter, Map<String, Integer> lastCounts) {
            this.userId = userId;
            this.emitter = emitter;
            this.lastCounts = lastCounts;
        }
    }
}
