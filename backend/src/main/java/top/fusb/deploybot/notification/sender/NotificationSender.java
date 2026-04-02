package top.fusb.deploybot.notification.sender;

import top.fusb.deploybot.notification.model.NotificationChannelEntity;
import top.fusb.deploybot.notification.model.NotificationChannelType;

public interface NotificationSender {

    NotificationChannelType getType();

    NotificationSendResult send(NotificationChannelEntity channel, NotificationMessage message) throws Exception;
}
