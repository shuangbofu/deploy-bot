package top.fusb.deploybot.config;

import top.fusb.deploybot.dto.Result;
import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorCode;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.kit.JsonKit;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@RestControllerAdvice(basePackages = {
        "top.fusb.deploybot.controller",
        "top.fusb.deploybot.notification.controller"
})
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
        return jsonFailure(
                ex.getErrorCode().getCode(),
                ex.getErrorCode().getDefaultMessage(),
                ex.getErrorSubCode().getSubCode(),
                ex.getMessage()
        );
    }

    @ExceptionHandler(JsonKit.JsonException.class)
    public ResponseEntity<Result<Void>> handleJsonException(JsonKit.JsonException ex, HttpServletRequest request) {
        ErrorSubCode subCode = switch (ex.getErrorType()) {
            case INVALID -> ErrorSubCode.JSON_INVALID;
            case BLANK -> ErrorSubCode.JSON_BLANK;
            case WRITE_FAILED -> ErrorSubCode.JSON_WRITE_FAILED;
        };
        log.warn(
                "API json error on {} {}: [{}:{}] {}",
                request.getMethod(),
                request.getRequestURI(),
                subCode.getErrorCode().getCode(),
                subCode.getSubCode(),
                ex.getMessage(),
                ex
        );
        return jsonFailure(
                subCode.getErrorCode().getCode(),
                subCode.getErrorCode().getDefaultMessage(),
                subCode.getSubCode(),
                subCode.getMessage()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> fieldError.getField() + (fieldError.getDefaultMessage() == null ? "校验失败" : fieldError.getDefaultMessage()))
                .filter(item -> item != null && !item.isBlank())
                .collect(Collectors.joining("；"));
        if (message.isBlank()) {
            message = "请求参数校验失败";
        }
        log.warn("API validation error on {} {}: {}", request.getMethod(), request.getRequestURI(), message, ex);
        return jsonFailure(
                ErrorCode.VALIDATION_ERROR.getCode(),
                ErrorCode.VALIDATION_ERROR.getDefaultMessage(),
                "VAL-000",
                message
        );
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Result<Void>> handleNotFound(NoSuchElementException ex, HttpServletRequest request) {
        log.warn("API resource not found on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        return jsonFailure(
                ErrorCode.RESOURCE_NOT_FOUND.getCode(),
                ErrorCode.RESOURCE_NOT_FOUND.getDefaultMessage(),
                "RES-000",
                "请求的资源不存在。"
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<Void>> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String rootMessage = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        String userMessage = "数据存在关联引用，当前操作无法完成。";
        if (isUniqueViolation(rootMessage)) {
            userMessage = resolveUniqueViolationMessage(rootMessage);
        } else if (rootMessage != null && rootMessage.contains("Value too long for column")) {
            userMessage = "提交内容过长，已超出字段限制，请检查部署变量、脚本或快照内容。";
        }
        log.warn("API data integrity error on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        return jsonFailure(
                ErrorCode.DATA_INTEGRITY_ERROR.getCode(),
                ErrorCode.DATA_INTEGRITY_ERROR.getDefaultMessage(),
                "DATA-000",
                userMessage
        );
    }

    private boolean isUniqueViolation(String rootMessage) {
        if (rootMessage == null || rootMessage.isBlank()) {
            return false;
        }
        String normalized = rootMessage.toLowerCase();
        return normalized.contains("unique index")
                || normalized.contains("unique constraint")
                || normalized.contains("primary key violation")
                || normalized.contains("duplicate key");
    }

    private String resolveUniqueViolationMessage(String rootMessage) {
        return "数据已存在，唯一约束冲突：" + extractConstraintName(rootMessage);
    }

    private String extractConstraintName(String rootMessage) {
        if (rootMessage == null || rootMessage.isBlank()) {
            return "未识别约束";
        }
        int quotedStart = rootMessage.indexOf('"');
        int quotedEnd = quotedStart >= 0 ? rootMessage.indexOf('"', quotedStart + 1) : -1;
        if (quotedStart >= 0 && quotedEnd > quotedStart) {
            return rootMessage.substring(quotedStart + 1, quotedEnd);
        }
        return rootMessage.length() > 120 ? rootMessage.substring(0, 120) : rootMessage;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("API unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return jsonFailure(
                ErrorCode.INTERNAL_ERROR.getCode(),
                ErrorCode.INTERNAL_ERROR.getDefaultMessage(),
                "SYS-000",
                "系统内部异常，请查看服务日志。"
        );
    }

    private ResponseEntity<Result<Void>> jsonFailure(String code, String message, String subCode, String subMessage) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Result.failure(code, message, subCode, subMessage));
    }
}
