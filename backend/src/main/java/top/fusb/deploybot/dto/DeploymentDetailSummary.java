package top.fusb.deploybot.dto;

import top.fusb.deploybot.model.DeploymentStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record DeploymentDetailSummary(
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
        Integer progressPercent,
        String progressStage,
        Integer progressCurrent,
        Integer progressTotal,
        String pipelineName,
        List<String> pipelineImportantTags,
        String projectName,
        DeploymentListSummary.PipelineRef pipeline,
        String artifactPath,
        Map<String, Object> executionSnapshot,
        Long rollbackFromDeploymentId,
        Long monitoredPid,
        String commitSha,
        Map<String, Object> gitDiffSnapshot
) {
}
