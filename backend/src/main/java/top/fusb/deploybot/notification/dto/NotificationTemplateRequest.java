package top.fusb.deploybot.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import top.fusb.deploybot.notification.model.NotificationTemplateMode;

public record NotificationTemplateRequest(
        @NotBlank String name,
        String description,
        @NotNull NotificationTemplateMode templateMode,
        @NotBlank String messageTemplate,
        Boolean enabled
) {
}
