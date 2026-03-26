package top.fusb.deploybot.repo;

import top.fusb.deploybot.model.RuntimeEnvironmentEntity;
import top.fusb.deploybot.model.RuntimeEnvironmentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RuntimeEnvironmentRepository extends JpaRepository<RuntimeEnvironmentEntity, Long> {
    List<RuntimeEnvironmentEntity> findAllByOrderByTypeAscNameAsc();
    List<RuntimeEnvironmentEntity> findByTypeAndEnabledTrueOrderByNameAsc(RuntimeEnvironmentType type);
    List<RuntimeEnvironmentEntity> findAllByHostIdOrderByTypeAscNameAsc(Long hostId);
    List<RuntimeEnvironmentEntity> findByHostIdAndTypeAndEnabledTrueOrderByNameAsc(Long hostId, RuntimeEnvironmentType type);
    boolean existsByHostId(Long hostId);
}
