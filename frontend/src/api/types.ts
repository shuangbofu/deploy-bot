import type {
  DashboardAnalytics,
  DashboardAnalyticsQuery,
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

export type { DashboardAnalytics, DashboardAnalyticsQuery };

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

export type ApiTokenScope = 'READ' | 'PROJECT_WRITE' | 'TEMPLATE_WRITE' | 'PIPELINE_WRITE' | 'DEPLOYMENT_RUN' | 'ADMIN';

export interface ApiTokenSummary {
  id: number;
  name: string;
  tokenPrefix: string;
  userId?: number | null;
  username?: string | null;
  displayName?: string | null;
  scopes: ApiTokenScope[];
  expiresAt?: string | null;
  lastUsedAt?: string | null;
  lastUsedIp?: string | null;
  enabled: boolean;
  createdAt: string;
  revokedAt?: string | null;
}

export interface ApiTokenCreatePayload {
  name: string;
  userId?: number;
  scopes: ApiTokenScope[];
  expiresAt?: string | null;
}

export interface ApiTokenCreateResult {
  token: string;
  summary: ApiTokenSummary;
}

export interface ApiTokenUpdatePayload {
  name?: string;
  enabled?: boolean;
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
  importantTags?: string[];
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

export type DeploymentRestrictionScopeType = 'GLOBAL' | 'PROJECT' | 'PIPELINE';

export interface DeploymentRestrictionWeeklyWindow {
  daysOfWeek: number[];
  startTime?: string | null;
  endTime?: string | null;
  startMinute?: number | null;
  endMinute?: number | null;
}

export interface DeploymentRestrictionPolicyConfig {
  id: string;
  name: string;
  enabled: boolean;
  scopeType: DeploymentRestrictionScopeType;
  scopeIds?: number[];
  weeklyWindows?: DeploymentRestrictionWeeklyWindow[];
  reason?: string | null;
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

export interface DeploymentPrecheckResult {
  passed: boolean;
  missingItems: DeploymentPrecheckMissingItem[];
}

export interface DeploymentPrecheckMissingItem {
  code: string;
  name?: string | null;
  label?: string | null;
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
  deploymentRestrictionPolicies?: DeploymentRestrictionPolicyConfig[];
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
  resultCode: string;
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
  deploymentRestrictionPolicies?: DeploymentRestrictionPolicyConfig[];
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
