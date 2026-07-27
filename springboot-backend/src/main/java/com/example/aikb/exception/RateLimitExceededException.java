package com.example.aikb.exception;

/**
 * 表示用户调用 AI 接口过于频繁。
 *
 * 这个异常会被全局异常处理器转换成 HTTP 429。
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
