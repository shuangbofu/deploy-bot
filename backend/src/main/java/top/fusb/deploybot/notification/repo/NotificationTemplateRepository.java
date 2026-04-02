package top.fusb.deploybot.notification.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import top.fusb.deploybot.notification.model.NotificationTemplateEntity;

import java.util.Optional;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplateEntity, Long> {
    Optional<NotificationTemplateEntity> findByName(String name);
}
