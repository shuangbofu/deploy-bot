package top.fusb.deploybot.notification.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Sort;
import top.fusb.deploybot.dto.PageResult;
import top.fusb.deploybot.model.UserEntity;
import top.fusb.deploybot.notification.model.NotificationDeliveryRecordEntity;
import top.fusb.deploybot.notification.model.NotificationChannelType;
import top.fusb.deploybot.notification.model.NotificationEventType;
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

    public PageResult<NotificationDeliveryRecordEntity> findPage(
            int page,
            int pageSize,
            NotificationChannelType channelType,
            NotificationEventType eventType
    ) {
        return PageResult.of(repository.findAll((root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (channelType != null) {
                predicates.add(cb.equal(root.join("channel", jakarta.persistence.criteria.JoinType.LEFT).get("type"), channelType));
            }
            if (eventType != null) {
                predicates.add(cb.equal(root.get("eventType"), eventType));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, PageRequest.of(
                Math.max(0, page - 1),
                Math.max(1, Math.min(100, pageSize)),
                NOTIFICATION_RECORD_SORT
        )).map(this::enrichDeliveryRecord));
    }

    public PageResult<NotificationDeliveryRecordEntity> findMinePage(
            int page,
            int pageSize,
            NotificationChannelType channelType,
            NotificationEventType eventType
    ) {
        AuthenticatedUser currentUser = AuthContextHolder.get();
        if (currentUser == null || currentUser.username() == null || currentUser.username().isBlank()) {
            return new PageResult<>(List.of(), 0, Math.max(1, page), Math.max(1, Math.min(100, pageSize)));
        }
        if (currentUser.isAdmin()) {
            return findPage(page, pageSize, channelType, eventType);
        }
        return PageResult.of(repository.findAll((root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            predicates.add(cb.equal(root.join("deployment", jakarta.persistence.criteria.JoinType.LEFT).get("triggeredBy"), currentUser.username()));
            if (channelType != null) {
                predicates.add(cb.equal(root.join("channel", jakarta.persistence.criteria.JoinType.LEFT).get("type"), channelType));
            }
            if (eventType != null) {
                predicates.add(cb.equal(root.get("eventType"), eventType));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, PageRequest.of(
                Math.max(0, page - 1),
                Math.max(1, Math.min(100, pageSize)),
                NOTIFICATION_RECORD_SORT
        )).map(this::enrichDeliveryRecord));
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
