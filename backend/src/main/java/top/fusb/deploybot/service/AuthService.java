package top.fusb.deploybot.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import top.fusb.deploybot.dto.ChangePasswordRequest;
import top.fusb.deploybot.dto.LoginRequest;
import top.fusb.deploybot.dto.LoginResponse;
import top.fusb.deploybot.dto.UserProfile;
import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.model.UserEntity;
import top.fusb.deploybot.repo.UserRepository;
import top.fusb.deploybot.security.AuthContextHolder;
import top.fusb.deploybot.security.AuthenticatedUser;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 登录鉴权服务。
 */
@Service
public class AuthService {
    private static final int TOKEN_EXPIRE_DAYS = 7;

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public LoginResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByUsername(request.username().trim())
                .orElseThrow(() -> new BusinessException(ErrorSubCode.AUTH_LOGIN_FAILED));
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new BusinessException(ErrorSubCode.AUTH_USER_DISABLED);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorSubCode.AUTH_LOGIN_FAILED);
        }
        user.setAuthToken(UUID.randomUUID().toString().replace("-", ""));
        user.setAuthTokenExpiresAt(LocalDateTime.now().plusDays(TOKEN_EXPIRE_DAYS));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return new LoginResponse(user.getAuthToken(), toProfile(user));
    }

    public UserProfile currentUserProfile() {
        return toProfile(getCurrentUserEntity());
    }

    public void logout() {
        UserEntity user = getCurrentUserEntity();
        user.setAuthToken(null);
        user.setAuthTokenExpiresAt(null);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    public void changePassword(ChangePasswordRequest request) {
        UserEntity user = getCurrentUserEntity();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorSubCode.AUTH_CURRENT_PASSWORD_INCORRECT);
        }
        String newPassword = request.newPassword().trim();
        if (newPassword.length() < 8) {
            throw new BusinessException(ErrorSubCode.AUTH_NEW_PASSWORD_TOO_SHORT);
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorSubCode.AUTH_NEW_PASSWORD_SAME_AS_OLD);
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    public AuthenticatedUser authenticate(String token) {
        UserEntity user = userRepository.findByAuthTokenAndAuthTokenExpiresAtAfter(token, LocalDateTime.now())
                .orElseThrow(() -> new BusinessException(ErrorSubCode.AUTH_TOKEN_INVALID));
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new BusinessException(ErrorSubCode.AUTH_USER_DISABLED);
        }
        return new AuthenticatedUser(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }

    public String encodePassword(String password) {
        return passwordEncoder.encode(password);
    }

    public UserProfile toProfile(UserEntity user) {
        return new UserProfile(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(), user.getEnabled());
    }

    private UserEntity getCurrentUserEntity() {
        AuthenticatedUser currentUser = AuthContextHolder.get();
        if (currentUser == null) {
            throw new BusinessException(ErrorSubCode.AUTH_REQUIRED);
        }
        return userRepository.findById(currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorSubCode.AUTH_TOKEN_INVALID));
    }
}
