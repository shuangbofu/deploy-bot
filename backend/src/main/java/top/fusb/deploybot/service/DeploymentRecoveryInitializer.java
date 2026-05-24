package top.fusb.deploybot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeploymentRecoveryInitializer {

    private final DeploymentService deploymentService;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedDeployments() {
        deploymentService.failInterruptedDeploymentsOnStartup();
    }
}
