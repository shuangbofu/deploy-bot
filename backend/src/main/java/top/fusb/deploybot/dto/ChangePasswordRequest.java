package top.fusb.deploybot.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 当前登录用户修改自己密码的请求体。
 */
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank String newPassword
) {
}
