package top.fusb.deploybot.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import top.fusb.deploybot.model.ApiTokenEntity;

import java.util.Optional;

public interface ApiTokenRepository extends JpaRepository<ApiTokenEntity, Long> {
    Optional<ApiTokenEntity> findByTokenHash(String tokenHash);
}
