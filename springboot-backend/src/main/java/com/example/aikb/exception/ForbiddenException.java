package com.example.aikb.exception;

/**
 * 权限不足异常。
 *
 * BusinessException 表示普通业务条件不满足，例如知识库不存在、文件类型不支持。
 * ForbiddenException 专门表示“用户身份有效，但没有权限访问这个资源”，
 * 全局异常处理器会把它转换成 HTTP 403 Forbidden。
 */
public class ForbiddenException extends BusinessException {

    public ForbiddenException(String message) {
        super(message);
    }
}
