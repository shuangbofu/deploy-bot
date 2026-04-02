package top.fusb.deploybot.notification.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import top.fusb.deploybot.notification.model.NotificationWebhookConfigEntity;

public interface NotificationWebhookConfigRepository extends JpaRepository<NotificationWebhookConfigEntity, Long> {
}
