package com.example.aikb.common;

/**
 * 统一 API 响应结构。
 *
 * Controller 不直接返回裸对象，而是统一包一层：
 * - success：是否成功。
 * - message：给前端或调用方看的提示。
 * - data：真正的业务数据。
 *
 * 这样以后 Vue、Postman 或其他服务调用 Spring Boot 时，响应格式稳定。
 */
public record ApiResponse<T>(
        boolean success,
        String message,
        T data
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "ok", data);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
