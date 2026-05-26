import type { DeploymentSummary } from '../types/domain';
import { formatDateTime, formatDateTimeWithoutYear } from './datetime';
import { formatDeploymentElapsed } from './deploymentDuration';

export function formatDeploymentTimeline(deployment: DeploymentSummary | undefined, tick?: number) {
  const startedAt = formatDateTimeWithoutYear(deployment?.startedAt || deployment?.createdAt);
  const finishedAt = formatDateTimeWithoutYear(deployment?.finishedAt);
  const elapsed = formatDeploymentElapsed(deployment, tick);
  return `${startedAt} ~ ${finishedAt} · ${elapsed}`;
}

export function formatDeploymentTimelineTitle(deployment: DeploymentSummary | undefined) {
  const startedAt = formatDateTime(deployment?.startedAt || deployment?.createdAt);
  const finishedAt = formatDateTime(deployment?.finishedAt);
  return `${startedAt} ~ ${finishedAt}`;
}
