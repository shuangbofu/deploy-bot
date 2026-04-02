package top.fusb.deploybot.notification.dto;

import top.fusb.deploybot.notification.model.NotificationEventType;

public record NotificationBinding(
        Long notificationId,
        NotificationEventType eventType
) {
}
