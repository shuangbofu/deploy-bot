package top.fusb.deploybot.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Entity
@Table(name = "notification_templates")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class NotificationTemplateEntity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Lob
    @Column(name = "message_template", nullable = false)
    private String messageTemplate;

    @Column(name = "built_in", nullable = false)
    private Boolean builtIn = Boolean.FALSE;

    @Column(nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
