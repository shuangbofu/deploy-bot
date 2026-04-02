package top.fusb.deploybot.notification.dto;

import jakarta.validation.constraints.NotBlank;

public record NotificationTemplateRequest(
        @NotBlank String name,
        String description,
        @NotBlank String messageTemplate,
        Boolean enabled
) {
}
