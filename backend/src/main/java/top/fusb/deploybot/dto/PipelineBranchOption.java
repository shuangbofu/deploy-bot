package top.fusb.deploybot.dto;

/**
 * 流水线部署分支选项。
 */
public record PipelineBranchOption(
        String name,
        boolean defaultBranch,
        boolean recent
) {
}
