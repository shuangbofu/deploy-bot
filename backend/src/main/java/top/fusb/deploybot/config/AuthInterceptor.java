package top.fusb.deploybot.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.security.AdminOnly;
import top.fusb.deploybot.security.AuthContextHolder;
import top.fusb.deploybot.security.AuthenticatedUser;
import top.fusb.deploybot.service.AuthService;

/**
 * 统一接口鉴权拦截器。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

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
        AuthenticatedUser user = authService.authenticate(token);
        AuthContextHolder.set(user);
        if (handler instanceof HandlerMethod handlerMethod) {
            boolean adminOnly = handlerMethod.hasMethodAnnotation(AdminOnly.class)
                    || handlerMethod.getBeanType().isAnnotationPresent(AdminOnly.class);
            if (adminOnly && !user.isAdmin()) {
                throw new BusinessException(ErrorSubCode.AUTH_ADMIN_REQUIRED);
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContextHolder.clear();
    }
}
