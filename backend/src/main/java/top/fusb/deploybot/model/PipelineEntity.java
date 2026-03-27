package top.fusb.deploybot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 流水线是平台最核心的可执行对象。
 * 它绑定项目、模板、目标主机以及默认变量。
 */
@Data
@Entity
@Table(name = "pipelines")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"project", "template", "targetHost", "javaEnvironment", "nodeEnvironment", "mavenEnvironment", "runtimeJavaEnvironment"})
public class PipelineEntity {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 流水线名称。 */
    @Column(nullable = false, unique = true)
    private String name;

    /** 流水线说明。 */
    @Column(length = 1000)
    private String description;

    /** 关联项目。 */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "project_id")
    private ProjectEntity project;

    /** 关联模板。 */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "template_id")
    private TemplateEntity template;

    /** 部署目标主机。 */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "target_host_id")
    private HostEntity targetHost;

    /** 默认分支。 */
    @Column(nullable = false)
    private String defaultBranch;

    /** 默认变量 JSON。 */
    @Column(length = 4000)
    private String variablesJson;

    /** 自定义标签 JSON，用于大厅分组和快速筛选。 */
    @Column(length = 2000)
    private String tagsJson;

    /** 本机构建 Java 环境。 */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "java_environment_id")
    private RuntimeEnvironmentEntity javaEnvironment;

    /** 本机构建 Node 环境。 */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "node_environment_id")
    private RuntimeEnvironmentEntity nodeEnvironment;

    /** 本机构建 Maven 环境。 */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "maven_environment_id")
    private RuntimeEnvironmentEntity mavenEnvironment;

    /** 目标主机运行 Java 环境。 */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "runtime_java_environment_id")
    private RuntimeEnvironmentEntity runtimeJavaEnvironment;

    /** 启用服务监测时使用的启动关键字，用于更精准判断业务是否真正启动成功。 */
    @Column(name = "process_keyword", length = 500)
    private String startupKeyword;

    /** 启用服务监测时的启动观察窗口，单位秒。 */
    private Integer startupTimeoutSeconds;
}
