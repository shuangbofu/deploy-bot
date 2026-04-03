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
import top.fusb.deploybot.dto.Result;
import top.fusb.deploybot.notification.dto.NotificationChannelRequest;
import top.fusb.deploybot.notification.model.NotificationChannelEntity;
import top.fusb.deploybot.security.AdminOnly;
import top.fusb.deploybot.notification.service.NotificationChannelService;

import java.util.List;

@AdminOnly
@RestController
@RequestMapping("/api/notifications")
public class NotificationChannelController {

    private final NotificationChannelService service;

    public NotificationChannelController(NotificationChannelService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<NotificationChannelEntity>> list() {
        return Result.success(service.findAll());
    }

    @PostMapping
    public Result<NotificationChannelEntity> create(@Valid @RequestBody NotificationChannelRequest request) {
        return Result.success(service.save(request, null));
    }

    @PutMapping("/{id}")
    public Result<NotificationChannelEntity> update(@PathVariable Long id, @Valid @RequestBody NotificationChannelRequest request) {
        return Result.success(service.save(request, id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success(null);
    }
}
