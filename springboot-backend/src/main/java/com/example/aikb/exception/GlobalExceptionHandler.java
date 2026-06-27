package com.example.aikb.exception;

import com.example.aikb.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理。
 *
 * Controller 里不需要到处 try/catch。
 * 业务异常、参数校验异常和未知异常会在这里统一转换成固定响应结构。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleForbiddenException(ForbiddenException exception) {
        return ApiResponse.fail(exception.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBusinessException(BusinessException exception) {
        return ApiResponse.fail(exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("请求参数不合法");

        return ApiResponse.fail(message);
    }

    /**
     * 静态资源不存在，例如浏览器自动请求 /favicon.ico。
     *
     * 这类问题不是服务器内部错误，应该返回 404。
     * 否则前端 Network 面板里会看到误导性的 500。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNoResourceFoundException(NoResourceFoundException exception) {
        return ApiResponse.fail("资源不存在：" + exception.getResourcePath());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception exception) {
        return ApiResponse.fail("服务器内部错误：" + exception.getMessage());
    }
}
