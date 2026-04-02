package top.fusb.deploybot.notification.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import top.fusb.deploybot.notification.model.NotificationChannelEntity;

public interface NotificationChannelRepository extends JpaRepository<NotificationChannelEntity, Long> {
}
