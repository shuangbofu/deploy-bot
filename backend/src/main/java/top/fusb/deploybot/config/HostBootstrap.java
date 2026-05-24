package top.fusb.deploybot.config;

import lombok.RequiredArgsConstructor;
import top.fusb.deploybot.service.HostService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HostBootstrap {

    private final HostService hostService;

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        hostService.ensureLocalHost();
    }
}
