package top.fusb.deploybot.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PipelineHallSummary(
        Long pipelineId,
        String pipelineName,
        String pipelineDescription,
        String defaultBranch,
        String projectName,
        String templateType,
        List<String> tags,
        List<String> importantTags,
        Boolean deploymentRestricted,
        String restrictionName,
        String restrictionReason,
        Long latestDeploymentId,
        Long latestDeploymentOrder,
        String latestStatus,
        String latestBranchName,
        String latestTriggeredBy,
        String latestTriggeredByDisplayName,
        String latestTriggeredByAvatar,
        LocalDateTime latestCreatedAt,
        LocalDateTime latestStartedAt,
        LocalDateTime latestFinishedAt,
        Integer latestProgressPercent,
        String latestProgressStage,
        Integer latestProgressCurrent,
        Integer latestProgressTotal,
        Long version,
        Boolean favorited
) {
}
