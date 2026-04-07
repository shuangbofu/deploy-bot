package top.fusb.deploybot.dto;

import top.fusb.deploybot.model.DeploymentStatus;

import java.time.LocalDateTime;

public record DashboardDeploymentSummary(
        Long id,
        String pipelineName,
        String projectName,
        String branchName,
        String triggeredBy,
        String triggeredByDisplayName,
        DeploymentStatus status,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Integer progressPercent,
        String progressText
) {
}
