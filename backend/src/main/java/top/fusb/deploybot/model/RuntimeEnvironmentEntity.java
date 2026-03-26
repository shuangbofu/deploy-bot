package top.fusb.deploybot.model;

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

/**
 * 运行环境描述某台主机上的 Java、Node、Maven 等工具链。
 */
@Data
@Entity
@Table(name = "runtime_environments")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"host"})
public class RuntimeEnvironmentEntity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 环境名称。 */
    @Column(nullable = false)
    private String name;

    /** 环境类型。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RuntimeEnvironmentType type;

    /** 所属主机。 */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "host_id")
    private HostEntity host;

    /** 版本号。 */
    @Column(length = 255)
    private String version;

    /** 安装根目录。 */
    @Column(length = 1000)
    private String homePath;

    /** bin 目录。 */
    @Column(length = 1000)
    private String binPath;

    /** 激活脚本。 */
    @Column(length = 4000)
    private String activationScript;

    /** 环境变量 JSON。 */
    @Column(length = 4000)
    private String environmentJson;

    /** 是否启用。 */
    @Column(nullable = false)
    private Boolean enabled = true;
}
