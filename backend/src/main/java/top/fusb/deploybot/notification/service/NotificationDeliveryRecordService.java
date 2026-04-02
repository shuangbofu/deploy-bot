package top.fusb.deploybot.notification.service;

import org.springframework.stereotype.Service;
import org.springframework.data.domain.Sort;
import top.fusb.deploybot.notification.model.NotificationDeliveryRecordEntity;
import top.fusb.deploybot.notification.repo.NotificationDeliveryRecordRepository;
import top.fusb.deploybot.security.AuthContextHolder;
import top.fusb.deploybot.security.AuthenticatedUser;

import java.util.List;

@Service
public class NotificationDeliveryRecordService {
    private static final Sort NOTIFICATION_RECORD_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
    );

    private final NotificationDeliveryRecordRepository repository;

    public NotificationDeliveryRecordService(NotificationDeliveryRecordRepository repository) {
        this.repository = repository;
    }

    public List<NotificationDeliveryRecordEntity> findAll() {
        return repository.findAll(NOTIFICATION_RECORD_SORT);
    }

    public List<NotificationDeliveryRecordEntity> findMine() {
        AuthenticatedUser currentUser = AuthContextHolder.get();
        if (currentUser == null || currentUser.username() == null || currentUser.username().isBlank()) {
            return List.of();
        }
        if (currentUser.isAdmin()) {
            return repository.findAll(NOTIFICATION_RECORD_SORT);
        }
        return repository.findByDeployment_TriggeredBy(currentUser.username(), NOTIFICATION_RECORD_SORT);
    }

    public NotificationDeliveryRecordEntity save(NotificationDeliveryRecordEntity entity) {
        return repository.save(entity);
    }
}
