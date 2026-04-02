package top.fusb.deploybot.repo;

import top.fusb.deploybot.model.HostEntity;
import top.fusb.deploybot.model.HostType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface HostRepository extends JpaRepository<HostEntity, Long>, JpaSpecificationExecutor<HostEntity> {
    List<HostEntity> findAllByOrderByBuiltInDescTypeAscNameAsc();
    List<HostEntity> findByEnabledTrueOrderByBuiltInDescTypeAscNameAsc();
    Optional<HostEntity> findFirstByTypeAndBuiltInTrue(HostType type);
}
