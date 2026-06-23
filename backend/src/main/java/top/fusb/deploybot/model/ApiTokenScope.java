package top.fusb.deploybot.model;

/**
 * API Token 可调用的平台能力范围。
 */
public enum ApiTokenScope {
    READ,
    PROJECT_WRITE,
    TEMPLATE_WRITE,
    PIPELINE_WRITE,
    DEPLOYMENT_RUN,
    ADMIN
}
