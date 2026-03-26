import type { DashboardTrendItem, DeploymentSummary } from '../types/domain';

const DASHBOARD_TREND_DAYS = 7;
const DATE_KEY_LENGTH = 10;

/**
 * 根据部署记录生成最近 7 天的趋势数据，供管理端和用户端控制台共用。
 */
export function buildSevenDayTrend(deployments: DeploymentSummary[]): DashboardTrendItem[] {
  const now = new Date();
  const days = Array.from({ length: DASHBOARD_TREND_DAYS }, (_, index) => {
    const date = new Date(now);
    date.setDate(now.getDate() - (DASHBOARD_TREND_DAYS - 1 - index));
    const key = date.toISOString().slice(0, DATE_KEY_LENGTH);
    return {
      key,
      label: `${date.getMonth() + 1}/${date.getDate()}`,
      total: 0,
      success: 0,
    };
  });

  deployments.forEach((deployment) => {
    const key = deployment.createdAt?.slice?.(0, DATE_KEY_LENGTH);
    const bucket = days.find((item) => item.key === key);
    if (!bucket) {
      return;
    }
    bucket.total += 1;
    if (deployment.status === 'SUCCESS') {
      bucket.success += 1;
    }
  });

  return days;
}
