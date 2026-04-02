package top.fusb.deploybot.notification.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import top.fusb.deploybot.notification.model.NotificationEventType;

@Service
public class DeploymentNotificationAsyncService {

    private final DeploymentNotificationService deploymentNotificationService;

    public DeploymentNotificationAsyncService(DeploymentNotificationService deploymentNotificationService) {
        this.deploymentNotificationService = deploymentNotificationService;
    }

    @Async
    public void notifyAsync(Long deploymentId, NotificationEventType eventType) {
        deploymentNotificationService.notifyDeploymentEvent(deploymentId, eventType);
    }
}
