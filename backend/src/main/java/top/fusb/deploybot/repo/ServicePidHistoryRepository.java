package top.fusb.deploybot.repo;

import top.fusb.deploybot.model.ServicePidHistoryEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ServicePidHistoryRepository extends JpaRepository<ServicePidHistoryEntity, Long> {
    List<ServicePidHistoryEntity> findTop50ByServiceIdOrderByCreatedAtDescIdDesc(Long serviceId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ServicePidHistoryEntity h where h.service.pipeline.id = :pipelineId")
    void deleteByPipelineId(@Param("pipelineId") Long pipelineId);
}
