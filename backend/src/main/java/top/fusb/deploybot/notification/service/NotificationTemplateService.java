package top.fusb.deploybot.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.fusb.deploybot.kit.JsonKit;
import top.fusb.deploybot.kit.TextKit;
import top.fusb.deploybot.notification.dto.NotificationTemplateRequest;
import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.notification.model.NotificationTemplateEntity;
import top.fusb.deploybot.notification.model.NotificationTemplateMode;
import top.fusb.deploybot.notification.repo.NotificationTemplateRepository;

import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class NotificationTemplateService {
    private static final Pattern TEMPLATE_VARIABLE_PATTERN = Pattern.compile("\\{\\{[^}]+}}");

    private final NotificationTemplateRepository repository;

    public List<NotificationTemplateEntity> findAll() {
        return repository.findAll();
    }

    public NotificationTemplateEntity save(NotificationTemplateRequest request, Long id) {
        NotificationTemplateEntity entity = id == null ? new NotificationTemplateEntity() : repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.NOTIFICATION_TEMPLATE_NOT_FOUND));
        entity.setName(TextKit.trimToNull(request.name()));
        entity.setDescription(TextKit.trimToNull(request.description()));
        entity.setTemplateMode(request.templateMode());
        String normalizedTemplate = TextKit.normalizeMultiline(request.messageTemplate());
        if (request.templateMode() == NotificationTemplateMode.FEISHU_CARD && TextKit.isNotBlank(normalizedTemplate)) {
            validateFeishuCardTemplate(normalizedTemplate);
        }
        entity.setMessageTemplate(normalizedTemplate);
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

    private void validateFeishuCardTemplate(String content) {
        String sanitized = TEMPLATE_VARIABLE_PATTERN.matcher(content).replaceAll("DEPLOY_BOT");
        try {
            JsonKit.readTree(sanitized);
        } catch (Exception ex) {
            throw new BusinessException(ErrorSubCode.NOTIFICATION_TEMPLATE_CARD_INVALID, ex);
        }
    }
}
