import { DEPLOYMENT_STATUS_META } from '../constants/deployment';
import type { DeploymentStatus } from '../types/domain';
import { getRunningProgressSolidColor } from '../utils/deploymentProgress';

interface StatusTagProps {
  status?: DeploymentStatus | null;
  runningLabel?: string;
  runningTone?: 'default' | 'service';
  progress?: number | null;
}

/**
 * 统一渲染部署状态，避免各页面重复维护颜色和中文文案。
 */
export default function StatusTag({ status, runningLabel, runningTone = 'default', progress }: StatusTagProps) {
  const meta = status ? DEPLOYMENT_STATUS_META[status] : null;
  const label = status === 'RUNNING' && runningLabel ? runningLabel : (meta?.label || status || '未部署');
  const hasRunningProgress = status === 'RUNNING' && runningTone !== 'service' && typeof progress === 'number';
  const runningProgressColor = hasRunningProgress ? getRunningProgressSolidColor(progress) : undefined;
  const dotClass = status === 'RUNNING' && runningTone === 'service'
    ? 'status-dot--success'
    : (hasRunningProgress ? 'status-dot--running-progress' : (meta?.dotClass || 'status-dot--pending'));
  const chipClassName = status === 'RUNNING' && runningTone === 'service'
    ? 'status-chip status-chip--running-service'
    : 'status-chip';

  if (!status) {
    return (
      <span className="status-chip">
        <span className="status-dot status-dot--pending" />
        <span>未部署</span>
      </span>
    );
  }

  return (
    <span className={chipClassName}>
      <span
        className={`status-dot ${dotClass}`}
        style={runningProgressColor ? {
          background: runningProgressColor,
          boxShadow: `0 0 0 4px ${runningProgressColor}24`,
        } : undefined}
      />
      <span>{label}</span>
    </span>
  );
}
