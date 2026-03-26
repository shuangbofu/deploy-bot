package top.fusb.deploybot.repo;

import top.fusb.deploybot.model.SystemSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSettingsRepository extends JpaRepository<SystemSettingsEntity, Long> {
}
