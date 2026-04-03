package top.fusb.deploybot.dto;

import java.time.LocalDateTime;

public record UserRecentPipelineSummary(
        Long pipelineId,
        String pipelineName,
        String projectName,
        String defaultBranch,
        String templateType,
        long count,
        LocalDateTime latestDeploymentAt
) {
}
