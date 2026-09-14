package com.frameflow.learning.product.storage;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.util.StringUtils;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

/**
 * S3 兼容适配器（AWS SDK v2 → MinIO）。
 */
public class S3StorageAdapter implements StoragePort {

    private final StorageProperties props;
    private final S3Client s3;
    private final S3Presigner presigner;
    // ★ 核心：bucket 就绪检查做成惰性重试——应用启动时 MinIO 可能还没起来
    //（本机开发常见），不要因为存储暂时不可用就拒绝整个应用启动；
    // 第一次真正用到存储时再补建 bucket。日志级别 warn 提示真实原因。
    private final AtomicBoolean bucketReady = new AtomicBoolean(false);

    public S3StorageAdapter(StorageProperties props) {
        this.props = props;
        StaticCredentialsProvider creds = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.accessKey(), props.secretKey()));
        // ★ 核心：MinIO 必须开 path-style 访问（bucket 名在路径里而不是
        // 子域名）。云 S3 默认 virtual-host-style（bucket.s3.amazonaws.com），
        // 自建 MinIO 没有泛域名解析，不开这个开关所有请求都会 404。
        S3Configuration pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        this.s3 = S3Client.builder()
                .endpointOverride(validatedEndpoint("endpoint", props.endpoint()))
                .region(Region.of(props.region()))
                .credentialsProvider(creds)
                .serviceConfiguration(pathStyle)
                .build();
        // ★ 核心：容器内部访问地址与浏览器直传地址不是同一个网络视角。
        // Java 在 Compose 里访问 http://minio:9000，但把这个主机名签进
        // presigned URL 后，用户浏览器无法解析。因此 presigner 必须单独使用
        // publicEndpoint；改回单 endpoint 会让容器化直传必然在一端失败。
        String publicEndpoint = StringUtils.hasText(props.publicEndpoint())
                ? props.publicEndpoint() : props.endpoint();
        this.presigner = S3Presigner.builder()
                .endpointOverride(validatedEndpoint("public-endpoint", publicEndpoint))
                .region(Region.of(props.region()))
                .credentialsProvider(creds)
                .serviceConfiguration(pathStyle)
                .build();
    }

    static URI validatedEndpoint(String name, String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("frameflow.storage." + name + " 不能为空");
        }
        URI uri;
        try {
            uri = URI.create(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("frameflow.storage." + name + " 不是合法 URI", e);
        }
        boolean safeScheme = "http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme());
        String path = uri.getPath();
        boolean rootPath = path == null || path.isBlank() || "/".equals(path);
        if (!safeScheme || !StringUtils.hasText(uri.getHost()) || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null || !rootPath) {
            throw new IllegalArgumentException("frameflow.storage." + name
                    + " 必须是无账号、无查询参数且无路径前缀的 http(s) 根地址");
        }
        return uri;
    }

    @Override
    public String presignPut(String objectKey, Duration ttl) {
        ensureBucket();
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(props.bucket()).key(objectKey).build();
        PresignedPutObjectRequest presigned =
                presigner.presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(ttl).putObjectRequest(put).build());
        return presigned.url().toString();
    }

    @Override
    public String presignGet(String objectKey, Duration ttl) {
        ensureBucket();
        var get = software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                .bucket(props.bucket()).key(objectKey).build();
        return presigner.presignGetObject(
                software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
                        .builder().signatureDuration(ttl).getObjectRequest(get).build())
                .url().toString();
    }

    @Override
    public String initiateMultipart(String objectKey) {
        ensureBucket();
        return s3.createMultipartUpload(CreateMultipartUploadRequest.builder()
                        .bucket(props.bucket()).key(objectKey).build())
                .uploadId();
    }

    @Override
    public String presignUploadPart(String objectKey, String uploadId, int partNumber, Duration ttl) {
        ensureBucket();
        UploadPartRequest part = UploadPartRequest.builder()
                .bucket(props.bucket()).key(objectKey)
                .uploadId(uploadId).partNumber(partNumber).build();
        PresignedUploadPartRequest presigned = presigner.presignUploadPart(
                UploadPartPresignRequest.builder()
                        .signatureDuration(ttl).uploadPartRequest(part).build());
        return presigned.url().toString();
    }

    @Override
    public void completeMultipart(String objectKey, String uploadId, List<PartETag> parts) {
        List<CompletedPart> completed = parts.stream()
                .map(p -> CompletedPart.builder()
                        .partNumber(p.partNumber()).eTag(normalizeEtag(p.etag())).build())
                .toList();
        s3.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                .bucket(props.bucket()).key(objectKey).uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(completed).build())
                .build());
    }

    @Override
    public void abortMultipart(String objectKey, String uploadId) {
        s3.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                .bucket(props.bucket()).key(objectKey).uploadId(uploadId).build());
    }

    @Override
    public List<PartETag> listParts(String objectKey, String uploadId) {
        try {
            return s3.listParts(ListPartsRequest.builder()
                            .bucket(props.bucket()).key(objectKey).uploadId(uploadId).build())
                    .parts().stream()
                    .map(p -> new PartETag(p.partNumber(), p.eTag()))
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public HeadInfo head(String objectKey) {
        try {
            HeadObjectResponse resp = s3.headObject(HeadObjectRequest.builder()
                    .bucket(props.bucket()).key(objectKey).build());
            return new HeadInfo(true, resp.contentLength(), resp.eTag());
        } catch (NoSuchKeyException e) {
            return new HeadInfo(false, -1, null);
        }
    }

    @Override
    public byte[] rangeGet(String objectKey, long offset, long length) {
        // HTTP Range 语法是闭区间：bytes=0-15 共 16 字节
        return s3.getObjectAsBytes(GetObjectRequest.builder()
                        .bucket(props.bucket()).key(objectKey)
                        .range("bytes=" + offset + "-" + (offset + length - 1)).build())
                .asByteArray();
    }

    @Override
    public List<String> listKeys(String prefix) {
        ListObjectsV2Response resp = s3.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(props.bucket()).prefix(prefix).build());
        return resp.contents().stream().map(o -> o.key()).toList();
    }

    @Override
    public void put(String objectKey, byte[] bytes) {
        ensureBucket();
        s3.putObject(PutObjectRequest.builder().bucket(props.bucket()).key(objectKey).build(),
                RequestBody.fromBytes(bytes));
    }

    @Override
    public void delete(String objectKey) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(props.bucket()).key(objectKey).build());
    }

    private void ensureBucket() {
        if (bucketReady.get()) {
            return;
        }
        synchronized (this) {
            if (bucketReady.get()) {
                return;
            }
            try {
                s3.headBucket(b -> b.bucket(props.bucket()));
            } catch (Exception e) {
                s3.createBucket(b -> b.bucket(props.bucket()));
            }
            bucketReady.set(true);
        }
    }

    /** S3 的 ETag 常带双引号（"abc"），统一去掉，存储与比较都用裸值。 */
    public static String normalizeEtag(String etag) {
        if (etag == null) {
            return null;
        }
        return etag.startsWith("\"") && etag.endsWith("\"")
                ? etag.substring(1, etag.length() - 1) : etag;
    }
}
