package top.fusb.deploybot.dto;

import top.fusb.deploybot.model.DeploymentStatus;

import java.time.LocalDateTime;

public record DeploymentStreamStatus(
        Long id,
        DeploymentStatus status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String errorMessage,
        Long monitoredPid,
        String commitSha,
        Integer progressPercent,
        String progressStage,
        Integer progressCurrent,
        Integer progressTotal
) {
}
