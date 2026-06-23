package top.fusb.deploybot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.fusb.deploybot.dto.ApiTokenCreateRequest;
import top.fusb.deploybot.dto.ApiTokenCreateResult;
import top.fusb.deploybot.dto.ApiTokenSummary;
import top.fusb.deploybot.dto.ApiTokenUpdateRequest;
import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.model.ApiTokenEntity;
import top.fusb.deploybot.model.ApiTokenScope;
import top.fusb.deploybot.model.UserEntity;
import top.fusb.deploybot.repo.ApiTokenRepository;
import top.fusb.deploybot.repo.UserRepository;
import top.fusb.deploybot.security.AuthContextHolder;
import top.fusb.deploybot.security.AuthenticatedUser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;

/**
 * API Token 管理与鉴权服务。
 */
@Service
@RequiredArgsConstructor
public class ApiTokenService {
    private static final String TOKEN_PREFIX = "dbot_";
    private static final int TOKEN_RANDOM_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ApiTokenRepository apiTokenRepository;
    private final UserRepository userRepository;

    public boolean supports(String token) {
        return token != null && token.startsWith(TOKEN_PREFIX);
    }

    @Transactional(readOnly = true)
    public List<ApiTokenSummary> findAll() {
        return apiTokenRepository.findAll(Sort.by(Sort.Order.desc("id")))
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public ApiTokenCreateResult create(ApiTokenCreateRequest request) {
        AuthenticatedUser currentUser = AuthContextHolder.get();
        Long ownerId = request.userId() == null ? currentUser.id() : request.userId();
        UserEntity owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.USER_NOT_FOUND));
        if (!Boolean.TRUE.equals(owner.getEnabled())) {
            throw new BusinessException(ErrorSubCode.AUTH_USER_DISABLED);
        }

        ApiTokenEntity entity = new ApiTokenEntity();
        String rawToken = generateToken();
        entity.setName(request.name().trim());
        entity.setTokenHash(hash(rawToken));
        entity.setTokenPrefix(rawToken.substring(0, Math.min(rawToken.length(), 18)));
        entity.setUser(owner);
        entity.setScopes(normalizeScopes(request.scopes()));
        entity.setExpiresAt(request.expiresAt());
        entity.setEnabled(true);
        entity.setCreatedAt(LocalDateTime.now());
        ApiTokenEntity saved = apiTokenRepository.save(entity);
        return new ApiTokenCreateResult(rawToken, toSummary(saved));
    }

    @Transactional
    public ApiTokenSummary update(Long id, ApiTokenUpdateRequest request) {
        ApiTokenEntity entity = apiTokenRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.API_TOKEN_NOT_FOUND));
        if (request.name() != null && !request.name().isBlank()) {
            entity.setName(request.name().trim());
        }
        if (request.enabled() != null) {
            entity.setEnabled(request.enabled());
        }
        return toSummary(apiTokenRepository.save(entity));
    }

    @Transactional
    public void revoke(Long id) {
        ApiTokenEntity entity = apiTokenRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.API_TOKEN_NOT_FOUND));
        entity.setEnabled(false);
        entity.setRevokedAt(LocalDateTime.now());
        apiTokenRepository.save(entity);
    }

    @Transactional
    public AuthenticatedUser authenticate(String token, String remoteAddress) {
        ApiTokenEntity entity = apiTokenRepository.findByTokenHash(hash(token))
                .orElseThrow(() -> new BusinessException(ErrorSubCode.AUTH_TOKEN_INVALID));
        if (!Boolean.TRUE.equals(entity.getEnabled()) || entity.getRevokedAt() != null) {
            throw new BusinessException(ErrorSubCode.AUTH_TOKEN_INVALID);
        }
        if (entity.getExpiresAt() != null && entity.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorSubCode.AUTH_TOKEN_INVALID);
        }
        UserEntity user = entity.getUser();
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new BusinessException(ErrorSubCode.AUTH_USER_DISABLED);
        }
        entity.setLastUsedAt(LocalDateTime.now());
        entity.setLastUsedIp(remoteAddress);
        apiTokenRepository.save(entity);
        return AuthenticatedUser.apiToken(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole(),
                entity.getId(),
                entity.getScopes()
        );
    }

    @Transactional(readOnly = true)
    public ApiTokenSummary toSummary(ApiTokenEntity entity) {
        UserEntity user = entity.getUser();
        return new ApiTokenSummary(
                entity.getId(),
                entity.getName(),
                entity.getTokenPrefix(),
                user == null ? null : user.getId(),
                user == null ? null : user.getUsername(),
                user == null ? null : user.getDisplayName(),
                entity.getScopes(),
                entity.getExpiresAt(),
                entity.getLastUsedAt(),
                entity.getLastUsedIp(),
                entity.getEnabled(),
                entity.getCreatedAt(),
                entity.getRevokedAt()
        );
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_RANDOM_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private List<ApiTokenScope> normalizeScopes(List<ApiTokenScope> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            throw new BusinessException(ErrorSubCode.API_TOKEN_SCOPE_REQUIRED);
        }
        EnumSet<ApiTokenScope> normalized = EnumSet.noneOf(ApiTokenScope.class);
        normalized.addAll(scopes);
        return normalized.stream().toList();
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("API Token hash failed.", ex);
        }
    }
}
