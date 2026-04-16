package top.fusb.deploybot.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DeploymentRecoveryInitializer {

    private final DeploymentService deploymentService;

    public DeploymentRecoveryInitializer(DeploymentService deploymentService) {
        this.deploymentService = deploymentService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedDeployments() {
        deploymentService.failInterruptedDeploymentsOnStartup();
    }
}
