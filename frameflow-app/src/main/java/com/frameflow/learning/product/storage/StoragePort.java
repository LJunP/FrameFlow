package com.frameflow.learning.product.storage;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 对象存储端口（hexagonal 架构的 Port）。
 *
 * ★ 核心：业务代码只依赖这个接口，不依赖 AWS SDK——将来换 MinIO 集群、
 * 云 S3、或测试里换本地容器，业务代码一行不改。这也是 Worker（F4）
 * 与主应用共享的存储语义。
 */
public interface StoragePort {

    /** 预签名单次 PUT 直传 URL（客户端带文件体直接 PUT 到此地址）。 */
    String presignPut(String objectKey, Duration ttl);

    /** 预签名 GET 下载/播放 URL（F8 审阅页的视频源）。 */
    String presignGet(String objectKey, Duration ttl);

    /** 发起分片上传会话，返回 S3 uploadId。 */
    String initiateMultipart(String objectKey);

    /** 为指定分片号预签名上传 URL（客户端逐片 PUT，拿回 ETag）。 */
    String presignUploadPart(String objectKey, String uploadId, int partNumber, Duration ttl);

    /** 完成分片上传（parts 必须按分片号升序且从 1 连续）。 */
    void completeMultipart(String objectKey, String uploadId, List<PartETag> parts);

    /** 放弃分片会话（服务端错误回滚时清理用）。 */
    void abortMultipart(String objectKey, String uploadId);

    /** 已上传分片（断点续传：跳过这些 partNumber）。会话不存在时返回空列表。 */
    List<PartETag> listParts(String objectKey, String uploadId);

    /** 探测对象：不存在时 exists=false。 */
    HeadInfo head(String objectKey);

    /** 读取对象的一段字节（入口校验媒体签名用，只读前几十字节）。 */
    byte[] rangeGet(String objectKey, long offset, long length);

    /** 列出指定前缀下的对象 key（对账找孤儿对象用）。 */
    List<String> listKeys(String prefix);

    /** 测试与运维用：直接写入小对象。 */
    void put(String objectKey, byte[] bytes);

    void delete(String objectKey);

    record HeadInfo(boolean exists, long contentLength, String etag) {
    }

    record PartETag(int partNumber, String etag) {
    }
}
