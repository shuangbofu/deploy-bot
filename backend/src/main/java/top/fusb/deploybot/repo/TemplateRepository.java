package top.fusb.deploybot.repo;

import top.fusb.deploybot.model.TemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TemplateRepository extends JpaRepository<TemplateEntity, Long>, JpaSpecificationExecutor<TemplateEntity> {
}
