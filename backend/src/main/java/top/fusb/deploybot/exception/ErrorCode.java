package top.fusb.deploybot.exception;

/**
 * 统一的业务错误码定义，前后端通过 errorCode 协调异常分支处理。
 */
public enum ErrorCode {
    VALIDATION_ERROR("VALIDATION_ERROR", "请求参数不合法"),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "请求的资源不存在"),
    OPERATION_NOT_ALLOWED("OPERATION_NOT_ALLOWED", "当前操作不被允许"),
    CONFLICT("CONFLICT", "当前资源状态冲突，无法完成操作"),
    GIT_ERROR("GIT_ERROR", "Git 操作失败"),
    HOST_ERROR("HOST_ERROR", "主机操作失败"),
    RUNTIME_ENVIRONMENT_ERROR("RUNTIME_ENVIRONMENT_ERROR", "运行环境操作失败"),
    DEPLOYMENT_ERROR("DEPLOYMENT_ERROR", "部署操作失败"),
    SERVICE_ERROR("SERVICE_ERROR", "服务操作失败"),
    DATA_INTEGRITY_ERROR("DATA_INTEGRITY_ERROR", "数据完整性校验失败"),
    IO_ERROR("IO_ERROR", "文件或目录操作失败"),
    INTERNAL_ERROR("INTERNAL_ERROR", "系统内部异常");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
