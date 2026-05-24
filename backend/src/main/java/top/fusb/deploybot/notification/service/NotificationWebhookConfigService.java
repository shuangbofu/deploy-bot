package top.fusb.deploybot.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.fusb.deploybot.kit.SecretKit;
import top.fusb.deploybot.kit.TextKit;
import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.notification.dto.NotificationWebhookConfigRequest;
import top.fusb.deploybot.notification.model.NotificationWebhookConfigEntity;
import top.fusb.deploybot.notification.repo.NotificationWebhookConfigRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationWebhookConfigService {

    private final NotificationWebhookConfigRepository repository;

    public List<NotificationWebhookConfigEntity> findAll() {
        return repository.findAll();
    }

    public NotificationWebhookConfigEntity save(NotificationWebhookConfigRequest request, Long id) {
        NotificationWebhookConfigEntity entity = id == null ? new NotificationWebhookConfigEntity() : repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.NOTIFICATION_WEBHOOK_CONFIG_NOT_FOUND));
        entity.setName(TextKit.trimToNull(request.name()));
        entity.setDescription(TextKit.trimToNull(request.description()));
        entity.setType(request.type());
        entity.setWebhookUrl(TextKit.trimToNull(request.webhookUrl()));
        entity.setSecret(SecretKit.mergeOptionalSecret(entity.getSecret(), request.secret()));
        entity.setEnabled(request.enabled() == null ? Boolean.TRUE : request.enabled());
        return repository.save(entity);
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new BusinessException(ErrorSubCode.NOTIFICATION_WEBHOOK_CONFIG_NOT_FOUND);
        }
        repository.deleteById(id);
    }
}
