import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Button, Card, Col, Descriptions, Popconfirm, Progress, Row, Space, message } from 'antd';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { deploymentPluginsApi } from '../../api/deploymentPlugins';
import { deploymentsApi } from '../../api/deployments';
import DeploymentInspectionTabs from '../../components/DeploymentInspectionTabs';
import LogViewer from '../../components/LogViewer';
import PageHeaderBar from '../../components/PageHeaderBar';
import StatusTag from '../../components/StatusTag';
import { ACTIVE_DEPLOYMENT_STATUSES } from '../../constants/deployment';
import { useDeploymentLogStream } from '../../hooks/useDeploymentLogStream';
import type { DeploymentPluginDefinitionSummary, DeploymentSummary } from '../../types/domain';
import { copyText } from '../../utils/clipboard';
import { getDeploymentProgress, getDeploymentProgressColor, getDeploymentProgressLabel } from '../../utils/deploymentProgress';
import { formatDeploymentTimeline } from '../../utils/deploymentTimeline';

/**
 * 用户端部署详情页。
 * 这里是用户排查部署状态和日志的唯一主视图。
 */
export default function UserDeploymentDetailPage() {
  const { deploymentId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const [deployment, setDeployment] = useState<DeploymentSummary>();
  const [logContent, setLogContent] = useState('');
  const [detailLoading, setDetailLoading] = useState(true);
  const [logLoading, setLogLoading] = useState(true);
  const [logOffset, setLogOffset] = useState(0);
  const [lastLogUpdateAt, setLastLogUpdateAt] = useState(() => Date.now());
  const [plugins, setPlugins] = useState<DeploymentPluginDefinitionSummary[]>([]);
  const [tick, setTick] = useState(() => Date.now());
  const contentRef = useRef<HTMLDivElement | null>(null);
  const [contentHeight, setContentHeight] = useState<number>();

  /** 同步加载部署详情与当前日志内容。 */
  const loadDeploymentDetail = async (options?: { silent?: boolean }) => {
    if (!options?.silent) {
      setDetailLoading(true);
    }
    try {
      const detail = await deploymentsApi.detail(deploymentId || '');
      setDeployment(detail);
    } finally {
      if (!options?.silent) {
        setDetailLoading(false);
      }
    }
  };

  const loadDeploymentLog = async (options?: { silent?: boolean }) => {
    if (!options?.silent) {
      setLogLoading(true);
    }
    try {
      const log = await deploymentsApi.getLog(deploymentId || '');
      setLogContent(log.content);
      setLogOffset(new TextEncoder().encode(log.content || '').length);
      setLastLogUpdateAt(Date.now());
    } finally {
      if (!options?.silent) {
        setLogLoading(false);
      }
    }
  };

  useEffect(() => {
    loadDeploymentDetail().catch(() => message.error('加载部署详情失败'));
    loadDeploymentLog().catch(() => message.error('加载部署日志失败'));
    deploymentPluginsApi.list().then(setPlugins).catch(() => setPlugins([]));
  }, [deploymentId]);

  useEffect(() => {
    const timer = window.setInterval(() => setTick(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    if (!deployment?.status || !ACTIVE_DEPLOYMENT_STATUSES.includes(deployment.status)) {
      return undefined;
    }
    // 运行中的任务每 3 秒轮询一次，保持详情页与后台状态接近实时。
    const timer = window.setInterval(() => {
      loadDeploymentDetail({ silent: true }).catch(() => message.error('刷新部署详情失败'));
    }, 3000);
    return () => window.clearInterval(timer);
  }, [deployment, deploymentId]);

  useDeploymentLogStream({
    deploymentId,
    enabled: Boolean(deployment?.status && ACTIVE_DEPLOYMENT_STATUSES.includes(deployment.status)),
    offset: logOffset,
    onLog: (payload) => {
      if (payload.content) {
        setLogContent((previous) => `${previous}${payload.content}`);
        setLastLogUpdateAt(Date.now());
      }
      if (typeof payload.offset === 'number') {
        setLogOffset(payload.offset);
      }
      if (payload.finished) {
        loadDeploymentDetail({ silent: true }).catch(() => undefined);
      }
    },
    onDone: () => {
      loadDeploymentDetail({ silent: true }).catch(() => undefined);
      loadDeploymentLog({ silent: true }).catch(() => undefined);
    },
    onError: () => {
      if (deployment?.status && ACTIVE_DEPLOYMENT_STATUSES.includes(deployment.status)) {
        loadDeploymentLog({ silent: true }).catch(() => undefined);
      }
    },
  });

  /** 进度值由后端计算，前端只负责展示。 */
  const progress = useMemo(() => getDeploymentProgress(deployment), [deployment]);
  const stoppable = Boolean(deployment?.status && ACTIVE_DEPLOYMENT_STATUSES.includes(deployment.status));
  const logStreaming = Boolean(deployment?.status && ACTIVE_DEPLOYMENT_STATUSES.includes(deployment.status));
  const logIdleHint = logStreaming && !logLoading && tick - lastLogUpdateAt > 5000
    ? '任务仍在运行，等待新的日志输出...'
    : undefined;
  const rollbackable = Boolean(
    deployment?.artifactPath
      && deployment?.status
      && deployment.status === 'SUCCESS',
  );
  const backTarget = location.state?.from || '/user/pipelines';
  const backLabel = location.state?.backLabel || '返回流水线大厅';
  const renderDescriptionItem = (label: string, value?: string | number | null) => {
    if (value == null || String(value).trim() === '') {
      return null;
    }
    return <Descriptions.Item label={label}>{value}</Descriptions.Item>;
  };
  const executionSnapshot = useMemo(() => {
    const raw = deployment?.executionSnapshot;
    if (!raw) {
      return null;
    }
    if (typeof raw === 'object') {
      return raw as Record<string, unknown>;
    }
    try {
      return JSON.parse(raw) as Record<string, unknown>;
    } catch {
      return null;
    }
  }, [deployment?.executionSnapshot]);
  useLayoutEffect(() => {
    if (!contentRef.current) {
      return undefined;
    }
    const element = contentRef.current;
    const updateHeight = () => {
      if (window.innerWidth < 1200) {
        setContentHeight(undefined);
        return;
      }
      const top = element.getBoundingClientRect().top;
      setContentHeight(Math.max(420, Math.floor(window.innerHeight - top - 16)));
    };
    updateHeight();
    const observer = new ResizeObserver(() => updateHeight());
    observer.observe(element);
    window.addEventListener('resize', updateHeight);
    return () => {
      observer.disconnect();
      window.removeEventListener('resize', updateHeight);
    };
  }, [deployment]);

  return (
    <>
      <PageHeaderBar
        title={`部署详情 #${deploymentId}`}
        description="查看当前部署的状态、部署日志、耗时和部署快照。"
        extra={[
          <Button key="back" onClick={() => navigate(backTarget)}>{backLabel}</Button>,
          rollbackable ? (
            <Popconfirm
              key="rollback"
              title="确认回滚到此版本"
              description="会直接复用这次部署保留下来的构建产物回滚到这个版本，不会重新走构建流程。"
              okText="确认回滚"
              cancelText="取消"
              onConfirm={() => deploymentsApi.rollback(deploymentId || '').then((response) => {
                message.success('回滚任务已创建');
                navigate(`/user/deployments/${response.id}`, {
                  state: {
                    from: backTarget,
                    backLabel,
                  },
                });
              }).catch(() => message.error('创建回滚任务失败'))}
            >
              <Button>回滚到此版本</Button>
            </Popconfirm>
          ) : null,
          stoppable ? (
            <Popconfirm
              key="stop"
              title="确认停止部署？"
              description="会中断当前部署任务，已启动的服务也会尝试停止。"
              okText="确认停止"
              cancelText="取消"
              onConfirm={() => deploymentsApi.stop(deploymentId || '').then(() => {
                message.success('部署已停止');
                return Promise.all([loadDeploymentDetail(), loadDeploymentLog()]);
              }).catch(() => message.error('停止部署失败'))}
            >
              <Button danger>停止部署</Button>
            </Popconfirm>
          ) : null,
          <Button key="refresh" type="primary" onClick={() => {
            loadDeploymentDetail().catch(() => message.error('刷新详情失败'));
            loadDeploymentLog().catch(() => message.error('刷新日志失败'));
          }}
          >
            刷新
          </Button>,
        ]}
      />
      <div ref={contentRef} className="deployment-detail-content" style={contentHeight ? { height: contentHeight } : undefined}>
        <Row className="deployment-detail-grid" gutter={[0, 0]} style={contentHeight ? { height: '100%' } : undefined}>
        <Col className="deployment-detail-col deployment-detail-sidebar-col" xs={24} xl={5} xxl={4} style={contentHeight ? { height: '100%' } : undefined}>
          <div className="deployment-detail-sidebar" style={contentHeight ? { height: '100%' } : undefined}>
            <Card
              className="app-card"
              title="部署状态"
              extra={<StatusTag status={deployment?.status} progress={progress} />}
              loading={detailLoading}
            >
              <div className="deployment-progress-row">
                <Progress
                  className="deployment-progress"
                  percent={progress}
                  strokeWidth={10}
                  status={deployment?.status === 'RUNNING' ? 'active' : undefined}
                  strokeColor={getDeploymentProgressColor(deployment?.status)}
                  showInfo={false}
                />
                <div className="deployment-progress-text">
                  {getDeploymentProgressLabel(progress, deployment?.status, deployment?.progressText)}
                </div>
              </div>
              <Descriptions column={1} size="small" className="mt-4">
                {renderDescriptionItem('流水线', deployment?.pipelineName || deployment?.pipeline?.name)}
                {renderDescriptionItem('分支', deployment?.branchName)}
                {renderDescriptionItem('触发人', deployment?.triggeredByDisplayName || deployment?.triggeredBy)}
                {renderDescriptionItem('停止人', deployment?.stoppedByDisplayName || deployment?.stoppedBy)}
                {renderDescriptionItem('部署时间', formatDeploymentTimeline(deployment, tick))}
                {renderDescriptionItem('产物目录', deployment?.artifactPath)}
                {deployment?.rollbackFromDeploymentId ? (
                  <Descriptions.Item label="重发来源">
                    <Button
                      type="link"
                      className="!px-0"
                      onClick={() => navigate(`/user/deployments/${deployment.rollbackFromDeploymentId}`, {
                        state: {
                          from: backTarget,
                          backLabel,
                        },
                      })}
                    >
                      #{deployment.rollbackFromDeploymentId}
                    </Button>
                  </Descriptions.Item>
                ) : null}
                {renderDescriptionItem('监控 PID', deployment?.monitoredPid)}
                {deployment?.status !== 'STOPPED' ? renderDescriptionItem('错误信息', deployment?.errorMessage) : null}
              </Descriptions>
            </Card>
            <DeploymentInspectionTabs
              loading={detailLoading}
              executionSnapshot={executionSnapshot}
              pipelinePluginId={deployment?.pipeline?.templatePluginId || deployment?.pipeline?.template?.pluginId}
              plugins={plugins}
              commitSha={deployment?.commitSha}
              gitDiffSnapshot={deployment?.gitDiffSnapshot}
            />
          </div>
        </Col>
        <Col className="deployment-detail-col deployment-detail-main-col" xs={24} xl={19} xxl={20} style={contentHeight ? { height: '100%' } : undefined}>
          <Card
            className="app-card deployment-detail-log-card"
            style={contentHeight ? { height: '100%' } : undefined}
            title="部署日志"
            loading={logLoading}
            extra={(
              <Space>
                <Button onClick={() => copyText(logContent).then(() => message.success('日志已复制')).catch(() => message.error('复制失败'))}>
                  复制日志
                </Button>
              </Space>
            )}
          >
            <LogViewer
              content={logContent}
              autoScrollAvailable={logStreaming}
              idleHint={logIdleHint}
            />
          </Card>
        </Col>
      </Row>
      </div>
    </>
  );
}
