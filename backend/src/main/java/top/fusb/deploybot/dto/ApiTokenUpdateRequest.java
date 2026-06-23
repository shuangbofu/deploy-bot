package top.fusb.deploybot.dto;

/**
 * 更新 API Token 基础信息的请求体。
 */
public record ApiTokenUpdateRequest(
        String name,
        Boolean enabled
) {
}
