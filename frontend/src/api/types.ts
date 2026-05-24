import type {
  DashboardDeploymentSummary,
  DashboardServiceSummary,
  DashboardStatsSummary,
  DashboardSummary,
  DashboardTrendItem,
  DeploymentSummary,
  HostConnectionTestResult,
  HostResourceSnapshot,
  HostSummary,
  NotificationBinding,
  NotificationChannelSummary,
  NotificationWebhookConfigSummary,
  NotificationDeliveryRecordSummary,
  NotificationTemplateSummary,
  NotificationTemplateMode,
  MavenSettingsSummary,
  PipelineHallSummary,
  PipelineSummary,
  ProjectConnectionTestResult,
  ProjectSummary,
  RuntimeEnvironmentType,
  RuntimeEnvironmentSummary,
  ServiceSummary,
  ServicePidHistorySummary,
  TemplateSummary,
  UserSummary,
} from '../types/domain';

/**
 * 后端统一响应包装结构。
 */
export interface ApiResult<T> {
  success: boolean;
  code: string;
  message: string;
  subCode?: string | null;
  subMessage?: string | null;
  data: T;
  timestamp: string;
}

export interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
}

export interface DeploymentFilterOptions {
  projectNames: string[];
  pipelineNames: string[];
}

/**
 * 项目表单请求体。
 */
export interface ProjectPayload {
  name: string;
  description: string;
  gitUrl: string;
  gitAuthType: 'NONE' | 'BASIC' | 'SSH';
  gitUsername: string;
  gitPassword: string;
}

/**
 * 模板表单请求体。
 */
export interface TemplatePayload {
  name: string;
  description: string;
  templateType?: string;
  pluginId?: string;
  buildScriptContent?: string;
  deployScriptContent?: string;
  variablesSchema: string;
  monitorProcess?: boolean;
}

/**
 * 流水线表单请求体。
 */
export interface PipelinePayload {
  name: string;
  description: string;
  projectId?: number;
  templateId?: number;
  templatePluginId?: string;
  builtinTemplateKey?: string;
  targetHostId?: number;
  targetDir: string;
  defaultBranch: string;
  variables: Record<string, string>;
  tags: string[];
  javaEnvironmentId?: number;
  nodeEnvironmentId?: number;
  mavenEnvironmentId?: number;
  mavenSettingsId?: number;
  runtimeJavaEnvironmentId?: number;
  pluginConfig?: Record<string, string>;
  startupKeyword?: string;
  startupTimeoutSeconds?: number;
  notificationBindings?: Array<{ notificationId: number; eventType: string }>;
}

export interface NotificationPayload {
  name: string;
  description?: string;
  type: 'FEISHU';
  eventType: 'DEPLOYMENT_STARTED' | 'DEPLOYMENT_FINISHED';
  webhookConfigId?: number;
  templateId?: number;
  messageTemplate?: string;
  enabled: boolean;
}

export interface NotificationWebhookConfigPayload {
  name: string;
  description?: string;
  type: 'FEISHU';
  webhookUrl: string;
  secret?: string;
  enabled: boolean;
}

export interface NotificationTemplatePayload {
  name: string;
  description?: string;
  templateMode: NotificationTemplateMode;
  messageTemplate: string;
  enabled: boolean;
}

export interface MavenSettingsPayload {
  name: string;
  description?: string;
  contentXml: string;
  enabled: boolean;
  isDefault?: boolean;
}

export interface LoginPayload {
  username: string;
  password: string;
}

export interface ChangePasswordPayload {
  currentPassword: string;
  newPassword: string;
}

export interface LoginResponse {
  token: string;
  user: UserSummary;
}

export interface UserPayload {
  username: string;
  displayName: string;
  avatar?: string;
  role: 'ADMIN' | 'USER';
  enabled: boolean;
}

export interface AvatarUploadResponse {
  url: string;
}

/**
 * 主机表单请求体。
 */
export interface HostPayload {
  name: string;
  type: 'LOCAL' | 'SSH';
  description: string;
  hostname: string;
  port: number;
  username: string;
  sshAuthType: 'PASSWORD' | 'PRIVATE_KEY' | 'SYSTEM_KEY_PAIR';
  sshPassword: string;
  sshPrivateKey: string;
  sshPassphrase: string;
  workspaceRoot: string;
  sshKnownHosts: string;
  enabled: boolean;
}

/**
 * 运行环境表单请求体。
 */
export interface RuntimeEnvironmentPayload {
  name: string;
  type: RuntimeEnvironmentType;
  hostId?: number;
  version: string;
  homePath: string;
  binPath: string;
  activationScript: string;
  environment: Record<string, unknown>;
  enabled: boolean;
}

/**
 * 部署创建请求体。
 */
export interface DeploymentPayload {
  pipelineId: number;
  branchName?: string;
  triggeredBy: string;
  replaceRunning: boolean;
}

/**
 * 系统设置表单请求体。
 */
export interface SystemSettingsPayload {
  gitExecutable: string;
  gitSshPublicKey?: string;
  gitSshKnownHosts?: string;
  hostSshPublicKey?: string;
  cleanupEnabled?: boolean;
  artifactRetainSuccessCount?: number;
  cleanRunsOnSuccess?: boolean;
  failedRunRetainDays?: number;
}

export interface LogResponse {
  content: string;
}

export interface DetectionItem {
  name: string;
  type: RuntimeEnvironmentType;
  version: string;
  homePath?: string;
  binPath?: string;
}

export interface PresetItem {
  id: string;
  name: string;
  type: RuntimeEnvironmentType;
  version: string;
  description: string;
  downloadUrl: string;
  homePath: string;
  binPath: string;
}

export interface RuntimeEnvironmentInstallPayload {
  presetId: string;
  hostId: number;
}

export interface RuntimeEnvironmentInstallAccepted {
  accepted: boolean;
  message: string;
  taskId: string;
}

export interface RuntimeEnvironmentInstallTaskStatus {
  taskId: string;
  presetId: string;
  presetName: string;
  hostId: number;
  hostName: string;
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';
  message: string;
  runtimeEnvironmentId?: number | null;
  startedAt: string;
  finishedAt?: string | null;
}

export interface SystemSettingsResponse {
  gitExecutable?: string;
  gitSshPublicKey?: string;
  gitSshKnownHosts?: string;
  hostSshPublicKey?: string;
  cleanupEnabled?: boolean;
  artifactRetainSuccessCount?: number;
  cleanRunsOnSuccess?: boolean;
  failedRunRetainDays?: number;
}

export interface ServiceProcessSummary {
  pid: number;
  command?: string | null;
  commandLine?: string | null;
}

export type {
  DeploymentSummary,
  HostConnectionTestResult,
  HostResourceSnapshot,
  HostSummary,
  NotificationBinding,
  NotificationChannelSummary,
  NotificationWebhookConfigSummary,
  NotificationDeliveryRecordSummary,
  NotificationTemplateSummary,
  MavenSettingsSummary,
  PipelineSummary,
  ProjectConnectionTestResult,
  ProjectSummary,
  RuntimeEnvironmentSummary,
  RuntimeEnvironmentInstallAccepted,
  RuntimeEnvironmentInstallTaskStatus,
  ServiceSummary,
  ServicePidHistorySummary,
  TemplateSummary,
  AvatarUploadResponse,
  UserSummary,
};
