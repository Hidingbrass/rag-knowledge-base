package com.example.aikb.exception;

import com.example.aikb.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理。
 *
 * Controller 里不需要到处 try/catch。
 * 业务异常、参数校验异常和未知异常会在这里统一转换成固定响应结构。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleForbiddenException(ForbiddenException exception) {
        log.warn("Forbidden request: {}", exception.getMessage());
        return ApiResponse.fail(exception.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleUnauthorizedException(UnauthorizedException exception) {
        log.warn("Unauthorized request: {}", exception.getMessage());
        return ApiResponse.fail(exception.getMessage());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ApiResponse<Void> handleRateLimitExceededException(RateLimitExceededException exception) {
        log.warn("Rate limited request: {}", exception.getMessage());
        return ApiResponse.fail(exception.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBusinessException(BusinessException exception) {
        log.warn("Business request failed: {}", exception.getMessage());
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

        log.warn("Validation request failed: {}", message);
        return ApiResponse.fail(message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMissingRequestParameterException(MissingServletRequestParameterException exception) {
        log.warn("Missing request parameter: {}", exception.getParameterName());
        return ApiResponse.fail("请求参数缺失: " + exception.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException exception) {
        log.warn("Request parameter type mismatch: {}", exception.getName());
        return ApiResponse.fail("请求参数类型不合法: " + exception.getName());
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
        log.debug("Static resource not found: {}", exception.getResourcePath());
        return ApiResponse.fail("资源不存在：" + exception.getResourcePath());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception exception) {
        log.error("Unhandled server error", exception);
        return ApiResponse.fail("服务器内部错误：" + exception.getMessage());
    }
}
