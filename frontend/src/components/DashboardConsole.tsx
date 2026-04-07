import {
  CloudServerOutlined,
  DeploymentUnitOutlined,
  FileTextOutlined,
  FolderOpenOutlined,
  ProfileOutlined,
  RadarChartOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { Card, Col, Empty, Progress, Row, Skeleton, Statistic, Tooltip } from 'antd';
import { useNavigate } from 'react-router-dom';
import PageHeaderBar from './PageHeaderBar';
import StatusTag from './StatusTag';
import type {
  DashboardDeploymentSummary,
  DashboardServiceSummary,
  DashboardStatsSummary,
  DashboardTrendItem,
  HostResourceSnapshot,
} from '../types/domain';
import { buildTrendAreaPath, buildTrendPoints, buildTrendSmoothPath } from '../utils/dashboard';
import { formatDateTime } from '../utils/datetime';
import { formatDeploymentElapsed } from '../utils/deploymentDuration';

interface DashboardConsoleProps {
  title: string;
  description: string;
  loading: boolean;
  resourceLoading?: boolean;
  stats: DashboardStatsSummary;
  trend: DashboardTrendItem[];
  latestDeployments: DashboardDeploymentSummary[];
  attentionDeployments: DashboardDeploymentSummary[];
  services: DashboardServiceSummary[];
  resources?: HostResourceSnapshot[];
  isAdmin: boolean;
  detailBasePath: string;
  listPath: string;
  backFrom: string;
  backLabel: string;
  idPrefix: string;
  tick?: number;
}

export default function DashboardConsole({
  title,
  description,
  loading,
  resourceLoading = false,
  stats,
  trend,
  latestDeployments,
  attentionDeployments,
  services,
  resources = [],
  isAdmin,
  detailBasePath,
  listPath,
  backFrom,
  backLabel,
  idPrefix,
  tick,
}: DashboardConsoleProps) {
  const navigate = useNavigate();
  const trendAxisMax = Math.max(...trend.map((item) => item.total), 1);
  const totalTrendPoints = buildTrendPoints(trend.map((item) => item.total), 560, 220, 18, trendAxisMax);
  const successTrendPoints = buildTrendPoints(trend.map((item) => item.success), 560, 220, 18, trendAxisMax);
  const totalTrendPath = buildTrendSmoothPath(totalTrendPoints);
  const successTrendPath = buildTrendSmoothPath(successTrendPoints);
  const totalTrendArea = buildTrendAreaPath(totalTrendPoints, 220);
  const successTrendArea = buildTrendAreaPath(successTrendPoints, 220);

  const formatMemoryMb = (value?: number | null) => {
    if (value == null) return '-';
    if (value >= 1024) return `${(value / 1024).toFixed(value >= 10240 ? 0 : 1)} GB`;
    return `${value} MB`;
  };

  const getUsageColor = (value?: number | null) => {
    const percent = typeof value === 'number' ? value : 0;
    if (percent < 20) return '#166534';
    if (percent < 40) return '#16a34a';
    if (percent < 65) return '#eab308';
    if (percent < 85) return '#f97316';
    return '#dc2626';
  };

  const statItems = [
    { key: 'projects', title: '项目数', value: stats.projects, prefix: <FolderOpenOutlined style={{ color: '#2563eb' }} /> },
    { key: 'hosts', title: '主机数', value: stats.hosts, prefix: <CloudServerOutlined style={{ color: '#0f766e' }} /> },
    { key: 'templates', title: '模板数', value: stats.templates, prefix: <FileTextOutlined style={{ color: '#7c3aed' }} /> },
    { key: 'pipelines', title: '流水线数', value: stats.pipelines, prefix: <DeploymentUnitOutlined style={{ color: '#ea580c' }} /> },
    { key: 'deployments', title: '部署记录', value: stats.deployments, prefix: <ProfileOutlined style={{ color: '#dc2626' }} /> },
    { key: 'services', title: '服务数', value: stats.services, prefix: <RadarChartOutlined style={{ color: '#0891b2' }} /> },
    { key: 'users', title: '用户数', value: stats.users, prefix: <TeamOutlined style={{ color: '#ca8a04' }} /> },
  ];

  const renderDeploymentList = (items: DashboardDeploymentSummary[], emptyText: string) => {
    if (items.length === 0) return <Empty description={emptyText} />;
    return (
      <div className="dashboard-recent-list">
        {items.map((item) => (
          <div
            key={item.id}
            className="dashboard-recent-item dashboard-recent-item--clickable"
            onClick={() => navigate(`${detailBasePath}/${item.id}`, { state: { from: backFrom, backLabel } })}
          >
            <div>
              <div className="dashboard-recent-title">
                <span>{item.pipelineName || `部署 #${item.id}`}</span>
                <StatusTag status={item.status ?? undefined} />
              </div>
              <div className="dashboard-recent-meta">
                {item.projectName || '-'} · {item.branchName || '-'}
                {item.triggeredByDisplayName || item.triggeredBy ? ` · ${item.triggeredByDisplayName || item.triggeredBy}` : ''}
              </div>
              <div className="dashboard-recent-timeline">
                <div>{formatDateTime(item.startedAt || item.createdAt)} ~ {formatDateTime(item.finishedAt)}</div>
                <div>{formatDeploymentElapsed(item, tick)}</div>
              </div>
            </div>
          </div>
        ))}
      </div>
    );
  };

  return (
    <>
      <PageHeaderBar title={title} description={description} />
      <div className="app-page-scroll">
        <div className="dashboard-stat-grid">
          {statItems.map((item) => (
            <Card key={item.key} className="app-card dashboard-stat-card" loading={loading}>
              <Statistic title={item.title} value={item.value} prefix={item.prefix} />
            </Card>
          ))}
        </div>

        <div style={{ height: 16 }} />

        {isAdmin ? (
          <>
            <Row gutter={[16, 16]}>
              <Col xs={24}>
                <Card className="app-card dashboard-panel-card" title={`主机资源概览 · ${stats.hosts} 台`} loading={loading}>
                  {resources.length === 0 ? (
                    <Empty description="当前没有主机资源数据。" />
                  ) : (
                    <div className="dashboard-host-list">
                      {resources.map((resource) => (
                        <div key={resource.hostId} className="dashboard-recent-item dashboard-host-item">
                          <div className="min-w-0 flex-1">
                            <div className="dashboard-recent-title">
                              <span>{resource.hostName}</span>
                            </div>
                            {resource.loading ? (
                              <div className="mt-2 space-y-3">
                                <Skeleton.Input active block size="small" style={{ height: 14 }} />
                                <Skeleton.Input active block size="small" style={{ height: 14 }} />
                                <Skeleton.Input active block size="small" style={{ height: 14 }} />
                                <Skeleton.Input active block size="small" style={{ height: 14 }} />
                              </div>
                            ) : (
                              <>
                                <div className="dashboard-recent-meta">
                                  {resource.osType || '-'} · CPU {resource.cpuCores ?? '-'} 核 · 负载 {resource.loadAverage ?? '-'} · 工作空间 {resource.workspaceRoot || '-'}
                                </div>
                                <div className="mt-3">
                                  <div className="mb-1 text-xs text-slate-500">CPU 使用率</div>
                                  <Progress percent={resource.cpuUsagePercent ?? 0} size="small" strokeColor={getUsageColor(resource.cpuUsagePercent)} />
                                  <div className="mt-1 text-xs text-slate-500">CPU {resource.cpuUsagePercent ?? '-'}%</div>
                                </div>
                                <div className="mt-3">
                                  <div className="mb-1 text-xs text-slate-500">内存使用率</div>
                                  <Progress percent={resource.memoryUsagePercent ?? 0} size="small" strokeColor={getUsageColor(resource.memoryUsagePercent)} />
                                  <div className="mt-1 text-xs text-slate-500">
                                    {formatMemoryMb(resource.memoryUsedMb)} / {formatMemoryMb(resource.memoryTotalMb)}
                                  </div>
                                </div>
                                <div className="mt-2">
                                  <div className="mb-1 text-xs text-slate-500">磁盘使用率</div>
                                  <Progress percent={resource.diskUsagePercent ?? 0} size="small" strokeColor={getUsageColor(resource.diskUsagePercent)} />
                                  <div className="mt-1 text-xs text-slate-500">
                                    {resource.diskUsedGb ?? '-'} / {resource.diskTotalGb ?? '-'} GB
                                  </div>
                                </div>
                              </>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </Card>
              </Col>
            </Row>
            <div style={{ height: 16 }} />
          </>
        ) : null}

        <Row gutter={[16, 16]}>
          <Col xs={24} xl={6}>
            <div className="flex flex-col gap-4">
              <Card className="app-card" title="状态分布" loading={loading}>
                <div className="dashboard-circle-wrap">
                  <Progress
                    type="circle"
                    percent={stats.successRate}
                    strokeColor="#16a34a"
                    trailColor="#dbe5f0"
                    format={() => `${stats.successRate}%`}
                  />
                  <div className="dashboard-status-legend">
                    <div className="dashboard-status-row">
                      <span>部署中</span>
                      <strong>{stats.runningDeployments}</strong>
                    </div>
                    <div className="dashboard-status-row">
                      <span>失败</span>
                      <strong>{stats.failedDeployments}</strong>
                    </div>
                    <div className="dashboard-status-row">
                      <span>总记录</span>
                      <strong>{stats.deployments}</strong>
                    </div>
                  </div>
                </div>
              </Card>
              <Card
                className="app-card"
                title="近 7 天部署趋势"
                extra={(
                  <div className="dashboard-line-legend dashboard-line-legend--header">
                    <span><i className="dashboard-line-legend-dot dashboard-line-legend-dot--total" />总部署</span>
                    <span><i className="dashboard-line-legend-dot dashboard-line-legend-dot--success" />成功部署</span>
                  </div>
                )}
                loading={loading}
              >
                <div className="dashboard-line-chart">
                  <svg viewBox="0 0 560 220" className="dashboard-line-svg" preserveAspectRatio="xMidYMid meet">
                    <defs>
                      <linearGradient id={`${idPrefix}-total-area`} x1="0" x2="0" y1="0" y2="1">
                        <stop offset="0%" stopColor="#2563eb" stopOpacity="0.24" />
                        <stop offset="100%" stopColor="#2563eb" stopOpacity="0.02" />
                      </linearGradient>
                      <linearGradient id={`${idPrefix}-success-area`} x1="0" x2="0" y1="0" y2="1">
                        <stop offset="0%" stopColor="#16a34a" stopOpacity="0.22" />
                        <stop offset="100%" stopColor="#16a34a" stopOpacity="0.02" />
                      </linearGradient>
                    </defs>
                    {[48, 96, 144, 192].map((y) => (
                      <line key={y} className="dashboard-grid-line" x1="18" x2="542" y1={y} y2={y} />
                    ))}
                    <path className="dashboard-area" fill={`url(#${idPrefix}-total-area)`} d={totalTrendArea} />
                    <path className="dashboard-area" fill={`url(#${idPrefix}-success-area)`} d={successTrendArea} />
                    <path className="dashboard-line dashboard-line--total" d={totalTrendPath} />
                    <path className="dashboard-line dashboard-line--success" d={successTrendPath} />
                    {trend.map((item, index) => {
                      const totalPoint = totalTrendPoints[index];
                      const successPoint = successTrendPoints[index];
                      if (!totalPoint) return null;
                      return (
                        <g key={item.key}>
                          <circle className="dashboard-line-dot-svg dashboard-line-dot-svg--total" cx={totalPoint.x} cy={totalPoint.y} r="4" />
                          {successPoint ? <circle className="dashboard-line-dot-svg dashboard-line-dot-svg--success" cx={successPoint.x} cy={successPoint.y} r="4" /> : null}
                          <text className="dashboard-line-value-svg" x={totalPoint.x} y={Math.max(totalPoint.y - 12, 14)} textAnchor="middle">
                            {item.total}
                          </text>
                        </g>
                      );
                    })}
                  </svg>
                  <div className="dashboard-line-overlay">
                    {trend.map((item, index) => {
                      const totalPoint = totalTrendPoints[index];
                      return (
                        <Tooltip key={item.key} title={`${item.label}｜总部署 ${item.total} 次｜成功 ${item.success} 次`} placement="top">
                          <button
                            type="button"
                            className="dashboard-line-hotspot"
                            style={{ left: `calc(${((totalPoint?.x || 0) / 560) * 100}% - 20px)` }}
                            aria-label={`${item.label} 总部署 ${item.total} 次，成功 ${item.success} 次`}
                          />
                        </Tooltip>
                      );
                    })}
                  </div>
                </div>
                <div className="dashboard-line-labels">
                  {trend.map((item) => (
                    <div key={item.key} className="dashboard-line-label-item">
                      <div className="dashboard-line-label">{item.label}</div>
                    </div>
                  ))}
                </div>
              </Card>
            </div>
          </Col>
          <Col xs={24} xl={18}>
            <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
              <Card className="app-card dashboard-panel-card" title={`服务概况 · 运行中 ${stats.runningServices}`} loading={loading}>
                {services.length === 0 ? (
                  <Empty description="当前没有受管服务。" />
                ) : (
                  <div className="dashboard-recent-list">
                    {services.slice(0, 6).map((service) => (
                      <div key={service.id} className="dashboard-recent-item">
                        <div>
                          <div className="dashboard-recent-title">
                            <span>{service.serviceName || `服务 #${service.id}`}</span>
                            <StatusTag status={(service.status as never) || undefined} />
                          </div>
                          <div className="dashboard-recent-meta">
                            {service.pipelineName || '-'} · {service.targetHostName || '本机'}
                          </div>
                          <div className="dashboard-recent-timeline">
                            <div>{formatDateTime(service.updatedAt)}</div>
                            {isAdmin ? <div>成功率 {stats.successRate}%</div> : null}
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </Card>
              <Card className="app-card dashboard-panel-card" title="最近部署" extra={<a onClick={() => navigate(listPath)}>查看全部</a>} loading={loading}>
                {renderDeploymentList(latestDeployments, '还没有部署记录。')}
              </Card>
              <Card className="app-card dashboard-panel-card" title="部署异常" extra={<a onClick={() => navigate(listPath)}>查看全部</a>} loading={loading}>
                {renderDeploymentList(attentionDeployments, '当前没有需要优先处理的部署。')}
              </Card>
            </div>
          </Col>
        </Row>
      </div>
    </>
  );
}
