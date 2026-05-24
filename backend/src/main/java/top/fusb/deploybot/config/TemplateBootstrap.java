package top.fusb.deploybot.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import top.fusb.deploybot.service.TemplateVariableSchemaCleanupService;

@Component
@RequiredArgsConstructor
public class TemplateBootstrap {

    private final TemplateVariableSchemaCleanupService templateVariableSchemaCleanupService;

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        templateVariableSchemaCleanupService.cleanupExistingTemplates();
    }
}
