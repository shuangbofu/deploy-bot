package top.fusb.deploybot.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@Table(name = "notification_webhook_configs")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"secret"})
public class NotificationWebhookConfigEntity {

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

    @Column(name = "webhook_url", nullable = false, length = 2000)
    private String webhookUrl;

    @Column(length = 1000)
    private String secret;

    @Column(nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
