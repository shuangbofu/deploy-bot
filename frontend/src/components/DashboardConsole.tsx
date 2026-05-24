import { AppstoreOutlined, DeploymentUnitOutlined, ProfileOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { Card, DatePicker, Segmented, Select, Space, Tabs } from 'antd';
import type { EChartsOption } from 'echarts';
import { useMemo, useState } from 'react';
import PageHeaderBar from './PageHeaderBar';
import DashboardChartCard from './DashboardChartCard';
import type { DashboardAnalytics, DashboardAnalyticsQuery, DeploymentStatus } from '../types/domain';
import { useAppTheme } from '../theme/AppThemeProvider';

interface DashboardConsoleProps {
  title: string;
  description: string;
  loading: boolean;
  analytics?: DashboardAnalytics;
  query: DashboardAnalyticsQuery;
  onQueryChange: (query: DashboardAnalyticsQuery) => void;
  isAdmin: boolean;
}

const { RangePicker } = DatePicker;

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

const STATUS_OPTIONS: Array<{ label: string; value: DeploymentStatus }> = [
  { label: '等待中', value: 'PENDING' },
  { label: '运行中', value: 'RUNNING' },
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAILED' },
  { label: '已停止', value: 'STOPPED' },
];

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
  return {
    ...baseChartOption(isDark),
    tooltip: { trigger: 'axis' },
    grid: { top: 16, right: 24, bottom: 24, left: 92 },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: sorted.map((item) => item.label) },
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
        name: item.label,
        value: item.value,
        itemStyle: { color: statusColor(item.label, isDark) || chartColors(isDark)[index % chartColors(isDark).length] },
      })),
    }],
  };
}

function polarRankingOption(title: string, data: DashboardAnalytics['pipelineRanking'], isDark: boolean): EChartsOption {
  const items = data.slice(0, 6);
  return {
    ...baseChartOption(isDark),
    tooltip: { trigger: 'item' },
    angleAxis: { type: 'category', data: items.map((item) => item.label), axisLabel: { color: isDark ? '#94a3b8' : '#64748b' } },
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
        name: item.label,
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
          formatter: item.label.length > 6 ? `${item.label.slice(0, 6)}...` : item.label,
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

  const trendOption = useMemo<EChartsOption>(() => {
    const labels = Array.from(new Set(data.trend.map((item) => item.label)));
    const categories = Array.from(new Set(data.trend.map((item) => item.category)));
    return {
      ...baseChartOption(isDark),
      xAxis: { type: 'category', boundaryGap: false, data: labels },
      yAxis: { type: 'value' },
      series: categories.map((category) => ({
        name: category,
        type: 'line',
        smooth: true,
        symbolSize: 7,
        lineStyle: { width: category === '部署总数' ? 3 : 2, color: statusColor(category, isDark) },
        itemStyle: { color: statusColor(category, isDark) },
        areaStyle: { opacity: category === '部署总数' ? 0.18 : 0.08 },
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
        name: item.label,
        value: item.value,
        itemStyle: { color: statusColor(item.label, isDark) },
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
        itemStyle: { color: statusColor(item.status === 'SUCCESS' ? '成功' : item.status === 'FAILED' ? '失败' : item.status === 'STOPPED' ? '已停止' : item.status === 'PENDING' ? '等待中' : '运行中', isDark) },
      })),
    }],
  }), [data.recentDeployments, isDark]);

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
        <div className="dashboard-metric-grid">
          {data.metrics.map((item, index) => (
            <Card key={item.key} className="app-card dashboard-metric-card" loading={loading}>
              <div className="dashboard-metric-icon">
                {index % 4 === 0 ? <DeploymentUnitOutlined /> : index % 4 === 1 ? <ThunderboltOutlined /> : index % 4 === 2 ? <ProfileOutlined /> : <AppstoreOutlined />}
              </div>
              <div className="dashboard-metric-label">{item.label}</div>
              <div className="dashboard-metric-value">
                {item.value}<span>{item.suffix || ''}</span>
              </div>
              {item.trendLabel ? <div className="dashboard-metric-help">{item.trendLabel}</div> : null}
            </Card>
          ))}
        </div>

        <Tabs
          className="dashboard-chart-tabs"
          defaultActiveKey="overview"
          items={[
            {
              key: 'overview',
              label: '常用看板',
              children: (
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
              ),
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
          ]}
        />
      </div>
    </>
  );
}
