package top.fusb.deploybot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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
@Table(name = "maven_settings")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "runtimeEnvironment")
public class MavenSettingsEntity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "runtime_environment_id")
    private RuntimeEnvironmentEntity runtimeEnvironment;

    @Column(length = 1000)
    private String description;

    @Lob
    @Column(name = "content_xml", nullable = false)
    private String contentXml;

    @Column(nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = Boolean.FALSE;
}
