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
import top.fusb.deploybot.notification.dto.NotificationTemplateRequest;
import top.fusb.deploybot.notification.model.NotificationTemplateEntity;
import top.fusb.deploybot.security.AdminOnly;
import top.fusb.deploybot.notification.service.NotificationTemplateService;

import java.util.List;

@AdminOnly
@RestController
@RequestMapping("/api/notification-templates")
public class NotificationTemplateController {

    private final NotificationTemplateService service;

    public NotificationTemplateController(NotificationTemplateService service) {
        this.service = service;
    }

    @GetMapping
    public List<NotificationTemplateEntity> list() {
        return service.findAll();
    }

    @PostMapping
    public NotificationTemplateEntity create(@Valid @RequestBody NotificationTemplateRequest request) {
        return service.save(request, null);
    }

    @PutMapping("/{id}")
    public NotificationTemplateEntity update(@PathVariable Long id, @Valid @RequestBody NotificationTemplateRequest request) {
        return service.save(request, id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
