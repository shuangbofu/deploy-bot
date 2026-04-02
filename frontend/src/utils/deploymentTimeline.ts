import type { DeploymentSummary } from '../types/domain';
import { formatDateTime } from './datetime';
import { formatDeploymentElapsed } from './deploymentDuration';

export function formatDeploymentTimeline(deployment: DeploymentSummary | undefined, tick?: number) {
  const startedAt = formatDateTime(deployment?.startedAt || deployment?.createdAt);
  const finishedAt = formatDateTime(deployment?.finishedAt);
  const elapsed = formatDeploymentElapsed(deployment, tick);
  return `${startedAt} ~ ${finishedAt} · ${elapsed}`;
}
