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
    @Query("""
            select distinct p.name
              from DeploymentEntity d
              left join d.pipeline p
             where d.triggeredBy = :triggeredBy
               and p.name is not null
             order by p.name
            """)
    List<String> findDistinctPipelineNamesByTriggeredBy(@Param("triggeredBy") String triggeredBy);
    @Query("""
            select distinct pr.name
              from DeploymentEntity d
              left join d.pipeline p
              left join p.project pr
             where d.triggeredBy = :triggeredBy
               and pr.name is not null
             order by pr.name
            """)
    List<String> findDistinctProjectNamesByTriggeredBy(@Param("triggeredBy") String triggeredBy);
    java.util.Optional<DeploymentEntity> findFirstByPipelineIdOrderByCreatedAtDesc(Long pipelineId);
    List<DeploymentEntity> findByPipelineIdAndStatusInOrderByCreatedAtDesc(Long pipelineId, List<DeploymentStatus> statuses);
    Optional<DeploymentEntity> findFirstByPipelineIdAndStatusOrderByCreatedAtDesc(Long pipelineId, DeploymentStatus status);
    Optional<DeploymentEntity> findFirstByPipelineIdAndStatusAndIdLessThanAndCommitShaIsNotNullOrderByCreatedAtDesc(Long pipelineId, DeploymentStatus status, Long id);
    List<DeploymentEntity> findByPipelineIdAndStatusAndArtifactPathIsNotNullOrderByCreatedAtDesc(Long pipelineId, DeploymentStatus status);
    List<DeploymentEntity> findByStatusInAndCreatedAtBefore(List<DeploymentStatus> statuses, LocalDateTime createdAt);
    long countByPipelineId(Long pipelineId);
    long countByTriggeredBy(String triggeredBy);
    long countByStatus(DeploymentStatus status);
    long countByTriggeredByAndStatus(String triggeredBy, DeploymentStatus status);
    long countByStatusIn(List<DeploymentStatus> statuses);
    long countByTriggeredByAndStatusIn(String triggeredBy, List<DeploymentStatus> statuses);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update DeploymentEntity d
               set d.pipelineName = coalesce(d.pipelineName, :pipelineName),
                   d.projectName = coalesce(d.projectName, :projectName)
             where d.pipeline.id = :pipelineId
            """)
    void backfillPipelineSnapshot(@Param("pipelineId") Long pipelineId,
                                  @Param("pipelineName") String pipelineName,
                                  @Param("projectName") String projectName);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DeploymentEntity d set d.pipeline = null where d.pipeline.id = :pipelineId")
    void detachPipeline(@Param("pipelineId") Long pipelineId);
}
