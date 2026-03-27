package top.fusb.deploybot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import top.fusb.deploybot.model.UserEntity;
import top.fusb.deploybot.model.UserRole;
import top.fusb.deploybot.repo.UserRepository;
import top.fusb.deploybot.service.AuthService;

import java.time.LocalDateTime;

/**
 * 初始化默认管理员账号。
 */
@Component
public class UserBootstrap {
    private static final Logger log = LoggerFactory.getLogger(UserBootstrap.class);
    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_DISPLAY_NAME = "系统管理员";
    private static final String DEFAULT_ADMIN_PASSWORD = "Admin@123456";

    private final UserRepository userRepository;
    private final AuthService authService;

    public UserBootstrap(UserRepository userRepository, AuthService authService) {
        this.userRepository = userRepository;
        this.authService = authService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrapAdmin() {
        if (userRepository.count() > 0) {
            return;
        }
        UserEntity admin = new UserEntity();
        admin.setUsername(DEFAULT_ADMIN_USERNAME);
        admin.setDisplayName(DEFAULT_ADMIN_DISPLAY_NAME);
        admin.setPasswordHash(authService.encodePassword(DEFAULT_ADMIN_PASSWORD));
        admin.setRole(UserRole.ADMIN);
        admin.setEnabled(true);
        admin.setCreatedAt(LocalDateTime.now());
        admin.setUpdatedAt(LocalDateTime.now());
        userRepository.save(admin);
        log.info("已初始化默认管理员账号：username='{}'。", DEFAULT_ADMIN_USERNAME);
    }
}
