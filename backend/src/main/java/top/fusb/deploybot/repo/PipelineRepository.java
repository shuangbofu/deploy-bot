package top.fusb.deploybot.repo;

import top.fusb.deploybot.model.PipelineEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PipelineRepository extends JpaRepository<PipelineEntity, Long> {
    boolean existsByTargetHostId(Long targetHostId);
}
