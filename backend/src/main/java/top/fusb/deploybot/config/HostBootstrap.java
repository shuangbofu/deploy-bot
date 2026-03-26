package top.fusb.deploybot.config;

import top.fusb.deploybot.service.HostService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class HostBootstrap {

    private final HostService hostService;

    public HostBootstrap(HostService hostService) {
        this.hostService = hostService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        hostService.ensureLocalHost();
    }
}
