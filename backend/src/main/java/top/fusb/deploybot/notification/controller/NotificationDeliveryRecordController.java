package top.fusb.deploybot.notification.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.fusb.deploybot.dto.PageResult;
import top.fusb.deploybot.dto.Result;
import top.fusb.deploybot.notification.model.NotificationDeliveryRecordEntity;
import top.fusb.deploybot.notification.model.NotificationChannelType;
import top.fusb.deploybot.notification.model.NotificationEventType;
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

    @AdminOnly
    @GetMapping("/page")
    public Result<PageResult<NotificationDeliveryRecordEntity>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) NotificationChannelType channelType,
            @RequestParam(required = false) NotificationEventType eventType
    ) {
        return Result.success(service.findPage(page, pageSize, channelType, eventType));
    }

    @GetMapping("/mine/page")
    public Result<PageResult<NotificationDeliveryRecordEntity>> minePage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) NotificationChannelType channelType,
            @RequestParam(required = false) NotificationEventType eventType
    ) {
        return Result.success(service.findMinePage(page, pageSize, channelType, eventType));
    }
}
