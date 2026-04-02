package top.fusb.deploybot.notification.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.fusb.deploybot.notification.dto.NotificationWebhookConfigRequest;
import top.fusb.deploybot.notification.model.NotificationWebhookConfigEntity;
import top.fusb.deploybot.notification.service.NotificationWebhookConfigService;
import top.fusb.deploybot.security.AdminOnly;

import java.util.List;

@AdminOnly
@RestController
@RequestMapping("/api/notification-webhook-configs")
public class NotificationWebhookConfigController {

    private final NotificationWebhookConfigService service;

    public NotificationWebhookConfigController(NotificationWebhookConfigService service) {
        this.service = service;
    }

    @GetMapping
    public List<NotificationWebhookConfigEntity> list() {
        return service.findAll();
    }

    @PostMapping
    public NotificationWebhookConfigEntity create(@Valid @RequestBody NotificationWebhookConfigRequest request) {
        return service.save(request, null);
    }

    @PutMapping("/{id}")
    public NotificationWebhookConfigEntity update(@PathVariable Long id, @Valid @RequestBody NotificationWebhookConfigRequest request) {
        return service.save(request, id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
