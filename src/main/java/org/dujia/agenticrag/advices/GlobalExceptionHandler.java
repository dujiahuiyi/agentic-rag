package org.dujia.agenticrag.advices;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.dujia.agenticrag.domain.Result;
import org.dujia.agenticrag.exceptions.BaseException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public Result<Void> handleBaseException(BaseException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    public Result<Void> handleException(Exception e) {
        log.error("发生异常：", e);
        return Result.fail(e.getMessage() == null ? "系统异常" : e.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResourceFoundException(NoResourceFoundException e) {
        return Result.fail(404, "路径不存在" + e.getResourcePath());
    }

    //study: validation异常报错处理

    /**
     * 处理 @RequestBody 参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidException(MethodArgumentNotValidException e) {
        // 获取第一个错误信息
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        return Result.fail(msg);
    }

    /**
     * 处理 @RequestParam / @PathVariable 校验异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<?> handleConstraintViolation(ConstraintViolationException e) {
        Set<ConstraintViolation<?>> violations = e.getConstraintViolations();
        String msg = violations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(","));
        return Result.fail(msg);
    }

    /**
     * 处理 get 请求表单参数校验
     */
    @ExceptionHandler(BindException.class)
    public Result<?> handleBindException(BindException e) {
        FieldError fieldError = e.getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : "参数错误";
        return Result.fail(msg);
    }

}
