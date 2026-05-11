package top.fusb.deploybot.dto;

import top.fusb.deploybot.model.ServicePidChangeSource;
import top.fusb.deploybot.model.ServiceStatus;

import java.time.LocalDateTime;

public record ServicePidHistorySummary(
        Long id,
        Long serviceId,
        Long deploymentId,
        Long previousPid,
        Long currentPid,
        ServiceStatus previousStatus,
        ServiceStatus currentStatus,
        ServicePidChangeSource changeSource,
        String note,
        LocalDateTime createdAt
) {
}
