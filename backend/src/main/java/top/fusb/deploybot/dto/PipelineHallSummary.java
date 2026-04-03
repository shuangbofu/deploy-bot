package top.fusb.deploybot.dto;

import java.time.LocalDateTime;

public record PipelineHallSummary(
        Long pipelineId,
        String pipelineName,
        String pipelineDescription,
        String defaultBranch,
        String projectName,
        String templateType,
        String tagsJson,
        Long latestDeploymentId,
        Long latestDeploymentOrder,
        String latestStatus,
        String latestBranchName,
        String latestTriggeredBy,
        String latestTriggeredByDisplayName,
        LocalDateTime latestCreatedAt,
        LocalDateTime latestStartedAt,
        LocalDateTime latestFinishedAt,
        Integer latestProgressPercent,
        String latestProgressText
) {
}
