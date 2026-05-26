package top.fusb.deploybot.dto;

import top.fusb.deploybot.model.ServiceStatus;

import java.time.LocalDateTime;
import java.util.List;

public record PipelineHallRunningServiceSummary(
        Long serviceId,
        Long pipelineId,
        String pipelineName,
        List<String> importantTags,
        String serviceName,
        String templateType,
        String targetHostName,
        Long currentPid,
        ServiceStatus status,
        LocalDateTime activeSince,
        LocalDateTime lastHeartbeatAt
) {
}
