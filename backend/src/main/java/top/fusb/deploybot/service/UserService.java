package top.fusb.deploybot.service;

import org.springframework.stereotype.Service;
import top.fusb.deploybot.dto.UserRequest;
import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.model.UserEntity;
import top.fusb.deploybot.model.UserRole;
import top.fusb.deploybot.repo.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户管理服务。
 */
@Service
public class UserService {
    private final UserRepository userRepository;
    private final AuthService authService;

    public UserService(UserRepository userRepository, AuthService authService) {
        this.userRepository = userRepository;
        this.authService = authService;
    }

    public List<UserEntity> findAll() {
        return userRepository.findAll();
    }

    public UserEntity save(UserRequest request, Long id) {
        UserEntity entity = id == null ? new UserEntity() : userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.USER_NOT_FOUND));

        userRepository.findByUsername(request.username().trim())
                .filter(existing -> id == null || !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorSubCode.USERNAME_ALREADY_EXISTS);
                });

        entity.setUsername(request.username().trim());
        entity.setDisplayName(request.displayName().trim());
        entity.setRole(request.role() == null ? UserRole.USER : request.role());
        entity.setEnabled(Boolean.TRUE.equals(request.enabled()));
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }
        entity.setUpdatedAt(LocalDateTime.now());

        if (id == null) {
            if (request.password() == null || request.password().isBlank()) {
                throw new BusinessException(ErrorSubCode.USER_PASSWORD_REQUIRED);
            }
            entity.setPasswordHash(authService.encodePassword(request.password().trim()));
        } else if (request.password() != null && !request.password().isBlank()) {
            entity.setPasswordHash(authService.encodePassword(request.password().trim()));
        }

        return userRepository.save(entity);
    }

    public void delete(Long id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.USER_NOT_FOUND));
        if (user.getRole() == UserRole.ADMIN && userRepository.countByRole(UserRole.ADMIN) <= 1) {
            throw new BusinessException(ErrorSubCode.LAST_ADMIN_DELETE_FORBIDDEN);
        }
        userRepository.deleteById(id);
    }
}
