package top.fusb.deploybot.repo;

import top.fusb.deploybot.model.ServiceEntity;
import top.fusb.deploybot.model.ServiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServiceRepository extends JpaRepository<ServiceEntity, Long> {
    Optional<ServiceEntity> findByPipelineId(Long pipelineId);
    List<ServiceEntity> findAllByOrderByIdDesc();
    List<ServiceEntity> findAllByOrderByUpdatedAtDesc();
    List<ServiceEntity> findTop6ByOrderByUpdatedAtDesc();
    List<ServiceEntity> findAllByStatusOrderByLastHeartbeatAtDescUpdatedAtDescIdDesc(ServiceStatus status);
    long countByStatus(ServiceStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ServiceEntity s where s.pipeline.id = :pipelineId")
    void deleteByPipelineId(@Param("pipelineId") Long pipelineId);
}
