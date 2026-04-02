import { CheckCircleOutlined, ClockCircleOutlined, FieldTimeOutlined, FireOutlined } from '@ant-design/icons';
import { Card, Col, Empty, Progress, Row, Statistic, Tooltip, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { deploymentsApi } from '../../api/deployments';
import { hostsApi } from '../../api/hosts';
import { pipelinesApi } from '../../api/pipelines';
import { projectsApi } from '../../api/projects';
import { servicesApi } from '../../api/services';
import { templatesApi } from '../../api/templates';
import PageHeaderBar from '../../components/PageHeaderBar';
import StatusTag from '../../components/StatusTag';
import { ACTIVE_DEPLOYMENT_STATUSES, FINISHED_DEPLOYMENT_STATUSES } from '../../constants/deployment';
import type { DeploymentSummary, HostResourceSnapshot, HostSummary, PipelineSummary, ProjectSummary, ServiceSummary, TemplateSummary } from '../../types/domain';
import { buildSevenDayTrend, buildTrendAreaPath, buildTrendPoints, buildTrendSmoothPath } from '../../utils/dashboard';
import { formatDateTime } from '../../utils/datetime';
import { formatDeploymentTimeline } from '../../utils/deploymentTimeline';

interface DashboardData {
  /** 项目总览数据。 */
  projects: ProjectSummary[];
  /** 模板总览数据。 */
  templates: TemplateSummary[];
  /** 流水线总览数据。 */
  pipelines: PipelineSummary[];
  /** 部署记录总览数据。 */
  deployments: DeploymentSummary[];
  /** 主机列表。 */
  hosts: HostSummary[];
  /** 受管服务列表。 */
  services: ServiceSummary[];
  /** 主机资源快照。 */
  resources: HostResourceSnapshot[];
}

/**
 * 管理端控制台。
 * 这里主要解决“当前系统整体运转得怎么样”这个问题。
 */
export default function AdminDashboardPage() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<DashboardData>({
    projects: [],
    templates: [],
    pipelines: [],
    deployments: [],
    hosts: [],
    services: [],
    resources: [],
  });

  /** 并行加载控制台所需的全部统计数据。 */
  const load = async () => {
    setLoading(true);
    try {
      const [projects, templates, pipelines, deployments, hosts, services] = await Promise.all([
        projectsApi.list(),
        templatesApi.list(),
        pipelinesApi.list(),
        deploymentsApi.list(),
        hostsApi.list(),
        servicesApi.list(),
      ]);
      const resources = await Promise.all(
        hosts.map((host) => hostsApi.previewResources(host.id).catch(() => ({
          hostId: host.id,
          hostName: host.name,
          workspaceRoot: host.workspaceRoot,
          preview: '资源采集失败',
        } as HostResourceSnapshot))),
      );
      setData({
        projects,
        templates,
        pipelines,
        deployments,
        hosts,
        services,
        resources,
      });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load().catch(() => message.error('加载控制台数据失败'));
  }, []);

  /** 聚合得到顶部统计卡与状态分布需要的指标。 */
  const stats = useMemo(() => {
    const running = data.deployments.filter((item) => item.status && ACTIVE_DEPLOYMENT_STATUSES.includes(item.status)).length;
    const successCount = data.deployments.filter((item) => item.status === 'SUCCESS').length;
    const failed = data.deployments.filter((item) => item.status === 'FAILED').length;
    const totalFinished = data.deployments.filter((item) => item.status && FINISHED_DEPLOYMENT_STATUSES.includes(item.status)).length;
    const successRate = totalFinished > 0 ? Math.round((successCount * 100) / totalFinished) : 0;

    return {
      projects: data.projects.length,
      templates: data.templates.length,
      pipelines: data.pipelines.length,
      deployments: data.deployments.length,
      hosts: data.hosts.length,
      runningServices: data.services.filter((item) => item.status === 'RUNNING').length,
      running,
      failed,
      successRate,
    };
  }, [data]);

  /** 最近 7 天趋势图数据。 */
  const trend = useMemo(() => buildSevenDayTrend(data.deployments), [data.deployments]);
  const trendAxisMax = useMemo(() => Math.max(...trend.map((item) => item.total), 1), [trend]);
  const totalTrendPoints = useMemo(() => buildTrendPoints(trend.map((item) => item.total), 560, 220, 18, trendAxisMax), [trend, trendAxisMax]);
  const successTrendPoints = useMemo(() => buildTrendPoints(trend.map((item) => item.success), 560, 220, 18, trendAxisMax), [trend, trendAxisMax]);
  const totalTrendPath = useMemo(() => buildTrendSmoothPath(totalTrendPoints), [totalTrendPoints]);
  const successTrendPath = useMemo(() => buildTrendSmoothPath(successTrendPoints), [successTrendPoints]);
  const totalTrendArea = useMemo(() => buildTrendAreaPath(totalTrendPoints, 220), [totalTrendPoints]);
  const successTrendArea = useMemo(() => buildTrendAreaPath(successTrendPoints, 220), [successTrendPoints]);
  /** 最近发生的部署记录。 */
  const latestDeployments = data.deployments.slice(0, 5);
  /** 需要管理员优先处理的部署，例如失败或仍在执行中的任务。 */
  const attentionDeployments = data.deployments
    .filter((item) => item.status === 'FAILED' || item.status === 'RUNNING' || item.status === 'PENDING')
    .slice(0, 5);

  return (
    <>
      <PageHeaderBar
        title="控制台"
        description="查看平台概览、部署趋势、最近执行记录与异常情况。"
      />

      <Row gutter={[16, 16]}>
        <Col xs={24} md={12} xl={6}>
          <Card className="app-card dashboard-stat-card" loading={loading}>
            <Statistic title="项目数" value={stats.projects} prefix={<FireOutlined />} />
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="app-card dashboard-stat-card" loading={loading}>
            <Statistic title="模板数" value={stats.templates} prefix={<ClockCircleOutlined />} />
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="app-card dashboard-stat-card" loading={loading}>
            <Statistic title="流水线数" value={stats.pipelines} prefix={<FieldTimeOutlined />} />
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="app-card dashboard-stat-card" loading={loading}>
            <Statistic title="部署记录" value={stats.deployments} suffix="" prefix={<CheckCircleOutlined />} />
          </Card>
        </Col>
      </Row>

      <div style={{ height: 16 }} />

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={12}>
          <Card className="app-card dashboard-panel-card" title={`主机资源概览 · ${stats.hosts} 台`} loading={loading}>
            {data.resources.length === 0 ? (
              <Empty description="当前没有主机资源数据。" />
            ) : (
              <div className="dashboard-recent-list">
                {data.resources.map((resource) => (
                  <div key={resource.hostId} className="dashboard-recent-item">
                    <div className="min-w-0 flex-1">
                      <div className="dashboard-recent-title">
                        <span>{resource.hostName}</span>
                      </div>
                      <div className="dashboard-recent-meta">
                        {resource.osType || '-'} · CPU {resource.cpuCores ?? '-'} 核 · 负载 {resource.loadAverage ?? '-'} · 工作空间 {resource.workspaceRoot || '-'}
                      </div>
                      <div className="mt-3">
                        <div className="mb-1 text-xs text-slate-500">CPU 使用率</div>
                        <Progress percent={resource.cpuUsagePercent ?? 0} size="small" strokeColor="#f59e0b" />
                      </div>
                      <div className="mt-3">
                        <div className="mb-1 text-xs text-slate-500">内存使用率</div>
                        <Progress percent={resource.memoryUsagePercent ?? 0} size="small" strokeColor="#2563eb" />
                      </div>
                      <div className="mt-2">
                        <div className="mb-1 text-xs text-slate-500">磁盘使用率</div>
                        <Progress percent={resource.diskUsagePercent ?? 0} size="small" strokeColor="#16a34a" />
                      </div>
                    </div>
                    <div className="dashboard-recent-side">
                      <div>CPU {resource.cpuUsagePercent ?? '-'}%</div>
                      <div>{resource.memoryUsedMb ?? '-'} / {resource.memoryTotalMb ?? '-'} MB</div>
                      <div>{resource.diskUsedGb ?? '-'} / {resource.diskTotalGb ?? '-'} GB</div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Card>
        </Col>
        <Col xs={24} xl={12}>
          <Card className="app-card dashboard-panel-card" title={`服务概况 · 运行中 ${stats.runningServices}`} loading={loading}>
            {data.services.length === 0 ? (
              <Empty description="当前没有受管服务。" />
            ) : (
              <div className="dashboard-recent-list">
                {data.services.slice(0, 6).map((service) => (
                  <div key={service.id} className="dashboard-recent-item">
                    <div>
                      <div className="dashboard-recent-title">
                        <span>{service.serviceName || `服务 #${service.id}`}</span>
                        <StatusTag status={service.status} />
                      </div>
                      <div className="dashboard-recent-meta">
                        {service.pipeline?.name || '-'} · {service.pipeline?.targetHost?.name || '本机'}
                      </div>
                    </div>
                    <div className="dashboard-recent-side">
                      <div>PID {service.currentPid || '-'}</div>
                      <div>{formatDateTime(service.updatedAt)}</div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Card>
        </Col>
      </Row>

      <div style={{ height: 16 }} />

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={15}>
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
                    <span>执行中</span>
                    <strong>{stats.running}</strong>
                  </div>
                  <div className="dashboard-status-row">
                    <span>失败</span>
                    <strong>{stats.failed}</strong>
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
                  <linearGradient id="admin-total-area" x1="0" x2="0" y1="0" y2="1">
                    <stop offset="0%" stopColor="#2563eb" stopOpacity="0.24" />
                    <stop offset="100%" stopColor="#2563eb" stopOpacity="0.02" />
                  </linearGradient>
                  <linearGradient id="admin-success-area" x1="0" x2="0" y1="0" y2="1">
                    <stop offset="0%" stopColor="#16a34a" stopOpacity="0.22" />
                    <stop offset="100%" stopColor="#16a34a" stopOpacity="0.02" />
                  </linearGradient>
                </defs>
                {[48, 96, 144, 192].map((y) => (
                  <line key={y} className="dashboard-grid-line" x1="18" x2="542" y1={y} y2={y} />
                ))}
                <path className="dashboard-area" fill="url(#admin-total-area)" d={totalTrendArea} />
                <path className="dashboard-area" fill="url(#admin-success-area)" d={successTrendArea} />
                <path className="dashboard-line dashboard-line--total" d={totalTrendPath} />
                <path className="dashboard-line dashboard-line--success" d={successTrendPath} />
                {trend.map((item, index) => {
                  const totalPoint = totalTrendPoints[index];
                  const successPoint = successTrendPoints[index];
                  if (!totalPoint) {
                    return null;
                  }
                  return (
                    <g key={item.key}>
                      <circle className="dashboard-line-dot-svg dashboard-line-dot-svg--total" cx={totalPoint.x} cy={totalPoint.y} r="4" />
                      {successPoint ? (
                        <circle className="dashboard-line-dot-svg dashboard-line-dot-svg--success" cx={successPoint.x} cy={successPoint.y} r="4" />
                      ) : null}
                      <text
                        className="dashboard-line-value-svg"
                        x={totalPoint.x}
                        y={Math.max(totalPoint.y - 12, 14)}
                        textAnchor="middle"
                      >
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
                    <Tooltip
                      key={item.key}
                      title={`${item.label}｜总部署 ${item.total} 次｜成功 ${item.success} 次`}
                      placement="top"
                    >
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
        <Col xs={24} xl={9}>
          <div className="flex flex-col gap-4">
            <Card
              className="app-card dashboard-panel-card"
              title="最近部署"
              extra={<a onClick={() => navigate('/admin/deployments')}>查看全部</a>}
              loading={loading}
            >
              {latestDeployments.length === 0 ? (
                <Empty description="还没有部署记录。" />
              ) : (
                <div className="dashboard-recent-list">
                  {latestDeployments.map((item: DeploymentSummary) => (
                    <div
                      key={item.id}
                      className="dashboard-recent-item dashboard-recent-item--clickable"
                      onClick={() => navigate(`/admin/deployments/${item.id}`)}
                    >
                      <div>
                        <div className="dashboard-recent-title">
                          <span>{item.pipeline?.name || `部署 #${item.id}`}</span>
                          <StatusTag status={item.status} />
                        </div>
                        <div className="dashboard-recent-meta">
                          {item.pipeline?.project?.name || '-'} · {item.branchName || '-'} · {item.triggeredByDisplayName || item.triggeredBy || '-'}
                        </div>
                      </div>
                      <div className="dashboard-recent-side" title={formatDeploymentTimeline(item)}>
                        {formatDeploymentTimeline(item)}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </Card>
            <Card
              className="app-card dashboard-panel-card"
              title="执行异常"
              extra={<a onClick={() => navigate('/admin/deployments')}>查看全部</a>}
              loading={loading}
            >
              {attentionDeployments.length === 0 ? (
                <Empty description="当前没有需要优先处理的部署。" />
              ) : (
                <div className="dashboard-recent-list">
                  {attentionDeployments.map((item: DeploymentSummary) => (
                    <div
                      key={item.id}
                      className="dashboard-recent-item dashboard-recent-item--clickable"
                      onClick={() => navigate(`/admin/deployments/${item.id}`)}
                    >
                      <div>
                        <div className="dashboard-recent-title">
                          <span>{item.pipeline?.name || `部署 #${item.id}`}</span>
                          <StatusTag status={item.status} />
                        </div>
                        <div className="dashboard-recent-meta">
                          {item.pipeline?.project?.name || '-'} · {item.branchName || '-'}
                        </div>
                      </div>
                      <div className="dashboard-recent-side" title={formatDeploymentTimeline(item)}>
                        {formatDeploymentTimeline(item)}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </Card>
          </div>
        </Col>
      </Row>

    </>
  );
}
