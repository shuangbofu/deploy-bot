package top.fusb.deploybot.repo;

import top.fusb.deploybot.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByUsername(String username);

    Optional<UserEntity> findByAuthTokenAndAuthTokenExpiresAtAfter(String authToken, LocalDateTime now);

    long countByRole(top.fusb.deploybot.model.UserRole role);
}
