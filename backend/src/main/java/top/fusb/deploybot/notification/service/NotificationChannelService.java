package top.fusb.deploybot.notification.service;

import org.springframework.stereotype.Service;
import top.fusb.deploybot.notification.dto.NotificationChannelRequest;
import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.notification.model.NotificationChannelEntity;
import top.fusb.deploybot.notification.model.NotificationTemplateEntity;
import top.fusb.deploybot.notification.model.NotificationWebhookConfigEntity;
import top.fusb.deploybot.notification.repo.NotificationChannelRepository;
import top.fusb.deploybot.notification.repo.NotificationTemplateRepository;
import top.fusb.deploybot.notification.repo.NotificationWebhookConfigRepository;

import java.util.List;

@Service
public class NotificationChannelService {

    private final NotificationChannelRepository repository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationWebhookConfigRepository webhookConfigRepository;

    public NotificationChannelService(
            NotificationChannelRepository repository,
            NotificationTemplateRepository templateRepository,
            NotificationWebhookConfigRepository webhookConfigRepository
    ) {
        this.repository = repository;
        this.templateRepository = templateRepository;
        this.webhookConfigRepository = webhookConfigRepository;
    }

    public List<NotificationChannelEntity> findAll() {
        return repository.findAll();
    }

    public NotificationChannelEntity save(NotificationChannelRequest request, Long id) {
        NotificationChannelEntity entity = id == null ? new NotificationChannelEntity() : repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.NOTIFICATION_CHANNEL_NOT_FOUND));
        entity.setName(request.name().trim());
        entity.setDescription(trimToNull(request.description()));
        entity.setType(request.type());
        entity.setEventType(request.eventType());
        entity.setWebhookConfig(resolveWebhookConfig(request.webhookConfigId()));
        entity.setTemplate(resolveTemplate(request.templateId()));
        entity.setMessageTemplate(normalizeTemplate(request.messageTemplate()));
        entity.setEnabled(request.enabled() == null ? Boolean.TRUE : request.enabled());
        return repository.save(entity);
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new BusinessException(ErrorSubCode.NOTIFICATION_CHANNEL_NOT_FOUND);
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

    private String normalizeTemplate(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace("\r\n", "\n").trim();
        return normalized.isBlank() ? null : normalized;
    }

    private NotificationTemplateEntity resolveTemplate(Long templateId) {
        if (templateId == null) {
            return null;
        }
        return templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.NOTIFICATION_TEMPLATE_NOT_FOUND));
    }

    private NotificationWebhookConfigEntity resolveWebhookConfig(Long webhookConfigId) {
        if (webhookConfigId == null) {
            throw new BusinessException(ErrorSubCode.NOTIFICATION_CHANNEL_WEBHOOK_REQUIRED);
        }
        return webhookConfigRepository.findById(webhookConfigId)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.NOTIFICATION_CHANNEL_NOT_FOUND));
    }
}
