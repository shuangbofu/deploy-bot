package top.fusb.deploybot.exception;

/**
 * 统一业务异常，所有可预期的应用层失败都通过错误码表达。
 */
public class BusinessException extends RuntimeException {

    private final ErrorSubCode errorSubCode;

    public BusinessException(ErrorSubCode errorSubCode) {
        super(errorSubCode.getMessage());
        this.errorSubCode = errorSubCode;
    }

    public BusinessException(ErrorSubCode errorSubCode, Throwable cause) {
        super(errorSubCode.getMessage(), cause);
        this.errorSubCode = errorSubCode;
    }

    public BusinessException(ErrorSubCode errorSubCode, String detailMessage) {
        super(detailMessage == null || detailMessage.isBlank() ? errorSubCode.getMessage() : errorSubCode.getMessage() + " " + detailMessage);
        this.errorSubCode = errorSubCode;
    }

    public BusinessException(ErrorSubCode errorSubCode, String detailMessage, Throwable cause) {
        super(detailMessage == null || detailMessage.isBlank() ? errorSubCode.getMessage() : errorSubCode.getMessage() + " " + detailMessage, cause);
        this.errorSubCode = errorSubCode;
    }

    public ErrorSubCode getErrorSubCode() {
        return errorSubCode;
    }

    public ErrorCode getErrorCode() {
        return errorSubCode.getErrorCode();
    }
}
