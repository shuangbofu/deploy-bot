package top.fusb.deploybot.notification.service;

import org.springframework.stereotype.Service;
import org.springframework.data.domain.Sort;
import top.fusb.deploybot.model.UserEntity;
import top.fusb.deploybot.notification.model.NotificationDeliveryRecordEntity;
import top.fusb.deploybot.notification.repo.NotificationDeliveryRecordRepository;
import top.fusb.deploybot.repo.UserRepository;
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
    private final UserRepository userRepository;

    public NotificationDeliveryRecordService(NotificationDeliveryRecordRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    public List<NotificationDeliveryRecordEntity> findAll() {
        return repository.findAll(NOTIFICATION_RECORD_SORT).stream()
                .map(this::enrichDeliveryRecord)
                .toList();
    }

    public List<NotificationDeliveryRecordEntity> findMine() {
        AuthenticatedUser currentUser = AuthContextHolder.get();
        if (currentUser == null || currentUser.username() == null || currentUser.username().isBlank()) {
            return List.of();
        }
        if (currentUser.isAdmin()) {
            return repository.findAll(NOTIFICATION_RECORD_SORT).stream()
                    .map(this::enrichDeliveryRecord)
                    .toList();
        }
        return repository.findByDeployment_TriggeredBy(currentUser.username(), NOTIFICATION_RECORD_SORT).stream()
                .map(this::enrichDeliveryRecord)
                .toList();
    }

    public NotificationDeliveryRecordEntity save(NotificationDeliveryRecordEntity entity) {
        return repository.save(entity);
    }

    private NotificationDeliveryRecordEntity enrichDeliveryRecord(NotificationDeliveryRecordEntity entity) {
        if (entity.getDeployment() != null) {
            entity.getDeployment().setTriggeredByDisplayName(resolveDisplayName(entity.getDeployment().getTriggeredBy()));
            entity.getDeployment().setStoppedByDisplayName(resolveDisplayName(entity.getDeployment().getStoppedBy()));
        }
        return entity;
    }

    private String resolveDisplayName(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return userRepository.findByUsername(username)
                .map(UserEntity::getDisplayName)
                .filter(value -> value != null && !value.isBlank())
                .orElse(username);
    }
}
