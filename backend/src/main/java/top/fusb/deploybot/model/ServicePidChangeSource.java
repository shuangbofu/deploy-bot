package top.fusb.deploybot.model;

/**
 * 服务 PID 变化来源。
 */
public enum ServicePidChangeSource {
    DEPLOYMENT_CONFIRMED,
    PRE_DEPLOY_STOP,
    MANUAL_BIND,
    MANUAL_STOP,
    HEARTBEAT_STOPPED,
    HEARTBEAT_RECOVERED
}
