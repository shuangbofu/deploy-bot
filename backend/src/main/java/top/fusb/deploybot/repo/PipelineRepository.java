package top.fusb.deploybot.repo;

import top.fusb.deploybot.model.PipelineEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PipelineRepository extends JpaRepository<PipelineEntity, Long>, JpaSpecificationExecutor<PipelineEntity> {
    boolean existsByTargetHostId(Long targetHostId);
}
