package top.fusb.deploybot.repo;

import top.fusb.deploybot.model.ServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceRepository extends JpaRepository<ServiceEntity, Long> {
    Optional<ServiceEntity> findFirstByPipelineId(Long pipelineId);
    List<ServiceEntity> findAllByOrderByUpdatedAtDesc();
}
