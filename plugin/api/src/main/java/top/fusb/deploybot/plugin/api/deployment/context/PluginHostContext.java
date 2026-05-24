package top.fusb.deploybot.plugin.api.deployment.context;

/**
 * 描述目标主机与工作空间的标准化信息。
 *
 * @param hostType 主机类型
 * @param remoteHost 是否远程主机
 * @param hostName 主机名称
 * @param workspaceRoot 工作空间根目录
 */
public record PluginHostContext(
        String hostType,
        boolean remoteHost,
        String hostName,
        String workspaceRoot
) {
}
