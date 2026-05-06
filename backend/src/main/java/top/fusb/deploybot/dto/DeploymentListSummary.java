package top.fusb.deploybot.dto;

import top.fusb.deploybot.model.DeploymentStatus;

import java.time.LocalDateTime;

public record DeploymentListSummary(
        Long id,
        String branchName,
        String triggeredBy,
        String triggeredByDisplayName,
        String stoppedBy,
        String stoppedByDisplayName,
        DeploymentStatus status,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String logPath,
        String errorMessage,
        PipelineRef pipeline,
        String artifactPath,
        Long rollbackFromDeploymentId,
        Long monitoredPid
) {
    public record PipelineRef(
            Long id,
            String name,
            ProjectRef project
    ) {
    }

    public record ProjectRef(
            Long id,
            String name
    ) {
    }
}
