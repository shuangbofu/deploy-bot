package top.fusb.deploybot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import top.fusb.deploybot.dto.PageResult;
import top.fusb.deploybot.dto.UserRequest;
import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.model.UserEntity;
import top.fusb.deploybot.model.UserRole;
import top.fusb.deploybot.repo.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 用户管理服务。
 */
@Service
public class UserService {
    private final UserRepository userRepository;
    private final AuthService authService;
    private final String defaultPassword;
    private final Path avatarDirectory;

    public UserService(
            UserRepository userRepository,
            AuthService authService,
            @Value("${deploybot.user.default-password:12345}") String defaultPassword,
            @Value("${deploybot.workspace-root:./runtime}") String workspaceRoot
    ) {
        this.userRepository = userRepository;
        this.authService = authService;
        this.defaultPassword = defaultPassword;
        this.avatarDirectory = Path.of(workspaceRoot == null || workspaceRoot.isBlank() ? "./runtime" : workspaceRoot.trim())
                .toAbsolutePath()
                .normalize()
                .resolve("files")
                .resolve("avatars");
    }

    public List<UserEntity> findAll() {
        return userRepository.findAll();
    }

    public PageResult<UserEntity> findPage(int page, int pageSize, String keyword, UserRole role, Boolean enabled) {
        return PageResult.of(userRepository.findAll((root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("username")), pattern),
                        cb.like(cb.lower(root.get("displayName")), pattern)
                ));
            }
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, PageRequest.of(Math.max(0, page - 1), Math.max(1, Math.min(100, pageSize)), Sort.by(Sort.Order.desc("id")))));
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
        entity.setAvatar(request.avatar());
        entity.setRole(request.role() == null ? UserRole.USER : request.role());
        entity.setEnabled(Boolean.TRUE.equals(request.enabled()));
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }
        entity.setUpdatedAt(LocalDateTime.now());

        if (id == null) {
            entity.setPasswordHash(authService.encodePassword(defaultPassword.trim()));
        }

        return userRepository.save(entity);
    }

    public void resetPassword(Long id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.USER_NOT_FOUND));
        user.setPasswordHash(authService.encodePassword(defaultPassword.trim()));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    public String uploadAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorSubCode.USER_AVATAR_INVALID);
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException(ErrorSubCode.USER_AVATAR_INVALID);
        }
        String fileName = UUID.randomUUID().toString().replace("-", "") + resolveExtension(file.getOriginalFilename(), contentType);
        try {
            Files.createDirectories(avatarDirectory);
            Path target = avatarDirectory.resolve(fileName);
            file.transferTo(target);
            return "/files/avatars/" + fileName;
        } catch (Exception ex) {
            throw new BusinessException(ErrorSubCode.USER_AVATAR_UPLOAD_FAILED);
        }
    }

    private String resolveExtension(String originalFilename, String contentType) {
        if (originalFilename != null) {
            int dotIndex = originalFilename.lastIndexOf('.');
            if (dotIndex >= 0 && dotIndex < originalFilename.length() - 1) {
                return originalFilename.substring(dotIndex);
            }
        }
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "image/svg+xml" -> ".svg";
            default -> ".img";
        };
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
