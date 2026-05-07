package top.fusb.deploybot.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import top.fusb.deploybot.notification.dto.NotificationTemplateRequest;
import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.notification.model.NotificationTemplateEntity;
import top.fusb.deploybot.notification.model.NotificationTemplateMode;
import top.fusb.deploybot.notification.repo.NotificationTemplateRepository;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class NotificationTemplateService {
    private static final Pattern TEMPLATE_VARIABLE_PATTERN = Pattern.compile("\\{\\{[^}]+}}");

    private final NotificationTemplateRepository repository;
    private final ObjectMapper objectMapper;

    public NotificationTemplateService(NotificationTemplateRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public List<NotificationTemplateEntity> findAll() {
        return repository.findAll();
    }

    public NotificationTemplateEntity save(NotificationTemplateRequest request, Long id) {
        NotificationTemplateEntity entity = id == null ? new NotificationTemplateEntity() : repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.NOTIFICATION_TEMPLATE_NOT_FOUND));
        entity.setName(request.name().trim());
        entity.setDescription(trimToNull(request.description()));
        entity.setTemplateMode(request.templateMode());
        entity.setMessageTemplate(normalizeTemplate(request.messageTemplate(), request.templateMode()));
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

    private String normalizeTemplate(String value, NotificationTemplateMode templateMode) {
        String normalized = value.replace("\r\n", "\n").trim();
        if (normalized.isBlank()) {
            return null;
        }
        if (templateMode == NotificationTemplateMode.FEISHU_CARD) {
            validateFeishuCardTemplate(normalized);
        }
        return normalized;
    }

    private void validateFeishuCardTemplate(String content) {
        String sanitized = TEMPLATE_VARIABLE_PATTERN.matcher(content).replaceAll("DEPLOY_BOT");
        try {
            objectMapper.readTree(sanitized);
        } catch (Exception ex) {
            throw new BusinessException(ErrorSubCode.NOTIFICATION_TEMPLATE_CARD_INVALID, ex);
        }
    }
}
