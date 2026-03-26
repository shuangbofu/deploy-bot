package top.fusb.deploybot.model;

/**
 * 远程主机 SSH 登录认证方式。
 */
public enum HostSshAuthType {
    PASSWORD,
    PRIVATE_KEY,
    SYSTEM_KEY_PAIR
}
