#!/usr/bin/env python3
"""FrameFlow 异步质检管线压测。

测什么（以及为什么）
--------------------
FrameFlow 的功能文档有两万五千字，性能一个数字都没有。这个脚本补的是后者。

管线形态是「预签名直传 → MQ 派发 → Worker 分析 → 幂等回写」。
真正值得量的不是单点延迟，而是**背压下的行为**：
队列积压时吞吐怎么衰减、延迟分布怎么变、DLQ 什么时候开始接东西。

五个阶段
--------
  A 吞吐基线   固定并发跑通 N 个视频，得端到端吞吐（视频/分钟）
  B 耗时分解   每个候选打点：注册 / 上传 / 确认 / 派发 / 分析完成 / 排名
  C 背压       以递增速率投递，采样队列深度与完成数，得吞吐衰减曲线
  D DLQ        统计 dlqDepth 增量与投递总数的比值
  E 弱网续传   MULTIPART 故意中断部分分片，用 upload-session 恢复，测成功率

输出
----
  <out>/loadtest-<ts>.json   结构化全量数据
  stdout                     可读摘要

用法
----
  bash scripts/loadtest/run-loadtest.sh                 # 默认档
  python3 scripts/loadtest/loadtest.py --preflight      # 只检查环境，不跑负载
  python3 scripts/loadtest/loadtest.py --videos 40 --concurrency 8

前置
----
  本地栈已起（见 README「方式一」），且存在一个可登录账号。
  账号凭据从环境变量读，绝不写进仓库：
      FF_EMAIL / FF_PASSWORD        必填
      FF_BASE_URL                   默认 http://127.0.0.1:18080
      FF_ADMIN_KEY                  可选；没有就跳过队列深度采样（阶段 C/D 降级）
"""

from __future__ import annotations

import argparse
import json
import os
import statistics
import subprocess
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ffclient import ApiError, Client  # noqa: E402

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_BASE_URL = "http://127.0.0.1:18080"


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def pct(values: list[float], p: float) -> float | None:
    """百分位。样本不足时返回 None 而不是编一个数——
    并发不够的时候 P99 和 P50 会贴在一起，那不是「尾延迟很好」。"""
    if not values:
        return None
    if len(values) < 2:
        return round(values[0], 4)
    ordered = sorted(values)
    k = (len(ordered) - 1) * (p / 100.0)
    lo, hi = int(k), min(int(k) + 1, len(ordered) - 1)
    return round(ordered[lo] + (ordered[hi] - ordered[lo]) * (k - lo), 4)


def summarize(values: list[float]) -> dict:
    vals = [v for v in values if v is not None]
    if not vals:
        return {"n": 0}
    return {
        "n": len(vals),
        "min": round(min(vals), 4),
        "p50": pct(vals, 50),
        "p90": pct(vals, 90),
        "p99": pct(vals, 99) if len(vals) >= 10 else None,
        "max": round(max(vals), 4),
        "mean": round(statistics.fmean(vals), 4),
        "p99_trustworthy": len(vals) >= 100,
    }


# ════════════════════════════════════════════════════════════════
@dataclass
class CandidateTrace:
    """一个候选视频在管线里的完整打点。阶段耗时分解就是它的聚合。"""

    index: int
    candidate_id: int | None = None
    size_bytes: int = 0
    mode: str | None = None
    t_register: float | None = None     # POST /batches/{id}/candidates
    t_upload: float | None = None       # 预签名直传字节
    t_complete: float | None = None     # POST /candidates/{id}/complete
    t_analyze_wait: float | None = None # 派发 → 终态
    terminal_status: str | None = None
    parts_total: int = 0
    parts_retried: int = 0
    error: str | None = None

    @property
    def t_total(self) -> float | None:
        legs = [self.t_register, self.t_upload, self.t_complete, self.t_analyze_wait]
        return round(sum(x for x in legs if x), 4) if any(legs) else None


@dataclass
class Result:
    started_at: str = field(default_factory=now_iso)
    finished_at: str | None = None
    config: dict = field(default_factory=dict)
    environment: dict = field(default_factory=dict)
    preflight: dict = field(default_factory=dict)
    phase_a_throughput: dict = field(default_factory=dict)
    phase_b_breakdown: dict = field(default_factory=dict)
    phase_c_backpressure: dict = field(default_factory=dict)
    phase_d_dlq: dict = field(default_factory=dict)
    phase_e_resume: dict = field(default_factory=dict)
    traces: list = field(default_factory=list)
    notes: list = field(default_factory=list)


# ════════════════════════════════════════════════════════════════
class LoadTest:
    def __init__(self, args: argparse.Namespace) -> None:
        self.args = args
        self.result = Result()
        self.client = Client(
            base_url=args.base_url,
            admin_key=os.environ.get("FF_ADMIN_KEY"),
            timeout=args.timeout,
        )
        self.has_admin = bool(os.environ.get("FF_ADMIN_KEY"))
        self.project_id: int | None = None
        self.profile_id: int | None = None
        self._lock = threading.Lock()

    # ── 环境核查 ────────────────────────────────────────────
    def preflight(self) -> bool:
        pf: dict = {"checks": []}

        def check(name: str, fn) -> bool:
            try:
                detail = fn()
                pf["checks"].append({"name": name, "ok": True, "detail": detail})
                return True
            except Exception as exc:  # noqa: BLE001 - 预检就是要把所有失败都摊开
                pf["checks"].append({"name": name, "ok": False, "detail": str(exc)[:300]})
                return False

        ok = True
        ok &= check("API 可达 (/api/v1/ping)", lambda: self.client.get("/api/v1/ping").value)

        email, password = os.environ.get("FF_EMAIL"), os.environ.get("FF_PASSWORD")
        if not email or not password:
            pf["checks"].append({
                "name": "凭据", "ok": False,
                "detail": "FF_EMAIL / FF_PASSWORD 未设置。凭据只从环境变量读，不写入仓库。",
            })
            ok = False
        else:
            ok &= check("登录", lambda: {"email": email, "token": "已获取"} if self.client.login(email, password) else None)

        if self.has_admin:
            ok &= check("队列深度可读 (/admin/mq/stats)", self.client.mq_stats)
        else:
            pf["checks"].append({
                "name": "队列深度",
                "ok": True,
                "detail": "FF_ADMIN_KEY 未设置 —— 阶段 C 的队列深度曲线与阶段 D 的 DLQ 触发率将不可用（降级，不是失败）",
                "degraded": True,
            })
            self.result.notes.append("FF_ADMIN_KEY 未设置：C/D 阶段降级为仅基于 progress 的观测")

        ok &= check("ffmpeg 或 Docker 可用（生成测试媒体）", self._probe_media_tooling)

        pf["ok"] = ok
        self.result.preflight = pf
        return ok

    @staticmethod
    def _probe_media_tooling() -> str:
        for cmd, label in (("ffmpeg", "本机 ffmpeg"), ("docker", "Docker")):
            try:
                subprocess.run([cmd, "-version"] if cmd == "ffmpeg" else [cmd, "version"],
                               capture_output=True, timeout=15, check=True)
                return label
            except Exception:  # noqa: BLE001
                continue
        raise RuntimeError("ffmpeg 与 docker 都不可用；无法生成真实媒体")

    # ── 测试媒体 ────────────────────────────────────────────
    def ensure_media(self, count: int) -> list[Path]:
        media_dir = Path(self.args.media_dir)
        media_dir.mkdir(parents=True, exist_ok=True)
        existing = sorted(p for p in media_dir.glob("*.mp4"))
        if len(existing) >= count:
            return existing[:count]

        gen = REPO_ROOT / "scripts" / "gen_test_media.py"
        if not gen.exists():
            raise RuntimeError(f"找不到媒体生成器: {gen}")
        # 复用仓库自带的可复现生成器：同一 SHA 必须产出同样的字节
        subprocess.run(
            [sys.executable, str(gen), "--out", str(media_dir),
             "--count", str(count), "--duration", str(self.args.clip_seconds)],
            check=True, cwd=REPO_ROOT,
        )
        found = sorted(p for p in media_dir.glob("*.mp4"))
        if len(found) < count:
            raise RuntimeError(f"生成器只产出 {len(found)} 个文件，需要 {count}")
        return found[:count]

    # ── 项目 / Profile / 批次 ───────────────────────────────
    def ensure_project_and_profile(self) -> None:
        projects = self.client.get("/api/v1/projects").value
        items = projects.get("items", projects) if isinstance(projects, dict) else projects
        existing = next((p for p in (items or []) if p.get("name", "").startswith("loadtest")), None)
        if existing:
            self.project_id = existing["id"]
        else:
            created = self.client.post("/api/v1/projects", {
                "name": f"loadtest-{int(time.time())}",
                "description": "压测专用，可安全删除",
            }).value
            self.project_id = created["id"]

        profiles = self.client.get("/api/v1/quality-profiles").value
        pitems = profiles.get("items", profiles) if isinstance(profiles, dict) else profiles
        if pitems:
            self.profile_id = pitems[0]["id"]
        else:
            created = self.client.post("/api/v1/quality-profiles", {
                "name": "loadtest-profile",
                "description": "压测专用",
                "spec": json.dumps({"deterministic": {"enabled": True}, "semantic": {"enabled": False}}),
            }).value
            self.profile_id = created["id"]

    def create_batch(self, capacity: int) -> int:
        res = self.client.post("/api/v1/batches", {
            "projectId": self.project_id,
            "profileId": self.profile_id,
            "capacity": max(1, min(300, capacity)),
        })
        return res.value["id"]

    # ── 单个候选的完整链路 ──────────────────────────────────
    def push_candidate(self, batch_id: int, path: Path, index: int,
                       *, interrupt_parts: bool = False) -> CandidateTrace:
        tr = CandidateTrace(index=index, size_bytes=path.stat().st_size)
        payload = path.read_bytes()
        try:
            reg = self.client.post(f"/api/v1/batches/{batch_id}/candidates", {
                "fileName": path.name,
                "contentType": "video/mp4",
                "sizeBytes": tr.size_bytes,
            })
            tr.t_register = round(reg.seconds, 4)
            body = reg.value
            tr.candidate_id = body["candidateId"]
            tr.mode = body.get("mode")

            if tr.mode == "MULTIPART":
                tr.t_upload, tr.parts_total, tr.parts_retried = self._upload_multipart(
                    tr.candidate_id, payload, int(body.get("partSizeBytes") or 5 * 1024 * 1024),
                    interrupt=interrupt_parts,
                )
            else:
                up = self.client.put_bytes(body["uploadUrl"], payload, "video/mp4")
                tr.t_upload = round(up.seconds, 4)
                tr.parts_total = 1

            comp = self.client.post(f"/api/v1/candidates/{tr.candidate_id}/complete")
            tr.t_complete = round(comp.seconds, 4)
            tr.terminal_status = (comp.value or {}).get("status")
        except ApiError as exc:
            tr.error = str(exc)[:300]
        except Exception as exc:  # noqa: BLE001
            tr.error = f"{type(exc).__name__}: {exc}"[:300]
        return tr

    def _upload_multipart(self, candidate_id: int, payload: bytes, part_size: int,
                          *, interrupt: bool) -> tuple[float, int, int]:
        """分片直传。interrupt=True 时故意跳过一半分片，再走续传路径补齐。

        这一段模拟的是弱网：不是「慢」，是「断」。
        断点续传真正要证明的是 upload-session 能把已完成分片准确报回来。
        """
        chunks = [payload[i:i + part_size] for i in range(0, len(payload), part_size)] or [b""]
        total = len(chunks)
        numbers = list(range(1, total + 1))
        start = time.perf_counter()

        first_pass = numbers[: max(1, total // 2)] if interrupt else numbers
        urls = self.client.post(f"/api/v1/candidates/{candidate_id}/upload-parts",
                                {"partNumbers": first_pass}).value["partUrls"]
        for n in first_pass:
            self.client.put_bytes(urls[str(n)] if str(n) in urls else urls[n], chunks[n - 1])

        retried = 0
        if interrupt:
            # 续传：问服务端「你收到了哪些」，而不是本地记账。
            session = self.client.get(f"/api/v1/candidates/{candidate_id}/upload-session").value
            done = {int(p["partNumber"]) for p in (session.get("completedParts") or [])}
            missing = [n for n in numbers if n not in done]
            retried = len(missing)
            if missing:
                urls2 = self.client.post(f"/api/v1/candidates/{candidate_id}/upload-parts",
                                         {"partNumbers": missing}).value["partUrls"]
                for n in missing:
                    self.client.put_bytes(urls2[str(n)] if str(n) in urls2 else urls2[n], chunks[n - 1])
        return round(time.perf_counter() - start, 4), total, retried

    # ── 等待批次分析完成 ────────────────────────────────────
    TERMINAL = {"ANALYZED", "ANALYSIS_ERROR", "AUTO_REJECT", "REVIEW_REQUIRED", "INVALID"}

    def wait_for_analysis(self, batch_id: int, expected: int, timeout: float,
                          sampler: list | None = None) -> dict:
        start = time.perf_counter()
        last: dict[str, int] = {}
        while time.perf_counter() - start < timeout:
            last = self.client.progress(batch_id)
            done = sum(v for k, v in last.items() if k in self.TERMINAL)
            if sampler is not None:
                point = {
                    "t": round(time.perf_counter() - start, 3),
                    "done": done,
                    "progress": dict(last),
                }
                if self.has_admin:
                    try:
                        point["queue"] = self.client.mq_stats()
                    except ApiError:
                        point["queue"] = None
                sampler.append(point)
            if done >= expected:
                return {"completed": True, "seconds": round(time.perf_counter() - start, 3), "progress": last}
            time.sleep(self.args.poll_interval)
        return {"completed": False, "seconds": round(time.perf_counter() - start, 3),
                "progress": last, "reason": "TIMEOUT"}

    # ══════════════════════════════════════════════════════
    def phase_a_and_b(self) -> None:
        n, conc = self.args.videos, self.args.concurrency
        media = self.ensure_media(min(n, self.args.distinct_clips))
        batch_id = self.create_batch(n)

        wall_start = time.perf_counter()
        traces: list[CandidateTrace] = []
        with ThreadPoolExecutor(max_workers=conc) as pool:
            futs = [pool.submit(self.push_candidate, batch_id, media[i % len(media)], i) for i in range(n)]
            for f in as_completed(futs):
                traces.append(f.result())
        upload_wall = time.perf_counter() - wall_start

        self.client.post(f"/api/v1/batches/{batch_id}/close")
        dispatch = self.client.post(f"/api/v1/batches/{batch_id}/analyze")
        dispatched = (dispatch.value or {}).get("dispatched", 0)

        uploaded = sum(1 for t in traces if t.terminal_status == "UPLOADED")
        analysis = self.wait_for_analysis(batch_id, uploaded, self.args.analysis_timeout)
        total_wall = time.perf_counter() - wall_start

        rank_sec = None
        try:
            rank_sec = round(self.client.post(f"/api/v1/batches/{batch_id}/rank").seconds, 4)
        except ApiError as exc:
            self.result.notes.append(f"排名调用失败（不影响吞吐结论）: {exc}")

        ok = [t for t in traces if not t.error]
        self.result.phase_a_throughput = {
            "batch_id": batch_id,
            "videos_requested": n,
            "videos_uploaded_ok": uploaded,
            "videos_failed": len(traces) - len(ok),
            "concurrency": conc,
            "dispatched": dispatched,
            "upload_wall_seconds": round(upload_wall, 3),
            "total_wall_seconds": round(total_wall, 3),
            "analysis_completed": analysis["completed"],
            "throughput_videos_per_min": (
                round(uploaded / total_wall * 60, 2) if total_wall > 0 and uploaded else 0.0
            ),
            "upload_throughput_videos_per_min": (
                round(uploaded / upload_wall * 60, 2) if upload_wall > 0 and uploaded else 0.0
            ),
            "final_progress": analysis["progress"],
        }

        legs = {
            "register": [t.t_register for t in ok],
            "upload": [t.t_upload for t in ok],
            "complete": [t.t_complete for t in ok],
        }
        stage_totals = {k: round(sum(v for v in vals if v), 3) for k, vals in legs.items()}
        analysis_sec = analysis["seconds"]
        stage_totals["analysis_wait"] = round(analysis_sec, 3)
        if rank_sec is not None:
            stage_totals["rank"] = rank_sec
        grand = sum(stage_totals.values()) or 1.0

        self.result.phase_b_breakdown = {
            "per_stage_latency": {k: summarize([v for v in vals if v]) for k, vals in legs.items()},
            "analysis_wait_seconds": round(analysis_sec, 3),
            "rank_seconds": rank_sec,
            "stage_share_percent": {k: round(v / grand * 100, 1) for k, v in stage_totals.items()},
            "note": (
                "register/upload/complete 是并发累加的耗时，analysis_wait 是墙钟；"
                "占比只用于看量级，不能当作串行时间轴读。"
            ),
        }
        self.result.traces = [asdict(t) | {"t_total": t.t_total} for t in traces]

    def phase_c_and_d(self) -> None:
        """背压：一次性灌入远超消费能力的量，采样队列深度与完成曲线。"""
        n = self.args.backpressure_videos
        if n <= 0:
            self.result.phase_c_backpressure = {"skipped": True, "reason": "--backpressure-videos 0"}
            return
        media = self.ensure_media(min(n, self.args.distinct_clips))
        batch_id = self.create_batch(n)

        dlq_before = self.client.mq_stats().get("dlqDepth", -1) if self.has_admin else -1

        with ThreadPoolExecutor(max_workers=self.args.backpressure_concurrency) as pool:
            futs = [pool.submit(self.push_candidate, batch_id, media[i % len(media)], i) for i in range(n)]
            traces = [f.result() for f in as_completed(futs)]

        uploaded = sum(1 for t in traces if t.terminal_status == "UPLOADED")
        self.client.post(f"/api/v1/batches/{batch_id}/close")
        self.client.post(f"/api/v1/batches/{batch_id}/analyze")

        samples: list = []
        analysis = self.wait_for_analysis(batch_id, uploaded, self.args.analysis_timeout, sampler=samples)

        # 吞吐衰减曲线：相邻采样点之间的瞬时完成速率
        curve = []
        for prev, cur in zip(samples, samples[1:]):
            dt = cur["t"] - prev["t"]
            if dt <= 0:
                continue
            curve.append({
                "t": cur["t"],
                "instant_videos_per_min": round((cur["done"] - prev["done"]) / dt * 60, 2),
                "queue_depth": (cur.get("queue") or {}).get("taskQueueDepth"),
                "done": cur["done"],
            })

        peak_depth = max((c["queue_depth"] for c in curve if isinstance(c.get("queue_depth"), int)), default=None)
        rates = [c["instant_videos_per_min"] for c in curve]
        self.result.phase_c_backpressure = {
            "batch_id": batch_id,
            "videos": n,
            "uploaded_ok": uploaded,
            "concurrency": self.args.backpressure_concurrency,
            "peak_queue_depth": peak_depth,
            "samples": samples if self.args.keep_samples else f"<{len(samples)} 个采样点，用 --keep-samples 保留>",
            "throughput_curve": curve,
            "instant_rate_summary": summarize(rates),
            "sustained_videos_per_min": (
                round(uploaded / analysis["seconds"] * 60, 2) if analysis["seconds"] > 0 else None
            ),
            "analysis_completed": analysis["completed"],
            "degraded": not self.has_admin,
        }

        dlq_after = self.client.mq_stats().get("dlqDepth", -1) if self.has_admin else -1
        if self.has_admin and dlq_before >= 0 and dlq_after >= 0:
            delta = max(0, dlq_after - dlq_before)
            self.result.phase_d_dlq = {
                "dlq_before": dlq_before, "dlq_after": dlq_after, "delta": delta,
                "dispatched": uploaded,
                "dlq_rate": round(delta / uploaded, 4) if uploaded else None,
                "note": "delta 为 0 只说明本轮没进 DLQ，不证明 DLQ 路径可用；那需要单独的故障注入。",
            }
        else:
            self.result.phase_d_dlq = {
                "measured": False,
                "reason": "FF_ADMIN_KEY 未设置，无法读取 dlqDepth",
            }

    def phase_e_resume(self) -> None:
        """弱网分片续传成功率。"""
        n = self.args.resume_attempts
        if n <= 0:
            self.result.phase_e_resume = {"skipped": True}
            return
        # 分片模式需要文件大于 partSize，用更长的片子
        media = self.ensure_media(1)
        batch_id = self.create_batch(n)
        traces = [self.push_candidate(batch_id, media[0], i, interrupt_parts=True) for i in range(n)]

        multipart = [t for t in traces if t.mode == "MULTIPART"]
        ok = [t for t in multipart if not t.error and t.terminal_status == "UPLOADED"]
        self.result.phase_e_resume = {
            "attempts": n,
            "multipart_attempts": len(multipart),
            "resumed_ok": len(ok),
            "success_rate": round(len(ok) / len(multipart), 4) if multipart else None,
            "avg_parts_retried": round(statistics.fmean([t.parts_retried for t in multipart]), 2) if multipart else None,
            "errors": [t.error for t in traces if t.error][:5],
            "note": (
                "若 multipart_attempts = 0，说明测试素材小于 partSize，服务端选了 SIMPLE 模式；"
                "增大 --clip-seconds 重跑才能测到续传路径。"
            ),
        }

    # ══════════════════════════════════════════════════════
    def run(self) -> int:
        self.result.config = vars(self.args) | {"base_url": self.args.base_url}
        self.result.environment = {
            "python": sys.version.split()[0],
            "host_time": now_iso(),
            "admin_key_present": self.has_admin,
        }
        if not self.preflight():
            self._render()
            sys.stdout.flush()
            print("\n预检未通过，未执行负载。", file=sys.stderr)
            return 2
        if self.args.preflight:
            self._render()
            return 0

        self.ensure_project_and_profile()
        self.phase_a_and_b()
        self.phase_c_and_d()
        self.phase_e_resume()
        self.result.finished_at = now_iso()
        self._render()
        return 0

    def _render(self) -> None:
        out_dir = Path(self.args.out)
        out_dir.mkdir(parents=True, exist_ok=True)
        stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        path = out_dir / f"loadtest-{stamp}.json"
        path.write_text(json.dumps(asdict(self.result), ensure_ascii=False, indent=2), encoding="utf-8")
        print(render_summary(self.result))
        print(f"\n结构化结果：{path}")
        sys.stdout.flush()


# ════════════════════════════════════════════════════════════════
def render_summary(r: Result) -> str:
    L: list[str] = []
    add = L.append
    add("=" * 66)
    add("FrameFlow 异步管线压测结果")
    add("=" * 66)

    pf = r.preflight or {}
    add("\n【预检】")
    for c in pf.get("checks", []):
        mark = "✓" if c["ok"] else "✗"
        tag = "（降级）" if c.get("degraded") else ""
        add(f"  {mark} {c['name']}{tag}: {str(c['detail'])[:90]}")
    if not pf.get("ok", False):
        return "\n".join(L)

    a = r.phase_a_throughput
    if a:
        add("\n【A · 吞吐基线】")
        add(f"  视频数 {a['videos_uploaded_ok']}/{a['videos_requested']}（失败 {a['videos_failed']}），并发 {a['concurrency']}")
        add(f"  上传墙钟 {a['upload_wall_seconds']}s   端到端墙钟 {a['total_wall_seconds']}s")
        add(f"  ▶ 端到端吞吐  {a['throughput_videos_per_min']} 视频/分钟")
        add(f"    仅上传吞吐  {a['upload_throughput_videos_per_min']} 视频/分钟")
        if not a["analysis_completed"]:
            add("  ⚠ 分析未在超时内完成，吞吐是下界而非实测值")

    b = r.phase_b_breakdown
    if b:
        add("\n【B · 阶段耗时分解】")
        for stage, s in (b.get("per_stage_latency") or {}).items():
            if s.get("n"):
                p99 = s.get("p99")
                p99s = f"  p99 {p99}s" if p99 is not None else "  p99 样本不足"
                add(f"  {stage:<10} n={s['n']:<4} p50 {s['p50']}s  p90 {s['p90']}s{p99s}  max {s['max']}s")
                if s.get("p99") is not None and not s.get("p99_trustworthy"):
                    add(f"             （n<100，p99 不可信）")
        add(f"  分析等待 {b['analysis_wait_seconds']}s   排名 {b['rank_seconds']}s")
        add(f"  占比 {b['stage_share_percent']}")
        add(f"  注：{b['note']}")

    c = r.phase_c_backpressure
    if c and not c.get("skipped"):
        add("\n【C · 背压】")
        add(f"  投递 {c['uploaded_ok']}/{c['videos']}，并发 {c['concurrency']}")
        add(f"  峰值队列深度 {c['peak_queue_depth']}")
        add(f"  持续吞吐 {c['sustained_videos_per_min']} 视频/分钟")
        s = c.get("instant_rate_summary") or {}
        if s.get("n"):
            add(f"  瞬时速率 p50 {s['p50']}  p90 {s['p90']}  max {s['max']} 视频/分钟")
        if c.get("degraded"):
            add("  ⚠ 无 ADMIN_KEY，队列深度缺失，衰减曲线仅基于完成数")

    d = r.phase_d_dlq
    if d:
        add("\n【D · DLQ】")
        if d.get("measured") is False:
            add(f"  未测量：{d['reason']}")
        else:
            add(f"  {d['dlq_before']} → {d['dlq_after']}（+{d['delta']}），触发率 {d['dlq_rate']}")
            add(f"  注：{d['note']}")

    e = r.phase_e_resume
    if e and not e.get("skipped"):
        add("\n【E · 弱网分片续传】")
        add(f"  尝试 {e['attempts']}，其中 MULTIPART {e['multipart_attempts']}")
        add(f"  ▶ 续传成功率 {e['success_rate']}   平均补传分片 {e['avg_parts_retried']}")
        if not e["multipart_attempts"]:
            add(f"  ⚠ {e['note']}")

    if r.notes:
        add("\n【备注】")
        for n in r.notes:
            add(f"  · {n}")
    add("\n" + "=" * 66)
    return "\n".join(L)


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        description="FrameFlow 异步质检管线压测",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    p.add_argument("--base-url", default=os.environ.get("FF_BASE_URL", DEFAULT_BASE_URL))
    p.add_argument("--videos", type=int, default=20, help="阶段 A 的视频数")
    p.add_argument("--concurrency", type=int, default=4)
    p.add_argument("--backpressure-videos", type=int, default=40, help="阶段 C 的投递量；0 跳过")
    p.add_argument("--backpressure-concurrency", type=int, default=12)
    p.add_argument("--resume-attempts", type=int, default=5, help="阶段 E 的尝试次数；0 跳过")
    p.add_argument("--distinct-clips", type=int, default=5, help="生成多少个不同的素材（其余复用）")
    p.add_argument("--clip-seconds", type=int, default=8)
    p.add_argument("--analysis-timeout", type=float, default=900.0)
    p.add_argument("--poll-interval", type=float, default=2.0)
    p.add_argument("--timeout", type=float, default=120.0, help="单次 HTTP 超时")
    p.add_argument("--media-dir", default="local-loadtest-data/media")
    p.add_argument("--out", default="local-loadtest-data/results")
    p.add_argument("--keep-samples", action="store_true", help="在 JSON 里保留全部采样点")
    p.add_argument("--preflight", action="store_true", help="只做环境核查，不跑负载")
    return p


if __name__ == "__main__":
    sys.exit(LoadTest(build_parser().parse_args()).run())
