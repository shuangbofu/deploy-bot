package top.fusb.deploybot.security;

import top.fusb.deploybot.model.UserRole;

/**
 * 当前请求里的登录用户快照。
 */
public record AuthenticatedUser(
        Long id,
        String username,
        String displayName,
        UserRole role
) {
    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
