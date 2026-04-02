import type { DeploymentStatus } from '../types/domain';

/**
 * 正在运行中的部署状态，常用于“禁止重复提交”和轮询判断。
 */
export const ACTIVE_DEPLOYMENT_STATUSES: DeploymentStatus[] = ['PENDING', 'RUNNING'];

/**
 * 已结束部署状态，常用于成功率和记录统计。
 */
export const FINISHED_DEPLOYMENT_STATUSES: DeploymentStatus[] = ['SUCCESS', 'FAILED', 'STOPPED'];

/**
 * 所有状态统一在这里维护，避免页面里反复写死中英文映射。
 */
export const DEPLOYMENT_STATUS_META: Record<
  DeploymentStatus,
  { label: string; dotClass: string; color: string }
> = {
  PENDING: { label: '待执行', dotClass: 'status-dot--pending', color: '#94a3b8' },
  RUNNING: { label: '部署中', dotClass: 'status-dot--running', color: '#eab308' },
  STOPPED: { label: '已停止', dotClass: 'status-dot--stopped', color: '#f97316' },
  SUCCESS: { label: '成功', dotClass: 'status-dot--success', color: '#16a34a' },
  FAILED: { label: '失败', dotClass: 'status-dot--failed', color: '#dc2626' },
};

/**
 * 所有筛选器与表单统一复用这一份状态下拉选项。
 */
export const DEPLOYMENT_STATUS_OPTIONS = Object.entries(DEPLOYMENT_STATUS_META).map(
  ([value, meta]) => ({
    label: meta.label,
    value: value as DeploymentStatus,
  }),
);
