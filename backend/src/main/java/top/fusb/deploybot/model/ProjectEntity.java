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
 * 项目代表一个可部署代码仓库。
 */
@Data
@Entity
@Table(name = "projects")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"gitPassword", "gitSshPrivateKey", "gitSshPublicKey", "gitSshKnownHosts"})
public class ProjectEntity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 项目名称。 */
    @Column(nullable = false, unique = true)
    private String name;

    /** 项目说明。 */
    @Column(length = 1000)
    private String description;

    /** 仓库地址。 */
    @Column(nullable = false)
    private String gitUrl;

    /** Git 认证方式。 */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private GitAuthType gitAuthType = GitAuthType.NONE;

    /** HTTP 认证用户名。 */
    @Column(length = 200)
    private String gitUsername;

    /** HTTP 认证密码。 */
    @Column(length = 1000)
    private String gitPassword;

    /** 项目级 SSH 私钥。 */
    @Column(length = 8000)
    private String gitSshPrivateKey;

    /** 项目级 SSH 公钥。 */
    @Column(length = 8000)
    private String gitSshPublicKey;

    /** 项目级 known_hosts 内容。 */
    @Column(length = 4000)
    private String gitSshKnownHosts;
}
