package top.fusb.deploybot.model;

/**
 * 部署执行状态。
 */
public enum DeploymentStatus {
    PENDING,
    RUNNING,
    STOPPED,
    SUCCESS,
    FAILED
}
