package top.fusb.deploybot.model;

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

/**
 * 主机是部署目标的统一抽象。
 * 本机和 SSH 远程主机都用同一张表管理。
 */
@Data
@Entity
@Table(name = "hosts")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"sshPassword", "sshPrivateKey", "sshPassphrase", "sshKnownHosts"})
public class HostEntity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 主机名称。 */
    @Column(nullable = false, unique = true)
    private String name;

    /** 主机类型，本机或 SSH。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HostType type = HostType.LOCAL;

    /** 主机说明。 */
    @Column(length = 500)
    private String description;

    /** 远程主机地址。 */
    @Column(length = 255)
    private String hostname;

    /** SSH 端口。 */
    private Integer port;

    /** SSH 登录用户名。 */
    @Column(length = 255)
    private String username;

    /** SSH 认证方式。 */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private HostSshAuthType sshAuthType;

    /** 密码模式下的 SSH 密码。 */
    @Column(length = 2000)
    private String sshPassword;

    /** 私钥模式下的私钥内容。 */
    @Column(length = 8000)
    private String sshPrivateKey;

    /** 私钥口令。 */
    @Column(length = 1000)
    private String sshPassphrase;

    /** 主机工作空间目录。 */
    @Column(length = 1000)
    private String workspaceRoot;

    /** SSH known_hosts 内容。 */
    @Column(length = 4000)
    private String sshKnownHosts;

    /** 主机是否启用。 */
    @Column(nullable = false)
    private Boolean enabled = true;

    /** 是否为系统自动生成的内置主机。 */
    @Column(nullable = false)
    private Boolean builtIn = false;
}
