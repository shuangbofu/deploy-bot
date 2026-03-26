package top.fusb.deploybot.dto;

import top.fusb.deploybot.model.GitAuthType;
import jakarta.validation.constraints.NotBlank;

/**
 * 项目新增或编辑请求体。
 */
public record ProjectRequest(
        /** 项目名称。 */
        @NotBlank String name,
        /** 项目说明。 */
        String description,
        /** 仓库地址。 */
        @NotBlank String gitUrl,
        /** Git 认证方式。 */
        GitAuthType gitAuthType,
        /** HTTP 认证用户名。 */
        String gitUsername,
        /** HTTP 认证密码。 */
        String gitPassword,
        /** 项目级 SSH 私钥。 */
        String gitSshPrivateKey,
        /** 项目级 SSH 公钥。 */
        String gitSshPublicKey,
        /** 项目级 known_hosts 内容。 */
        String gitSshKnownHosts
) {
}
