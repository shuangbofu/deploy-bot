package top.fusb.deploybot.dto;

/**
 * 登录成功响应。
 */
public record LoginResponse(
        /** 登录令牌。 */
        String token,
        /** 当前用户。 */
        UserProfile user
) {
}
