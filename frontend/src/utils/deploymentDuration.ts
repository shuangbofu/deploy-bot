import type { DeploymentSummary } from '../types/domain';

/**
 * 部署耗时既用于已完成任务，也用于运行中任务的实时滚动展示。
 */
export function formatDeploymentElapsed(deployment?: DeploymentSummary | null, now = Date.now()) {
  if (!deployment?.startedAt) {
    return '-';
  }

  const startedAt = new Date(deployment.startedAt).getTime();
  const finishedAt = deployment.finishedAt ? new Date(deployment.finishedAt).getTime() : now;
  if (Number.isNaN(startedAt) || Number.isNaN(finishedAt) || finishedAt < startedAt) {
    return '-';
  }

  const totalSeconds = Math.floor((finishedAt - startedAt) / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;

  if (hours > 0) {
    return `${hours}小时 ${minutes}分 ${seconds}秒`;
  }
  if (minutes > 0) {
    return `${minutes}分 ${seconds}秒`;
  }
  return `${seconds}秒`;
}
