package top.fusb.deploybot.repo;

import top.fusb.deploybot.model.DeploymentEntity;
import top.fusb.deploybot.model.DeploymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeploymentRepository extends JpaRepository<DeploymentEntity, Long> {
    List<DeploymentEntity> findAllByOrderByCreatedAtDesc();
    List<DeploymentEntity> findByPipelineIdAndStatusInOrderByCreatedAtDesc(Long pipelineId, List<DeploymentStatus> statuses);
    Optional<DeploymentEntity> findFirstByPipelineIdAndStatusOrderByCreatedAtDesc(Long pipelineId, DeploymentStatus status);
}
