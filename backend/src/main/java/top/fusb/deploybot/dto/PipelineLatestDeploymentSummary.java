package top.fusb.deploybot.dto;

import top.fusb.deploybot.model.DeploymentStatus;

import java.time.LocalDateTime;

public record PipelineLatestDeploymentSummary(
        Long id,
        Long pipelineId,
        String branchName,
        String triggeredBy,
        DeploymentStatus status,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String logPath,
        Integer buildStepTotal,
        Integer deployStepTotal,
        Boolean monitorProcess,
        Integer startupTimeoutSeconds
) {
}
