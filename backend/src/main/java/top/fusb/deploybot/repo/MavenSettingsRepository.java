package top.fusb.deploybot.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import top.fusb.deploybot.model.MavenSettingsEntity;

import java.util.List;

public interface MavenSettingsRepository extends JpaRepository<MavenSettingsEntity, Long> {
    List<MavenSettingsEntity> findByRuntimeEnvironmentIdAndDeletedFalseOrderByIsDefaultDescNameAsc(Long runtimeEnvironmentId);
    java.util.Optional<MavenSettingsEntity> findByIdAndDeletedFalse(Long id);
}
