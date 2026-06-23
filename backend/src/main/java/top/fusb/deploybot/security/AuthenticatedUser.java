package top.fusb.deploybot.security;

import top.fusb.deploybot.model.ApiTokenScope;
import top.fusb.deploybot.model.UserRole;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 当前请求里的登录用户快照。
 */
public record AuthenticatedUser(
        Long id,
        String username,
        String displayName,
        UserRole role,
        Long apiTokenId,
        Set<ApiTokenScope> apiTokenScopes
) {
    public static AuthenticatedUser loginSession(Long id, String username, String displayName, UserRole role) {
        return new AuthenticatedUser(id, username, displayName, role, null, Set.of());
    }

    public static AuthenticatedUser apiToken(Long id, String username, String displayName, UserRole role, Long apiTokenId, List<ApiTokenScope> scopes) {
        EnumSet<ApiTokenScope> normalized = EnumSet.noneOf(ApiTokenScope.class);
        if (scopes != null) {
            normalized.addAll(scopes);
        }
        return new AuthenticatedUser(id, username, displayName, role, apiTokenId, Set.copyOf(normalized));
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    public boolean isApiToken() {
        return apiTokenId != null;
    }

    public boolean hasApiScope(ApiTokenScope scope) {
        return apiTokenScopes.contains(ApiTokenScope.ADMIN) || apiTokenScopes.contains(scope);
    }
}
