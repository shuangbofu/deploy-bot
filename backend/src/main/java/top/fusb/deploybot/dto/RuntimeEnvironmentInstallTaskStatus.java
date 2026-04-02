package top.fusb.deploybot.dto;

import java.time.LocalDateTime;

public record RuntimeEnvironmentInstallTaskStatus(
        String taskId,
        String presetId,
        String presetName,
        Long hostId,
        String hostName,
        String status,
        String message,
        Long runtimeEnvironmentId,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}
