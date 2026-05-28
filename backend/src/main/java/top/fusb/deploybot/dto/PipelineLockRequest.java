package top.fusb.deploybot.dto;

import java.time.LocalDateTime;

/**
 * 流水线锁定请求。
 */
public record PipelineLockRequest(
        String reason,
        LocalDateTime startAt,
        LocalDateTime endAt
) {
}
