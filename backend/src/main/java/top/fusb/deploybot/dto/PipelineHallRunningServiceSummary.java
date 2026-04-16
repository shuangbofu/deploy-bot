package top.fusb.deploybot.dto;

import top.fusb.deploybot.model.ServiceStatus;

import java.time.LocalDateTime;

public record PipelineHallRunningServiceSummary(
        Long serviceId,
        Long pipelineId,
        String pipelineName,
        String serviceName,
        String templateType,
        String targetHostName,
        Long currentPid,
        ServiceStatus status,
        LocalDateTime activeSince,
        LocalDateTime lastHeartbeatAt
) {
}
