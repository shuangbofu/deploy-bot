import { DEPLOYMENT_STATUS_META } from '../constants/deployment';
import type { DeploymentStatus } from '../types/domain';

interface StatusTagProps {
  status?: DeploymentStatus | null;
  runningLabel?: string;
}

/**
 * 统一渲染部署状态，避免各页面重复维护颜色和中文文案。
 */
export default function StatusTag({ status, runningLabel }: StatusTagProps) {
  const meta = status ? DEPLOYMENT_STATUS_META[status] : null;
  const label = status === 'RUNNING' && runningLabel ? runningLabel : (meta?.label || status || '未执行');
  const dotClass = meta?.dotClass || 'status-dot--pending';

  if (!status) {
    return (
      <span className="status-chip">
        <span className="status-dot status-dot--pending" />
        <span>未执行</span>
      </span>
    );
  }

  return (
    <span className="status-chip">
      <span className={`status-dot ${dotClass}`} />
      <span>{label}</span>
    </span>
  );
}
