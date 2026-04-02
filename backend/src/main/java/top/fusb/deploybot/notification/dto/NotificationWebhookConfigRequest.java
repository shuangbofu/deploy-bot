package top.fusb.deploybot.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import top.fusb.deploybot.notification.model.NotificationChannelType;

public record NotificationWebhookConfigRequest(
        @NotBlank String name,
        String description,
        @NotNull NotificationChannelType type,
        @NotBlank String webhookUrl,
        String secret,
        Boolean enabled
) {
}
