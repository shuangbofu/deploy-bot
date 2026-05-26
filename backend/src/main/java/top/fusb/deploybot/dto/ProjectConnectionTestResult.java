package top.fusb.deploybot.dto;

/**
 * 项目仓库连通性测试结果。
 */
public record ProjectConnectionTestResult(
        /** 本次测试是否成功。 */
        boolean success,
        /** 测试结果码，前端据此渲染提示文案。 */
        String resultCode,
        /** 实际使用的 Git 地址。 */
        String gitUrl,
        /** 认证方式。 */
        String gitAuthType,
        /** Git 命令输出摘要。 */
        String output
) {
}
