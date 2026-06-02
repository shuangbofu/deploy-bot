import type { ReactNode } from 'react';
import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Button, Card, Col, Descriptions, Popconfirm, Progress, Row, Skeleton, Space, message } from 'antd';
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { deploymentPluginsApi } from '../../api/deploymentPlugins';
import { deploymentsApi } from '../../api/deployments';
import DeploymentInspectionTabs from '../../components/DeploymentInspectionTabs';
import LogViewer, { type LogAnchor, type LogViewerHandle } from '../../components/LogViewer';
import PageHeaderBar from '../../components/PageHeaderBar';
import RefreshIconButton from '../../components/RefreshIconButton';
import PipelineNameWithTags from '../../components/PipelineNameWithTags';
import StatusTag from '../../components/StatusTag';
import { ACTIVE_DEPLOYMENT_STATUSES } from '../../constants/deployment';
import { useDeploymentLogStream } from '../../hooks/useDeploymentLogStream';
import type { DeploymentPluginDefinitionSummary, DeploymentSummary } from '../../types/domain';
import { copyText } from '../../utils/clipboard';
import { getDeploymentProgress, getDeploymentProgressColor, getDeploymentProgressLabel } from '../../utils/deploymentProgress';
import { formatDeploymentTimeline, formatDeploymentTimelineTitle } from '../../utils/deploymentTimeline';

type Props = {
  scope: 'admin' | 'user';
};

export default function DeploymentDetailPage({ scope }: Props) {
  const { deploymentId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [deployment, setDeployment] = useState<DeploymentSummary>();
  const [logContent, setLogContent] = useState('');
  const [detailLoading, setDetailLoading] = useState(true);
  const [logLoading, setLogLoading] = useState(true);
  const [logOffset, setLogOffset] = useState(0);
  const [logStreamEnabled, setLogStreamEnabled] = useState(false);
  const [lastLogUpdateAt, setLastLogUpdateAt] = useState(() => Date.now());
  const [logStreamClosing, setLogStreamClosing] = useState(false);
  const [plugins, setPlugins] = useState<DeploymentPluginDefinitionSummary[]>([]);
  const [tick, setTick] = useState(() => Date.now());
  const contentRef = useRef<HTMLDivElement | null>(null);
  const logViewerRef = useRef<LogViewerHandle | null>(null);
  const [contentHeight, setContentHeight] = useState<number>();
  const [, setLogControlTick] = useState(0);
  const [logAnchors, setLogAnchors] = useState<LogAnchor[]>([]);
  const [logAnchorOpen, setLogAnchorOpen] = useState(false);

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
        setLogLoading(false);
      }
    }
  };

  useEffect(() => {
    setLogContent('');
    setLogOffset(0);
    setLogLoading(true);
    setLogStreamEnabled(Boolean(deploymentId));
    setLogStreamClosing(false);
    loadDeploymentDetail().catch(() => message.error('加载部署详情失败'));
    deploymentPluginsApi.list().then(setPlugins).catch(() => setPlugins([]));
  }, [deploymentId]);

  const reloadDeploymentLog = () => {
    setLogContent('');
    setLogOffset(0);
    setLogLoading(true);
    setLogStreamClosing(false);
    setLogStreamEnabled(false);
    window.setTimeout(() => setLogStreamEnabled(Boolean(deploymentId)), 0);
  };

  useEffect(() => {
    const timer = window.setInterval(() => setTick(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  useDeploymentLogStream({
    deploymentId,
    enabled: Boolean(
      deploymentId
        && logStreamEnabled,
    ),
    offset: logOffset,
    onLog: (payload) => {
      if (payload.content) {
        setLogContent((previous) => `${previous}${payload.content}`);
        setLastLogUpdateAt(Date.now());
      }
      setLogLoading(false);
      if (typeof payload.offset === 'number') {
        setLogOffset(payload.offset);
      }
      if (payload.finished) {
        setLogStreamClosing(true);
        loadDeploymentDetail({ silent: true }).catch(() => undefined);
      }
    },
    onDeploymentStatus: (streamStatus) => {
      setLogLoading(false);
      setDeployment((previous) => (
        previous
          ? {
            ...previous,
            status: streamStatus.status,
            startedAt: streamStatus.startedAt,
            finishedAt: streamStatus.finishedAt,
            errorMessage: streamStatus.errorMessage,
            monitoredPid: streamStatus.monitoredPid,
            commitSha: streamStatus.commitSha,
            progressPercent: streamStatus.progressPercent,
            progressStage: streamStatus.progressStage,
            progressCurrent: streamStatus.progressCurrent,
            progressTotal: streamStatus.progressTotal,
          }
          : previous
      ));
    },
    onDone: () => {
      setLogLoading(false);
      setLogStreamClosing(false);
      setLogStreamEnabled(false);
      loadDeploymentDetail({ silent: true }).catch(() => undefined);
    },
    onError: () => {
      setLogLoading(false);
    },
  });

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
  const deploymentDetailBasePath = scope === 'admin' ? '/admin/deployments' : '/user/deployments';
  const defaultBackPath = scope === 'admin'
    ? (searchParams.get('from') === 'services' ? '/admin/services' : '/admin/deployments')
    : '/user/pipelines';
  const backPath = location.state?.from || defaultBackPath;
  const backLabel = location.state?.backLabel
    || (scope === 'admin'
      ? (backPath === '/admin/services' ? '返回服务管理' : backPath === '/admin/dashboard' ? '返回仪表盘' : '返回部署记录')
      : '返回流水线大厅');
  const renderDescriptionItem = (label: string, value?: ReactNode) => {
    if (value == null || (typeof value === 'string' && value.trim() === '')) {
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
      if (window.innerWidth < 768) {
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
        description="查看部署状态、部署日志、耗时、错误信息和部署快照。"
        extra={[
          <Button key="back" onClick={() => navigate(backPath)}>
            {backLabel}
          </Button>,
          rollbackable ? (
            <Popconfirm
              key="rollback"
              title="确认回滚到此版本"
              description="会直接复用这次部署保留下来的构建产物回滚到这个版本，不会重新走构建流程。"
              okText="确认回滚"
              cancelText="取消"
              onConfirm={() => deploymentsApi.rollback(deploymentId || '').then((response) => {
                message.success('回滚任务已创建');
                navigate(`${deploymentDetailBasePath}/${response.id}`, {
                  state: { from: backPath, backLabel },
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
                reloadDeploymentLog();
                return loadDeploymentDetail();
              }).catch(() => message.error('停止部署失败'))}
            >
              <Button danger>停止部署</Button>
            </Popconfirm>
          ) : null,
          <RefreshIconButton key="refresh" onClick={() => {
            loadDeploymentDetail().catch(() => message.error('刷新详情失败'));
            reloadDeploymentLog();
          }} />,
        ]}
      />
      <div ref={contentRef} className="deployment-detail-content" style={contentHeight ? { height: contentHeight } : undefined}>
        <Row className="deployment-detail-grid" gutter={[0, 0]} style={contentHeight ? { height: '100%' } : undefined}>
          <Col className="deployment-detail-col deployment-detail-sidebar-col" xs={24} md={8} xl={6} xxl={5} style={contentHeight ? { height: '100%' } : undefined}>
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
                    {getDeploymentProgressLabel(
                      progress,
                      deployment?.status,
                      deployment?.progressStage,
                      deployment?.progressCurrent,
                      deployment?.progressTotal,
                    )}
                  </div>
                </div>
                <Descriptions column={1} size="small" className="mt-4">
                  {renderDescriptionItem('流水线', <PipelineNameWithTags name={deployment?.pipelineName || deployment?.pipeline?.name} importantTags={deployment?.pipelineImportantTags || deployment?.pipeline?.importantTags} />)}
                  {renderDescriptionItem('项目', deployment?.projectName || deployment?.pipeline?.project?.name)}
                  {renderDescriptionItem('分支', deployment?.branchName)}
                  {renderDescriptionItem('触发人', deployment?.triggeredByDisplayName || deployment?.triggeredBy)}
                  {renderDescriptionItem('停止人', deployment?.stoppedByDisplayName || deployment?.stoppedBy)}
                  {renderDescriptionItem('部署时间', (
                    <span title={formatDeploymentTimelineTitle(deployment)}>
                      {formatDeploymentTimeline(deployment, tick)}
                    </span>
                  ))}
                  {renderDescriptionItem('产物目录', deployment?.artifactPath)}
                  {deployment?.rollbackFromDeploymentId ? (
                    <Descriptions.Item label="重发来源">
                      <Button
                        type="link"
                        className="!px-0"
                        onClick={() => navigate(`${deploymentDetailBasePath}/${deployment.rollbackFromDeploymentId}`, {
                          state: { from: backPath, backLabel },
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
          <Col className="deployment-detail-col deployment-detail-main-col" xs={24} md={16} xl={18} xxl={19} style={contentHeight ? { height: '100%' } : undefined}>
            <Card
              className="app-card deployment-detail-log-card"
              style={contentHeight ? { height: '100%' } : undefined}
              title="部署日志"
              extra={(
                <Space>
                  {logViewerRef.current?.canBackToTop ? (
                    <Button onClick={() => logViewerRef.current?.scrollToTop()}>回到顶部</Button>
                  ) : null}
                  {logViewerRef.current?.canBackToBottom ? (
                    <Button onClick={() => logViewerRef.current?.scrollToBottom()}>回到底部</Button>
                  ) : null}
                  {logStreaming ? (
                    <Button onClick={() => logViewerRef.current?.toggleAutoScroll()}>
                      {logViewerRef.current?.autoScroll === false ? '恢复滚动' : '暂停滚动'}
                    </Button>
                  ) : null}
                  <Button onClick={() => copyText(logContent).then(() => message.success('日志已复制')).catch(() => message.error('复制失败'))}>
                    复制日志
                  </Button>
                  {logAnchors.length > 0 ? (
                    <Button onClick={() => setLogAnchorOpen((open) => !open)}>日志目录 {logAnchors.length}</Button>
                  ) : null}
                </Space>
              )}
            >
              {logAnchorOpen && logAnchors.length > 0 ? (
                <div className="log-viewer-anchor-list">
                  {logAnchors.map((anchor) => (
                    <button
                      key={`${anchor.index}-${anchor.label}`}
                      type="button"
                      className={anchor.level === 'error' ? 'log-viewer-anchor-list__item--error' : undefined}
                      onClick={() => {
                        logViewerRef.current?.scrollToLine(anchor.index);
                        setLogAnchorOpen(false);
                      }}
                    >
                      {anchor.label}
                    </button>
                  ))}
                </div>
              ) : null}
              {logLoading ? (
                <div className="deployment-detail-log-skeleton">
                  <Skeleton active paragraph={{ rows: 12 }} title={false} />
                </div>
              ) : (
                <LogViewer
                  ref={logViewerRef}
                  content={logContent}
                  autoScrollAvailable={logStreaming}
                  idleHint={logIdleHint}
                  onControlStateChange={() => setLogControlTick((current) => current + 1)}
                  onAnchorsChange={setLogAnchors}
                />
              )}
            </Card>
          </Col>
        </Row>
      </div>
    </>
  );
}
