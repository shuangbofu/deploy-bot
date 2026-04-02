import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Button, Card, Col, Collapse, Descriptions, Popconfirm, Progress, Row, Space, message } from 'antd';
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { deploymentsApi } from '../../api/deployments';
import LogViewer from '../../components/LogViewer';
import PageHeaderBar from '../../components/PageHeaderBar';
import StatusTag from '../../components/StatusTag';
import { ACTIVE_DEPLOYMENT_STATUSES } from '../../constants/deployment';
import type { DeploymentSummary } from '../../types/domain';
import { copyText } from '../../utils/clipboard';
import { formatDateTime } from '../../utils/datetime';
import { formatDeploymentElapsed } from '../../utils/deploymentDuration';
import { getDeploymentProgress, getDeploymentProgressColor } from '../../utils/deploymentProgress';
import { formatDeploymentTimeline } from '../../utils/deploymentTimeline';

/**
 * 管理端部署详情页。
 * 相比用户端，这里更强调回滚、停止和排查错误。
 */
export default function AdminDeploymentDetailPage() {
  const { deploymentId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [deployment, setDeployment] = useState<DeploymentSummary>();
  const [logContent, setLogContent] = useState('');
  const [tick, setTick] = useState(() => Date.now());
  const contentRef = useRef<HTMLDivElement | null>(null);
  const [contentHeight, setContentHeight] = useState<number>();

  /** 同步加载部署详情与日志内容。 */
  const loadDetail = async () => {
    const [detailResponse, logResponse] = await Promise.all([
      deploymentsApi.detail(String(deploymentId)),
      deploymentsApi.getLog(String(deploymentId)),
    ]);
    setDeployment(detailResponse);
    setLogContent(logResponse.content);
  };

  useEffect(() => {
    loadDetail().catch(() => message.error('加载部署详情失败'));
  }, [deploymentId]);

  useEffect(() => {
    const timer = window.setInterval(() => setTick(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    if (!deployment?.status || !ACTIVE_DEPLOYMENT_STATUSES.includes(deployment.status)) {
      return undefined;
    }
    const timer = window.setInterval(() => {
      loadDetail().catch(() => message.error('刷新部署详情失败'));
    }, 3000);
    return () => window.clearInterval(timer);
  }, [deployment, deploymentId]);

  /** 进度值使用后端阶段化结果，避免前端重复推导。 */
  const progress = useMemo(() => getDeploymentProgress(deployment), [deployment]);
  const stoppable = Boolean(deployment?.status && ACTIVE_DEPLOYMENT_STATUSES.includes(deployment.status));
  const rollbackable = Boolean(
    deployment?.artifactPath
      && deployment?.status
      && deployment.status === 'SUCCESS',
  );
  const backPath = location.state?.from || (searchParams.get('from') === 'services' ? '/admin/services' : '/admin/deployments');
  const backLabel = location.state?.backLabel || (backPath === '/admin/services' ? '返回服务管理' : backPath === '/admin/dashboard' ? '返回控制台' : '返回部署记录');
  const executionSnapshot = useMemo(() => {
    const raw = deployment?.executionSnapshotJson;
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
  }, [deployment?.executionSnapshotJson]);
  const snapshotVariables = useMemo(() => {
    const raw = executionSnapshot?.variables;
    if (Array.isArray(raw)) {
      return raw.map((item) => {
        const entry = item as { name?: unknown; label?: unknown; value?: unknown };
        const name = String(entry.name ?? '-');
        return {
          name,
          label: String(entry.label ?? name),
          value: String(entry.value ?? '-'),
        };
      });
    }
    return Object.entries((raw as Record<string, unknown> | undefined) || {}).map(([name, value]) => ({
      name,
      label: name,
      value: String(value ?? '-'),
    }));
  }, [executionSnapshot]);

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
  }, [deployment, snapshotVariables.length]);

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
              title="确认重新发布"
              description="会直接复用这次部署保留下来的构建产物重新发布，不会重新走构建流程。"
              okText="确认重新发布"
              cancelText="取消"
              onConfirm={() => deploymentsApi.rollback(String(deploymentId)).then((response) => {
                message.success('重新发布任务已创建');
                navigate(`/admin/deployments/${response.id}`);
              }).catch(() => message.error('创建回滚任务失败'))}
            >
              <Button>重新发布此版本</Button>
            </Popconfirm>
          ) : null,
          stoppable ? (
            <Button
              key="stop"
              danger
              onClick={() => deploymentsApi.stop(String(deploymentId)).then(() => {
                message.success('部署已停止');
                return loadDetail();
              }).catch(() => message.error('停止部署失败'))}
            >
              停止部署
            </Button>
          ) : null,
          <Button key="refresh" type="primary" onClick={() => loadDetail().catch(() => message.error('刷新失败'))}>刷新</Button>,
        ]}
      />
      <div ref={contentRef} className="deployment-detail-content" style={contentHeight ? { height: contentHeight } : undefined}>
        <Row className="deployment-detail-grid" gutter={[0, 0]} style={contentHeight ? { height: '100%' } : undefined}>
        <Col className="deployment-detail-col deployment-detail-sidebar-col" xs={24} xl={7} xxl={6} style={contentHeight ? { height: '100%' } : undefined}>
          <div className="deployment-detail-sidebar" style={contentHeight ? { height: '100%' } : undefined}>
            <Card
              className="app-card"
              title="部署状态"
              extra={<StatusTag status={deployment?.status} />}
            >
              <Progress
                percent={progress}
                strokeColor={getDeploymentProgressColor(deployment?.status)}
                format={() => deployment?.progressText || `${progress}%`}
              />
              <Descriptions column={1} size="small" className="mt-4">
                <Descriptions.Item label="流水线">{deployment?.pipeline?.name || '-'}</Descriptions.Item>
                <Descriptions.Item label="项目">{deployment?.pipeline?.project?.name || '-'}</Descriptions.Item>
                <Descriptions.Item label="分支">{deployment?.branchName || '-'}</Descriptions.Item>
                <Descriptions.Item label="触发人">{deployment?.triggeredByDisplayName || deployment?.triggeredBy || '-'}</Descriptions.Item>
                <Descriptions.Item label="部署时间">{formatDeploymentTimeline(deployment, tick)}</Descriptions.Item>
                <Descriptions.Item label="产物目录">{deployment?.artifactPath || '-'}</Descriptions.Item>
                <Descriptions.Item label="重发来源">
                  {deployment?.rollbackFromDeploymentId ? (
                    <Button type="link" className="!px-0" onClick={() => navigate(`/admin/deployments/${deployment.rollbackFromDeploymentId}`)}>
                      #{deployment.rollbackFromDeploymentId}
                    </Button>
                  ) : '-'}
                </Descriptions.Item>
                <Descriptions.Item label="监控 PID">{deployment?.monitoredPid || '-'}</Descriptions.Item>
                <Descriptions.Item label="错误信息">{deployment?.errorMessage || '-'}</Descriptions.Item>
              </Descriptions>
            </Card>
            <Card className="app-card deployment-detail-snapshot-card" title="部署快照">
              <Descriptions column={1} size="small">
                <Descriptions.Item label="模板">{String(executionSnapshot?.templateName || '-')}</Descriptions.Item>
                <Descriptions.Item label="模板类型">{String(executionSnapshot?.templateType || '-')}</Descriptions.Item>
                <Descriptions.Item label="目标主机">{String(executionSnapshot?.targetHost || '-')}</Descriptions.Item>
                <Descriptions.Item label="应用名">{String(executionSnapshot?.applicationName || '-')}</Descriptions.Item>
                <Descriptions.Item label="Spring Profile">{String(executionSnapshot?.springProfile || '-')}</Descriptions.Item>
                <Descriptions.Item label="启动关键字">{String(executionSnapshot?.startupKeyword || '-')}</Descriptions.Item>
                <Descriptions.Item label="启动超时">{String(executionSnapshot?.startupTimeoutSeconds || '-')}</Descriptions.Item>
                <Descriptions.Item label="构建 Java">{String((executionSnapshot?.javaEnvironment as { version?: string; name?: string } | undefined)?.name || '-')} {String((executionSnapshot?.javaEnvironment as { version?: string } | undefined)?.version || '')}</Descriptions.Item>
                <Descriptions.Item label="构建 Node">{String((executionSnapshot?.nodeEnvironment as { version?: string; name?: string } | undefined)?.name || '-')} {String((executionSnapshot?.nodeEnvironment as { version?: string } | undefined)?.version || '')}</Descriptions.Item>
                <Descriptions.Item label="构建 Maven">{String((executionSnapshot?.mavenEnvironment as { version?: string; name?: string } | undefined)?.name || '-')} {String((executionSnapshot?.mavenEnvironment as { version?: string } | undefined)?.version || '')}</Descriptions.Item>
                <Descriptions.Item label="运行 Java">{String((executionSnapshot?.runtimeJavaEnvironment as { version?: string; name?: string } | undefined)?.name || '-')} {String((executionSnapshot?.runtimeJavaEnvironment as { version?: string } | undefined)?.version || '')}</Descriptions.Item>
              </Descriptions>
              <Collapse
                className="mt-4"
                ghost
                items={[
                  {
                    key: 'variables',
                    label: '变量快照',
                    children: (
                      <div className="space-y-2">
                        {snapshotVariables.length > 0 ? snapshotVariables.map((item) => (
                          <div key={item.name} className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                            <div className="flex items-start gap-3 text-sm">
                              <div className="w-40 shrink-0 font-semibold text-slate-700">{item.label}</div>
                              <code className="block flex-1 break-all text-[13px] font-medium text-slate-600">
                                {item.value}
                              </code>
                            </div>
                          </div>
                        )) : (
                          <div className="rounded-lg border border-dashed border-slate-200 px-3 py-4 text-sm text-slate-400">
                            暂无变量快照
                          </div>
                        )}
                      </div>
                    ),
                  },
                ]}
              />
            </Card>
          </div>
        </Col>
        <Col className="deployment-detail-col deployment-detail-main-col" xs={24} xl={17} xxl={18} style={contentHeight ? { height: '100%' } : undefined}>
          <Card
            className="app-card deployment-detail-log-card"
            style={contentHeight ? { height: '100%' } : undefined}
            title="部署日志"
            extra={(
              <Space>
                <Button onClick={() => copyText(logContent).then(() => message.success('日志已复制')).catch(() => message.error('复制失败'))}>
                  复制日志
                </Button>
              </Space>
            )}
          >
            <LogViewer content={logContent} />
          </Card>
        </Col>
      </Row>
      </div>
    </>
  );
}
