package top.fusb.deploybot.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import top.fusb.deploybot.notification.model.NotificationChannelType;
import top.fusb.deploybot.notification.model.NotificationEventType;

public record NotificationChannelRequest(
        @NotBlank String name,
        String description,
        @NotNull NotificationChannelType type,
        @NotNull NotificationEventType eventType,
        Long webhookConfigId,
        Long templateId,
        String messageTemplate,
        Boolean enabled
) {
}
