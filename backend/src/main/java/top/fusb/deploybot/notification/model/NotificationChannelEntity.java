package top.fusb.deploybot.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@Table(name = "notification_channels")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"messageTemplate"})
public class NotificationChannelEntity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationChannelType type = NotificationChannelType.FEISHU;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private NotificationEventType eventType = NotificationEventType.DEPLOYMENT_FINISHED;

    @ManyToOne
    @JoinColumn(name = "webhook_config_id")
    private NotificationWebhookConfigEntity webhookConfig;

    @ManyToOne
    @JoinColumn(name = "template_id")
    private NotificationTemplateEntity template;

    @Lob
    @Column(name = "message_template")
    private String messageTemplate;

    @Column(nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
