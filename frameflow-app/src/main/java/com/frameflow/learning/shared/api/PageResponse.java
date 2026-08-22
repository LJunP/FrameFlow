package com.frameflow.learning.shared.api;

import java.util.List;

/**
 * 统一分页响应。F2 起所有列表接口都用这个形状，前端只需一套分页逻辑。
 *
 * @param items  当前页数据
 * @param page   页码（从 0 开始）
 * @param size   每页条数
 * @param total  总条数（用于算总页数）
 */
public record PageResponse<T>(List<T> items, int page, int size, long total) {

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long total) {
        return new PageResponse<>(items, page, size, total);
    }
}
