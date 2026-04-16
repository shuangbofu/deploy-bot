package top.fusb.deploybot.dto;

import java.time.LocalDateTime;

public record PipelineHallRunningServiceSummary(
        Long serviceId,
        Long pipelineId,
        String pipelineName,
        String serviceName,
        String templateType,
        String targetHostName,
        Long currentPid,
        LocalDateTime activeSince,
        LocalDateTime lastHeartbeatAt
) {
}
