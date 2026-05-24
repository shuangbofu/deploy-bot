/**
 * 部署状态由后端统一定义，前端仅做展示映射。
 */
export type DeploymentStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'STOPPED';

/**
 * 主机类型决定脚本是本地执行还是通过 SSH 下发到远端执行。
 */
export type HostType = 'LOCAL' | 'SSH';

/**
 * 项目的 Git 认证方式。
 */
export type GitAuthType = 'NONE' | 'BASIC' | 'SSH';

/**
 * 远程主机登录认证方式。
 */
export type HostSshAuthType = 'PASSWORD' | 'PRIVATE_KEY' | 'SYSTEM_KEY_PAIR';

/**
 * 运行环境组件类型由后端和插件共同声明，前端不要限制为固定技术栈。
 */
export type RuntimeEnvironmentType = string;
export type UserRole = 'ADMIN' | 'USER';
export type NotificationChannelType = 'FEISHU';
export type NotificationEventType = 'DEPLOYMENT_STARTED' | 'DEPLOYMENT_FINISHED';
export type NotificationTemplateMode = 'TEXT' | 'FEISHU_CARD';
export type ServiceStatus = 'RUNNING' | 'STOPPED';
export type PluginFormFieldType = 'TEXT' | 'TEXTAREA' | 'CODE' | 'SELECT' | 'SWITCH';
export type PluginFormFieldBindingScope = 'PLUGIN_CONFIG' | 'PIPELINE_VARIABLE';
export type TemplateVariableBindingScope = 'COMMAND_SLOT';
export type PluginVariableMutationType = 'VARIABLE_INJECT' | 'COMMAND_REWRITE' | 'COMPOSITE_REUSE';
export type PluginVariableTriggerMode = 'ALL_PRESENT' | 'ANY_PRESENT';
export type PluginValueReferenceScope = 'CONTEXT' | 'COMMAND_SLOT' | 'VARIABLE';
export type ServicePidChangeSource =
  | 'DEPLOYMENT_CONFIRMED'
  | 'PRE_DEPLOY_STOP'
  | 'MANUAL_BIND'
  | 'MANUAL_STOP'
  | 'HEARTBEAT_STOPPED'
  | 'HEARTBEAT_RECOVERED';

export interface MavenSettingsSummary {
  id: number;
  name: string;
  description?: string;
  contentXml: string;
  enabled: boolean;
  isDefault?: boolean;
  runtimeEnvironment?: RuntimeEnvironmentSummary | null;
}

export interface NotificationWebhookConfigSummary {
  id: number;
  name: string;
  description?: string;
  type: NotificationChannelType;
  webhookUrl?: string;
  secret?: string;
  enabled: boolean;
}

/**
 * 模板变量按照构建、发布、共用三个阶段分组展示。
 */
export type TemplateVariablePhase = 'build' | 'deploy' | 'shared';

export interface ProjectSummary {
  /** 项目主键。 */
  id: number;
  /** 项目名称。 */
  name: string;
  /** 项目描述。 */
  description?: string;
  /** 仓库地址。 */
  gitUrl?: string;
  /** 仓库认证方式。 */
  gitAuthType?: GitAuthType;
}

export interface ProjectConnectionTestResult {
  /** 测试是否成功。 */
  success: boolean;
  /** 测试说明。 */
  message: string;
  /** 实际使用的 Git 地址。 */
  gitUrl: string;
  /** 实际使用的认证方式。 */
  gitAuthType: GitAuthType;
  /** Git 命令输出摘要。 */
  output?: string;
}

export interface HostSummary {
  /** 主机主键。 */
  id: number;
  /** 主机名称。 */
  name: string;
  /** 主机说明。 */
  description?: string;
  /** 主机类型，本机或 SSH 远程主机。 */
  type: HostType;
  /** 远程主机地址。 */
  hostname?: string;
  /** SSH 端口。 */
  port?: number;
  /** SSH 登录用户名。 */
  username?: string;
  /** 远程主机 SSH 认证方式。 */
  sshAuthType?: HostSshAuthType;
  /** SSH 密码，仅编辑时由后端回填。 */
  sshPassword?: string;
  /** SSH 私钥内容，仅编辑时由后端回填。 */
  sshPrivateKey?: string;
  /** SSH 私钥口令。 */
  sshPassphrase?: string;
  /** 主机工作空间目录。 */
  workspaceRoot?: string;
  /** SSH known_hosts 内容。 */
  sshKnownHosts?: string;
  /** 主机是否启用。 */
  enabled?: boolean;
  /** 是否为系统内置主机。 */
  builtIn?: boolean;
}

export interface RuntimeEnvironmentSummary {
  /** 运行环境主键。 */
  id: number;
  /** 运行环境名称。 */
  name: string;
  /** 运行环境版本。 */
  version?: string;
  /** 运行环境类型，例如 Java / Node / Maven。 */
  type: RuntimeEnvironmentType;
  /** 是否启用。 */
  enabled?: boolean;
  /** 归属主机。 */
  host?: HostSummary | null;
  homePath?: string;
  binPath?: string;
  activationScript?: string;
  environment?: Record<string, unknown>;
}

export interface TemplateVariableDefinition {
  /** 模板变量名，对应脚本里的 {{variableName}}。 */
  name: string;
  /** 表单显示名称。 */
  label?: string;
  /** 是否必填。 */
  required?: boolean;
  /** 是否允许在流水线层面填写。 */
  pipelineInput?: boolean;
  /** 输入框占位提示。 */
  placeholder?: string;
  /** 变量所属阶段。 */
  phase?: TemplateVariablePhase;
  /** 该变量默认挂载的自动改写规则标识。 */
  mutationRuleIds?: string[];
  /** 该变量绑定的平台标准语义。 */
  binding?: {
    scope: TemplateVariableBindingScope;
    key: string;
  };
}

export interface TemplateSummary {
  /** 模板主键。 */
  id: number;
  /** 模板名称。 */
  name: string;
  /** 模板说明。 */
  description?: string;
  /** 模板类型，用于图标和环境依赖推导。 */
  templateType?: string;
  /** 归属插件标识。 */
  pluginId?: string | null;
  /** 模板变量定义，接口里可能是 JSON 字符串也可能已被解析。 */
  variablesSchema?: string | TemplateVariableDefinition[];
  /** 构建脚本。 */
  buildScriptContent?: string;
  /** 发布脚本。 */
  deployScriptContent?: string;
  /** 发布后是否需要监控进程。 */
  monitorProcess?: boolean;
}

export interface PluginDescriptorSummary {
  pluginId: string;
  displayName: string;
  category: string;
  templateTypes: string[];
  composite: boolean;
  builtin: boolean;
  provider: string;
  description: string;
}

export interface PluginRuntimeRequirementSummary {
  buildRuntimeTypes: string[];
  targetRuntimeTypes: string[];
}

export interface PluginFormOptionSummary {
  value: string;
  label: string;
}

export interface PluginFormFieldBindingSummary {
  scope: PluginFormFieldBindingScope;
  key: string;
}

export interface PluginFormFieldSummary {
  key: string;
  label: string;
  type: PluginFormFieldType;
  required: boolean;
  placeholder?: string | null;
  helpText?: string | null;
  options: PluginFormOptionSummary[];
  binding?: PluginFormFieldBindingSummary | null;
}

export interface PluginFormSectionSummary {
  key: string;
  title: string;
  description?: string | null;
  fields: PluginFormFieldSummary[];
}

export interface PluginFormSchemaSummary {
  sections: PluginFormSectionSummary[];
}

export interface PluginVariableDefinitionSummary {
  key: string;
  label: string;
  description?: string | null;
  required: boolean;
  secret: boolean;
  pipelineInput: boolean;
  defaultValue?: string | null;
  mutationRuleIds?: string[];
  binding?: {
    scope: TemplateVariableBindingScope;
    key: string;
  } | null;
}

export interface PluginVariableTriggerGroupSummary {
  mode: PluginVariableTriggerMode;
  references: PluginValueReferenceSummary[];
}

export interface PluginValueReferenceSummary {
  scope: PluginValueReferenceScope;
  key: string;
  label?: string | null;
}

export interface PluginVariableMutationRuleSummary {
  ruleId?: string | null;
  phase: 'BUILD' | 'DEPLOY';
  mutationType: PluginVariableMutationType;
  sourceReferences: PluginValueReferenceSummary[];
  targetReferences: PluginValueReferenceSummary[];
  generatedReferences: PluginValueReferenceSummary[];
  triggerGroups: PluginVariableTriggerGroupSummary[];
}

export interface PluginBuiltinTemplateSummary {
  templateKey?: string | null;
  name: string;
  description?: string | null;
  templateType?: string | null;
  buildScriptContent?: string | null;
  deployScriptContent?: string | null;
  variablesSchema?: string | null;
  monitorProcess?: boolean | null;
}

export interface DeploymentPluginDefinitionSummary {
  descriptor: PluginDescriptorSummary;
  runtimeRequirement: PluginRuntimeRequirementSummary;
  pipelineFormSchema: PluginFormSchemaSummary;
  variableDefinitions: PluginVariableDefinitionSummary[];
  variableMutationRules: PluginVariableMutationRuleSummary[];
  builtinTemplates: PluginBuiltinTemplateSummary[];
}

export interface ShellVariableSummary {
  key: string;
  expression: string;
  description: string;
  stage: 'BUILD' | 'DEPLOY' | 'ALL';
  source: 'PLATFORM' | 'RUNTIME' | 'PLUGIN';
  contextKey?: string | null;
}

export interface PipelineSummary {
  /** 流水线主键。 */
  id: number;
  /** 流水线名称。 */
  name: string;
  /** 流水线说明。 */
  description?: string;
  /** 默认分支。 */
  defaultBranch?: string;
  /** 关联项目。 */
  project?: ProjectSummary | null;
  /** 关联模板。 */
  template?: TemplateSummary | null;
  /** 选用插件默认模板时的插件标识。 */
  templatePluginId?: string | null;
  /** 选用插件默认模板时的模板键。 */
  builtinTemplateKey?: string | null;
  /** 模板名称快照。 */
  templateNameSnapshot?: string | null;
  /** 模板类型快照。 */
  templateTypeSnapshot?: string | null;
  /** 模板监控进程开关快照。 */
  templateMonitorProcess?: boolean | null;
  /** 目标主机。 */
  targetHost?: HostSummary | null;
  /** 目标主机上的部署目录。 */
  targetDir?: string | null;
  /** 本机构建 Java 环境。 */
  javaEnvironment?: RuntimeEnvironmentSummary | null;
  /** 本机构建 Node 环境。 */
  nodeEnvironment?: RuntimeEnvironmentSummary | null;
  /** 本机构建 Maven 环境。 */
  mavenEnvironment?: RuntimeEnvironmentSummary | null;
  /** 本机构建时使用的 Maven Settings。 */
  mavenSettings?: MavenSettingsSummary | null;
  /** 目标主机 Java 运行环境。 */
  runtimeJavaEnvironment?: RuntimeEnvironmentSummary | null;
  /** 插件运行配置，前端按插件字段 key 读写。 */
  pluginConfig?: Record<string, string> | null;
  /** 启用服务监测时的启动关键字。 */
  startupKeyword?: string | null;
  /** 启用服务监测时的启动观察窗口，单位秒。 */
  startupTimeoutSeconds?: number | null;
  /** 流水线绑定的通知配置。 */
  notificationBindings?: NotificationBinding[];
  /** 流水线默认变量。 */
  variables?: Record<string, string>;
  /** 自定义标签。 */
  tags?: string[];
}

export interface PipelineHallSummary {
  pipelineId: number;
  pipelineName: string;
  pipelineDescription?: string | null;
  defaultBranch?: string | null;
  projectName?: string | null;
  templateType?: string | null;
  tags?: string[] | null;
  latestDeploymentId?: number | null;
  latestDeploymentOrder?: number | null;
  latestStatus?: DeploymentStatus | null;
  latestBranchName?: string | null;
  latestTriggeredBy?: string | null;
  latestTriggeredByDisplayName?: string | null;
  latestCreatedAt?: string | null;
  latestStartedAt?: string | null;
  latestFinishedAt?: string | null;
  latestProgressPercent?: number | null;
  latestProgressText?: string | null;
  favorited?: boolean | null;
}

export interface PipelineHallRunningServiceSummary {
  serviceId: number;
  pipelineId?: number | null;
  pipelineName?: string | null;
  serviceName?: string | null;
  templateType?: string | null;
  targetHostName?: string | null;
  currentPid?: number | null;
  status?: ServiceStatus | null;
  activeSince?: string | null;
  lastHeartbeatAt?: string | null;
}

export interface NotificationBinding {
  notificationId: number;
  eventType: NotificationEventType;
}

export interface NotificationChannelSummary {
  id: number;
  name: string;
  description?: string;
  type: NotificationChannelType;
  eventType: NotificationEventType;
  webhookConfig?: NotificationWebhookConfigSummary | null;
  template?: NotificationTemplateSummary | null;
  messageTemplate?: string;
  enabled: boolean;
}

export interface NotificationTemplateSummary {
  id: number;
  name: string;
  description?: string;
  templateMode: NotificationTemplateMode;
  messageTemplate: string;
  builtIn: boolean;
  enabled: boolean;
}

export interface NotificationDeliveryRecordSummary {
  id: number;
  channel?: NotificationChannelSummary | null;
  deployment?: DeploymentSummary | null;
  eventType: NotificationEventType;
  status: 'SUCCESS' | 'FAILED';
  channelName?: string;
  pipelineName?: string;
  message?: string;
  responseMessage?: string;
  errorMessage?: string;
  createdAt?: string;
  finishedAt?: string;
}

export interface UserSummary {
  /** 用户主键。 */
  id: number;
  /** 登录用户名。 */
  username: string;
  /** 展示名称。 */
  displayName: string;
  /** 用户头像。 */
  avatar?: string;
  /** 用户角色。 */
  role: UserRole;
  /** 是否启用。 */
  enabled: boolean;
}

export interface DeploymentSummary {
  /** 部署记录主键。 */
  id: number;
  /** 部署创建时固化的流水线名称。 */
  pipelineName?: string | null;
  /** 部署创建时固化的项目名称。 */
  projectName?: string | null;
  /** 本次部署使用的分支。 */
  branchName?: string;
  /** 触发人。 */
  triggeredBy?: string;
  /** 触发人展示名称。 */
  triggeredByDisplayName?: string;
  /** 部署状态。 */
  status?: DeploymentStatus;
  /** 创建时间。 */
  createdAt?: string;
  /** 开始执行时间。 */
  startedAt?: string;
  /** 结束时间。 */
  finishedAt?: string;
  /** 日志文件路径。 */
  logPath?: string | null;
  /** 错误信息。 */
  errorMessage?: string | null;
  /** 后端计算好的进度百分比。 */
  progressPercent?: number | null;
  /** 后端计算好的进度文案，例如“构建 2/4”。 */
  progressText?: string | null;
  /** 当前阶段，例如 BUILD / DEPLOY / ROLLBACK。 */
  progressStage?: string | null;
  /** 关联流水线。 */
  pipeline?: PipelineSummary | null;
  /** 本次部署保留下来的构建产物目录，可用于重新发布同一版本。 */
  artifactPath?: string | null;
  /** 本次部署创建时固化的执行快照。 */
  executionSnapshot?: Record<string, unknown> | null;
  /** 若这是重新发布历史版本的任务，则记录来源部署 ID。 */
  rollbackFromDeploymentId?: number | null;
  /** 被监控的进程 PID。 */
  monitoredPid?: number | null;
}

export interface UserRecentPipelineSummary {
  pipelineId: number;
  pipelineName: string;
  projectName?: string | null;
  defaultBranch?: string | null;
  templateType?: string | null;
  count: number;
  latestDeploymentAt?: string | null;
}

export interface ServiceSummary {
  /** 服务主键。 */
  id: number;
  /** 服务展示名称。 */
  serviceName: string;
  /** 当前进程 PID。 */
  currentPid: number | null;
  /** 服务状态。 */
  status: ServiceStatus;
  /** 最近更新时间。 */
  updatedAt: string;
  /** 当前这次运行开始被系统接管的时间。 */
  activeSince: string | null;
  /** 最近一次心跳确认仍然存活的时间。 */
  lastHeartbeatAt: string | null;
  /** 关联流水线。 */
  pipeline: PipelineSummary | null;
  /** 最近一次部署。 */
  lastDeployment: DeploymentSummary | null;
}

export interface ServicePidHistorySummary {
  id: number;
  serviceId: number;
  deploymentId: number | null;
  previousPid: number | null;
  currentPid: number | null;
  previousStatus: ServiceStatus | null;
  currentStatus: ServiceStatus;
  changeSource: ServicePidChangeSource;
  note: string | null;
  createdAt: string;
}

export interface HostConnectionTestResult {
  /** 测试是否成功。 */
  success: boolean;
  /** 测试结果说明。 */
  message?: string;
  /** 远程返回的实际登录用户。 */
  remoteUser?: string;
  /** 远程主机名。 */
  remoteHost?: string;
  /** 检测时使用的工作空间目录。 */
  workspaceRoot?: string;
}

export interface HostResourceSnapshot {
  /** 主机主键。 */
  hostId: number;
  /** 主机名称。 */
  hostName: string;
  /** 采集时间。 */
  collectedAt?: string;
  /** 操作系统类型。 */
  osType?: string;
  /** 工作空间目录。 */
  workspaceRoot?: string;
  /** CPU 核数。 */
  cpuCores?: number | null;
  /** CPU 使用率。 */
  cpuUsagePercent?: number | null;
  /** 1 分钟平均负载。 */
  loadAverage?: number | null;
  /** 内存总量（MB）。 */
  memoryTotalMb?: number | null;
  /** 已用内存（MB）。 */
  memoryUsedMb?: number | null;
  /** 内存使用率。 */
  memoryUsagePercent?: number | null;
  /** 磁盘总量（GB）。 */
  diskTotalGb?: number | null;
  /** 已用磁盘（GB）。 */
  diskUsedGb?: number | null;
  /** 磁盘使用率。 */
  diskUsagePercent?: number | null;
  /** 预览文本。 */
  preview?: string;
}

export interface DashboardTrendItem {
  /** 日期键值，通常为 YYYY-MM-DD。 */
  key: string;
  /** 图表横轴显示文案。 */
  label: string;
  /** 当天部署总数。 */
  total: number;
  /** 当天成功数。 */
  success: number;
}

export interface DashboardDeploymentSummary {
  id: number;
  pipelineName?: string | null;
  projectName?: string | null;
  branchName?: string | null;
  triggeredBy?: string | null;
  triggeredByDisplayName?: string | null;
  status?: DeploymentStatus | null;
  createdAt?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
  progressPercent?: number | null;
  progressText?: string | null;
}

export interface DashboardServiceSummary {
  id: number;
  serviceName?: string | null;
  status?: string | null;
  pipelineName?: string | null;
  targetHostName?: string | null;
  updatedAt?: string | null;
}

export interface DashboardStatsSummary {
  projects: number;
  templates: number;
  pipelines: number;
  deployments: number;
  hosts: number;
  services: number;
  users: number;
  runningServices: number;
  successRate: number;
  runningDeployments: number;
  failedDeployments: number;
}

export interface DashboardSummary {
  stats: DashboardStatsSummary;
  trend: DashboardTrendItem[];
  latestDeployments: DashboardDeploymentSummary[];
  attentionDeployments: DashboardDeploymentSummary[];
  services: DashboardServiceSummary[];
}

export interface DeploymentRecordFilters {
  /** 项目名称筛选。 */
  projectName?: string;
  /** 流水线名称筛选。 */
  pipelineName?: string;
  /** 触发人筛选。 */
  triggeredBy: string;
  /** 状态筛选。 */
  status?: DeploymentStatus;
  /** 时间区间筛选。 */
  timeRange?: unknown;
}

export interface PipelineHistoryFilters {
  /** 分支名筛选。 */
  branchName: string;
  /** 触发人筛选。 */
  triggeredBy: string;
  /** 状态筛选。 */
  status?: DeploymentStatus;
  /** 时间区间筛选。 */
  timeRange?: unknown;
}
