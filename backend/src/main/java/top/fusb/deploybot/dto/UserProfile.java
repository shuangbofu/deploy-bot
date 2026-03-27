package top.fusb.deploybot.dto;

import top.fusb.deploybot.model.UserRole;

/**
 * 当前登录用户信息。
 */
public record UserProfile(
        /** 用户主键。 */
        Long id,
        /** 登录用户名。 */
        String username,
        /** 展示名称。 */
        String displayName,
        /** 用户角色。 */
        UserRole role,
        /** 是否启用。 */
        Boolean enabled
) {
}
