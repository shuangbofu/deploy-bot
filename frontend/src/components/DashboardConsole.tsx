import { AppstoreOutlined, DeploymentUnitOutlined, ProfileOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { DatePicker, Segmented, Select, Space, Tabs } from 'antd';
import type { EChartsOption } from 'echarts';
import { useMemo, useState } from 'react';
import PageHeaderBar from './PageHeaderBar';
import DashboardChartCard from './DashboardChartCard';
import EmptyPane from './EmptyPane';
import type { DashboardAnalytics, DashboardAnalyticsQuery, DeploymentStatus, HostResourceSnapshot } from '../types/domain';
import { useAppTheme } from '../theme/AppThemeProvider';
import { DEPLOYMENT_STATUS_META } from '../constants/deployment';

interface DashboardConsoleProps {
  title: string;
  description: string;
  loading: boolean;
  analytics?: DashboardAnalytics;
  resources?: HostResourceSnapshot[];
  query: DashboardAnalyticsQuery;
  onQueryChange: (query: DashboardAnalyticsQuery) => void;
  isAdmin: boolean;
}

const { RangePicker } = DatePicker;

function DashboardSkeleton() {
  return (
    <>
      <div className="dashboard-metric-grid">
        {Array.from({ length: 8 }).map((_, index) => (
          <div key={index} className="dashboard-metric-skeleton">
            <div className="dashboard-skeleton-line dashboard-skeleton-line--short" />
            <div className="dashboard-skeleton-line dashboard-skeleton-line--value" />
            <div className="dashboard-skeleton-line" />
          </div>
        ))}
      </div>
      <div className="dashboard-chart-grid dashboard-chart-grid--main">
        <div className="dashboard-chart-skeleton dashboard-chart-skeleton--wide" />
        <div className="dashboard-chart-skeleton" />
      </div>
      <div className="dashboard-chart-grid dashboard-chart-grid--main">
        <div className="dashboard-chart-skeleton" />
        <div className="dashboard-chart-skeleton dashboard-chart-skeleton--bars" />
      </div>
    </>
  );
}

const RANGE_OPTIONS = [
  { label: '今天', value: 'today' },
  { label: '近 7 天', value: '7d' },
  { label: '近 30 天', value: '30d' },
  { label: '近 90 天', value: '90d' },
];

const GRANULARITY_OPTIONS = [
  { label: '小时', value: 'hour' },
  { label: '天', value: 'day' },
  { label: '周', value: 'week' },
  { label: '月', value: 'month' },
];

const STATUS_OPTIONS = Object.entries(DEPLOYMENT_STATUS_META).map(([value, meta]) => ({
  label: meta.label,
  value: value as DeploymentStatus,
}));

const DASHBOARD_METRIC_META: Record<string, { label: string; suffix?: string; help?: string; format?: 'duration' }> = {
  deployments: { label: '部署总数', suffix: '次', help: '当前筛选范围' },
  successRate: { label: '成功率', suffix: '%', help: '成功 / 已结束' },
  failed: { label: '失败数', suffix: '次', help: '需要关注' },
  avgDuration: { label: '平均耗时', help: '已结束部署', format: 'duration' },
  running: { label: '进行中', suffix: '个', help: '等待或运行' },
  stopped: { label: '已停止', suffix: '次', help: '人工停止' },
  activePipelines: { label: '活跃流水线', suffix: '条', help: '有部署记录' },
  activeProjects: { label: '活跃项目', suffix: '个', help: '有部署记录' },
};

const DASHBOARD_CATEGORY_META: Record<string, { label: string }> = {
  total: { label: '部署总数' },
  ACTIVE: { label: '运行中' },
  deployments: { label: '部署次数' },
  status: { label: '状态' },
  UNKNOWN: { label: '未知' },
  lt60: { label: '1 分钟内' },
  '1to5m': { label: '1-5 分钟' },
  '5to15m': { label: '5-15 分钟' },
  '15to30m': { label: '15-30 分钟' },
  gte30m: { label: '30 分钟以上' },
};

const RESOURCE_PRESSURE_LEVELS = [
  { min: 85, label: '压力较高', className: 'dashboard-resource-status dashboard-resource-status--danger' },
  { min: 65, label: '需要关注', className: 'dashboard-resource-status dashboard-resource-status--warning' },
  { min: 0, label: '运行平稳', className: 'dashboard-resource-status dashboard-resource-status--success' },
] as const;

const LIGHT_COLORS = ['#4f8fd8', '#2db7a3', '#f0a44d', '#e8697a', '#55b6d8', '#8d7fe6', '#94a3b8', '#6fc28a'];
const DARK_COLORS = ['#7da6e2', '#5eead4', '#fbbf24', '#fb7185', '#38bdf8', '#a5b4fc', '#94a3b8', '#86efac'];
const LIGHT_STATUS_COLORS: Record<string, string> = {
  成功: '#2db7a3',
  失败: '#e8697a',
  运行中: '#4f8fd8',
  已停止: '#94a3b8',
  等待中: '#f0a44d',
};
const DARK_STATUS_COLORS: Record<string, string> = {
  成功: '#5eead4',
  失败: '#fb7185',
  运行中: '#7da6e2',
  已停止: '#94a3b8',
  等待中: '#fbbf24',
};

type DimensionKey = 'project' | 'pipeline' | 'trigger' | 'templateType' | 'host';

function chartColors(isDark: boolean) {
  return isDark ? DARK_COLORS : LIGHT_COLORS;
}

function statusColor(label: string, isDark: boolean) {
  return (isDark ? DARK_STATUS_COLORS : LIGHT_STATUS_COLORS)[label];
}

function statusLabel(status?: string | null) {
  if (!status) {
    return DASHBOARD_CATEGORY_META.UNKNOWN.label;
  }
  if (status === 'ACTIVE') {
    return DASHBOARD_CATEGORY_META.ACTIVE.label;
  }
  return DEPLOYMENT_STATUS_META[status as DeploymentStatus]?.label || DASHBOARD_CATEGORY_META.UNKNOWN.label;
}

function chartPointLabel(item: { key: string; label: string }) {
  return DASHBOARD_CATEGORY_META[item.key]?.label
    || DEPLOYMENT_STATUS_META[item.key as DeploymentStatus]?.label
    || item.label;
}

function categoryLabel(category: string) {
  return DASHBOARD_CATEGORY_META[category]?.label
    || DEPLOYMENT_STATUS_META[category as DeploymentStatus]?.label
    || category;
}

function formatSeconds(secondsValue: string) {
  const seconds = Number(secondsValue);
  if (!Number.isFinite(seconds) || seconds <= 0) {
    return '-';
  }
  if (seconds < 60) {
    return `${seconds} 秒`;
  }
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) {
    return `${minutes} 分钟`;
  }
  return `${Math.round(minutes / 60)} 小时`;
}

function metricValue(item: { key: string; value: string }) {
  const meta = DASHBOARD_METRIC_META[item.key];
  return meta?.format === 'duration' ? formatSeconds(item.value) : item.value;
}

function percentValue(value?: number | null) {
  return Math.max(0, Math.min(100, value ?? 0));
}

function resourcePressure(snapshot: HostResourceSnapshot) {
  return Math.max(
    percentValue(snapshot.cpuUsagePercent),
    percentValue(snapshot.memoryUsagePercent),
    percentValue(snapshot.diskUsagePercent),
  );
}

function resourcePressureLabel(value: number) {
  return RESOURCE_PRESSURE_LEVELS.find((item) => value >= item.min)?.label || RESOURCE_PRESSURE_LEVELS[RESOURCE_PRESSURE_LEVELS.length - 1].label;
}

function resourcePressureClassName(value: number) {
  return RESOURCE_PRESSURE_LEVELS.find((item) => value >= item.min)?.className || RESOURCE_PRESSURE_LEVELS[RESOURCE_PRESSURE_LEVELS.length - 1].className;
}

function averagePercent(values: Array<number | null | undefined>) {
  const validValues = values.filter((value): value is number => typeof value === 'number');
  if (validValues.length === 0) {
    return 0;
  }
  return Math.round(validValues.reduce((sum, value) => sum + value, 0) / validValues.length);
}

function emptyAnalytics(): DashboardAnalytics {
  return {
    metrics: [],
    trend: [],
    statusDistribution: [],
    projectRanking: [],
    pipelineRanking: [],
    triggerRanking: [],
    templateTypeDistribution: [],
    hostDistribution: [],
    recentDeployments: [],
    durationDistribution: [],
  };
}

function baseChartOption(isDark: boolean): EChartsOption {
  return {
    color: chartColors(isDark),
    backgroundColor: 'transparent',
    textStyle: { color: isDark ? '#d8e3f0' : '#173055' },
    grid: { top: 40, right: 20, bottom: 42, left: 44 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: isDark ? '#0f172a' : '#ffffff',
      borderColor: isDark ? '#334155' : '#e2e8f0',
      textStyle: { color: isDark ? '#d8e3f0' : '#173055' },
    },
    legend: {
      top: 0,
      textStyle: { color: isDark ? '#cbd5e1' : '#475569' },
    },
    xAxis: {
      axisLine: { lineStyle: { color: isDark ? '#334155' : '#cbd5e1' } },
      axisLabel: { color: isDark ? '#94a3b8' : '#64748b' },
      splitLine: { lineStyle: { color: isDark ? '#1e293b' : '#e2e8f0' } },
    },
    yAxis: {
      axisLine: { lineStyle: { color: isDark ? '#334155' : '#cbd5e1' } },
      axisLabel: { color: isDark ? '#94a3b8' : '#64748b' },
      splitLine: { lineStyle: { color: isDark ? '#1e293b' : '#e2e8f0' } },
    },
  };
}

function rankingOption(title: string, data: DashboardAnalytics['projectRanking'], isDark: boolean): EChartsOption {
  const sorted = [...data].reverse();
  const labels = sorted.map(chartPointLabel);
  return {
    ...baseChartOption(isDark),
    tooltip: { trigger: 'axis' },
    grid: { top: 16, right: 24, bottom: 24, left: 92 },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: labels },
    series: [{
      name: title,
      type: 'bar',
      data: sorted.map((item, index) => ({
        value: item.value,
        itemStyle: { color: chartColors(isDark)[index % chartColors(isDark).length] },
      })),
      barWidth: 14,
      itemStyle: { borderRadius: [0, 8, 8, 0] },
    }],
  };
}

function roseOption(title: string, data: DashboardAnalytics['durationDistribution'], isDark: boolean): EChartsOption {
  return {
    ...baseChartOption(isDark),
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { color: isDark ? '#cbd5e1' : '#475569' } },
    series: [{
      name: title,
      type: 'pie',
      roseType: 'radius',
      radius: ['18%', '72%'],
      center: ['50%', '44%'],
      label: { color: isDark ? '#d8e3f0' : '#173055' },
      data: data.map((item, index) => ({
        name: chartPointLabel(item),
        value: item.value,
        itemStyle: { color: statusColor(chartPointLabel(item), isDark) || chartColors(isDark)[index % chartColors(isDark).length] },
      })),
    }],
  };
}

function polarRankingOption(title: string, data: DashboardAnalytics['pipelineRanking'], isDark: boolean): EChartsOption {
  const items = data.slice(0, 6);
  const labels = items.map(chartPointLabel);
  return {
    ...baseChartOption(isDark),
    tooltip: { trigger: 'item' },
    angleAxis: { type: 'category', data: labels, axisLabel: { color: isDark ? '#94a3b8' : '#64748b' } },
    radiusAxis: { axisLabel: { color: isDark ? '#94a3b8' : '#64748b' } },
    polar: { radius: ['12%', '72%'] },
    series: [{
      name: title,
      type: 'bar',
      coordinateSystem: 'polar',
      data: items.map((item, index) => ({
        value: item.value,
        itemStyle: { color: chartColors(isDark)[index % chartColors(isDark).length] },
      })),
      roundCap: true,
    }],
    legend: { show: false },
  };
}

function bubbleOption(title: string, data: DashboardAnalytics['pipelineRanking'], isDark: boolean): EChartsOption {
  const items = data.slice(0, 12);
  return {
    ...baseChartOption(isDark),
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => `${params.name}<br/>${title}：${params.value?.[2] || 0}`,
    },
    grid: { top: 20, right: 24, bottom: 34, left: 44 },
    xAxis: { type: 'value', show: false, min: 0, max: 100 },
    yAxis: { type: 'value', show: false, min: 0, max: 100 },
    series: [{
      name: title,
      type: 'scatter',
      data: items.map((item, index) => ({
        name: chartPointLabel(item),
        value: [
          12 + (index % 4) * 24,
          78 - Math.floor(index / 4) * 28,
          item.value,
        ],
        symbolSize: Math.max(18, Math.min(58, item.value * 8 + 14)),
        itemStyle: {
          color: chartColors(isDark)[index % chartColors(isDark).length],
          opacity: 0.82,
        },
        label: {
          show: true,
          formatter: chartPointLabel(item).length > 6 ? `${chartPointLabel(item).slice(0, 6)}...` : chartPointLabel(item),
          color: isDark ? '#e2e8f0' : '#173055',
          fontSize: 11,
        },
      })),
    }],
  };
}

export default function DashboardConsole({
  title,
  description,
  loading,
  analytics,
  resources = [],
  query,
  onQueryChange,
  isAdmin,
}: DashboardConsoleProps) {
  const { isDark } = useAppTheme();
  const data = analytics || emptyAnalytics();
  const [dimensionKey, setDimensionKey] = useState<DimensionKey>('pipeline');
  const dimensionOptions = useMemo<Array<{ label: string; value: DimensionKey }>>(() => {
    const options: Array<{ label: string; value: DimensionKey }> = [
      { label: '流水线', value: 'pipeline' },
      { label: '项目', value: 'project' },
      { label: '模板类型', value: 'templateType' },
    ];
    if (isAdmin) {
      options.push({ label: '触发人', value: 'trigger' });
      options.push({ label: '目标主机', value: 'host' });
    }
    return options;
  }, [isAdmin]);
  const dimensionConfig = useMemo(() => {
    const configs: Record<DimensionKey, { title: string; data: DashboardAnalytics['projectRanking'] }> = {
      project: { title: '项目', data: data.projectRanking },
      pipeline: { title: '流水线', data: data.pipelineRanking },
      trigger: { title: '触发人', data: data.triggerRanking },
      templateType: { title: '模板类型', data: data.templateTypeDistribution },
      host: { title: '目标主机', data: data.hostDistribution },
    };
    return configs[dimensionKey];
  }, [data.hostDistribution, data.pipelineRanking, data.projectRanking, data.templateTypeDistribution, data.triggerRanking, dimensionKey]);
  const dimensionCharts = useMemo(() => [
    { key: 'bar', title: `${dimensionConfig.title}排行`, description: '按部署次数排序，适合精确对比。', height: 360, option: rankingOption(`${dimensionConfig.title}部署次数`, dimensionConfig.data, isDark) },
    { key: 'rose', title: `${dimensionConfig.title}占比`, description: '查看不同项在当前范围内的占比。', height: 360, option: roseOption(`${dimensionConfig.title}占比`, dimensionConfig.data, isDark) },
    { key: 'polar', title: `${dimensionConfig.title}热度`, description: '用环形热度看头部集中程度。', height: 360, option: polarRankingOption(`${dimensionConfig.title}热度`, dimensionConfig.data, isDark) },
    { key: 'bubble', title: `${dimensionConfig.title}规模分布`, description: '气泡大小表示部署次数，适合快速找出大头。', height: 360, option: bubbleOption(`${dimensionConfig.title}部署次数`, dimensionConfig.data, isDark) },
  ], [dimensionConfig, isDark]);
  const validResources = useMemo(() => resources.filter((item) => !item.errorMessage), [resources]);
  const resourceCards = useMemo(() => {
    const avgCpu = averagePercent(validResources.map((item) => item.cpuUsagePercent));
    const avgMemory = averagePercent(validResources.map((item) => item.memoryUsagePercent));
    const avgDisk = averagePercent(validResources.map((item) => item.diskUsagePercent));
    const maxPressure = validResources.reduce((max, item) => Math.max(max, resourcePressure(item)), 0);
    return [
      { key: 'hosts', label: '启用主机', value: resources.length, suffix: '台', help: `${resources.filter((item) => item.errorMessage).length} 台采集异常` },
      { key: 'cpu', label: '平均 CPU', value: avgCpu, suffix: '%', help: '启用主机平均值' },
      { key: 'memory', label: '平均内存', value: avgMemory, suffix: '%', help: '启用主机平均值' },
      { key: 'disk', label: '平均磁盘', value: avgDisk, suffix: '%', help: `最高压力 ${maxPressure}%` },
    ];
  }, [resources, validResources]);
  const resourceDistribution = useMemo(() => {
    const buckets = RESOURCE_PRESSURE_LEVELS.map((level) => ({ key: level.label, label: level.label, value: 0 }));
    validResources.forEach((item) => {
      const label = resourcePressureLabel(resourcePressure(item));
      const bucket = buckets.find((entry) => entry.key === label);
      if (bucket) {
        bucket.value += 1;
      }
    });
    return buckets;
  }, [validResources]);
  const resourceRanking = useMemo(() => [...resources]
    .sort((left, right) => resourcePressure(right) - resourcePressure(left))
    .map((item) => ({
      key: String(item.hostId),
      label: item.hostName,
      category: 'resourcePressure',
      value: resourcePressure(item),
    })), [resources]);

  const trendOption = useMemo<EChartsOption>(() => {
    const labels = Array.from(new Set(data.trend.map((item) => item.label)));
    const categories = Array.from(new Set(data.trend.map((item) => item.category)));
    return {
      ...baseChartOption(isDark),
      xAxis: { type: 'category', boundaryGap: false, data: labels },
      yAxis: { type: 'value' },
      series: categories.map((category) => ({
        name: categoryLabel(category),
        type: 'line',
        smooth: true,
        symbolSize: 7,
        lineStyle: { width: category === 'total' ? 3 : 2, color: statusColor(categoryLabel(category), isDark) },
        itemStyle: { color: statusColor(categoryLabel(category), isDark) },
        areaStyle: { opacity: category === 'total' ? 0.18 : 0.08 },
        data: labels.map((label) => data.trend.find((item) => item.label === label && item.category === category)?.value || 0),
      })),
    };
  }, [data.trend, isDark]);

  const statusOption = useMemo<EChartsOption>(() => ({
    ...baseChartOption(isDark),
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { color: isDark ? '#cbd5e1' : '#475569' } },
    series: [{
      type: 'pie',
      radius: ['48%', '72%'],
      center: ['50%', '45%'],
      label: { color: isDark ? '#d8e3f0' : '#173055' },
      data: data.statusDistribution.map((item) => ({
        name: statusLabel(item.key),
        value: item.value,
        itemStyle: { color: statusColor(statusLabel(item.key), isDark) },
      })),
    }],
  }), [data.statusDistribution, isDark]);

  const recentOption = useMemo<EChartsOption>(() => ({
    ...baseChartOption(isDark),
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => {
        const item = data.recentDeployments[params.dataIndex];
        if (!item) return '';
        return `${item.time}<br/>${item.pipelineName || '-'}<br/>${item.projectName || '-'}<br/>耗时 ${item.durationSeconds || 0} 秒`;
      },
    },
    grid: { top: 24, right: 24, bottom: 42, left: 110 },
    xAxis: { type: 'category', data: data.recentDeployments.map((item) => item.time) },
    yAxis: { type: 'category', data: Array.from(new Set(data.recentDeployments.map((item) => item.axisName))) },
    series: [{
      name: '最近部署',
      type: 'scatter',
      symbolSize: (value: any) => Math.max(8, Math.min(24, Number(value?.[2] || 0) / 20 + 8)),
      data: data.recentDeployments.map((item) => ({
        value: [item.time, item.axisName, item.durationSeconds || 1, item.status],
        itemStyle: { color: statusColor(statusLabel(item.status), isDark) },
      })),
    }],
  }), [data.recentDeployments, isDark]);

  const overviewCharts = useMemo(() => (
    <>
      <div className="dashboard-chart-grid dashboard-chart-grid--main">
        <DashboardChartCard title="部署趋势" description="按时间粒度统计部署总数、成功、失败和运行中" loading={loading} empty={data.trend.length === 0} height={340} option={trendOption} />
        <DashboardChartCard title="状态分布" description="当前筛选范围内的部署状态占比" loading={loading} empty={data.statusDistribution.length === 0} height={340} option={statusOption} />
      </div>
      <div className="dashboard-chart-grid dashboard-chart-grid--main">
        <DashboardChartCard title="最近部署分布" description="以时间和流水线展示最近部署，点大小表示耗时" loading={loading} empty={data.recentDeployments.length === 0} height={340} option={recentOption} />
        <DashboardChartCard title="部署耗时分布" description="按执行耗时区间统计，玫瑰图更容易看出耗时集中区间" loading={loading} empty={data.durationDistribution.length === 0} height={340} option={roseOption('部署次数', data.durationDistribution, isDark)} />
      </div>
      <div className="dashboard-chart-grid dashboard-chart-grid--main">
        <DashboardChartCard title="流水线热度" description="最近范围内使用最频繁的流水线" loading={loading} empty={data.pipelineRanking.length === 0} height={340} option={polarRankingOption('流水线热度', data.pipelineRanking, isDark)} />
        <DashboardChartCard title="项目部署排行" loading={loading} empty={data.projectRanking.length === 0} height={340} option={rankingOption('项目部署', data.projectRanking, isDark)} />
      </div>
    </>
  ), [data.durationDistribution, data.pipelineRanking.length, data.projectRanking.length, data.recentDeployments.length, data.statusDistribution.length, data.trend.length, isDark, loading, recentOption, statusOption, trendOption]);
  const resourceCharts = useMemo(() => (
    <>
      <div className="dashboard-metric-grid">
        {resourceCards.map((item, index) => (
          <div key={item.key} className="app-card dashboard-metric-card">
            <div className="dashboard-metric-icon">
              {index % 4 === 0 ? <AppstoreOutlined /> : index % 4 === 1 ? <ThunderboltOutlined /> : index % 4 === 2 ? <ProfileOutlined /> : <DeploymentUnitOutlined />}
            </div>
            <div className="dashboard-metric-label">{item.label}</div>
            <div className="dashboard-metric-value">
              {item.value}<span>{item.suffix}</span>
            </div>
            <div className="dashboard-metric-help">{item.help}</div>
          </div>
        ))}
      </div>
      <div className="dashboard-chart-grid dashboard-chart-grid--main">
        <DashboardChartCard
          title="主机资源压力"
          description="按 CPU、内存、磁盘三项中的最高占用排序。"
          loading={loading}
          empty={resourceRanking.length === 0}
          height={340}
          option={rankingOption('资源压力', resourceRanking, isDark)}
        />
        <DashboardChartCard
          title="资源状态分布"
          description="按主机当前资源压力分组。"
          loading={loading}
          empty={resourceDistribution.every((item) => item.value === 0)}
          height={340}
          option={roseOption('主机数量', resourceDistribution.map((item) => ({
            key: item.key,
            label: item.label,
            category: 'resourcePressure',
            value: item.value,
          })), isDark)}
        />
      </div>
      <div className="app-card dashboard-resource-list">
        <div className="dashboard-chart-card-header">
          <div>
            <div className="dashboard-chart-card-title">主机资源明细</div>
            <div className="dashboard-chart-card-description">展示启用主机的实时采集结果，异常主机会标出原因。</div>
          </div>
        </div>
        {resources.length ? (
          <div className="dashboard-resource-table">
            {resources.map((item) => {
              const pressure = resourcePressure(item);
              return (
                <div key={item.hostId} className="dashboard-resource-row">
                  <div className="dashboard-resource-row__main">
                    <strong>{item.hostName}</strong>
                    <span>{item.workspaceRoot || '-'}</span>
                  </div>
                  {item.errorMessage ? (
                    <span className="dashboard-resource-error">{item.errorMessage}</span>
                  ) : (
                    <>
                      <span>CPU {item.cpuUsagePercent ?? '-'}%</span>
                      <span>内存 {item.memoryUsagePercent ?? '-'}%</span>
                      <span>磁盘 {item.diskUsagePercent ?? '-'}%</span>
                      <span className={resourcePressureClassName(pressure)}>{resourcePressureLabel(pressure)}</span>
                    </>
                  )}
                </div>
              );
            })}
          </div>
        ) : (
          <EmptyPane description="暂无可展示的主机资源。" />
        )}
      </div>
    </>
  ), [isDark, loading, resourceCards, resourceDistribution, resourceRanking, resources]);

  return (
    <>
      <PageHeaderBar
        title={title}
        description={description}
        extra={(
          <Space wrap>
            <Select
              className="min-w-28"
              value={query.range || '30d'}
              options={RANGE_OPTIONS}
              onChange={(range) => onQueryChange({ ...query, range })}
            />
            <Select
              className="min-w-24"
              value={query.granularity || 'day'}
              options={GRANULARITY_OPTIONS}
              onChange={(granularity) => onQueryChange({ ...query, granularity })}
            />
            <Select
              allowClear
              className="min-w-28"
              placeholder="状态"
              value={query.status}
              options={STATUS_OPTIONS}
              onChange={(status) => onQueryChange({ ...query, status })}
            />
            {isAdmin ? <RangePicker disabled className="hidden" /> : null}
          </Space>
        )}
      />
      <div className="app-page-scroll">
        {loading && !analytics ? (
          <DashboardSkeleton />
        ) : (
          <>
        <div className="dashboard-metric-grid">
          {data.metrics.map((item, index) => (
            <div key={item.key} className="app-card dashboard-metric-card">
              <div className="dashboard-metric-icon">
                {index % 4 === 0 ? <DeploymentUnitOutlined /> : index % 4 === 1 ? <ThunderboltOutlined /> : index % 4 === 2 ? <ProfileOutlined /> : <AppstoreOutlined />}
              </div>
              <div className="dashboard-metric-label">{DASHBOARD_METRIC_META[item.key]?.label || item.key}</div>
              <div className="dashboard-metric-value">
                {metricValue(item)}<span>{DASHBOARD_METRIC_META[item.key]?.suffix || ''}</span>
              </div>
              {DASHBOARD_METRIC_META[item.key]?.help ? <div className="dashboard-metric-help">{DASHBOARD_METRIC_META[item.key].help}</div> : null}
            </div>
          ))}
        </div>

        {isAdmin ? (
          <Tabs
          className="dashboard-chart-tabs"
          defaultActiveKey="overview"
          items={[
            {
              key: 'overview',
              label: '部署概览',
              children: overviewCharts,
            },
            {
              key: 'dimensions',
              label: '维度分析',
              children: (
                <>
                  <div className="dashboard-dimension-toolbar">
                    <Space wrap>
                      <Segmented
                        value={dimensionKey}
                        options={dimensionOptions}
                        onChange={(value) => setDimensionKey(value as DimensionKey)}
                      />
                    </Space>
                  </div>
                  <div className="dashboard-chart-grid dashboard-chart-grid--two">
                    {dimensionCharts.map((item) => (
                      <DashboardChartCard
                        key={item.key}
                        title={item.title}
                        chartKey={`dimension-${dimensionKey}-${item.key}`}
                        description={item.description}
                        loading={loading}
                        empty={dimensionConfig.data.length === 0}
                        height={item.height}
                        option={item.option}
                      />
                    ))}
                  </div>
                </>
              ),
            },
            {
              key: 'resources',
              label: '资源监控',
              children: resourceCharts,
            },
          ]}
          />
        ) : (
          <div className="dashboard-overview-direct">
            {overviewCharts}
          </div>
        )}
          </>
        )}
      </div>
    </>
  );
}
