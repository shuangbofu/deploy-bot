package top.fusb.deploybot.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求。
 */
public record LoginRequest(
        /** 用户名。 */
        @NotBlank String username,
        /** 密码。 */
        @NotBlank String password
) {
}
