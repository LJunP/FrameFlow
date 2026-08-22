package com.frameflow.learning.product.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** product 模块接口 DTO（校验注解在入口挡非法输入）。 */
public final class ProductDtos {

    private ProductDtos() {
    }

    // ---------- Project ----------

    public record CreateProjectRequest(
            @NotBlank @Size(max = 96) String name,
            @Size(max = 512) String description) {
    }

    /** lockVersion 必填：没有它就无法检测并发覆盖。 */
    public record UpdateProjectRequest(
            @NotBlank @Size(max = 96) String name,
            @Size(max = 512) String description,
            @NotNull Integer lockVersion) {
    }

    public record ArchiveProjectRequest(@NotNull Integer lockVersion) {
    }

    public record ProjectResponse(Long id, String name, String description, String status,
                                  Long currentBriefId, Integer lockVersion) {
    }

    // ---------- Brief ----------

    public record PublishBriefRequest(@NotBlank @Size(max = 16384) String content) {
    }

    public record BriefResponse(Long id, Long projectId, String content,
                                Long createdBy, String createdAt) {
    }

    // ---------- Quality Profile ----------

    /** spec 以原始 JSON 字符串传入，Service 校验结构后入库。 */
    public record CreateProfileRequest(
            @NotBlank @Size(max = 96) String name,
            @Size(max = 512) String description,
            @NotBlank @Size(max = 16384) String spec) {
    }

    public record PublishVersionRequest(@NotBlank @Size(max = 16384) String spec) {
    }

    public record ProfileResponse(Long id, String name, String description,
                                  Integer latestVersion) {
    }

    /** spec 字段是解析后的 JSON 对象（Jackson 序列化时原样输出）。 */
    public record ProfileVersionResponse(Long id, Long profileId, int versionNo,
                                         com.fasterxml.jackson.databind.JsonNode spec,
                                         Long publishedBy, String publishedAt) {
    }
}
