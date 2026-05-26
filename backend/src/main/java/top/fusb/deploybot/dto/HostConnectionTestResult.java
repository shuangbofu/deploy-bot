package top.fusb.deploybot.dto;

/**
 * 主机连通性测试结果。
 */
public record HostConnectionTestResult(
        /** SSH 测试是否成功。 */
        boolean success,
        /** 测试结果码，前端据此渲染提示文案。 */
        String resultCode,
        /** 命令输出或诊断明细。 */
        String detail,
        /** 远程返回的登录用户。 */
        String remoteUser,
        /** 远程返回的主机名。 */
        String remoteHost,
        /** 测试时使用的工作空间目录。 */
        String workspaceRoot
) {
}
