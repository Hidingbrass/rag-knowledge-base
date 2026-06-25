package com.example.aikb.exception;

/**
 * 业务异常。
 *
 * 用于表达“代码正常运行，但业务条件不满足”的情况，
 * 例如知识库不存在、用户无权限、FastAPI 返回空结果等。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
