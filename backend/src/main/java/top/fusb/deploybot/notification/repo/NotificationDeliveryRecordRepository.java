package top.fusb.deploybot.notification.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Sort;
import top.fusb.deploybot.notification.model.NotificationDeliveryRecordEntity;

import java.util.List;

public interface NotificationDeliveryRecordRepository extends JpaRepository<NotificationDeliveryRecordEntity, Long> {
    List<NotificationDeliveryRecordEntity> findByDeployment_TriggeredBy(String triggeredBy, Sort sort);
}
