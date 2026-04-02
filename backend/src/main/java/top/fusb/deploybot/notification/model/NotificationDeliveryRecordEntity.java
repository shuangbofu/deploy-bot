package top.fusb.deploybot.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import top.fusb.deploybot.model.DeploymentEntity;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "notification_delivery_records")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"channel", "deployment"})
public class NotificationDeliveryRecordEntity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "channel_id")
    private NotificationChannelEntity channel;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "deployment_id")
    private DeploymentEntity deployment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationDeliveryStatus status;

    @Column(name = "channel_name", length = 200)
    private String channelName;

    @Column(name = "pipeline_name", length = 200)
    private String pipelineName;

    @Column(length = 1000)
    private String message;

    @Column(name = "response_message", length = 2000)
    private String responseMessage;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}
