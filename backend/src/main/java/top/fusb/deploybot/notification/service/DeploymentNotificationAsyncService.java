package top.fusb.deploybot.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import top.fusb.deploybot.notification.model.NotificationEventType;

@Service
@RequiredArgsConstructor
public class DeploymentNotificationAsyncService {

    private final DeploymentNotificationService deploymentNotificationService;

    @Async
    public void notifyAsync(Long deploymentId, NotificationEventType eventType) {
        deploymentNotificationService.notifyDeploymentEvent(deploymentId, eventType);
    }
}
