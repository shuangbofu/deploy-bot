import { CheckCircleOutlined, ClockCircleOutlined, FieldTimeOutlined, FireOutlined } from '@ant-design/icons';
import { Card, Col, Empty, Progress, Row, Statistic, Tooltip, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { deploymentsApi } from '../../api/deployments';
import { pipelinesApi } from '../../api/pipelines';
import PageHeaderBar from '../../components/PageHeaderBar';
import StatusTag from '../../components/StatusTag';
import { ACTIVE_DEPLOYMENT_STATUSES, FINISHED_DEPLOYMENT_STATUSES } from '../../constants/deployment';
import type { DeploymentSummary, PipelineSummary } from '../../types/domain';
import { buildSevenDayTrend, buildTrendAreaPath, buildTrendPoints, buildTrendSmoothPath } from '../../utils/dashboard';
import { formatDateTime } from '../../utils/datetime';
import { formatDeploymentElapsed } from '../../utils/deploymentDuration';

/**
 * 用户端控制台。
 * 用户不需要配置资源，只需要快速看到“能不能部署、最近稳不稳定”。
 */
export default function UserDashboardPage() {
  const [loading, setLoading] = useState(false);
  const [pipelines, setPipelines] = useState<PipelineSummary[]>([]);
  const [deployments, setDeployments] = useState<DeploymentSummary[]>([]);
  const [tick, setTick] = useState(() => Date.now());

  /** 加载用户端控制台所需的流水线与部署记录。 */
  const loadData = async () => {
    setLoading(true);
    try {
      const [nextPipelines, nextDeployments] = await Promise.all([
        pipelinesApi.list(),
        deploymentsApi.list(),
      ]);
      setPipelines(nextPipelines);
      setDeployments(nextDeployments);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData().catch(() => message.error('加载控制台数据失败'));
  }, []);

  useEffect(() => {
    const timer = window.setInterval(() => setTick(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  /** 计算用户最关注的执行中数量、失败数量和成功率。 */
  const stats = useMemo(() => {
    const running = deployments.filter((item) => item.status && ACTIVE_DEPLOYMENT_STATUSES.includes(item.status)).length;
    const success = deployments.filter((item) => item.status === 'SUCCESS').length;
    const failed = deployments.filter((item) => item.status === 'FAILED').length;
    const totalFinished = deployments.filter((item) => item.status && FINISHED_DEPLOYMENT_STATUSES.includes(item.status)).length;
    const successRate = totalFinished > 0 ? Math.round((success * 100) / totalFinished) : 0;
    return {
      pipelines: pipelines.length,
      running,
      failed,
      successRate,
    };
  }, [deployments, pipelines]);

  /** 最近 7 天部署趋势图。 */
  const trend = useMemo(() => buildSevenDayTrend(deployments), [deployments]);
  const trendAxisMax = useMemo(() => Math.max(...trend.map((item) => item.total), 1), [trend]);
  const totalTrendPoints = useMemo(() => buildTrendPoints(trend.map((item) => item.total), 560, 220, 18, trendAxisMax), [trend, trendAxisMax]);
  const successTrendPoints = useMemo(() => buildTrendPoints(trend.map((item) => item.success), 560, 220, 18, trendAxisMax), [trend, trendAxisMax]);
  const totalTrendPath = useMemo(() => buildTrendSmoothPath(totalTrendPoints), [totalTrendPoints]);
  const successTrendPath = useMemo(() => buildTrendSmoothPath(successTrendPoints), [successTrendPoints]);
  const totalTrendArea = useMemo(() => buildTrendAreaPath(totalTrendPoints, 220), [totalTrendPoints]);
  const successTrendArea = useMemo(() => buildTrendAreaPath(successTrendPoints, 220), [successTrendPoints]);
  /** 最近部署列表。 */
  const latestDeployments = deployments.slice(0, 5);
  /** 当前需要优先关注的部署。 */
  const attentionDeployments = deployments
    .filter((item) => item.status === 'FAILED' || item.status === 'RUNNING' || item.status === 'PENDING')
    .slice(0, 5);

  return (
    <>
      <PageHeaderBar
        title="控制台"
        description="这里汇总用户最关心的内容：当前有哪些流水线可用、最近部署是否稳定、现在有没有任务正在跑。"
      />
      <Row gutter={[16, 16]}>
        <Col xs={24} md={12} xl={6}>
          <Card className="app-card dashboard-stat-card" loading={loading}>
            <Statistic title="可部署流水线" value={stats.pipelines} prefix={<FireOutlined />} />
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="app-card dashboard-stat-card" loading={loading}>
            <Statistic title="执行中任务" value={stats.running} prefix={<ClockCircleOutlined />} />
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="app-card dashboard-stat-card" loading={loading}>
            <Statistic title="失败任务" value={stats.failed} prefix={<FieldTimeOutlined />} />
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="app-card dashboard-stat-card" loading={loading}>
            <Statistic title="成功率" value={stats.successRate} suffix="%" prefix={<CheckCircleOutlined />} />
          </Card>
        </Col>
      </Row>

      <div style={{ height: 16 }} />

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={8}>
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
                  <strong>{deployments.length}</strong>
                </div>
              </div>
            </div>
          </Card>
        </Col>
        <Col xs={24} xl={16}>
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
                  <linearGradient id="user-total-area" x1="0" x2="0" y1="0" y2="1">
                    <stop offset="0%" stopColor="#2563eb" stopOpacity="0.24" />
                    <stop offset="100%" stopColor="#2563eb" stopOpacity="0.02" />
                  </linearGradient>
                  <linearGradient id="user-success-area" x1="0" x2="0" y1="0" y2="1">
                    <stop offset="0%" stopColor="#16a34a" stopOpacity="0.22" />
                    <stop offset="100%" stopColor="#16a34a" stopOpacity="0.02" />
                  </linearGradient>
                </defs>
                {[48, 96, 144, 192].map((y) => (
                  <line key={y} className="dashboard-grid-line" x1="18" x2="542" y1={y} y2={y} />
                ))}
                <path className="dashboard-area" fill="url(#user-total-area)" d={totalTrendArea} />
                <path className="dashboard-area" fill="url(#user-success-area)" d={successTrendArea} />
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
              <div className="dashboard-line-labels">
                {trend.map((item) => (
                  <div key={item.key} className="dashboard-line-label-item">
                    <div className="dashboard-line-label">{item.label}</div>
                  </div>
                ))}
              </div>
            </div>
          </Card>
        </Col>
      </Row>

      <div style={{ height: 16 }} />

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={16}>
          <Card className="app-card dashboard-panel-card" title="最近部署" loading={loading}>
            {latestDeployments.length === 0 ? (
              <Empty description="还没有部署记录。" />
            ) : (
              <div className="dashboard-recent-list">
                {latestDeployments.map((item: DeploymentSummary) => (
                  <div key={item.id} className="dashboard-recent-item">
                    <div>
                      <div className="dashboard-recent-title">
                        <span>{item.pipeline?.name || `部署 #${item.id}`}</span>
                        <StatusTag status={item.status} />
                      </div>
                      <div className="dashboard-recent-meta">
                        {item.pipeline?.project?.name || '-'} · {item.branchName || '-'} · {item.triggeredBy || '-'}
                      </div>
                    </div>
                    <div className="dashboard-recent-side">
                      <div>{formatDateTime(item.createdAt)}</div>
                      <div>{formatDeploymentElapsed(item, tick)}</div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Card>
        </Col>
        <Col xs={24} xl={8}>
          <Card className="app-card dashboard-panel-card" title="执行告警" loading={loading}>
            {attentionDeployments.length === 0 ? (
              <Empty description="当前没有需要优先处理的部署。" />
            ) : (
              <div className="dashboard-recent-list">
                {attentionDeployments.map((item: DeploymentSummary) => (
                  <div key={item.id} className="dashboard-recent-item">
                    <div>
                      <div className="dashboard-recent-title">
                        <span>{item.pipeline?.name || `部署 #${item.id}`}</span>
                        <StatusTag status={item.status} />
                      </div>
                      <div className="dashboard-recent-meta">
                        {item.pipeline?.project?.name || '-'} · {item.branchName || '-'}
                      </div>
                    </div>
                    <div className="dashboard-recent-side">
                      <div>{formatDateTime(item.createdAt)}</div>
                      <div>{formatDeploymentElapsed(item, tick)}</div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Card>
        </Col>
      </Row>
    </>
  );
}
