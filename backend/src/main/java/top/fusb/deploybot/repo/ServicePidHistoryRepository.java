package top.fusb.deploybot.repo;

import top.fusb.deploybot.model.ServicePidHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServicePidHistoryRepository extends JpaRepository<ServicePidHistoryEntity, Long> {
    List<ServicePidHistoryEntity> findTop50ByServiceIdOrderByCreatedAtDescIdDesc(Long serviceId);
}
