import { Empty, Spin } from 'antd';
import type { EChartsOption } from 'echarts';
import ReactECharts from 'echarts-for-react';
import { useAppTheme } from '../theme/AppThemeProvider';

interface DashboardChartCardProps {
  title: string;
  description?: string;
  chartKey?: string;
  loading?: boolean;
  empty?: boolean;
  height?: number;
  option: EChartsOption;
}

export default function DashboardChartCard({
  title,
  description,
  chartKey,
  loading = false,
  empty = false,
  height = 300,
  option,
}: DashboardChartCardProps) {
  const { isDark } = useAppTheme();

  return (
    <div className="dashboard-chart-card">
      <div className="dashboard-chart-card-header">
        <div>
          <div className="dashboard-chart-card-title">{title}</div>
          {description ? <div className="dashboard-chart-card-description">{description}</div> : null}
        </div>
      </div>
      <Spin spinning={loading}>
        {empty ? (
          <div className="dashboard-chart-empty" style={{ height }}>
            <Empty description="暂无图表数据" />
          </div>
        ) : (
          <ReactECharts
            key={chartKey}
            option={option}
            theme={isDark ? 'dark' : undefined}
            notMerge
            style={{ height, width: '100%' }}
          />
        )}
      </Spin>
    </div>
  );
}
