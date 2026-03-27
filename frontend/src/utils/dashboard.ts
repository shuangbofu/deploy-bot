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

/**
 * 根据趋势点生成折线路径。
 * 让控制台可以用轻量 SVG 直接画出最近 7 天趋势，而不是继续用柱状堆叠。
 */
export function buildTrendPolyline(
  values: number[],
  width: number,
  height: number,
  padding = 18,
): string {
  if (values.length === 0) {
    return '';
  }

  const maxValue = Math.max(...values, 1);
  const usableWidth = Math.max(width - padding * 2, 1);
  const usableHeight = Math.max(height - padding * 2, 1);

  return values.map((value, index) => {
    const x = padding + (values.length === 1 ? usableWidth / 2 : (usableWidth / (values.length - 1)) * index);
    const y = height - padding - (value / maxValue) * usableHeight;
    return `${x},${y}`;
  }).join(' ');
}

/**
 * 计算折线图坐标点。
 */
export function buildTrendPoints(
  values: number[],
  width: number,
  height: number,
  padding = 18,
  maxValue?: number,
): Array<{ x: number; y: number; value: number }> {
  if (values.length === 0) {
    return [];
  }

  const normalizedMaxValue = Math.max(maxValue ?? 0, ...values, 1);
  const usableWidth = Math.max(width - padding * 2, 1);
  const usableHeight = Math.max(height - padding * 2, 1);

  return values.map((value, index) => ({
    x: padding + (values.length === 1 ? usableWidth / 2 : (usableWidth / (values.length - 1)) * index),
    y: height - padding - (value / normalizedMaxValue) * usableHeight,
    value,
  }));
}

/**
 * 基于点集生成更顺滑的曲线路径。
 */
export function buildTrendSmoothPath(points: Array<{ x: number; y: number }>): string {
  if (points.length === 0) {
    return '';
  }
  if (points.length === 1) {
    return `M ${points[0].x} ${points[0].y}`;
  }

  const [first, ...rest] = points;
  let path = `M ${first.x} ${first.y}`;

  rest.forEach((point, index) => {
    const previous = points[index];
    const controlX = (previous.x + point.x) / 2;
    path += ` C ${controlX} ${previous.y}, ${controlX} ${point.y}, ${point.x} ${point.y}`;
  });

  return path;
}

/**
 * 生成折线下方的面积路径。
 */
export function buildTrendAreaPath(
  points: Array<{ x: number; y: number }>,
  height: number,
  bottomPadding = 18,
): string {
  if (points.length === 0) {
    return '';
  }

  const linePath = buildTrendSmoothPath(points);
  const first = points[0];
  const last = points[points.length - 1];
  const baseline = height - bottomPadding;

  return `${linePath} L ${last.x} ${baseline} L ${first.x} ${baseline} Z`;
}
