package top.fusb.deploybot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import top.fusb.deploybot.model.converter.NotificationBindingListJsonConverter;
import top.fusb.deploybot.model.converter.StringListJsonConverter;
import top.fusb.deploybot.model.converter.StringMapJsonConverter;
import top.fusb.deploybot.notification.dto.NotificationBinding;

import java.util.List;
import java.util.Map;

/**
 * 流水线是平台最核心的可执行对象。
 * 它绑定项目、模板、目标主机以及默认变量。
 */
@Data
@Entity
@Table(name = "pipelines")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"project", "template", "targetHost", "javaEnvironment", "nodeEnvironment", "mavenEnvironment", "runtimeJavaEnvironment", "mavenSettings"})
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
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "template_id")
    private TemplateEntity template;

    /** 选用插件默认模板时，对应的插件标识。 */
    @Column(name = "template_plugin_id", length = 100)
    private String templatePluginId;

    /** 选用插件默认模板时，对应的内置模板键。 */
    @Column(name = "builtin_template_key", length = 100)
    private String builtinTemplateKey;

    /** 模板名称快照，用于列表展示和历史快照。 */
    @Column(name = "template_name_snapshot", length = 255)
    private String templateNameSnapshot;

    /** 模板类型快照，用于图标、分类和插件兜底解析。 */
    @Column(name = "template_type_snapshot", length = 100)
    private String templateTypeSnapshot;

    /** 模板是否需要监控进程的快照。 */
    @Column(name = "template_monitor_process")
    private Boolean templateMonitorProcess = false;

    /** 部署目标主机。 */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "target_host_id")
    private HostEntity targetHost;

    /** 目标主机上的部署目录。 */
    @Column(name = "target_dir", length = 1000)
    private String targetDir;

    /** 默认分支。 */
    @Column(nullable = false)
    private String defaultBranch;

    /** 默认变量。 */
    @Convert(converter = StringMapJsonConverter.class)
    @Column(name = "variables_json", length = 4000)
    private Map<String, String> variables;

    /** 自定义标签，用于大厅分组和快速筛选。 */
    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "tags_json", length = 2000)
    private List<String> tags;

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

    /** 本机构建时可选的 Maven Settings。 */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "maven_settings_id")
    private MavenSettingsEntity mavenSettings;

    /** 目标主机运行 Java 环境。 */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "runtime_java_environment_id")
    private RuntimeEnvironmentEntity runtimeJavaEnvironment;

    /** 插件运行配置，前端按插件字段 key 读写，不感知具体插件字段含义。 */
    @Convert(converter = StringMapJsonConverter.class)
    @Lob
    @Column(name = "plugin_config_json")
    private Map<String, String> pluginConfig;

    /** 启用服务监测时使用的启动关键字，用于更精准判断业务是否真正启动成功。 */
    @Column(name = "process_keyword", length = 500)
    private String startupKeyword;

    /** 启用服务监测时的启动观察窗口，单位秒。 */
    private Integer startupTimeoutSeconds;

    /** 流水线绑定的通知配置。 */
    @Convert(converter = NotificationBindingListJsonConverter.class)
    @Lob
    @Column(name = "notification_bindings_json")
    private List<NotificationBinding> notificationBindings;
}
