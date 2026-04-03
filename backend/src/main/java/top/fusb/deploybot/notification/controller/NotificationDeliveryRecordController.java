package top.fusb.deploybot.notification.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.fusb.deploybot.dto.Result;
import top.fusb.deploybot.notification.model.NotificationDeliveryRecordEntity;
import top.fusb.deploybot.notification.service.NotificationDeliveryRecordService;
import top.fusb.deploybot.security.AdminOnly;

import java.util.List;

@RestController
@RequestMapping("/api/notification-records")
public class NotificationDeliveryRecordController {

    private final NotificationDeliveryRecordService service;

    public NotificationDeliveryRecordController(NotificationDeliveryRecordService service) {
        this.service = service;
    }

    @AdminOnly
    @GetMapping
    public Result<List<NotificationDeliveryRecordEntity>> list() {
        return Result.success(service.findAll());
    }

    @GetMapping("/mine")
    public Result<List<NotificationDeliveryRecordEntity>> mine() {
        return Result.success(service.findMine());
    }
}
