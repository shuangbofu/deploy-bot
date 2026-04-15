package top.fusb.deploybot.repo;

import top.fusb.deploybot.model.UserFavoritePipelineEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserFavoritePipelineRepository extends JpaRepository<UserFavoritePipelineEntity, Long> {
    List<UserFavoritePipelineEntity> findByUserId(Long userId);

    List<UserFavoritePipelineEntity> findByUserIdAndPipelineIdIn(Long userId, List<Long> pipelineIds);

    Optional<UserFavoritePipelineEntity> findByUserIdAndPipelineId(Long userId, Long pipelineId);
}
