package top.fusb.deploybot.repo;

import top.fusb.deploybot.model.TemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateRepository extends JpaRepository<TemplateEntity, Long> {
}
