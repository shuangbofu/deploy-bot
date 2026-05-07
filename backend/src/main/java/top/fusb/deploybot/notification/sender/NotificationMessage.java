package top.fusb.deploybot.notification.sender;

import top.fusb.deploybot.notification.model.NotificationTemplateMode;

public record NotificationMessage(
        NotificationTemplateMode mode,
        String content
) {
}
