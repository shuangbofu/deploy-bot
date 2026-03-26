package top.fusb.deploybot.config;

import top.fusb.deploybot.dto.Result;
import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        log.warn(
                "API business error on {} {}: [{}:{}] {}",
                request.getMethod(),
                request.getRequestURI(),
                ex.getErrorCode().getCode(),
                ex.getErrorSubCode().getSubCode(),
                ex.getMessage(),
                ex
        );
        return ResponseEntity.ok(Result.failure(
                ex.getErrorCode().getCode(),
                ex.getErrorCode().getDefaultMessage(),
                ex.getErrorSubCode().getSubCode(),
                ex.getMessage()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .filter(item -> item != null && !item.isBlank())
                .collect(Collectors.joining("；"));
        if (message.isBlank()) {
            message = "请求参数校验失败";
        }
        log.warn("API validation error on {} {}: {}", request.getMethod(), request.getRequestURI(), message, ex);
        return ResponseEntity.ok(Result.failure(
                ErrorCode.VALIDATION_ERROR.getCode(),
                ErrorCode.VALIDATION_ERROR.getDefaultMessage(),
                "VAL-000",
                message
        ));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Result<Void>> handleNotFound(NoSuchElementException ex, HttpServletRequest request) {
        log.warn("API resource not found on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.ok(Result.failure(
                ErrorCode.RESOURCE_NOT_FOUND.getCode(),
                ErrorCode.RESOURCE_NOT_FOUND.getDefaultMessage(),
                "RES-000",
                "请求的资源不存在。"
        ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<Void>> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("API data integrity error on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.ok(Result.failure(
                ErrorCode.DATA_INTEGRITY_ERROR.getCode(),
                ErrorCode.DATA_INTEGRITY_ERROR.getDefaultMessage(),
                "DATA-000",
                "数据存在关联引用，当前操作无法完成。"
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("API unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.ok(Result.failure(
                ErrorCode.INTERNAL_ERROR.getCode(),
                ErrorCode.INTERNAL_ERROR.getDefaultMessage(),
                "SYS-000",
                "系统内部异常，请查看服务日志。"
        ));
    }
}
