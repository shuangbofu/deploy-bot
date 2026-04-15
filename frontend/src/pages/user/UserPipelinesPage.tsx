import { AppstoreOutlined, BarsOutlined, BorderOutlined, StarFilled } from '@ant-design/icons';
import { useEffect, useMemo, useState } from 'react';
import { Button, Card, Col, Input, Modal, Popconfirm, Progress, Row, Segmented, Select, Skeleton, Space, Table, Tag, Typography, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import { deploymentsApi } from '../../api/deployments';
import { pipelinesApi } from '../../api/pipelines';
import EmptyPane from '../../components/EmptyPane';
import PageHeaderBar from '../../components/PageHeaderBar';
import PipelineIcon, { templateTypeOptions } from '../../components/PipelineIcon';
import StatusTag from '../../components/StatusTag';
import { ACTIVE_DEPLOYMENT_STATUSES } from '../../constants/deployment';
import type { PipelineHallSummary, PipelineSummary, UserRecentPipelineSummary } from '../../types/domain';
import { formatDateTime } from '../../utils/datetime';
import { formatDeploymentElapsed } from '../../utils/deploymentDuration';
import { getDeploymentProgress, getDeploymentProgressColor } from '../../utils/deploymentProgress';
import { getStableTagColor } from '../../utils/tagColors';

/**
 * 用户端流水线大厅。
 * 用户在这里只做三件事：选流水线、发部署、看记录。
 */
export default function UserPipelinesPage() {
  const IDLE_POLL_INTERVAL = 15000;
  const ACTIVE_POLL_INTERVAL = 3000;
  const VIEW_MODE_STORAGE_KEY = 'deploy-bot:user-pipelines-view';
  const FILTER_MODE_STORAGE_KEY = 'deploy-bot:user-pipelines-filter-mode';
  const [hallItems, setHallItems] = useState<PipelineHallSummary[]>([]);
  const [recentPipelines, setRecentPipelines] = useState<UserRecentPipelineSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [submittingId, setSubmittingId] = useState<number>();
  const [deployModalOpen, setDeployModalOpen] = useState(false);
  const [deployingPipeline, setDeployingPipeline] = useState<PipelineSummary>();
  const [branchOptions, setBranchOptions] = useState<string[]>([]);
  const [selectedBranch, setSelectedBranch] = useState<string>();
  const [branchesLoading, setBranchesLoading] = useState(false);
  const [tick, setTick] = useState(() => Date.now());
  const [keyword, setKeyword] = useState('');
  const [tagFilter, setTagFilter] = useState<string[]>();
  const [typeFilter, setTypeFilter] = useState<string>();
  const [selectedPipelineId, setSelectedPipelineId] = useState<number>();
  const [viewMode, setViewMode] = useState<'card' | 'table'>(() => {
    if (typeof window === 'undefined') {
      return 'card';
    }
    return window.localStorage.getItem(VIEW_MODE_STORAGE_KEY) === 'table' ? 'table' : 'card';
  });
  const [filterMode, setFilterMode] = useState<'all' | 'favorites'>(() => {
    if (typeof window === 'undefined') {
      return 'all';
    }
    return window.localStorage.getItem(FILTER_MODE_STORAGE_KEY) === 'favorites' ? 'favorites' : 'all';
  });
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

  /** 首次加载或慢轮询时同步刷新大厅卡片和左侧最近部署。 */
  const loadData = async (silent = false) => {
    if (!silent) {
      setLoading(true);
    }
    try {
      const [nextHallItems, nextRecentPipelines] = await Promise.all([
        pipelinesApi.listHall(),
        deploymentsApi.listMineRecentPipelines(),
      ]);
      setHallItems(nextHallItems);
      setRecentPipelines(nextRecentPipelines);
    } finally {
      if (!silent) {
        setLoading(false);
      }
    }
  };

  const refreshActiveHallItems = async () => {
    const activeIds = hallItems
      .filter((item) => item.latestStatus && ACTIVE_DEPLOYMENT_STATUSES.includes(item.latestStatus))
      .map((item) => item.pipelineId);
    if (activeIds.length === 0) {
      return;
    }
    const nextActiveItems = await pipelinesApi.listHallByIds(activeIds);
    if (nextActiveItems.length === 0) {
      return;
    }
    const nextActiveMap = new Map(nextActiveItems.map((item) => [item.pipelineId, item]));
    setHallItems((current) => current.map((item) => nextActiveMap.get(item.pipelineId) ?? item));
  };

  useEffect(() => {
    loadData().catch(() => message.error('加载流水线失败'));
  }, []);

  useEffect(() => {
    if (loading && hallItems.length === 0) {
      return undefined;
    }
    const hasActiveDeployment = hallItems.some((item) => item.latestStatus && ACTIVE_DEPLOYMENT_STATUSES.includes(item.latestStatus));
    const interval = window.setInterval(() => {
      if (hasActiveDeployment) {
        refreshActiveHallItems().catch(() => {});
        return;
      }
      loadData(true).catch(() => {});
    }, hasActiveDeployment ? ACTIVE_POLL_INTERVAL : IDLE_POLL_INTERVAL);
    return () => window.clearInterval(interval);
  }, [hallItems, loading]);

  useEffect(() => {
    const timer = window.setInterval(() => setTick(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(VIEW_MODE_STORAGE_KEY, viewMode);
    }
  }, [viewMode]);

  useEffect(() => {
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(FILTER_MODE_STORAGE_KEY, filterMode);
    }
  }, [filterMode]);

    const availableTags = useMemo(
    () => Array.from(new Set(hallItems.flatMap((item) => parseTagsJson(item.tagsJson)))).sort((left, right) => left.localeCompare(right, 'zh-CN')),
    [hallItems],
  );

  const frequentPipelines = useMemo(() => recentPipelines.slice(0, 8), [recentPipelines]);

  const availableTypeOptions = useMemo(
    () => templateTypeOptions.filter((option) => hallItems.some((item) => item.templateType === option.value)),
    [hallItems],
  );

  const filteredPipelineCards = useMemo(() => hallItems.filter((item) => {
    if (filterMode === 'favorites' && !item.favorited) {
      return false;
    }
    if (selectedPipelineId && item.pipelineId !== selectedPipelineId) {
      return false;
    }
    if (typeFilter && item.templateType !== typeFilter) {
      return false;
    }
    const tags = parseTagsJson(item.tagsJson);
    const normalizedKeyword = keyword.trim().toLowerCase();
    if (normalizedKeyword) {
      const matched = [item.pipelineName, item.pipelineDescription, item.projectName, item.defaultBranch, ...tags]
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
  }), [filterMode, hallItems, keyword, tagFilter, selectedPipelineId, typeFilter]);

  const toggleFavorite = async (pipelineId: number, favorited?: boolean | null) => {
    if (favorited) {
      await pipelinesApi.unfavorite(pipelineId);
    } else {
      await pipelinesApi.favorite(pipelineId);
    }
    setHallItems((current) => current.map((item) => item.pipelineId === pipelineId ? { ...item, favorited: !favorited } : item));
    setRecentPipelines((current) => [...current]);
  };

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
    const activePipeline = hallItems.find((item) => item.pipelineId === deployingPipeline.id);
    setSubmittingId(deployingPipeline.id);
    try {
      const deployment = await deploymentsApi.create({
        pipelineId: deployingPipeline.id,
        branchName: selectedBranch,
        triggeredBy: 'user',
        replaceRunning: Boolean(activePipeline?.latestStatus && ACTIVE_DEPLOYMENT_STATUSES.includes(activePipeline.latestStatus)),
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

  const stopDeployment = async (deploymentId: number) => {
    await deploymentsApi.stop(deploymentId);
    await loadData();
  };

  return (
    <>
      <PageHeaderBar
        title="流水线大厅"
        description="查看可用流水线，选择分支并发起部署。"
        extra={(
          <Space>
            <Button onClick={() => loadData().catch(() => message.error('加载流水线失败'))}>刷新</Button>
            <Segmented
              className="pipeline-hall-header-switch pipeline-hall-view-switch"
              value={filterMode}
              onChange={(value) => setFilterMode(value as 'all' | 'favorites')}
              options={[
                  { value: 'favorites', icon: <StarFilled />, label: '收藏' },
                { value: 'all', icon: <AppstoreOutlined />, label: '全部' }
              ]}
            />
            <Segmented
              className="pipeline-hall-header-switch pipeline-hall-view-switch"
              value={viewMode}
              onChange={(value) => setViewMode(value as 'card' | 'table')}
              options={[
                { value: 'card', icon: <BorderOutlined />, label: '卡片' },
                { value: 'table', icon: <BarsOutlined />, label: '表格' },
              ]}
            />
          </Space>
        )}
      />
      {loading ? (
        <div className="pipeline-hall-layout">
          <div className="pipeline-hall-sidebar">
            <Card className="app-card h-fit">
              <div className="space-y-3">
                <Skeleton.Input active block style={{ height: 40 }} />
                <Skeleton.Button active block style={{ height: 32 }} />
              </div>
              <div className="mt-4 flex flex-wrap gap-2">
                {Array.from({ length: 8 }).map((_, index) => (
                  <Skeleton.Button key={index} active size="small" style={{ width: 64, height: 28 }} />
                ))}
              </div>
            </Card>
            <Card className="app-card pipeline-hall-recent-card mt-4" title="最近部署">
              <div className="space-y-2">
                {Array.from({ length: 6 }).map((_, index) => (
                  <Skeleton.Button key={index} active block style={{ height: 34 }} />
                ))}
              </div>
            </Card>
          </div>
          <div className="pipeline-hall-content">
          <Row gutter={[16, 16]}>
            {Array.from({ length: 8 }).map((_, index) => (
              <Col xs={24} md={12} xl={8} xxl={6} key={index}>
                <Card className="pipeline-card" bordered={false}>
                  <div className="pipeline-card-header">
                    <div className="pipeline-card-content">
                      <Skeleton.Avatar active shape="square" size={44} />
                      <div className="min-w-0 flex-1">
                        <Skeleton.Input active style={{ width: 88, height: 14 }} />
                        <div className="mt-2">
                          <Skeleton.Input active style={{ width: '70%', height: 24 }} />
                        </div>
                        <div className="mt-3 space-y-2">
                          <Skeleton.Input active block style={{ height: 14 }} />
                          <Skeleton.Input active style={{ width: '82%', height: 14 }} />
                        </div>
                      </div>
                    </div>
                    <Skeleton.Button active size="small" style={{ width: 70, height: 24 }} />
                  </div>
                  <div className="mt-3 flex flex-wrap gap-2">
                    {Array.from({ length: 3 }).map((__, tagIndex) => (
                      <Skeleton.Button key={tagIndex} active size="small" style={{ width: 56, height: 24 }} />
                    ))}
                  </div>
                  <div className="pipeline-meta-panel mt-4">
                    {Array.from({ length: 6 }).map((__, rowIndex) => (
                      <div className="pipeline-meta-row" key={rowIndex}>
                        <Skeleton.Input active style={{ width: 64, height: 14 }} />
                        <Skeleton.Input active style={{ width: 96, height: 14 }} />
                      </div>
                    ))}
                    <div className="pt-2">
                      <Skeleton.Input active block style={{ height: 16 }} />
                    </div>
                  </div>
                  <Space>
                    <Skeleton.Button active style={{ width: 72, height: 32 }} />
                    <Skeleton.Button active style={{ width: 88, height: 32 }} />
                    <Skeleton.Button active style={{ width: 88, height: 32 }} />
                  </Space>
                </Card>
              </Col>
            ))}
          </Row>
          </div>
        </div>
      ) : hallItems.length === 0 ? (
        <Card className="app-card">
          <EmptyPane description="当前没有可部署流水线，请先到管理端创建。" />
        </Card>
      ) : (
        <>
          <div className="pipeline-hall-layout">
            <div className="pipeline-hall-sidebar">
              <Card className="app-card h-fit">
                <div className="space-y-3">
                  <Input
                    value={keyword}
                    placeholder="搜索名称 / 项目 / 分支"
                    onChange={(event) => setKeyword(event.target.value)}
                  />
                  {availableTypeOptions.length > 0 ? (
                    <div className="flex flex-wrap gap-2">
                      {availableTypeOptions.map((option) => {
                        const active = typeFilter === option.value;
                        return (
                          <button
                            key={option.value}
                            type="button"
                            className={`pipeline-hall-shortcut pipeline-hall-type-shortcut${active ? ' pipeline-hall-shortcut--active' : ''}`}
                            onClick={() => setTypeFilter((previous) => (previous === option.value ? undefined : option.value))}
                          >
                            <div className="flex flex-col items-center gap-1">
                              <PipelineIcon type={option.value} />
                              <span className="text-center text-[11px] font-medium text-slate-700">
                                {option.value}
                              </span>
                            </div>
                          </button>
                        );
                      })}
                    </div>
                  ) : null}
                  <Button
                    onClick={() => {
                      setKeyword('');
                      setTypeFilter(undefined);
                      setTagFilter(undefined);
                      setSelectedPipelineId(undefined);
                    }}
                  >
                    重置条件
                  </Button>
                </div>
                {availableTags.length > 0 ? (
                  <div className="mt-4 flex flex-wrap gap-2">
                    {availableTags.map((tag) => {
                      const active = Boolean(tagFilter?.includes(tag));
                      return (
                        <Tag
                          key={tag}
                          style={{
                            backgroundColor: getStableTagColor(tag),
                            color: '#fff',
                            borderColor: 'transparent',
                            opacity: active ? 1 : 0.55,
                          }}
                          className="cursor-pointer select-none !border-0 !px-3 !py-1"
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
              {frequentPipelines.length > 0 ? (
                <Card className="app-card pipeline-hall-recent-card mt-4" title="最近部署">
                  <div className="space-y-2">
                    {frequentPipelines.map((item) => (
                      <button
                        key={item.pipelineId}
                        type="button"
                        className={`pipeline-hall-shortcut${selectedPipelineId === item.pipelineId ? ' pipeline-hall-shortcut--active' : ''}`}
                        onClick={() => setSelectedPipelineId((previous) => (previous === item.pipelineId ? undefined : item.pipelineId))}
                      >
                        <div className="pipeline-hall-shortcut-main">
                          <div className="pipeline-hall-shortcut-title">
                            <PipelineIcon type={item.templateType} />
                            <span className="pipeline-hall-shortcut-name">{item.pipelineName}</span>
                          </div>
                          <span className="pipeline-hall-shortcut-count">近 {item.count} 次</span>
                        </div>
                        <div className="pipeline-hall-shortcut-meta">
                          <span>{item.projectName || '-'}</span>
                          <span>{item.defaultBranch || '-'}</span>
                        </div>
                      </button>
                    ))}
                  </div>
                </Card>
              ) : null}
            </div>
            <div className="pipeline-hall-content">
            {viewMode === 'card' ? (
            <Row gutter={[16, 16]}>
              {filteredPipelineCards.map((item) => {
              const tags = parseTagsJson(item.tagsJson);
              const activeDeployment = item.latestStatus && ACTIVE_DEPLOYMENT_STATUSES.includes(item.latestStatus)
                ? item
                : undefined;
              return (
                <Col xs={24} md={12} xl={8} xxl={6} key={item.pipelineId}>
                  <Card id={`pipeline-card-${item.pipelineId}`} className="pipeline-card" bordered={false}>
                    <div className="pipeline-card-header">
                      <div className="pipeline-card-content">
                        <PipelineIcon type={item.templateType} />
                        <div className="min-w-0 flex-1 pipeline-card-title-block">
                          <Typography.Text className="block text-[11px] uppercase tracking-[0.24em] text-slate-400">
                            {item.projectName || 'Project'}
                          </Typography.Text>
                          <Typography.Title level={4} className="!mb-1 !mt-0 flex rows items-start gap-1">
                          <button
                                                                                      type="button"
                                                                                      className={`mt-0.5 pipeline-hall-favorite-button${item.favorited ? ' pipeline-hall-favorite-button--active' : ''}`}
                                                                                      onClick={() => toggleFavorite(item.pipelineId, item.favorited).catch(() => message.error(item.favorited ? '取消收藏失败' : '收藏失败'))}
                                                                                    >
                                                                                      <StarFilled />
                                                                                    </button>
                            {item.pipelineName}
                          </Typography.Title>
                        </div>
                      </div>
                      <div className="pipeline-card-status">
                        {item.latestDeploymentOrder ? (
                          <div className="mb-1 text-right text-[22px] font-bold leading-none text-sky-600">
                            #{item.latestDeploymentOrder}
                          </div>
                        ) : null}
                        <StatusTag status={item.latestStatus || undefined} />
                      </div>
                    </div>
                    <Typography.Paragraph className="!mb-0 text-slate-600">
                      {item.pipelineDescription || '已配置完成，可直接部署。'}
                    </Typography.Paragraph>
                    {tags.length > 0 ? (
                      <Space className="!mt-3" wrap>
                        {tags.map((tag) => (
                          <Tag
                            key={tag}
                            style={{
                              backgroundColor: getStableTagColor(tag),
                              color: '#fff',
                              borderColor: 'transparent',
                            }}
                            className="!border-0"
                          >
                            {tag}
                          </Tag>
                        ))}
                      </Space>
                    ) : null}
                    <div className="pipeline-meta-panel">
                      <div className="pipeline-meta-row">
                        <span>{item.latestBranchName ? '部署分支' : '默认分支'}</span>
                        <span>{item.latestBranchName || item.defaultBranch}</span>
                      </div>
                      <div className="pipeline-meta-row">
                        <span>部署人</span>
                        <span>{item.latestTriggeredByDisplayName || item.latestTriggeredBy || '-'}</span>
                      </div>
                      <div className="pipeline-meta-row">
                        <span>开始时间</span>
                        <span>{formatDateTime(item.latestStartedAt || item.latestCreatedAt)}</span>
                      </div>
                      <div className="pipeline-meta-row">
                        <span>结束时间</span>
                        <span>{formatDateTime(item.latestFinishedAt)}</span>
                      </div>
                      <div className="pt-2">
                        <Progress
                          percent={item.latestProgressPercent ?? 0}
                          format={() => item.latestProgressText || `${item.latestProgressPercent ?? 0}%`}
                          strokeColor={getDeploymentProgressColor(item.latestStatus)}
                          trailColor="#d9e2f1"
                        />
                      </div>
                    </div>
                    <div className="mt-1 flex items-end justify-between gap-3">
                      <Space>
                        {activeDeployment ? (
                          <Popconfirm
                            title="确认停止部署？"
                            description="会中断当前部署任务，已启动的服务也会尝试停止。"
                            okText="确认停止"
                            cancelText="取消"
                            onConfirm={() => {
                              setSubmittingId(item.pipelineId);
                              return stopDeployment(activeDeployment.latestDeploymentId!)
                                .then(() => message.success('部署已停止'))
                                .catch(() => message.error('停止部署失败'))
                                .finally(() => setSubmittingId(undefined));
                            }}
                          >
                            <Button danger loading={submittingId === item.pipelineId}>停止</Button>
                          </Popconfirm>
                        ) : (
                          <Button
                            type="primary"
                            loading={submittingId === item.pipelineId}
                            onClick={() => openDeployModal({
                              id: item.pipelineId,
                              name: item.pipelineName,
                              description: item.pipelineDescription || undefined,
                              defaultBranch: item.defaultBranch || undefined,
                              tagsJson: item.tagsJson || undefined,
                              project: item.projectName ? { id: 0, name: item.projectName } : undefined,
                              template: item.templateType ? { id: 0, name: item.templateType, templateType: item.templateType } : undefined,
                            } as PipelineSummary).catch(() => message.error('打开部署窗口失败'))}
                          >
                            部署
                          </Button>
                        )}
                        <Button
                          disabled={!item.latestDeploymentId}
                          onClick={() => item.latestDeploymentId && navigate(`/user/deployments/${item.latestDeploymentId}`, {
                            state: { from: '/user/pipelines', backLabel: '返回流水线大厅' },
                          })}
                        >
                          查看
                        </Button>
                        <Button onClick={() => navigate(`/user/pipelines/${item.pipelineId}/history`)}>
                          记录
                        </Button>
                      </Space>
                      <div className="shrink-0 text-xs text-slate-500">
                        耗时 {formatDeploymentElapsed({
                          startedAt: item.latestStartedAt || undefined,
                          createdAt: item.latestCreatedAt || undefined,
                          finishedAt: item.latestFinishedAt || undefined,
                          status: item.latestStatus || undefined,
                        }, tick)}
                      </div>
                    </div>
                  </Card>
                </Col>
              );
              })}
            </Row>
            ) : (
              <Card className="app-card">
                <Table
                  rowKey="pipelineId"
                  loading={loading}
                  scroll={{ x: 1180 }}
                  dataSource={filteredPipelineCards}
                  locale={{ emptyText: <EmptyPane description="当前筛选条件下没有可部署流水线。" /> }}
                  pagination={{
                    pageSize: 10,
                    showSizeChanger: true,
                    showTotal: (total) => `共 ${total} 条`,
                  }}
                  columns={[
                    {
                      title: '名称',
                      width: 260,
                      render: (_, row) => (
                        <div className="flex items-center gap-2">
                          <button
                            type="button"
                            className={`pipeline-hall-favorite-button shrink-0${row.favorited ? ' pipeline-hall-favorite-button--active' : ''}`}
                            onClick={() => toggleFavorite(row.pipelineId, row.favorited).catch(() => message.error(row.favorited ? '取消收藏失败' : '收藏失败'))}
                          >
                            <StarFilled />
                          </button>
                          <PipelineIcon type={row.templateType} />
                          <div className="min-w-0">
                            <div className="truncate font-medium text-slate-900" title={row.pipelineName}>{row.pipelineName}</div>
                            <div className="truncate text-xs text-slate-500" title={row.projectName || ''}>{row.projectName || '-'}</div>
                          </div>
                        </div>
                      ),
                    },
                    {
                      title: '描述',
                      width: 260,
                      render: (_, row) => row.pipelineDescription ? (
                        <div className="line-clamp-2 text-sm leading-6 text-slate-600" title={row.pipelineDescription}>
                          {row.pipelineDescription}
                        </div>
                      ) : '-',
                    },
                    {
                      title: '标签',
                      width: 220,
                      render: (_, row) => {
                        const tags = parseTagsJson(row.tagsJson);
                        return tags.length > 0 ? (
                          <Space wrap>
                            {tags.map((tag) => (
                              <Tag
                                key={tag}
                                style={{
                                  backgroundColor: getStableTagColor(tag),
                                  color: '#fff',
                                  borderColor: 'transparent',
                                }}
                                className="!border-0"
                              >
                                {tag}
                              </Tag>
                            ))}
                          </Space>
                        ) : '-';
                      },
                    },
                    {
                      title: '分支',
                      width: 150,
                      render: (_, row) => row.latestBranchName || row.defaultBranch || '-',
                    },
                    {
                      title: '最近状态',
                      width: 140,
                      render: (_, row) => <StatusTag status={row.latestStatus || undefined} />,
                    },
                    {
                      title: '最近耗时',
                      width: 140,
                      render: (_, row) => formatDeploymentElapsed({
                        startedAt: row.latestStartedAt || undefined,
                        createdAt: row.latestCreatedAt || undefined,
                        finishedAt: row.latestFinishedAt || undefined,
                        status: row.latestStatus || undefined,
                      }, tick),
                    },
                    {
                      title: '操作',
                      width: 260,
                      render: (_, row) => {
                        const activeDeployment = row.latestStatus && ACTIVE_DEPLOYMENT_STATUSES.includes(row.latestStatus)
                          ? row
                          : undefined;
                        return (
                          <Space>
                            {activeDeployment ? (
                              <Popconfirm
                                title="确认停止部署？"
                                description="会中断当前部署任务，已启动的服务也会尝试停止。"
                                okText="确认停止"
                                cancelText="取消"
                                onConfirm={() => {
                                  setSubmittingId(row.pipelineId);
                                  return stopDeployment(activeDeployment.latestDeploymentId!)
                                    .then(() => message.success('部署已停止'))
                                    .catch(() => message.error('停止部署失败'))
                                    .finally(() => setSubmittingId(undefined));
                                }}
                              >
                                <Button size="small" danger loading={submittingId === row.pipelineId}>停止</Button>
                              </Popconfirm>
                            ) : (
                              <Button
                                size="small"
                                type="primary"
                                loading={submittingId === row.pipelineId}
                                onClick={() => openDeployModal({
                                  id: row.pipelineId,
                                  name: row.pipelineName,
                                  description: row.pipelineDescription || undefined,
                                  defaultBranch: row.defaultBranch || undefined,
                                  tagsJson: row.tagsJson || undefined,
                                  project: row.projectName ? { id: 0, name: row.projectName } : undefined,
                                  template: row.templateType ? { id: 0, name: row.templateType, templateType: row.templateType } : undefined,
                                } as PipelineSummary).catch(() => message.error('打开部署窗口失败'))}
                              >
                                部署
                              </Button>
                            )}
                            <Button
                              size="small"
                              disabled={!row.latestDeploymentId}
                              onClick={() => row.latestDeploymentId && navigate(`/user/deployments/${row.latestDeploymentId}`, {
                                state: { from: '/user/pipelines', backLabel: '返回流水线大厅' },
                              })}
                            >
                              查看
                            </Button>
                            <Button size="small" onClick={() => navigate(`/user/pipelines/${row.pipelineId}/history`)}>
                              记录
                            </Button>
                          </Space>
                        );
                      },
                    },
                  ]}
                />
              </Card>
            )}
            </div>
          </div>
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
        onOk={() => createDeployment().catch(() => undefined)}
        destroyOnClose
      >
          <div className="space-y-4">
          {deployingPipeline && hallItems.some((item) => item.pipelineId === deployingPipeline.id && item.latestStatus && ACTIVE_DEPLOYMENT_STATUSES.includes(item.latestStatus)) ? (
            <div className="rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-700">
              检测到当前流水线还有部署中的任务。继续部署会先停止前一个正在运行的任务。
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
