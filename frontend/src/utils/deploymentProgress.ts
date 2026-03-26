import { DEPLOYMENT_STATUS_META } from '../constants/deployment';
import type { DeploymentStatus, DeploymentSummary } from '../types/domain';

/**
 * 优先使用后端计算好的进度，避免前端重复推导构建/发布阶段细节。
 */
export function getDeploymentProgress(deployment?: DeploymentSummary | null) {
  if (!deployment) {
    return 0;
  }

  if (typeof deployment.progressPercent === 'number') {
    return deployment.progressPercent;
  }

  if (deployment.status === 'SUCCESS') {
    return 100;
  }

  return 0;
}

/**
 * 进度条颜色与状态点颜色保持同一套映射，避免出现“状态已失败但进度还是蓝色”的割裂感。
 */
export function getDeploymentProgressColor(status?: DeploymentStatus | null) {
  if (!status) {
    return DEPLOYMENT_STATUS_META.PENDING.color;
  }
  return DEPLOYMENT_STATUS_META[status]?.color || DEPLOYMENT_STATUS_META.PENDING.color;
}
