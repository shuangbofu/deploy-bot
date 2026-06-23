package top.fusb.deploybot.dto;

import top.fusb.deploybot.model.ApiTokenScope;

import java.time.LocalDateTime;
import java.util.List;

/**
 * API Token 列表展示摘要。
 */
public record ApiTokenSummary(
        Long id,
        String name,
        String tokenPrefix,
        Long userId,
        String username,
        String displayName,
        List<ApiTokenScope> scopes,
        LocalDateTime expiresAt,
        LocalDateTime lastUsedAt,
        String lastUsedIp,
        Boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime revokedAt
) {
}
