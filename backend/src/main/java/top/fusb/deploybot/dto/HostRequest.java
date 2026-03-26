package top.fusb.deploybot.dto;

import top.fusb.deploybot.model.HostType;
import top.fusb.deploybot.model.HostSshAuthType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 主机新增或编辑请求体。
 */
public record HostRequest(
        /** 主机名称。 */
        @NotBlank String name,
        /** 主机类型，本机或 SSH。 */
        @NotNull HostType type,
        /** 主机说明。 */
        String description,
        /** SSH 主机地址。 */
        String hostname,
        /** SSH 端口。 */
        Integer port,
        /** SSH 用户名。 */
        String username,
        /** SSH 认证方式。 */
        HostSshAuthType sshAuthType,
        /** 密码模式下的 SSH 密码。 */
        String sshPassword,
        /** 私钥模式下的私钥内容。 */
        String sshPrivateKey,
        /** 私钥口令。 */
        String sshPassphrase,
        /** 主机工作空间目录。 */
        String workspaceRoot,
        /** SSH known_hosts 内容。 */
        String sshKnownHosts,
        /** 主机是否启用。 */
        Boolean enabled
) {
}
