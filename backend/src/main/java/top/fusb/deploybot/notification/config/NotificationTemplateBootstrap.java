package top.fusb.deploybot.notification.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import top.fusb.deploybot.notification.model.NotificationTemplateEntity;
import top.fusb.deploybot.notification.repo.NotificationTemplateRepository;

@Component
public class NotificationTemplateBootstrap {

    private final NotificationTemplateRepository repository;

    public NotificationTemplateBootstrap(NotificationTemplateRepository repository) {
        this.repository = repository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        ensureTemplate(
                "开始通知",
                "系统内置的部署开始消息模板。",
                """
                【Deploy Bot】{{pipelineName}} 开始部署
                项目：{{projectName}}
                分支：{{branch}}
                部署人：{{triggeredByDisplayName}}
                目标主机：{{hostName}}
                开始时间：{{startedAt}}
                部署编号：#{{deploymentId}}
                详情链接：{{detailUrl}}
                """
        );
        ensureTemplate(
                "结束通知",
                "系统内置的部署结束消息模板。",
                """
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
                详情链接：{{detailUrl}}
                """
        );
    }

    private void ensureTemplate(String name, String description, String messageTemplate) {
        NotificationTemplateEntity entity = repository.findByName(name)
                .orElseGet(NotificationTemplateEntity::new);
        entity.setName(name);
        entity.setDescription(description);
        entity.setMessageTemplate(messageTemplate.strip());
        entity.setBuiltIn(Boolean.TRUE);
        entity.setEnabled(Boolean.TRUE);
        repository.save(entity);
    }
}
