import { useEffect, useMemo, useState } from 'react';
import { Button, Card, Col, Descriptions, Popconfirm, Progress, Row, Space, message } from 'antd';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { deploymentsApi } from '../../api/deployments';
import LogViewer from '../../components/LogViewer';
import PageHeaderBar from '../../components/PageHeaderBar';
import StatusTag from '../../components/StatusTag';
import { ACTIVE_DEPLOYMENT_STATUSES } from '../../constants/deployment';
import type { DeploymentSummary } from '../../types/domain';
import { formatDateTime } from '../../utils/datetime';
import { getDeploymentProgress, getDeploymentProgressColor } from '../../utils/deploymentProgress';

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

  /** 同步加载部署详情与当前日志内容。 */
  const loadDetail = async () => {
    const [detail, log] = await Promise.all([
      deploymentsApi.detail(deploymentId || ''),
      deploymentsApi.getLog(deploymentId || ''),
    ]);
    setDeployment(detail);
    setLogContent(log.content);
  };

  useEffect(() => {
    loadDetail().catch(() => message.error('加载部署详情失败'));
  }, [deploymentId]);

  useEffect(() => {
    if (!deployment?.status || !ACTIVE_DEPLOYMENT_STATUSES.includes(deployment.status)) {
      return undefined;
    }
    // 运行中的任务每 3 秒轮询一次，保持详情页与后台状态接近实时。
    const timer = window.setInterval(() => {
      loadDetail().catch(() => message.error('刷新部署详情失败'));
    }, 3000);
    return () => window.clearInterval(timer);
  }, [deployment, deploymentId]);

  /** 进度值由后端计算，前端只负责展示。 */
  const progress = useMemo(() => getDeploymentProgress(deployment), [deployment]);
  const stoppable = Boolean(deployment?.status && ACTIVE_DEPLOYMENT_STATUSES.includes(deployment.status));
  const rollbackable = Boolean(
    deployment?.artifactPath
      && deployment?.status
      && !ACTIVE_DEPLOYMENT_STATUSES.includes(deployment.status),
  );
  const backTarget = location.state?.from || '/user/pipelines';
  const backLabel = location.state?.backLabel || '返回流水线大厅';

  return (
    <>
      <PageHeaderBar
        title={`部署详情 #${deploymentId}`}
        description="部署触发后，用户只在这里跟进状态和日志，不再回到大厅做额外配置。"
        extra={[
          <Button key="back" onClick={() => navigate(backTarget)}>{backLabel}</Button>,
          rollbackable ? (
            <Popconfirm
              key="rollback"
              title="确认重新发布"
              description="会直接复用这次部署保留下来的构建产物重新发布，不会重新执行构建流程。"
              okText="确认重新发布"
              cancelText="取消"
              onConfirm={() => deploymentsApi.rollback(deploymentId || '').then((response) => {
                message.success('重新发布任务已创建');
                navigate(`/user/deployments/${response.id}`, {
                  state: {
                    from: backTarget,
                    backLabel,
                  },
                });
              }).catch(() => message.error('创建回滚任务失败'))}
            >
              <Button>重新发布此版本</Button>
            </Popconfirm>
          ) : null,
          stoppable ? (
            <Button
              key="stop"
              danger
              onClick={() => deploymentsApi.stop(deploymentId || '').then(() => {
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
      <Row gutter={[16, 16]}>
        <Col xs={24} xl={7} xxl={6}>
          <Card
            className="app-card"
            title="执行状态"
            extra={<StatusTag status={deployment?.status} />}
          >
            <Progress
              percent={progress}
              strokeColor={getDeploymentProgressColor(deployment?.status)}
              format={() => deployment?.progressText || `${progress}%`}
            />
            <Descriptions column={1} size="small" className="mt-4">
              <Descriptions.Item label="流水线">{deployment?.pipeline?.name || '-'}</Descriptions.Item>
              <Descriptions.Item label="分支">{deployment?.branchName || '-'}</Descriptions.Item>
              <Descriptions.Item label="触发人">{deployment?.triggeredBy || '-'}</Descriptions.Item>
              <Descriptions.Item label="创建时间">{formatDateTime(deployment?.createdAt)}</Descriptions.Item>
              <Descriptions.Item label="开始时间">{formatDateTime(deployment?.startedAt)}</Descriptions.Item>
              <Descriptions.Item label="结束时间">{formatDateTime(deployment?.finishedAt)}</Descriptions.Item>
              <Descriptions.Item label="产物目录">{deployment?.artifactPath || '-'}</Descriptions.Item>
              <Descriptions.Item label="重发来源">
                {deployment?.rollbackFromDeploymentId ? (
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
                ) : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="监控 PID">{deployment?.monitoredPid || '-'}</Descriptions.Item>
              <Descriptions.Item label="错误信息">{deployment?.errorMessage || '-'}</Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>
        <Col xs={24} xl={17} xxl={18}>
          <Card
            className="app-card"
            title="执行日志"
            extra={(
              <Space>
                <Button onClick={() => navigator.clipboard.writeText(logContent).then(() => message.success('日志已复制')).catch(() => message.error('复制失败'))}>
                  复制日志
                </Button>
              </Space>
            )}
          >
            <LogViewer content={logContent} maxHeight={680} />
          </Card>
        </Col>
      </Row>
    </>
  );
}
