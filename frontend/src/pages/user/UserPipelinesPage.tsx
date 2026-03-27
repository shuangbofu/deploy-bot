import { useEffect, useMemo, useState } from 'react';
import { Button, Card, Col, Input, Modal, Progress, Row, Select, Space, Tag, Typography, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import { deploymentsApi } from '../../api/deployments';
import { pipelinesApi } from '../../api/pipelines';
import EmptyPane from '../../components/EmptyPane';
import PageHeaderBar from '../../components/PageHeaderBar';
import PipelineIcon from '../../components/PipelineIcon';
import StatusTag from '../../components/StatusTag';
import { ACTIVE_DEPLOYMENT_STATUSES } from '../../constants/deployment';
import type { DeploymentSummary, PipelineSummary } from '../../types/domain';
import { formatDateTime } from '../../utils/datetime';
import { formatDeploymentElapsed } from '../../utils/deploymentDuration';
import { getDeploymentProgress, getDeploymentProgressColor } from '../../utils/deploymentProgress';

/**
 * 用户端流水线大厅。
 * 用户在这里只做三件事：选流水线、发部署、看记录。
 */
export default function UserPipelinesPage() {
  const [pipelines, setPipelines] = useState<PipelineSummary[]>([]);
  const [deployments, setDeployments] = useState<DeploymentSummary[]>([]);
  const [submittingId, setSubmittingId] = useState<number>();
  const [deployModalOpen, setDeployModalOpen] = useState(false);
  const [deployingPipeline, setDeployingPipeline] = useState<PipelineSummary>();
  const [branchOptions, setBranchOptions] = useState<string[]>([]);
  const [selectedBranch, setSelectedBranch] = useState<string>();
  const [branchesLoading, setBranchesLoading] = useState(false);
  const [tick, setTick] = useState(() => Date.now());
  const [keyword, setKeyword] = useState('');
  const [tagFilter, setTagFilter] = useState<string[]>();
  const navigate = useNavigate();

  const parseTagsJson = (content: unknown): string[] => {
    if (!content) {
      return [];
    }
    if (Array.isArray(content)) {
      return content.filter(Boolean).map((item) => String(item).trim()).filter(Boolean);
    }
    try {
      const parsed = JSON.parse(String(content));
      return Array.isArray(parsed) ? parsed.filter(Boolean).map((item) => String(item).trim()).filter(Boolean) : [];
    } catch {
      return [];
    }
  };

  /** 同步加载流水线列表和最新部署记录，用于拼装大厅卡片。 */
  const loadData = async () => {
    const [nextPipelines, nextDeployments] = await Promise.all([
      pipelinesApi.list(),
      deploymentsApi.list(),
    ]);
    setPipelines(nextPipelines);
    setDeployments(nextDeployments);
  };

  useEffect(() => {
    loadData().catch(() => message.error('加载流水线失败'));
  }, []);

  useEffect(() => {
    const timer = window.setInterval(() => setTick(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  /** 把流水线和它的最近一次部署拼成卡片展示结构。 */
  const pipelineCards = useMemo(() => pipelines.map((pipeline) => ({
    pipeline,
    latestDeployment: deployments.find((item) => item.pipeline?.id === pipeline.id),
  })), [pipelines, deployments]);

  const availableTags = useMemo(
    () => Array.from(new Set(pipelines.flatMap((item) => parseTagsJson(item.tagsJson)))).sort((left, right) => left.localeCompare(right, 'zh-CN')),
    [pipelines],
  );

  const filteredPipelineCards = useMemo(() => pipelineCards.filter(({ pipeline }) => {
    const tags = parseTagsJson(pipeline.tagsJson);
    const normalizedKeyword = keyword.trim().toLowerCase();
    if (normalizedKeyword) {
      const matched = [pipeline.name, pipeline.description, pipeline.project?.name, pipeline.defaultBranch, ...tags]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(normalizedKeyword));
      if (!matched) {
        return false;
      }
    }
    if (tagFilter && tagFilter.length > 0 && !tagFilter.every((tag) => tags.includes(tag))) {
      return false;
    }
    return true;
  }), [pipelineCards, keyword, tagFilter]);

  /** 打开部署弹窗时顺便拉取可选分支。 */
  const openDeployModal = async (pipeline: PipelineSummary) => {
    setDeployingPipeline(pipeline);
    setSelectedBranch(pipeline.defaultBranch);
    setDeployModalOpen(true);
    setBranchesLoading(true);
    try {
      const branches = await pipelinesApi.getBranches(pipeline.id);
      setBranchOptions(branches);
      if (branches.length > 0) {
        setSelectedBranch(branches.includes(pipeline.defaultBranch || '') ? pipeline.defaultBranch : branches[0]);
      }
    } catch {
      setBranchOptions(pipeline.defaultBranch ? [pipeline.defaultBranch] : []);
      message.error('加载分支失败，已回退到默认分支');
    } finally {
      setBranchesLoading(false);
    }
  };

  /** 创建部署任务；如果已有运行中的任务，则按约定替换前一个。 */
  const createDeployment = async () => {
    if (!deployingPipeline) {
      return;
    }
    const activeDeployment = deployments.find(
      (item) => item.pipeline?.id === deployingPipeline.id && item.status && ACTIVE_DEPLOYMENT_STATUSES.includes(item.status),
    );
    setSubmittingId(deployingPipeline.id);
    try {
      const deployment = await deploymentsApi.create({
        pipelineId: deployingPipeline.id,
        branchName: selectedBranch,
        triggeredBy: 'user',
        replaceRunning: Boolean(activeDeployment),
      });
      setDeployModalOpen(false);
      setDeployingPipeline(undefined);
      setBranchOptions([]);
      await loadData();
      navigate(`/user/deployments/${deployment.id}`, {
        state: { from: '/user/pipelines', backLabel: '返回流水线大厅' },
      });
      message.success('部署已触发');
    } finally {
      setSubmittingId(undefined);
    }
  };

  return (
    <>
      <PageHeaderBar
        title="流水线大厅"
        description="这里只展示已经由管理端准备好的流水线。用户只做部署和查看执行过程，不承担任何配置动作。"
      />
      {pipelineCards.length === 0 ? (
        <Card className="app-card">
          <EmptyPane description="当前没有可部署流水线，请先到管理端创建。" />
        </Card>
      ) : (
        <>
          <Card className="app-card !mb-4">
            <div className="grid grid-cols-1 gap-3 lg:grid-cols-[minmax(0,1fr)_320px_auto]">
              <Input
                value={keyword}
                placeholder="搜索流水线名称 / 项目 / 分支 / 标签"
                onChange={(event) => setKeyword(event.target.value)}
              />
              <div className="flex items-center">
                <Button
                  onClick={() => {
                    setKeyword('');
                    setTagFilter(undefined);
                  }}
                >
                  重置条件
                </Button>
              </div>
            </div>
            {availableTags.length > 0 ? (
              <div className="mt-3 flex flex-wrap gap-2">
                {availableTags.map((tag) => {
                  const active = Boolean(tagFilter?.includes(tag));
                  return (
                    <Tag
                      key={tag}
                      color={active ? 'blue' : 'default'}
                      className="cursor-pointer select-none !px-3 !py-1"
                      onClick={() => setTagFilter((previous) => {
                        const next = previous?.includes(tag)
                          ? previous.filter((item) => item !== tag)
                          : [...(previous || []), tag];
                        return next.length > 0 ? next : undefined;
                      })}
                    >
                      {tag}
                    </Tag>
                  );
                })}
              </div>
            ) : null}
          </Card>
          <Row gutter={[16, 16]}>
            {filteredPipelineCards.map(({ pipeline, latestDeployment }) => {
              const tags = parseTagsJson(pipeline.tagsJson);
              return (
            <Col xs={24} md={12} xl={8} xxl={6} key={pipeline.id}>
              <Card className="pipeline-card" bordered={false}>
                <div className="pipeline-card-header">
                  <div className="pipeline-card-content">
                    <PipelineIcon type={pipeline.template?.templateType} />
                    <div className="min-w-0 flex-1">
                      <Typography.Text className="block text-[11px] uppercase tracking-[0.24em] text-slate-400">
                        {pipeline.project?.name || 'Project'}
                      </Typography.Text>
                      <Typography.Title level={4} className="!mb-1 !mt-2">
                        {pipeline.name}
                      </Typography.Title>
                      <Typography.Paragraph className="!mb-0 text-slate-600">
                        {pipeline.description || '已配置完成，可直接部署。'}
                      </Typography.Paragraph>
                      {tags.length > 0 ? (
                        <Space className="!mt-3" wrap>
                          {tags.map((tag) => <Tag key={tag}>{tag}</Tag>)}
                        </Space>
                      ) : null}
                    </div>
                  </div>
                  <div className="pipeline-card-status">
                    <StatusTag status={latestDeployment?.status} />
                  </div>
                </div>
                <div className="pipeline-meta-panel">
                  <div className="pipeline-meta-row">
                    <span>默认分支</span>
                    <span>{pipeline.defaultBranch}</span>
                  </div>
                  <div className="pipeline-meta-row">
                    <span>模板</span>
                    <span>{pipeline.template?.name || '-'}</span>
                  </div>
                  <div className="pipeline-meta-row">
                    <span>最近部署</span>
                    <span>{latestDeployment ? `#${latestDeployment.id}` : '-'}</span>
                  </div>
                  <div className="pipeline-meta-row">
                    <span>部署人</span>
                    <span>{latestDeployment?.triggeredByDisplayName || latestDeployment?.triggeredBy || '-'}</span>
                  </div>
                  <div className="pipeline-meta-row">
                    <span>部署时间</span>
                    <span>{formatDateTime(latestDeployment?.createdAt)}</span>
                  </div>
                  <div className="pipeline-meta-row">
                    <span>当前耗时</span>
                    <span>{formatDeploymentElapsed(latestDeployment, tick)}</span>
                  </div>
                  <Progress
                    percent={getDeploymentProgress(latestDeployment)}
                    format={() => latestDeployment?.progressText || `${getDeploymentProgress(latestDeployment)}%`}
                    strokeColor={getDeploymentProgressColor(latestDeployment?.status)}
                    trailColor="#d9e2f1"
                  />
                </div>
                <Space>
                  <Button
                    type="primary"
                    loading={submittingId === pipeline.id}
                    onClick={() => openDeployModal(pipeline).catch(() => message.error('打开部署窗口失败'))}
                  >
                    部署
                  </Button>
                  <Button
                    disabled={!latestDeployment}
                    onClick={() => latestDeployment && navigate(`/user/deployments/${latestDeployment.id}`, {
                      state: { from: '/user/pipelines', backLabel: '返回流水线大厅' },
                    })}
                  >
                    查看进度
                  </Button>
                  <Button onClick={() => navigate(`/user/pipelines/${pipeline.id}/history`)}>
                    部署记录
                  </Button>
                </Space>
              </Card>
            </Col>
              );
            })}
          </Row>
        </>
      )}
      <Modal
        title={deployingPipeline ? `部署 ${deployingPipeline.name}` : '部署流水线'}
        open={deployModalOpen}
        okText="开始部署"
        cancelText="取消"
        confirmLoading={deployingPipeline ? submittingId === deployingPipeline.id : false}
        onCancel={() => {
          setDeployModalOpen(false);
          setDeployingPipeline(undefined);
          setBranchOptions([]);
        }}
        onOk={() => createDeployment().catch(() => message.error('触发部署失败'))}
        destroyOnClose
      >
        <div className="space-y-4">
          {deployingPipeline && deployments.some((item) => item.pipeline?.id === deployingPipeline.id && item.status && ACTIVE_DEPLOYMENT_STATUSES.includes(item.status)) ? (
            <div className="rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-700">
              检测到当前流水线还有执行中的部署。继续部署会先停止前一个正在运行的任务。
            </div>
          ) : null}
          <div className="text-sm text-slate-500">
            请选择这次部署要使用的分支。
          </div>
          <Select
            className="w-full"
            loading={branchesLoading}
            value={selectedBranch}
            options={branchOptions.map((item) => ({ label: item, value: item }))}
            onChange={setSelectedBranch}
            placeholder="请选择分支"
            showSearch
            optionFilterProp="label"
          />
        </div>
      </Modal>
    </>
  );
}
