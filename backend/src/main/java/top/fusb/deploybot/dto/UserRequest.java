package top.fusb.deploybot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import top.fusb.deploybot.model.UserRole;

/**
 * 用户新增或编辑请求。
 */
public record UserRequest(
        /** 用户名。 */
        @NotBlank String username,
        /** 展示名称。 */
        @NotBlank String displayName,
        /** 密码，新建必填，编辑时留空表示不修改。 */
        String password,
        /** 角色。 */
        @NotNull UserRole role,
        /** 是否启用。 */
        @NotNull Boolean enabled
) {
}
