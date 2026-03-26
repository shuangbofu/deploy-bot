package top.fusb.deploybot.dto;

import top.fusb.deploybot.model.GitAuthType;

/**
 * 系统设置请求体。
 */
public record SystemSettingsRequest(
        /** 系统默认工作空间。 */
        String workspaceRoot,
        /** Git 可执行文件路径。 */
        String gitExecutable,
        /** 全局 Git 认证方式。 */
        GitAuthType gitAuthType,
        /** 全局 Git 用户名。 */
        String gitUsername,
        /** 全局 Git 密码。 */
        String gitPassword,
        /** 系统级 Git SSH 私钥。 */
        String gitSshPrivateKey,
        /** 系统级 Git SSH 公钥。 */
        String gitSshPublicKey,
        /** 系统级 Git known_hosts。 */
        String gitSshKnownHosts,
        /** 系统级主机 SSH 私钥。 */
        String hostSshPrivateKey,
        /** 系统级主机 SSH 公钥。 */
        String hostSshPublicKey
) {
}
