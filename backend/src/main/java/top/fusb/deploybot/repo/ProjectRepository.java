package top.fusb.deploybot.repo;

import top.fusb.deploybot.model.ProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<ProjectEntity, Long> {
}
