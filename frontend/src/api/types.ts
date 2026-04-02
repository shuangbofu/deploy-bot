import type {
  DeploymentSummary,
  HostConnectionTestResult,
  HostResourceSnapshot,
  HostSummary,
  PipelineSummary,
  ProjectConnectionTestResult,
  ProjectSummary,
  RuntimeEnvironmentSummary,
  ServiceSummary,
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
  targetHostId?: number;
  defaultBranch: string;
  variablesJson: string;
  tagsJson: string;
  javaEnvironmentId?: number;
  nodeEnvironmentId?: number;
  mavenEnvironmentId?: number;
  runtimeJavaEnvironmentId?: number;
  applicationName?: string;
  springProfile?: string;
  runtimeConfigYaml?: string;
  startupKeyword?: string;
  startupTimeoutSeconds?: number;
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
  password?: string;
  role: 'ADMIN' | 'USER';
  enabled: boolean;
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
  type: 'JAVA' | 'NODE' | 'MAVEN';
  hostId?: number;
  version: string;
  homePath: string;
  binPath: string;
  activationScript: string;
  environmentJson: string;
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
}

export interface LogResponse {
  content: string;
}

export interface DetectionItem {
  name: string;
  type: 'JAVA' | 'NODE' | 'MAVEN';
  version: string;
  homePath?: string;
  binPath?: string;
}

export interface PresetItem {
  id: string;
  name: string;
  type: 'JAVA' | 'NODE' | 'MAVEN';
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
}

export interface SystemSettingsResponse {
  gitExecutable?: string;
  gitSshPublicKey?: string;
  gitSshKnownHosts?: string;
  hostSshPublicKey?: string;
}

export type {
  DeploymentSummary,
  HostConnectionTestResult,
  HostResourceSnapshot,
  HostSummary,
  PipelineSummary,
  ProjectConnectionTestResult,
  ProjectSummary,
  RuntimeEnvironmentSummary,
  RuntimeEnvironmentInstallAccepted,
  ServiceSummary,
  TemplateSummary,
  UserSummary,
};
