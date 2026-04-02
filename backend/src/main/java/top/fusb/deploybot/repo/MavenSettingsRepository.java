package top.fusb.deploybot.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import top.fusb.deploybot.model.MavenSettingsEntity;

import java.util.List;

public interface MavenSettingsRepository extends JpaRepository<MavenSettingsEntity, Long> {
    List<MavenSettingsEntity> findByRuntimeEnvironmentIdOrderByIsDefaultDescNameAsc(Long runtimeEnvironmentId);
    boolean existsByIdAndRuntimeEnvironmentId(Long id, Long runtimeEnvironmentId);
}
