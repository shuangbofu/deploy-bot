import { DEPLOYMENT_STATUS_META } from '../constants/deployment';
import type { DeploymentStatus, DeploymentSummary } from '../types/domain';

const RUNNING_PROGRESS_START = [253, 224, 71] as const;
const RUNNING_PROGRESS_END = [22, 163, 74] as const;
const RUNNING_PROGRESS_STOPS = 6;
const PROGRESS_STAGE_LABEL: Record<string, string> = {
  BUILD: '构建',
  DEPLOY: '发布',
  STARTUP: '启动',
  ROLLBACK: '回滚',
};

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

export function getDeploymentProgressLabel(
  progress: number,
  status?: DeploymentStatus | null,
  progressStage?: string | null,
  progressCurrent?: number | null,
  progressTotal?: number | null,
) {
  if (status === 'RUNNING' && progressStage === 'STARTUP') {
    return '等待启动';
  }
  const stageLabel = getProgressStageLabel(progressStage);
  if (stageLabel && typeof progressCurrent === 'number' && typeof progressTotal === 'number' && progressTotal > 0) {
    return `${stageLabel} ${progressCurrent}/${progressTotal}`;
  }
  if (status === 'RUNNING' && progressStage === 'DEPLOY') {
    return '准备发布';
  }
  if (status === 'RUNNING' && progressStage === 'BUILD') {
    return '准备构建';
  }
  if (status === 'RUNNING') {
    return '部署中';
  }
  return `${progress}%`;
}

function getProgressStageLabel(progressStage?: string | null) {
  return progressStage ? PROGRESS_STAGE_LABEL[progressStage] || null : null;
}

function interpolateChannel(start: number, end: number, ratio: number) {
  return Math.round(start + (end - start) * ratio);
}

function rgbToHex(red: number, green: number, blue: number) {
  return `#${[red, green, blue].map((item) => item.toString(16).padStart(2, '0')).join('')}`;
}

function buildRunningProgressGradient() {
  return Array.from({ length: RUNNING_PROGRESS_STOPS }, (_, index) => {
    const ratio = RUNNING_PROGRESS_STOPS === 1 ? 1 : index / (RUNNING_PROGRESS_STOPS - 1);
    const color = rgbToHex(
      interpolateChannel(RUNNING_PROGRESS_START[0], RUNNING_PROGRESS_END[0], ratio),
      interpolateChannel(RUNNING_PROGRESS_START[1], RUNNING_PROGRESS_END[1], ratio),
      interpolateChannel(RUNNING_PROGRESS_START[2], RUNNING_PROGRESS_END[2], ratio),
    );
    return [`${Math.round(ratio * 100)}%`, color] as const;
  }).reduce<Record<string, string>>((result, [offset, color]) => {
    result[offset] = color;
    return result;
  }, {});
}

const RUNNING_PROGRESS_GRADIENT = buildRunningProgressGradient();

export function getRunningProgressSolidColor(progress?: number | null) {
  const normalized = Math.max(0, Math.min(100, typeof progress === 'number' ? progress : 0)) / 100;
  return rgbToHex(
    interpolateChannel(RUNNING_PROGRESS_START[0], RUNNING_PROGRESS_END[0], normalized),
    interpolateChannel(RUNNING_PROGRESS_START[1], RUNNING_PROGRESS_END[1], normalized),
    interpolateChannel(RUNNING_PROGRESS_START[2], RUNNING_PROGRESS_END[2], normalized),
  );
}

/**
 * 进度条颜色与状态点颜色保持同一套映射，避免出现“状态已失败但进度还是蓝色”的割裂感。
 */
export function getDeploymentProgressColor(status?: DeploymentStatus | null) {
  if (!status) {
    return DEPLOYMENT_STATUS_META.PENDING.color;
  }
  if (status === 'RUNNING') {
    return RUNNING_PROGRESS_GRADIENT;
  }
  return DEPLOYMENT_STATUS_META[status]?.color || DEPLOYMENT_STATUS_META.PENDING.color;
}
