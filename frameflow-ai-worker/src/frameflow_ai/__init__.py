"""FrameFlow Select 确定性质检 worker（F4）。

模块地图（阅读顺序）：
  config    环境配置（RabbitMQ/MinIO/API 回写地址/密钥）
  detectors 检测器：probe(ffprobe 元数据) / rules(标准判定) / frames(黑帧·冻结)
  pipeline  一次分析的编排：下载 → 检测 → 组装结果（含 ANALYSIS_ERROR 语义）
  report    结果回写客户端（HTTP + X-Worker-Key）
  consume   RabbitMQ 消费循环（prefetch/ack/重试上限/毒消息兜底）
  main      入口
"""
__version__ = "0.1.0"
