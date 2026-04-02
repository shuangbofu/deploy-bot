package top.fusb.deploybot.notification.service;

import org.springframework.stereotype.Service;
import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.notification.dto.NotificationWebhookConfigRequest;
import top.fusb.deploybot.notification.model.NotificationWebhookConfigEntity;
import top.fusb.deploybot.notification.repo.NotificationWebhookConfigRepository;

import java.util.List;

@Service
public class NotificationWebhookConfigService {

    private final NotificationWebhookConfigRepository repository;

    public NotificationWebhookConfigService(NotificationWebhookConfigRepository repository) {
        this.repository = repository;
    }

    public List<NotificationWebhookConfigEntity> findAll() {
        return repository.findAll();
    }

    public NotificationWebhookConfigEntity save(NotificationWebhookConfigRequest request, Long id) {
        NotificationWebhookConfigEntity entity = id == null ? new NotificationWebhookConfigEntity() : repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.NOTIFICATION_WEBHOOK_CONFIG_NOT_FOUND));
        entity.setName(request.name().trim());
        entity.setDescription(trimToNull(request.description()));
        entity.setType(request.type());
        entity.setWebhookUrl(request.webhookUrl().trim());
        entity.setSecret(mergeOptionalSecret(entity.getSecret(), request.secret()));
        entity.setEnabled(request.enabled() == null ? Boolean.TRUE : request.enabled());
        return repository.save(entity);
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new BusinessException(ErrorSubCode.NOTIFICATION_WEBHOOK_CONFIG_NOT_FOUND);
        }
        repository.deleteById(id);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String mergeOptionalSecret(String currentValue, String requestValue) {
        if (requestValue == null) {
            return currentValue;
        }
        return trimToNull(requestValue);
    }
}
