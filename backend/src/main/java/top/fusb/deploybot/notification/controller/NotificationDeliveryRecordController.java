package top.fusb.deploybot.notification.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
    public List<NotificationDeliveryRecordEntity> list() {
        return service.findAll();
    }

    @GetMapping("/mine")
    public List<NotificationDeliveryRecordEntity> mine() {
        return service.findMine();
    }
}
