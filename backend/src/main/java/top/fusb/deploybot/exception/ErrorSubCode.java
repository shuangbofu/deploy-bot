package top.fusb.deploybot.exception;

/**
 * 细分业务错误枚举，负责具体场景编号与展示文案。
 */
public enum ErrorSubCode {
    AUTH_REQUIRED("AUTH-001", "当前请求未登录。", ErrorCode.AUTH_ERROR),
    AUTH_LOGIN_FAILED("AUTH-002", "用户名或密码错误。", ErrorCode.AUTH_ERROR),
    AUTH_TOKEN_INVALID("AUTH-003", "登录态已失效，请重新登录。", ErrorCode.AUTH_ERROR),
    AUTH_ADMIN_REQUIRED("AUTH-004", "当前账号没有管理员权限。", ErrorCode.AUTH_ERROR),
    AUTH_USER_DISABLED("AUTH-005", "当前账号已被停用。", ErrorCode.AUTH_ERROR),
    AUTH_CURRENT_PASSWORD_INCORRECT("AUTH-006", "当前密码不正确。", ErrorCode.AUTH_ERROR),
    AUTH_NEW_PASSWORD_TOO_SHORT("AUTH-007", "新密码至少需要 8 位。", ErrorCode.AUTH_ERROR),
    AUTH_NEW_PASSWORD_SAME_AS_OLD("AUTH-008", "新密码不能与当前密码相同。", ErrorCode.AUTH_ERROR),
    USER_NOT_FOUND("RES-USER-001", "用户不存在。", ErrorCode.RESOURCE_NOT_FOUND),
    USERNAME_ALREADY_EXISTS("CONFLICT-USER-001", "用户名已存在。", ErrorCode.CONFLICT),
    LAST_ADMIN_DELETE_FORBIDDEN("BIZ-USER-001", "至少需要保留一个管理员账号。", ErrorCode.OPERATION_NOT_ALLOWED),
    USER_AVATAR_INVALID("VAL-USER-001", "头像必须是图片文件。", ErrorCode.VALIDATION_ERROR),
    USER_AVATAR_UPLOAD_FAILED("IO-USER-001", "上传头像失败。", ErrorCode.IO_ERROR),

    PROJECT_NOT_FOUND("RES-PROJECT-001", "项目不存在。", ErrorCode.RESOURCE_NOT_FOUND),
    TEMPLATE_NOT_FOUND("RES-TEMPLATE-001", "模板不存在。", ErrorCode.RESOURCE_NOT_FOUND),
    PIPELINE_NOT_FOUND("RES-PIPELINE-001", "流水线不存在。", ErrorCode.RESOURCE_NOT_FOUND),
    HOST_NOT_FOUND("RES-HOST-001", "主机不存在。", ErrorCode.RESOURCE_NOT_FOUND),
    DEPLOYMENT_NOT_FOUND("RES-DEPLOYMENT-001", "部署记录不存在。", ErrorCode.RESOURCE_NOT_FOUND),
    SERVICE_NOT_FOUND("RES-SERVICE-001", "服务不存在。", ErrorCode.RESOURCE_NOT_FOUND),
    RUNTIME_ENVIRONMENT_NOT_FOUND("RES-ENV-001", "运行环境不存在。", ErrorCode.RESOURCE_NOT_FOUND),
    PRESET_NOT_FOUND("RES-PRESET-001", "运行环境预置不存在。", ErrorCode.RESOURCE_NOT_FOUND),

    TEMPLATE_NAME_REQUIRED("VAL-TEMPLATE-001", "模板名称不能为空。", ErrorCode.VALIDATION_ERROR),
    TEMPLATE_VARIABLES_REQUIRED("VAL-TEMPLATE-002", "模板变量定义不能为空。", ErrorCode.VALIDATION_ERROR),
    TEMPLATE_BUILD_SCRIPT_REQUIRED("VAL-TEMPLATE-003", "构建脚本不能为空。", ErrorCode.VALIDATION_ERROR),
    JSON_INVALID("VAL-JSON-001", "JSON 内容不合法。", ErrorCode.VALIDATION_ERROR),
    JSON_BLANK("VAL-JSON-002", "JSON 内容不能为空。", ErrorCode.VALIDATION_ERROR),
    JSON_WRITE_FAILED("VAL-JSON-003", "JSON 序列化失败。", ErrorCode.VALIDATION_ERROR),

    BUILT_IN_LOCAL_HOST_TYPE_CHANGE_FORBIDDEN("BIZ-HOST-001", "系统内置的本机记录不能改成远程主机。", ErrorCode.OPERATION_NOT_ALLOWED),
    BUILT_IN_LOCAL_HOST_DELETE_FORBIDDEN("BIZ-HOST-002", "系统内置的本机记录不能删除。", ErrorCode.OPERATION_NOT_ALLOWED),
    SSH_ONLY_REMOTE_SCRIPT("BIZ-HOST-003", "只有 SSH 主机支持远程命令执行。", ErrorCode.OPERATION_NOT_ALLOWED),
    PIPELINE_ENV_MUST_BE_LOCAL("BIZ-PIPELINE-001", "流水线构建环境只能选择本机下的运行环境。", ErrorCode.OPERATION_NOT_ALLOWED),
    PIPELINE_RUNTIME_ENV_MUST_BE_JAVA("BIZ-PIPELINE-002", "目标主机运行环境必须选择 Java。", ErrorCode.OPERATION_NOT_ALLOWED),
    PIPELINE_RUNTIME_ENV_MUST_MATCH_TARGET_HOST("BIZ-PIPELINE-003", "目标主机运行 Java 环境必须来自当前目标主机。", ErrorCode.OPERATION_NOT_ALLOWED),
    DEPLOYMENT_NOT_STOPPABLE("BIZ-DEPLOYMENT-001", "只有执行中的部署才能停止。", ErrorCode.OPERATION_NOT_ALLOWED),
    RUNNING_DEPLOYMENT_CANNOT_ROLLBACK("BIZ-DEPLOYMENT-002", "执行中的部署不能直接回滚，请先等待结束或停止任务。", ErrorCode.OPERATION_NOT_ALLOWED),
    DEPLOYMENT_ARTIFACT_MISSING("BIZ-DEPLOYMENT-003", "这条部署记录没有可复用的构建产物，暂时无法重新发布。", ErrorCode.OPERATION_NOT_ALLOWED),
    DEPLOYMENT_ROLLBACK_ONLY_SUCCESS("BIZ-DEPLOYMENT-004", "只有成功的部署记录才能重新发布此版本。", ErrorCode.OPERATION_NOT_ALLOWED),

    PIPELINE_HAS_RUNNING_DEPLOYMENT("CONFLICT-PIPELINE-001", "当前流水线已有执行中的部署，请先停止后再发起新的部署。", ErrorCode.CONFLICT),
    PIPELINE_HAS_DEPLOYMENTS("CONFLICT-PIPELINE-002", "该流水线下已有部署记录，无法直接删除。请先清理关联部署记录或保留流水线。", ErrorCode.CONFLICT),
    HOST_HAS_PIPELINES("CONFLICT-HOST-001", "仍有流水线绑定到这台主机，不能删除。", ErrorCode.CONFLICT),
    HOST_HAS_ENVIRONMENTS("CONFLICT-HOST-002", "仍有运行环境归属这台主机，不能删除。", ErrorCode.CONFLICT),

    PROJECT_GIT_CONNECTIVITY_FAILED("BIZ-PROJECT-001", "项目仓库连通性测试失败。", ErrorCode.OPERATION_NOT_ALLOWED),

    TEMPLATE_BUILD_SCRIPT_MISSING("DEP-TEMPLATE-001", "模板缺少构建脚本。", ErrorCode.DEPLOYMENT_ERROR),
    DEPLOYMENT_MONITORED_PROCESS_NOT_RUNNING("DEP-RUNTIME-001", "服务启动失败，未检测到可用进程。", ErrorCode.DEPLOYMENT_ERROR),
    SERVICE_NO_DEPLOYMENT_TO_START("SVC-START-001", "当前服务没有可用于启动的历史部署记录。", ErrorCode.SERVICE_ERROR),
    REMOTE_SERVICE_STOP_FAILED("SVC-STOP-001", "远程停止服务失败。", ErrorCode.SERVICE_ERROR),

    REMOTE_EXECUTION_FAILED("HOST-EXEC-001", "远程执行失败。", ErrorCode.HOST_ERROR),
    LOCAL_RESOURCE_PREVIEW_FAILED("HOST-RESOURCE-001", "读取本机资源信息失败。", ErrorCode.HOST_ERROR),
    REMOTE_DIRECTORY_PREPARE_FAILED("HOST-DEPLOY-001", "远程主机目录准备失败。", ErrorCode.HOST_ERROR),
    REMOTE_ARTIFACT_SYNC_FAILED("HOST-DEPLOY-002", "构建产物同步到远程主机失败。", ErrorCode.HOST_ERROR),

    PRESET_DOWNLOAD_FAILED("ENV-INSTALL-001", "下载运行环境失败。", ErrorCode.RUNTIME_ENVIRONMENT_ERROR),
    PRESET_EXTRACT_FAILED("ENV-INSTALL-002", "解压运行环境失败。", ErrorCode.RUNTIME_ENVIRONMENT_ERROR),
    PRESET_EXTRACT_ROOT_MISSING("ENV-INSTALL-003", "未找到解压后的目录。", ErrorCode.RUNTIME_ENVIRONMENT_ERROR),
    PRESET_REMOTE_INSTALL_FAILED("ENV-INSTALL-004", "远程主机安装预置环境失败。", ErrorCode.RUNTIME_ENVIRONMENT_ERROR),
    PRESET_ARCHIVE_INVALID("ENV-INSTALL-005", "下载结果不是有效压缩包。", ErrorCode.RUNTIME_ENVIRONMENT_ERROR),
    PRESET_ARCHIVE_VALIDATE_FAILED("ENV-INSTALL-006", "校验下载文件失败。", ErrorCode.RUNTIME_ENVIRONMENT_ERROR),
    PRESET_CONFIG_READ_FAILED("ENV-PRESET-001", "读取运行环境预置配置失败。", ErrorCode.RUNTIME_ENVIRONMENT_ERROR),

    SSH_KEY_GENERATE_FAILED("IO-SSH-001", "生成 SSH 密钥对失败。", ErrorCode.IO_ERROR);

    private final String subCode;
    private final String message;
    private final ErrorCode errorCode;

    ErrorSubCode(String subCode, String message, ErrorCode errorCode) {
        this.subCode = subCode;
        this.message = message;
        this.errorCode = errorCode;
    }

    public String getSubCode() {
        return subCode;
    }

    public String getMessage() {
        return message;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
