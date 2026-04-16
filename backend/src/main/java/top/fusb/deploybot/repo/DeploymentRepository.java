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
import java.time.LocalDateTime;

public interface DeploymentRepository extends JpaRepository<DeploymentEntity, Long>, JpaSpecificationExecutor<DeploymentEntity> {
    List<DeploymentEntity> findAllByOrderByCreatedAtDesc();
    List<DeploymentEntity> findByTriggeredByOrderByCreatedAtDesc(String triggeredBy);
    List<DeploymentEntity> findTop30ByTriggeredByOrderByCreatedAtDesc(String triggeredBy);
    List<DeploymentEntity> findTop5ByOrderByCreatedAtDesc();
    List<DeploymentEntity> findTop5ByTriggeredByOrderByCreatedAtDesc(String triggeredBy);
    List<DeploymentEntity> findTop5ByStatusInOrderByCreatedAtDesc(List<DeploymentStatus> statuses);
    List<DeploymentEntity> findTop5ByTriggeredByAndStatusInOrderByCreatedAtDesc(String triggeredBy, List<DeploymentStatus> statuses);
    List<DeploymentEntity> findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(LocalDateTime createdAt);
    List<DeploymentEntity> findByTriggeredByAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(String triggeredBy, LocalDateTime createdAt);
    List<DeploymentEntity> findByStatusInOrderByCreatedAtDesc(List<DeploymentStatus> statuses);
    java.util.Optional<DeploymentEntity> findFirstByPipelineIdOrderByCreatedAtDesc(Long pipelineId);
    List<DeploymentEntity> findByPipelineIdAndStatusInOrderByCreatedAtDesc(Long pipelineId, List<DeploymentStatus> statuses);
    Optional<DeploymentEntity> findFirstByPipelineIdAndStatusOrderByCreatedAtDesc(Long pipelineId, DeploymentStatus status);
    List<DeploymentEntity> findByPipelineIdAndStatusAndArtifactPathIsNotNullOrderByCreatedAtDesc(Long pipelineId, DeploymentStatus status);
    List<DeploymentEntity> findByStatusInAndCreatedAtBefore(List<DeploymentStatus> statuses, LocalDateTime createdAt);
    long countByPipelineId(Long pipelineId);
    long countByTriggeredBy(String triggeredBy);
    long countByStatus(DeploymentStatus status);
    long countByTriggeredByAndStatus(String triggeredBy, DeploymentStatus status);
    long countByStatusIn(List<DeploymentStatus> statuses);
    long countByTriggeredByAndStatusIn(String triggeredBy, List<DeploymentStatus> statuses);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DeploymentEntity d set d.pipeline = null where d.pipeline.id = :pipelineId")
    void detachPipeline(@Param("pipelineId") Long pipelineId);
}
