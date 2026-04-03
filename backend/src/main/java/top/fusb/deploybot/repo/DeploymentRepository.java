package top.fusb.deploybot.repo;

import top.fusb.deploybot.model.DeploymentEntity;
import top.fusb.deploybot.model.DeploymentStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeploymentRepository extends JpaRepository<DeploymentEntity, Long>, JpaSpecificationExecutor<DeploymentEntity> {
    List<DeploymentEntity> findAllByOrderByCreatedAtDesc();
    List<DeploymentEntity> findByTriggeredByOrderByCreatedAtDesc(String triggeredBy);
    List<DeploymentEntity> findTop30ByTriggeredByOrderByCreatedAtDesc(String triggeredBy);
    java.util.Optional<DeploymentEntity> findFirstByPipelineIdOrderByCreatedAtDesc(Long pipelineId);
    List<DeploymentEntity> findByPipelineIdAndStatusInOrderByCreatedAtDesc(Long pipelineId, List<DeploymentStatus> statuses);
    Optional<DeploymentEntity> findFirstByPipelineIdAndStatusOrderByCreatedAtDesc(Long pipelineId, DeploymentStatus status);
    long countByPipelineId(Long pipelineId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DeploymentEntity d set d.pipeline = null where d.pipeline.id = :pipelineId")
    void detachPipeline(@Param("pipelineId") Long pipelineId);
}
