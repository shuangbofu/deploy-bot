package top.fusb.deploybot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.ToString;
import top.fusb.deploybot.dto.DeploymentRestrictionPolicyConfig;
import top.fusb.deploybot.model.converter.DeploymentRestrictionPolicyListJsonConverter;

import java.util.ArrayList;
import java.util.List;

/**
 * 系统级设置。
 * 目前主要承载 Git 可执行文件以及系统级 SSH 密钥对。
 */
@Data
@Entity
@Table(name = "system_settings")
@ToString(exclude = {
        "gitPassword",
        "gitSshPrivateKey",
        "gitSshPublicKey",
        "gitSshKnownHosts",
        "hostSshPrivateKey",
        "hostSshPublicKey"
})
public class SystemSettingsEntity {

    @Id
    private Long id = 1L;

    /** 系统默认工作空间目录。 */
    @Column(length = 1000)
    private String workspaceRoot;

    /** 系统级 Git 认证方式。 */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private GitAuthType gitAuthType = GitAuthType.NONE;

    /** 系统级 Git 用户名。 */
    @Column(length = 200)
    private String gitUsername;

    /** 系统级 Git 密码。 */
    @Column(length = 1000)
    private String gitPassword;

    /** Git 可执行文件路径。 */
    @Column(length = 1000)
    private String gitExecutable = "git";

    /** 系统级 Git SSH 私钥。 */
    @Column(length = 8000)
    private String gitSshPrivateKey;

    /** 系统级 Git SSH 公钥。 */
    @Column(length = 8000)
    private String gitSshPublicKey;

    /** 系统级 Git known_hosts。 */
    @Column(length = 4000)
    private String gitSshKnownHosts;

    /** 系统级主机 SSH 私钥。 */
    @Column(length = 8000)
    private String hostSshPrivateKey;

    /** 系统级主机 SSH 公钥。 */
    @Column(length = 8000)
    private String hostSshPublicKey;

    /** 是否启用部署目录自动清理。 */
    private Boolean cleanupEnabled;

    /** 每条流水线保留最近成功产物数量。 */
    private Integer artifactRetainSuccessCount;

    /** 成功后是否立即清理 runs 工作区。 */
    private Boolean cleanRunsOnSuccess;

    /** 失败/停止的 runs 工作区保留天数，0 表示结束后立即清理。 */
    private Integer failedRunRetainDays;

    /** 部署限制策略。 */
    @Convert(converter = DeploymentRestrictionPolicyListJsonConverter.class)
    @Column(columnDefinition = "CLOB")
    private List<DeploymentRestrictionPolicyConfig> deploymentRestrictionPolicies = new ArrayList<>();
}
