package top.fusb.deploybot.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.model.ApiTokenScope;
import top.fusb.deploybot.security.AdminOnly;
import top.fusb.deploybot.security.AuthContextHolder;
import top.fusb.deploybot.security.AuthenticatedUser;
import top.fusb.deploybot.service.AuthService;

/**
 * 统一接口鉴权拦截器。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {
    private final AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!request.getRequestURI().startsWith("/api/")) {
            return true;
        }
        if (request.getRequestURI().startsWith("/api/auth/login")) {
            return true;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BusinessException(ErrorSubCode.AUTH_REQUIRED);
        }
        String token = authorization.substring("Bearer ".length()).trim();
        AuthenticatedUser user = authService.authenticate(token, request.getRemoteAddr());
        AuthContextHolder.set(user);
        try {
            if (user.isApiToken() && !apiTokenAllowed(user, request)) {
                throw new BusinessException(ErrorSubCode.AUTH_API_TOKEN_SCOPE_DENIED);
            }
            if (handler instanceof HandlerMethod handlerMethod) {
                boolean adminOnly = handlerMethod.hasMethodAnnotation(AdminOnly.class)
                        || handlerMethod.getBeanType().isAnnotationPresent(AdminOnly.class);
                if (adminOnly && !user.isApiToken() && !user.isAdmin()) {
                    throw new BusinessException(ErrorSubCode.AUTH_ADMIN_REQUIRED);
                }
            }
            return true;
        } catch (RuntimeException ex) {
            AuthContextHolder.clear();
            throw ex;
        }
    }

    private boolean apiTokenAllowed(AuthenticatedUser user, HttpServletRequest request) {
        if (user.hasApiScope(ApiTokenScope.ADMIN)) {
            return true;
        }
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("GET".equals(method) && apiTokenCanRead(path)) {
            return user.hasApiScope(ApiTokenScope.READ);
        }
        if (user.hasApiScope(ApiTokenScope.PROJECT_WRITE) && apiTokenCanWriteProject(method, path)) {
            return true;
        }
        if (user.hasApiScope(ApiTokenScope.TEMPLATE_WRITE) && apiTokenCanWriteTemplate(method, path)) {
            return true;
        }
        if (user.hasApiScope(ApiTokenScope.PIPELINE_WRITE) && apiTokenCanWritePipeline(method, path)) {
            return true;
        }
        return user.hasApiScope(ApiTokenScope.DEPLOYMENT_RUN) && apiTokenCanRunDeployment(method, path);
    }

    private boolean apiTokenCanRead(String path) {
        return path.startsWith("/api/projects")
                || path.startsWith("/api/hosts")
                || path.startsWith("/api/runtime-environments")
                || path.startsWith("/api/deployment-plugins")
                || path.startsWith("/api/templates")
                || path.startsWith("/api/pipelines")
                || path.startsWith("/api/deployments")
                || path.startsWith("/api/services");
    }

    private boolean apiTokenCanWriteProject(String method, String path) {
        return ("POST".equals(method) && "/api/projects".equals(path))
                || ("PUT".equals(method) && path.matches("/api/projects/\\d+"))
                || ("POST".equals(method) && "/api/projects/test-connection".equals(path));
    }

    private boolean apiTokenCanWritePipeline(String method, String path) {
        return ("POST".equals(method) && "/api/pipelines".equals(path))
                || ("PUT".equals(method) && path.matches("/api/pipelines/\\d+"));
    }

    private boolean apiTokenCanWriteTemplate(String method, String path) {
        return ("POST".equals(method) && "/api/templates".equals(path))
                || ("PUT".equals(method) && path.matches("/api/templates/\\d+"));
    }

    private boolean apiTokenCanRunDeployment(String method, String path) {
        return "POST".equals(method) && (
                "/api/deployments".equals(path)
                        || "/api/deployments/precheck".equals(path)
                        || path.matches("/api/deployments/\\d+/(stop|rollback)")
        );
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContextHolder.clear();
    }
}
