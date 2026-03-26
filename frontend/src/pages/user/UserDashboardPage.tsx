import { CheckCircleOutlined, ClockCircleOutlined, FieldTimeOutlined, FireOutlined } from '@ant-design/icons';
import { Card, Col, Empty, Progress, Row, Statistic, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { deploymentsApi } from '../../api/deployments';
import { pipelinesApi } from '../../api/pipelines';
import PageHeaderBar from '../../components/PageHeaderBar';
import StatusTag from '../../components/StatusTag';
import { ACTIVE_DEPLOYMENT_STATUSES, FINISHED_DEPLOYMENT_STATUSES } from '../../constants/deployment';
import type { DeploymentSummary, PipelineSummary } from '../../types/domain';
import { buildSevenDayTrend } from '../../utils/dashboard';
import { formatDateTime } from '../../utils/datetime';
import { formatDeploymentElapsed } from '../../utils/deploymentDuration';

/**
 * 用户端控制台。
 * 用户不需要配置资源，只需要快速看到“能不能部署、最近稳不稳定”。
 */
export default function UserDashboardPage() {
  const [pipelines, setPipelines] = useState<PipelineSummary[]>([]);
  const [deployments, setDeployments] = useState<DeploymentSummary[]>([]);
  const [tick, setTick] = useState(() => Date.now());

  /** 加载用户端控制台所需的流水线与部署记录。 */
  const loadData = async () => {
    const [nextPipelines, nextDeployments] = await Promise.all([
      pipelinesApi.list(),
      deploymentsApi.list(),
    ]);
    setPipelines(nextPipelines);
    setDeployments(nextDeployments);
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
  const maxTrendValue = Math.max(...trend.map((item) => item.total), 1);
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
          <Card className="app-card dashboard-stat-card">
            <Statistic title="可部署流水线" value={stats.pipelines} prefix={<FireOutlined />} />
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="app-card dashboard-stat-card">
            <Statistic title="执行中任务" value={stats.running} prefix={<ClockCircleOutlined />} />
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="app-card dashboard-stat-card">
            <Statistic title="失败任务" value={stats.failed} prefix={<FieldTimeOutlined />} />
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card className="app-card dashboard-stat-card">
            <Statistic title="成功率" value={stats.successRate} suffix="%" prefix={<CheckCircleOutlined />} />
          </Card>
        </Col>
      </Row>

      <div style={{ height: 16 }} />

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={8}>
          <Card className="app-card" title="状态分布">
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
          <Card className="app-card" title="近 7 天部署趋势">
            <div className="dashboard-bar-chart">
              {trend.map((item) => (
                <div key={item.key} className="dashboard-bar-item">
                  <div className="dashboard-bar-stack">
                    <div
                      className="dashboard-bar-fill"
                      style={{ height: `${Math.max(10, (item.total / maxTrendValue) * 100)}%` }}
                    />
                    <div
                      className="dashboard-bar-success"
                      style={{ height: item.total ? `${(item.success / item.total) * 100}%` : '0%' }}
                    />
                  </div>
                  <div className="dashboard-bar-label">{item.label}</div>
                  <div className="dashboard-bar-value">{item.total}</div>
                </div>
              ))}
            </div>
          </Card>
        </Col>
      </Row>

      <div style={{ height: 16 }} />

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={16}>
          <Card className="app-card dashboard-panel-card" title="最近部署">
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
          <Card className="app-card dashboard-panel-card" title="执行告警">
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
