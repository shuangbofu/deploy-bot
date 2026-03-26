package top.fusb.deploybot.dto;

/**
 * 主机连通性测试结果。
 */
public record HostConnectionTestResult(
        /** SSH 测试是否成功。 */
        boolean success,
        /** 测试结果说明。 */
        String message,
        /** 远程返回的登录用户。 */
        String remoteUser,
        /** 远程返回的主机名。 */
        String remoteHost,
        /** 测试时使用的工作空间目录。 */
        String workspaceRoot
) {
}
