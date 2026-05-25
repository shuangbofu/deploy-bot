import type { DashboardDeploymentSummary, DeploymentSummary } from '../types/domain';
import { ACTIVE_DEPLOYMENT_STATUSES } from '../constants/deployment';

/**
 * 部署耗时既用于已完成任务，也用于运行中任务的实时滚动展示。
 */
export function formatDeploymentElapsed(deployment?: DeploymentSummary | DashboardDeploymentSummary | null, now = Date.now()) {
  if (!deployment?.startedAt) {
    return '-';
  }

  const startedAt = new Date(deployment.startedAt).getTime();
  const isActive = deployment.status ? ACTIVE_DEPLOYMENT_STATUSES.includes(deployment.status) : false;
  const finishedAt = deployment.finishedAt
    ? new Date(deployment.finishedAt).getTime()
    : (isActive ? now : startedAt);
  if (Number.isNaN(startedAt) || Number.isNaN(finishedAt) || finishedAt < startedAt) {
    return '-';
  }

  const totalSeconds = Math.floor((finishedAt - startedAt) / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;

  if (hours > 0) {
    return `${hours}小时${minutes}分${seconds}秒`;
  }
  if (minutes > 0) {
    return `${minutes}分${seconds}秒`;
  }
  return `${seconds}秒`;
}
