package top.fusb.deploybot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import top.fusb.deploybot.model.ApiTokenScope;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建 API Token 的请求体。
 */
public record ApiTokenCreateRequest(
        @NotBlank(message = "Token 名称不能为空")
        String name,
        Long userId,
        @NotEmpty(message = "请至少选择一个权限范围")
        List<ApiTokenScope> scopes,
        LocalDateTime expiresAt
) {
}
