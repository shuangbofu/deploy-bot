package top.fusb.deploybot.repo;

import top.fusb.deploybot.model.HostEntity;
import top.fusb.deploybot.model.HostType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HostRepository extends JpaRepository<HostEntity, Long> {
    List<HostEntity> findAllByOrderByBuiltInDescTypeAscNameAsc();
    List<HostEntity> findByEnabledTrueOrderByBuiltInDescTypeAscNameAsc();
    Optional<HostEntity> findFirstByTypeAndBuiltInTrue(HostType type);
}
