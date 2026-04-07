package top.fusb.deploybot.dto;

import java.time.LocalDateTime;

public record DashboardServiceSummary(
        Long id,
        String serviceName,
        String status,
        String pipelineName,
        String targetHostName,
        LocalDateTime updatedAt
) {
}
