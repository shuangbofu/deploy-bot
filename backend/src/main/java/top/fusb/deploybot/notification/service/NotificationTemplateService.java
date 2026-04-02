package top.fusb.deploybot.notification.service;

import org.springframework.stereotype.Service;
import top.fusb.deploybot.notification.dto.NotificationTemplateRequest;
import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.notification.model.NotificationTemplateEntity;
import top.fusb.deploybot.notification.repo.NotificationTemplateRepository;

import java.util.List;

@Service
public class NotificationTemplateService {

    private final NotificationTemplateRepository repository;

    public NotificationTemplateService(NotificationTemplateRepository repository) {
        this.repository = repository;
    }

    public List<NotificationTemplateEntity> findAll() {
        return repository.findAll();
    }

    public NotificationTemplateEntity save(NotificationTemplateRequest request, Long id) {
        NotificationTemplateEntity entity = id == null ? new NotificationTemplateEntity() : repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.NOTIFICATION_TEMPLATE_NOT_FOUND));
        entity.setName(request.name().trim());
        entity.setDescription(trimToNull(request.description()));
        entity.setMessageTemplate(normalizeTemplate(request.messageTemplate()));
        entity.setEnabled(request.enabled() == null ? Boolean.TRUE : request.enabled());
        return repository.save(entity);
    }

    public void delete(Long id) {
        NotificationTemplateEntity entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.NOTIFICATION_TEMPLATE_NOT_FOUND));
        if (Boolean.TRUE.equals(entity.getBuiltIn())) {
            throw new BusinessException(ErrorSubCode.NOTIFICATION_TEMPLATE_BUILT_IN);
        }
        repository.delete(entity);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String normalizeTemplate(String value) {
        String normalized = value.replace("\r\n", "\n").trim();
        return normalized.isBlank() ? null : normalized;
    }
}
