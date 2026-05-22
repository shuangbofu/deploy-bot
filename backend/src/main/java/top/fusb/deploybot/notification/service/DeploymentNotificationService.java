package top.fusb.deploybot.notification.service;

import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import top.fusb.deploybot.kit.TextKit;
import top.fusb.deploybot.kit.TimeKit;
import top.fusb.deploybot.notification.dto.NotificationBinding;
import top.fusb.deploybot.model.DeploymentEntity;
import top.fusb.deploybot.notification.model.NotificationChannelEntity;
import top.fusb.deploybot.notification.model.NotificationDeliveryRecordEntity;
import top.fusb.deploybot.notification.model.NotificationDeliveryStatus;
import top.fusb.deploybot.notification.model.NotificationEventType;
import top.fusb.deploybot.notification.model.NotificationTemplateEntity;
import top.fusb.deploybot.notification.model.NotificationTemplateMode;
import top.fusb.deploybot.model.PipelineEntity;
import top.fusb.deploybot.repo.DeploymentRepository;
import top.fusb.deploybot.notification.repo.NotificationChannelRepository;
import top.fusb.deploybot.repo.UserRepository;
import top.fusb.deploybot.notification.sender.NotificationMessage;
import top.fusb.deploybot.notification.sender.NotificationSendResult;
import top.fusb.deploybot.notification.sender.NotificationSender;
import top.fusb.deploybot.service.JsonMapper;
import top.fusb.deploybot.service.ScriptTemplateService;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DeploymentNotificationService {
    private static final Logger log = LoggerFactory.getLogger(DeploymentNotificationService.class);
    private static final String DEFAULT_TEMPLATE = """
            【Deploy Bot】{{pipelineName}} {{eventLabel}}
            项目：{{projectName}}
            分支：{{branch}}
            部署人：{{triggeredByDisplayName}}
            停止人：{{stoppedByDisplayName}}
            目标主机：{{hostName}}
            开始时间：{{startedAt}}
            结束时间：{{finishedAt}}
            耗时：{{duration}}
            错误信息：{{errorMessage}}
            部署编号：#{{deploymentId}}
            """;

    private final DeploymentRepository deploymentRepository;
    private final NotificationChannelRepository notificationChannelRepository;
    private final List<NotificationSender> senders;
    private final ScriptTemplateService scriptTemplateService;
    private final JsonMapper jsonMapper;
    private final UserRepository userRepository;
    private final NotificationDeliveryRecordService notificationDeliveryRecordService;
    private final String baseUrl;

    public DeploymentNotificationService(
            DeploymentRepository deploymentRepository,
            NotificationChannelRepository notificationChannelRepository,
            List<NotificationSender> senders,
            ScriptTemplateService scriptTemplateService,
            JsonMapper jsonMapper,
            UserRepository userRepository,
            NotificationDeliveryRecordService notificationDeliveryRecordService,
            @org.springframework.beans.factory.annotation.Value("${deploybot.base-url:http://localhost:8080}") String baseUrl
    ) {
        this.deploymentRepository = deploymentRepository;
        this.notificationChannelRepository = notificationChannelRepository;
        this.senders = senders;
        this.scriptTemplateService = scriptTemplateService;
        this.jsonMapper = jsonMapper;
        this.userRepository = userRepository;
        this.notificationDeliveryRecordService = notificationDeliveryRecordService;
        this.baseUrl = baseUrl;
    }

    public void notifyDeploymentEvent(Long deploymentId, NotificationEventType eventType) {
        deploymentRepository.findById(deploymentId).ifPresent(item -> notifyDeploymentEvent(item, eventType));
    }

    public void notifyDeploymentEvent(DeploymentEntity deployment, NotificationEventType eventType) {
        try {
            PipelineEntity pipeline = deployment.getPipeline();
            if (pipeline == null || TextKit.isBlank(pipeline.getNotificationBindingsJson())) {
                return;
            }
            List<NotificationBinding> bindings = jsonMapper.read(
                    pipeline.getNotificationBindingsJson(),
                    new TypeReference<>() {
                    }
            );
            List<Long> channelIds = bindings.stream()
                    .filter(item -> item != null && item.notificationId() != null && item.eventType() == eventType)
                    .map(NotificationBinding::notificationId)
                    .distinct()
                    .toList();
            if (channelIds.isEmpty()) {
                return;
            }
            Map<Long, NotificationChannelEntity> channelMap = notificationChannelRepository.findAllById(channelIds).stream()
                    .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                    .collect(Collectors.toMap(NotificationChannelEntity::getId, Function.identity()));
            for (Long channelId : channelIds) {
                NotificationChannelEntity channel = channelMap.get(channelId);
                if (channel == null) {
                    continue;
                }
                NotificationSender sender = senders.stream()
                        .filter(item -> item.getType() == channel.getType())
                        .findFirst()
                        .orElse(null);
                if (sender == null) {
                    continue;
                }
                String template = resolveTemplate(channel);
                NotificationTemplateMode templateMode = resolveTemplateMode(channel);
                String renderedContent = scriptTemplateService.render(template, buildVariables(deployment, eventType)).trim();
                recordDelivery(channel, deployment, eventType, templateMode, renderedContent, sender);
            }
        } catch (Exception ex) {
            log.warn("部署 {} 发送 {} 通知失败：{}", deployment.getId(), eventType, ex.getMessage(), ex);
        }
    }

    private void recordDelivery(
            NotificationChannelEntity channel,
            DeploymentEntity deployment,
            NotificationEventType eventType,
            NotificationTemplateMode templateMode,
            String renderedContent,
            NotificationSender sender
    ) {
        NotificationDeliveryRecordEntity record = new NotificationDeliveryRecordEntity();
        record.setChannel(channel);
        record.setDeployment(deployment);
        record.setEventType(eventType);
        record.setChannelName(channel.getName());
        record.setPipelineName(deployment.getPipeline() == null ? null : deployment.getPipeline().getName());
        record.setMessage(renderedContent);
        record.setCreatedAt(LocalDateTime.now());
        try {
            NotificationSendResult result = sender.send(channel, new NotificationMessage(templateMode, renderedContent));
            record.setStatus(NotificationDeliveryStatus.SUCCESS);
            record.setResponseMessage(result.responseMessage());
        } catch (Exception ex) {
            record.setStatus(NotificationDeliveryStatus.FAILED);
            record.setErrorMessage(ex.getMessage());
            log.warn("通知渠道 {} 发送失败：{}", channel.getName(), ex.getMessage(), ex);
        } finally {
            record.setFinishedAt(LocalDateTime.now());
            notificationDeliveryRecordService.save(record);
        }
    }

    private String resolveTemplate(NotificationChannelEntity channel) {
        if (TextKit.isNotBlank(channel.getMessageTemplate())) {
            return channel.getMessageTemplate();
        }
        NotificationTemplateEntity template = channel.getTemplate();
        if (template != null && TextKit.isNotBlank(template.getMessageTemplate())) {
            return template.getMessageTemplate();
        }
        return DEFAULT_TEMPLATE;
    }

    private NotificationTemplateMode resolveTemplateMode(NotificationChannelEntity channel) {
        NotificationTemplateEntity template = channel.getTemplate();
        if (template != null && template.getTemplateMode() != null) {
            return template.getTemplateMode();
        }
        return NotificationTemplateMode.TEXT;
    }

    private Map<String, String> buildVariables(DeploymentEntity deployment, NotificationEventType eventType) {
        PipelineEntity pipeline = deployment.getPipeline();
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("deploymentId", String.valueOf(deployment.getId()));
        variables.put("pipelineName", pipeline == null ? "-" : TextKit.valueOrDash(pipeline.getName()));
        variables.put("projectName", pipeline == null || pipeline.getProject() == null ? "-" : TextKit.valueOrDash(pipeline.getProject().getName()));
        variables.put("branch", nullToDash(deployment.getBranchName()));
        variables.put("status", deployment.getStatus() == null ? "-" : deployment.getStatus().name());
        variables.put("statusLabel", mapStatusLabel(deployment));
        variables.put("eventType", eventType.name());
        variables.put("eventLabel", mapEventLabel(deployment, eventType));
        variables.put("triggeredBy", nullToDash(deployment.getTriggeredBy()));
        variables.put("triggeredByDisplayName", resolveDisplayName(deployment.getTriggeredBy()));
        variables.put("stoppedBy", nullToDash(deployment.getStoppedBy()));
        variables.put("stoppedByDisplayName", resolveDisplayName(deployment.getStoppedBy()));
        variables.put("hostName", pipeline == null || pipeline.getTargetHost() == null ? "本机" : TextKit.valueOrDash(pipeline.getTargetHost().getName()));
        variables.put("applicationName", pipeline == null ? "-" : TextKit.valueOrDash(pipeline.getApplicationName()));
        variables.put("springProfile", pipeline == null ? "-" : TextKit.valueOrDash(pipeline.getSpringProfile()));
        variables.put("startedAt", TimeKit.formatDateTime(deployment.getStartedAt()));
        variables.put("finishedAt", TimeKit.formatDateTime(deployment.getFinishedAt()));
        variables.put("duration", TimeKit.formatDuration(deployment.getStartedAt(), deployment.getFinishedAt()));
        variables.put("errorMessage", nullToDash(deployment.getErrorMessage()));
        variables.put("detailUrl", buildDeploymentDetailUrl(deployment));
        return variables;
    }

    private String buildDeploymentDetailUrl(DeploymentEntity deployment) {
        String normalizedBaseUrl = baseUrl == null ? "" : baseUrl.trim();
        if (TextKit.isBlank(normalizedBaseUrl)) {
            return "-";
        }
        String base = normalizedBaseUrl.endsWith("/") ? normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1) : normalizedBaseUrl;
        return base + "/#/user/deployments/" + deployment.getId();
    }

    private String mapEventLabel(DeploymentEntity deployment, NotificationEventType eventType) {
        return switch (eventType) {
            case DEPLOYMENT_STARTED -> "部署开始";
            case DEPLOYMENT_FINISHED -> mapStatusLabel(deployment);
        };
    }

    private String mapStatusLabel(DeploymentEntity deployment) {
        if (deployment.getStatus() == null) {
            return "-";
        }
        return switch (deployment.getStatus()) {
            case SUCCESS -> "部署成功";
            case FAILED -> "部署失败";
            case STOPPED -> "部署已停止";
            case RUNNING -> "部署中";
            case PENDING -> "等待部署";
        };
    }

    private String resolveDisplayName(String username) {
        if (TextKit.isBlank(username)) {
            return "-";
        }
        return userRepository.findByUsername(username)
                .map(item -> TextKit.isBlank(item.getDisplayName()) ? item.getUsername() : item.getDisplayName())
                .orElse(username);
    }

    private String nullToDash(String value) {
        return TextKit.valueOrDash(value);
    }
}
