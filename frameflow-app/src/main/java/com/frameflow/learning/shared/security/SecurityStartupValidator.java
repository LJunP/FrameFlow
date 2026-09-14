package com.frameflow.learning.shared.security;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 非本地环境的密钥 fail-closed 校验。
 *
 * ★ 核心：为什么必须在应用启动时拦，而不是只靠部署脚本？
 * {@code infra/scripts/check-env-isolation.sh} 确实会拒绝占位符密钥，但它只在
 * 走 {@code promote-release.sh} 时被执行。任何人绕过发布脚本直接起容器
 * （手工 docker run、改 compose、回滚到旧流程）就完全绕过了这道检查。
 * 防线要放在"离风险最近的地方"：密钥的使用者自己必须拒绝不安全的配置。
 *
 * 为什么本地/测试环境不校验？测试没有 FRAMEFLOW_ENV，本地默认密钥够用且
 * 只在 loopback；强制会让 99 个测试全部起不来。真正的风险只存在于
 * dev / staging / production——那里的数据和凭据是真实的。
 */
@Component
public class SecurityStartupValidator implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(SecurityStartupValidator.class);

    /** Worker 回写接口的历史默认值，写在源码里等于公开，绝不能出现在真实环境。 */
    private static final String WORKER_KEY_SOURCE_DEFAULT = "frameflow-dev-worker-key";

    private static final int MIN_SECRET_LENGTH = 24;
    private static final Pattern TEMPLATE_MARKER =
            Pattern.compile("(?i)(change[_-]?me|example|local[_-]?only)");
    private static final Set<String> ENFORCED_ENVIRONMENTS = Set.of("dev", "staging", "production");

    private final String environment;
    private final String workerKey;
    private final String adminKey;

    /**
     * 用 @Value 直接取值而不是注入 WorkerProperties：ArchitectureTest 禁止
     * {@code ..shared..} 依赖 {@code ..product..}，本类放在 shared 里就必须
     * 只认配置项的"值"，不认业务模块的类型。这条边界值得遵守——否则
     * shared 会慢慢变成什么都依赖的垃圾桶。
     */
    public SecurityStartupValidator(@Value("${frameflow.env:local}") String environment,
                                    @Value("${frameflow.worker.result-key:}") String workerKey,
                                    AdminProperties adminProperties) {
        this.environment = environment == null ? "local" : environment.trim().toLowerCase(Locale.ROOT);
        this.workerKey = workerKey;
        this.adminKey = adminProperties == null ? "" : adminProperties.apiKey();
    }

    /**
     * 用 InitializingBean 而不是 ApplicationRunner：单例初始化发生在
     * {@code finishBeanFactoryInitialization}，早于 {@code finishRefresh}
     * 里启动 Web 服务器。用 ApplicationRunner 的话，Tomcat 会先把端口监听起来
     * 再拒绝——中间那几秒服务已经对外了，本该被拦下的配置错误照样被访问到。
     * 校验必须在"任何端口对外之前"完成。
     */
    @Override
    public void afterPropertiesSet() {
        if (!ENFORCED_ENVIRONMENTS.contains(environment)) {
            log.info("环境 {} 跳过密钥 fail-closed 校验（仅 dev/staging/production 强制）", environment);
            return;
        }
        requireSecret("FRAMEFLOW_WORKER_KEY（内部回写接口共享密钥）", workerKey, true);
        requireSecret("FRAMEFLOW_ADMIN_API_KEY（平台运维接口密钥）", adminKey, false);
        log.info("密钥 fail-closed 校验通过：environment={}", environment);
    }

    /**
     * @param forbidSourceDefault 该密钥是否存在"源码内公开默认值"的历史包袱。
     *                            worker key 有，必须额外比对；admin key 是新增的，没有。
     */
    private void requireSecret(String name, String value, boolean forbidSourceDefault) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "拒绝启动：" + name + " 未配置。环境 " + environment + " 不允许留空。");
        }
        if (value.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("拒绝启动：" + name + " 长度不足 "
                    + MIN_SECRET_LENGTH + " 个字符。");
        }
        if (TEMPLATE_MARKER.matcher(value).find()) {
            throw new IllegalStateException("拒绝启动：" + name
                    + " 仍是模板占位值（含 change_me / example / local_only）。");
        }
        if (forbidSourceDefault && WORKER_KEY_SOURCE_DEFAULT.equals(value)) {
            throw new IllegalStateException("拒绝启动：" + name
                    + " 仍是源码内的公开默认值，等同于任何人都能伪造质检结论。");
        }
    }
}
