import { EllipsisOutlined, StarFilled } from '@ant-design/icons';
import { ClockCounterClockwise, GridFour, Heart, ListBullets, PlayCircle, SquaresFour, WarningCircle } from '@phosphor-icons/react';
import type { CSSProperties } from 'react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Button, Card, Dropdown, Input, Modal, Popconfirm, Progress, Segmented, Select, Space, Table, Tag, Typography, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import { deploymentsApi } from '../../api/deployments';
import { pipelinesApi } from '../../api/pipelines';
import EmptyPane from '../../components/EmptyPane';
import PageHeaderBar from '../../components/PageHeaderBar';
import PipelineIcon from '../../components/PipelineIcon';
import StatusTag from '../../components/StatusTag';
import { ACTIVE_DEPLOYMENT_STATUSES } from '../../constants/deployment';
import { usePipelineHallPreferences, type PipelineHallFilterMode } from '../../hooks/usePipelineHallPreferences';
import type { PipelineHallRunningServiceSummary, PipelineHallSummary, PipelineSummary, UserRecentPipelineSummary } from '../../types/domain';
import { formatDateTime } from '../../utils/datetime';
import { formatDeploymentElapsed } from '../../utils/deploymentDuration';
import { getDeploymentProgressColor, getDeploymentProgressLabel } from '../../utils/deploymentProgress';
import { formatDurationSince } from '../../utils/duration';
import { getStableTagColor, getStableTagDarkColor, sortTagNames } from '../../utils/tagColors';

type HallView = PipelineHallFilterMode;

const stableTagStyle = (tag: string): CSSProperties => ({
  '--app-tag-bg': getStableTagColor(tag),
  '--app-tag-bg-dark': getStableTagDarkColor(tag),
  '--app-tag-fg': '#ffffff',
} as CSSProperties);

function splitIntoColumns<T>(items: T[], columnCount: number): T[][] {
  const count = Math.max(1, columnCount);
  return items.reduce<T[][]>((columns, item, index) => {
    columns[index % count].push(item);
    return columns;
  }, Array.from({ length: count }, () => []));
}

function PipelineFilterSkeleton() {
  return (
    <div className="pipeline-filter-skeleton">
      <div className="pipeline-skeleton-line pipeline-skeleton-line--title" />
      <div className="pipeline-skeleton-block pipeline-skeleton-block--input" />
      <div className="pipeline-skeleton-block pipeline-skeleton-block--panel">
        <span />
        <span />
      </div>
      <div className="pipeline-skeleton-tag-grid">
        {Array.from({ length: 8 }).map((_, index) => <span key={index} />)}
      </div>
    </div>
  );
}

function RunningServicesSkeleton() {
  return (
    <div className="pipeline-hall-running-strip mb-4">
      {Array.from({ length: 4 }).map((_, index) => (
        <div key={index} className="pipeline-running-skeleton">
          <div className="pipeline-skeleton-icon" />
          <div className="min-w-0 flex-1 space-y-2">
            <div className="pipeline-skeleton-line pipeline-skeleton-line--medium" />
            <div className="pipeline-skeleton-line pipeline-skeleton-line--short" />
            <div className="pipeline-skeleton-line pipeline-skeleton-line--tiny" />
          </div>
        </div>
      ))}
    </div>
  );
}

function PipelineCardSkeleton() {
  return (
    <Card className="pipeline-card pipeline-card-skeleton" bordered={false}>
      <div className="pipeline-card-header">
        <div className="pipeline-card-content">
          <div className="pipeline-skeleton-icon" />
          <div className="min-w-0 flex-1">
            <div className="pipeline-skeleton-line pipeline-skeleton-line--eyebrow" />
            <div className="mt-3 flex items-center gap-2">
              <div className="pipeline-skeleton-dot" />
              <div className="pipeline-skeleton-line pipeline-skeleton-line--name" />
            </div>
          </div>
        </div>
        <div className="pipeline-skeleton-pill" />
      </div>
      <div className="pipeline-skeleton-paragraph">
        <span />
        <span />
      </div>
      <div className="pipeline-skeleton-tag-grid pipeline-skeleton-tag-grid--compact">
        {Array.from({ length: 3 }).map((_, index) => <span key={index} />)}
      </div>
      <div className="pipeline-card-skeleton__meta">
        {Array.from({ length: 4 }).map((_, index) => (
          <div key={index}>
            <span />
            <strong />
          </div>
        ))}
        <div className="pipeline-card-skeleton__progress" />
      </div>
      <div className="pipeline-card-skeleton__actions">
        <span />
        <span />
        <span />
      </div>
    </Card>
  );
}

function PipelineTableSkeleton() {
  return (
    <Card className="app-card">
      <div className="pipeline-table-skeleton">
        {Array.from({ length: 7 }).map((_, index) => (
          <div key={index} className="pipeline-table-skeleton__row">
            <div className="pipeline-skeleton-icon" />
            <div className="pipeline-table-skeleton__main">
              <span />
              <strong />
            </div>
            <div className="pipeline-table-skeleton__tags">
              <span />
              <span />
            </div>
            <div className="pipeline-table-skeleton__actions">
              <span />
              <span />
              <span />
            </div>
          </div>
        ))}
      </div>
    </Card>
  );
}

/**
 * 用户端流水线大厅。
 * 用户在这里只做三件事：选流水线、发部署、看记录。
 */
type UserPipelinesPageProps = {
  basePath?: string;
  deploymentDetailBasePath?: string;
  title?: string;
  description?: string;
};

export default function UserPipelinesPage({
  basePath = '/user/pipelines',
  deploymentDetailBasePath = '/user/deployments',
  title = '流水线大厅',
  description = '查看可用流水线，选择分支并发起部署。',
}: UserPipelinesPageProps) {
  const IDLE_POLL_INTERVAL = 15000;
  const ACTIVE_POLL_INTERVAL = 3000;
  const {
    viewMode,
    setViewMode,
    filterMode: hallView,
    setFilterMode: setHallView,
    autoOpenDeploymentDetail,
    pinActivePipelines,
    stopConfirmationEnabled,
    showRunningServices,
    hideStoppedServices,
  } = usePipelineHallPreferences();
  const [hallItems, setHallItems] = useState<PipelineHallSummary[]>([]);
  const [recentPipelines, setRecentPipelines] = useState<UserRecentPipelineSummary[]>([]);
  const [runningServices, setRunningServices] = useState<PipelineHallRunningServiceSummary[]>([]);
  const [hallLoading, setHallLoading] = useState(false);
  const [recentLoading, setRecentLoading] = useState(false);
  const [runningLoading, setRunningLoading] = useState(false);
  const [submittingId, setSubmittingId] = useState<number>();
  const [deployModalOpen, setDeployModalOpen] = useState(false);
  const [deployingPipeline, setDeployingPipeline] = useState<PipelineSummary>();
  const [branchOptions, setBranchOptions] = useState<string[]>([]);
  const [selectedBranch, setSelectedBranch] = useState<string>();
  const [branchesLoading, setBranchesLoading] = useState(false);
  const [tick, setTick] = useState(() => Date.now());
  const [keyword, setKeyword] = useState('');
  const [tagFilter, setTagFilter] = useState<string[]>();
  const [selectedPipelineId, setSelectedPipelineId] = useState<number>();
  const [cardColumnLimit, setCardColumnLimit] = useState(1);
  const navigate = useNavigate();
  const hallContentBodyRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const target = hallContentBodyRef.current;
    if (!target) {
      return undefined;
    }
    const updateColumnLimit = () => {
      const width = target.clientWidth;
      const next = Math.max(1, Math.min(4, Math.floor((width + 16) / 316)));
      setCardColumnLimit(next);
    };
    updateColumnLimit();
    const observer = new ResizeObserver(updateColumnLimit);
    observer.observe(target);
    return () => observer.disconnect();
  }, []);

  const normalizeTags = (content: unknown): string[] => {
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

  /** 首次加载或慢轮询时同步刷新大厅卡片和最近部署统计。 */
  const loadData = async (silent = false) => {
    if (!silent) {
      setHallLoading(true);
      setRecentLoading(true);
      if (showRunningServices) {
        setRunningLoading(true);
      }
    }
    const tasks: Promise<unknown>[] = [
      pipelinesApi.listHall()
        .then(setHallItems)
        .finally(() => {
          if (!silent) {
            setHallLoading(false);
          }
        }),
      deploymentsApi.listMineRecentPipelines()
        .then(setRecentPipelines)
        .finally(() => {
          if (!silent) {
            setRecentLoading(false);
          }
        }),
    ];
    if (showRunningServices) {
      tasks.push(
        pipelinesApi.listRunningServices()
          .then(setRunningServices)
          .finally(() => {
            if (!silent) {
              setRunningLoading(false);
            }
          }),
      );
    } else if (!silent) {
      setRunningLoading(false);
    }
    await Promise.allSettled(tasks);
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

  const refreshRunningServices = async () => {
    setRunningServices(await pipelinesApi.listRunningServices());
  };

  const hasLiveRunningServices = runningServices.some((item) => item.status === 'RUNNING');

  useEffect(() => {
    loadData().catch(() => message.error('加载流水线失败'));
  }, [showRunningServices]);

  useEffect(() => {
    if (hallLoading && hallItems.length === 0 && recentLoading && runningLoading) {
      return undefined;
    }
    const hasActiveDeployment = hallItems.some((item) => item.latestStatus && ACTIVE_DEPLOYMENT_STATUSES.includes(item.latestStatus));
    const hasRunningServices = showRunningServices && hasLiveRunningServices;
    const interval = window.setInterval(() => {
      if (hasActiveDeployment) {
        refreshActiveHallItems().catch(() => {});
        if (hasRunningServices) {
          refreshRunningServices().catch(() => {});
        }
        return;
      }
      if (hasRunningServices) {
        refreshRunningServices().catch(() => {});
        return;
      }
      loadData(true).catch(() => {});
    }, hasActiveDeployment || hasRunningServices ? ACTIVE_POLL_INTERVAL : IDLE_POLL_INTERVAL);
    return () => window.clearInterval(interval);
  }, [hallItems, hallLoading, recentLoading, runningLoading, hasLiveRunningServices, showRunningServices]);

  useEffect(() => {
    const timer = window.setInterval(() => setTick(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  const frequentPipelines = useMemo(() => recentPipelines.slice(0, 8), [recentPipelines]);
  const recentPipelineIdSet = useMemo(
    () => new Set(frequentPipelines.map((item) => item.pipelineId)),
    [frequentPipelines],
  );
  const hallViewStats = useMemo(() => ({
    all: hallItems.length,
    favorites: hallItems.filter((item) => item.favorited).length,
    running: hallItems.filter((item) => item.latestStatus && ACTIVE_DEPLOYMENT_STATUSES.includes(item.latestStatus)).length,
    failed: hallItems.filter((item) => item.latestStatus === 'FAILED').length,
    recent: hallItems.filter((item) => recentPipelineIdSet.has(item.pipelineId)).length,
  }), [hallItems, recentPipelineIdSet]);

  const baseFilteredPipelineCards = useMemo(() => {
    return hallItems.filter((item) => {
      if (hallView === 'favorites' && !item.favorited) {
        return false;
      }
      if (hallView === 'running' && !(item.latestStatus && ACTIVE_DEPLOYMENT_STATUSES.includes(item.latestStatus))) {
        return false;
      }
      if (hallView === 'failed' && item.latestStatus !== 'FAILED') {
        return false;
      }
      if (hallView === 'recent' && !recentPipelineIdSet.has(item.pipelineId)) {
        return false;
      }
      if (selectedPipelineId && item.pipelineId !== selectedPipelineId) {
        return false;
      }
      const tags = normalizeTags(item.tags);
      const normalizedKeyword = keyword.trim().toLowerCase();
      if (normalizedKeyword) {
        const matched = [item.pipelineName, item.pipelineDescription, item.projectName, item.defaultBranch, ...tags]
          .filter(Boolean)
          .some((value) => String(value).toLowerCase().includes(normalizedKeyword));
        if (!matched) {
          return false;
        }
      }
      return true;
    });
  }, [hallItems, hallView, keyword, recentPipelineIdSet, selectedPipelineId]);

  const filteredPipelineCards = useMemo(() => {
    const filteredItems = baseFilteredPipelineCards.filter((item) => {
      const tags = normalizeTags(item.tags);
      if (tagFilter && tagFilter.length > 0 && !tagFilter.every((tag) => tags.includes(tag))) {
        return false;
      }
      return true;
    });
    if (!pinActivePipelines) {
      return filteredItems;
    }
    return [...filteredItems].sort((left, right) => {
      const leftActive = Boolean(left.latestStatus && ACTIVE_DEPLOYMENT_STATUSES.includes(left.latestStatus));
      const rightActive = Boolean(right.latestStatus && ACTIVE_DEPLOYMENT_STATUSES.includes(right.latestStatus));
      if (leftActive !== rightActive) {
        return leftActive ? -1 : 1;
      }
      return 0;
    });
  }, [baseFilteredPipelineCards, pinActivePipelines, tagFilter]);

  const skeletonPipelineCards = useMemo(() => Array.from({ length: 8 }, (_, index) => index), []);
  const skeletonCardColumns = useMemo(
    () => splitIntoColumns(skeletonPipelineCards, cardColumnLimit),
    [cardColumnLimit, skeletonPipelineCards],
  );
  const pipelineCardColumns = useMemo(
    () => splitIntoColumns(filteredPipelineCards, Math.min(cardColumnLimit, filteredPipelineCards.length || 1)),
    [cardColumnLimit, filteredPipelineCards],
  );
  const skeletonCardColumnStyle = useMemo(
    () => ({ '--pipeline-card-columns': skeletonCardColumns.length }) as CSSProperties,
    [skeletonCardColumns.length],
  );
  const pipelineCardColumnStyle = useMemo(
    () => ({ '--pipeline-card-columns': pipelineCardColumns.length }) as CSSProperties,
    [pipelineCardColumns.length],
  );
  const pipelineCardMasonryClassName = `pipeline-card-masonry${filteredPipelineCards.length < cardColumnLimit ? ' pipeline-card-masonry--compact' : ''}`;

  const availableTags = useMemo(
    () => sortTagNames(Array.from(new Set(baseFilteredPipelineCards.flatMap((item) => normalizeTags(item.tags))))),
    [baseFilteredPipelineCards],
  );

  const visibleRunningServices = useMemo(
    () => runningServices.filter((item) => {
      if (!showRunningServices) {
        return false;
      }
      if (hideStoppedServices && item.status === 'STOPPED') {
        return false;
      }
      if (hallView === 'favorites') {
        if (!item.pipelineId) {
          return false;
        }
        return hallItems.some((pipeline) => pipeline.pipelineId === item.pipelineId && pipeline.favorited);
      }
      return true;
    }),
    [hallView, hallItems, hideStoppedServices, runningServices, showRunningServices],
  );

  const disabledTagSet = useMemo(() => {
    const next = new Set<string>();
    availableTags.forEach((tag) => {
      const enabled = baseFilteredPipelineCards.some((item) => {
        const tags = normalizeTags(item.tags);
        const nextFilter = tagFilter?.includes(tag)
          ? tagFilter.filter((itemTag) => itemTag !== tag)
          : [...(tagFilter || []), tag];
        return nextFilter.length === 0 || nextFilter.every((itemTag) => tags.includes(itemTag));
      });
      if (!enabled) {
        next.add(tag);
      }
    });
    return next;
  }, [availableTags, baseFilteredPipelineCards, tagFilter]);

  const initialHallLoading = hallLoading && hallItems.length === 0;
  const initialRecentLoading = recentLoading && recentPipelines.length === 0;
  const initialRunningLoading = runningLoading && runningServices.length === 0;
  const showHallEmpty = !initialHallLoading && hallItems.length === 0;
  const loading = initialHallLoading || initialRecentLoading || initialRunningLoading;
  const hasActiveFilters = Boolean(keyword.trim() || selectedPipelineId || (tagFilter && tagFilter.length > 0));
  const hallViewLabelMap: Record<HallView, string> = {
    all: '全部流水线',
    favorites: '收藏流水线',
    running: '运行中流水线',
    failed: '最近失败',
    recent: '最近部署',
  };

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
      message.success('部署已触发');
      if (autoOpenDeploymentDetail) {
        navigate(`${deploymentDetailBasePath}/${deployment.id}`, {
          state: { from: basePath, backLabel: '返回流水线大厅' },
        });
      }
    } finally {
      setSubmittingId(undefined);
    }
  };

  const stopDeployment = async (deploymentId: number) => {
    await deploymentsApi.stop(deploymentId);
    await loadData();
  };

  const handleStopDeployment = async (pipelineId: number, deploymentId: number) => {
    setSubmittingId(pipelineId);
    try {
      await stopDeployment(deploymentId);
      message.success('部署已停止');
    } catch {
      message.error('停止部署失败');
    } finally {
      setSubmittingId(undefined);
    }
  };

  const renderStopButton = (pipelineId: number, deploymentId: number, size?: 'small' | 'middle' | 'large') => {
    const button = (
      <Button size={size} danger loading={submittingId === pipelineId} onClick={() => !stopConfirmationEnabled && handleStopDeployment(pipelineId, deploymentId)}>
        停止
      </Button>
    );
    if (!stopConfirmationEnabled) {
      return button;
    }
    return (
      <Popconfirm
        title="确认停止部署？"
        description="会中断当前部署任务，已启动的服务也会尝试停止。"
        okText="确认停止"
        cancelText="取消"
        onConfirm={() => handleStopDeployment(pipelineId, deploymentId)}
      >
        {button}
      </Popconfirm>
    );
  };

  return (
    <div className="pipeline-hall-page">
      <PageHeaderBar
        title={title}
        description={description}
        extra={(
          <Space className="pipeline-hall-header-actions" wrap>
            <Button onClick={() => loadData().catch(() => message.error('加载流水线失败'))}>刷新</Button>
            <Segmented
              className="pipeline-hall-header-switch pipeline-hall-view-switch"
              value={hallView}
              onChange={(value) => {
                setHallView(value as HallView);
                setSelectedPipelineId(undefined);
              }}
              options={[
                { value: 'all', icon: <SquaresFour weight="fill" />, label: `全部 ${hallViewStats.all}` },
                { value: 'favorites', icon: <Heart weight="fill" />, label: `收藏 ${hallViewStats.favorites}` },
                { value: 'running', icon: <PlayCircle weight="fill" />, label: `运行中 ${hallViewStats.running}` },
                { value: 'failed', icon: <WarningCircle weight="fill" />, label: `失败 ${hallViewStats.failed}` },
                { value: 'recent', icon: <ClockCounterClockwise weight="fill" />, label: `最近 ${hallViewStats.recent}` },
              ]}
            />
            <Segmented
              className="pipeline-hall-header-switch pipeline-hall-view-switch"
              value={viewMode}
              onChange={(value) => setViewMode(value as 'card' | 'table')}
              options={[
                { value: 'card', icon: <GridFour weight="fill" />, label: '卡片' },
                { value: 'table', icon: <ListBullets weight="fill" />, label: '表格' },
              ]}
            />
          </Space>
        )}
      />
      <div className="pipeline-hall-top-filter">
        {initialHallLoading ? (
          <PipelineFilterSkeleton />
        ) : (
          <>
            <div className="pipeline-hall-top-filter-search">
              <Input
                value={keyword}
                placeholder="搜索名称 / 项目 / 分支"
                onChange={(event) => setKeyword(event.target.value)}
                allowClear
              />
              <Button
                size="small"
                disabled={!hasActiveFilters}
                onClick={() => {
                  setKeyword('');
                  setTagFilter(undefined);
                  setSelectedPipelineId(undefined);
                }}
              >
                清空
              </Button>
            </div>
            <div className="pipeline-hall-top-filter-tags">
              {availableTags.length > 0 ? (
                availableTags.map((tag) => {
                  const active = Boolean(tagFilter?.includes(tag));
                  const disabled = !active && disabledTagSet.has(tag);
                  return (
                    <Tag
                      key={tag}
                      style={{
                        ...(active ? stableTagStyle(tag) : {}),
                        opacity: disabled ? 0.36 : 1,
                      }}
                      className={`${active ? 'app-color-tag' : 'app-muted-tag'} ${disabled ? 'cursor-not-allowed' : 'cursor-pointer'} select-none !border-0 !px-3 !py-1`}
                      onClick={() => !disabled && setTagFilter((previous) => {
                        const next = previous?.includes(tag)
                          ? previous.filter((item) => item !== tag)
                          : [...(previous || []), tag];
                        return next.length > 0 ? next : undefined;
                      })}
                    >
                      {tag}
                    </Tag>
                  );
                })
              ) : (
                <span className="pipeline-hall-top-filter-empty">暂无可筛选标签</span>
              )}
            </div>
          </>
        )}
      </div>
      <div className="pipeline-hall-layout">
            <div className="pipeline-hall-content">
              {showRunningServices && initialRunningLoading ? (
                <RunningServicesSkeleton />
              ) : showRunningServices && visibleRunningServices.length > 0 ? (
                <div className="pipeline-hall-running-strip mb-4">
                  {visibleRunningServices.map((item) => (
                    <button
                      key={item.serviceId}
                      type="button"
                      className={`pipeline-hall-running-item${selectedPipelineId === item.pipelineId ? ' pipeline-hall-running-item--active' : ''}`}
                      onClick={() => item.pipelineId && setSelectedPipelineId((previous) => (previous === item.pipelineId ? undefined : item.pipelineId))}
                    >
                      <div className="pipeline-hall-running-item-title">
                        <PipelineIcon type={item.templateType} />
                        <div className="pipeline-hall-running-item-content">
                          <div className="pipeline-hall-running-item-name">{item.serviceName || item.pipelineName || `服务 #${item.serviceId}`}</div>
                          <div className="pipeline-hall-running-item-meta">{item.targetHostName || '本机'}{item.currentPid ? ` [${item.currentPid}]` : ''}</div>
                          <div className={`pipeline-hall-running-item-duration${item.status === 'STOPPED' ? ' pipeline-hall-running-item-duration--stopped' : ''}`}>
                            {item.status === 'STOPPED' ? '已停止' : `运行中(${formatDurationSince(item.activeSince)})`}
                          </div>
                        </div>
                      </div>
                    </button>
                  ))}
                </div>
              ) : null}
            <div className="pipeline-hall-content-body" ref={hallContentBodyRef}>
              {initialHallLoading ? (
                viewMode === 'card' ? (
                  <div className="pipeline-card-masonry" style={skeletonCardColumnStyle}>
                    {skeletonCardColumns.map((column, columnIndex) => (
                      <div className="pipeline-card-masonry-column" key={columnIndex}>
                        {column.map((index) => (
                          <PipelineCardSkeleton key={index} />
                        ))}
                      </div>
                    ))}
                  </div>
                ) : (
                  <PipelineTableSkeleton />
                )
              ) : showHallEmpty ? (
                <Card className="app-card">
                  <EmptyPane description="当前没有可部署流水线，请先到管理端创建。" />
                </Card>
              ) : viewMode === 'card' ? (
                filteredPipelineCards.length === 0 ? (
                  <Card className="app-card">
                    <EmptyPane description="当前筛选条件下没有可部署流水线。" />
                  </Card>
                ) : (
                  <div className={pipelineCardMasonryClassName} style={pipelineCardColumnStyle}>
                    {pipelineCardColumns.map((column, columnIndex) => (
                      <div className="pipeline-card-masonry-column" key={`${hallView}-${tagFilter?.join('|') || 'all'}-${columnIndex}`}>
                        {column.map((item) => {
                          const tags = sortTagNames(normalizeTags(item.tags));
                          const activeDeployment = item.latestStatus && ACTIVE_DEPLOYMENT_STATUSES.includes(item.latestStatus)
                            ? item
                            : undefined;
                          const latestDeploymentMenuItems = [
                            ...(item.latestDeploymentId ? [{
                              key: 'detail',
                              label: item.latestStatus === 'FAILED' ? '查看日志' : '查看详情',
                            }] : []),
                            {
                              key: 'history',
                              label: '部署记录',
                            },
                          ];
                          return (
                            <Card
                              id={`pipeline-card-${item.pipelineId}`}
                              className={`pipeline-card${item.latestStatus === 'FAILED' ? ' pipeline-card--failed' : ''}`}
                              bordered={false}
                              key={item.pipelineId}
                            >
                            <div className="pipeline-card-header">
                              <div className="pipeline-card-headline">
                                <div className="pipeline-card-content">
                                  <PipelineIcon type={item.templateType} />
                                  <div className="min-w-0 flex-1 pipeline-card-title-block">
                                    <Typography.Text className="block text-[11px] uppercase tracking-[0.24em] text-slate-400">
                                      {item.projectName || 'Project'}
                                    </Typography.Text>
                                    <div className="pipeline-card-title-meta-row">
                                      <button
                                        type="button"
                                        className={`pipeline-hall-favorite-button${item.favorited ? ' pipeline-hall-favorite-button--active' : ''}`}
                                        onClick={() => toggleFavorite(item.pipelineId, item.favorited).catch(() => message.error(item.favorited ? '取消收藏失败' : '收藏失败'))}
                                      >
                                        <StarFilled />
                                      </button>
                                      {item.latestDeploymentOrder ? (
                                        <span className="pipeline-card-order">
                                          #{item.latestDeploymentOrder}
                                        </span>
                                      ) : null}
                                    </div>
                                  </div>
                                </div>
                              </div>
                              <div className="pipeline-card-status">
                                <StatusTag status={item.latestStatus || undefined} progress={item.latestProgressPercent} />
                              </div>
                            </div>
                            <Typography.Title level={4} className="pipeline-card-title !m-0">
                              {item.pipelineName}
                            </Typography.Title>
                            <div className="pipeline-card-middle">
                              <Typography.Paragraph className="!mb-0 text-slate-600 dark:!text-slate-300">
                                {item.pipelineDescription || '已配置完成，可直接部署。'}
                              </Typography.Paragraph>
                              {tags.length > 0 ? (
                                <Space className="!mt-3" wrap>
                                  {tags.map((tag) => (
                                    <Tag
                                      key={tag}
                                      style={stableTagStyle(tag)}
                                      className="app-color-tag !border-0"
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
                                <div className="deployment-progress-row pt-2">
                                  <Progress
                                    className="deployment-progress"
                                    percent={item.latestProgressPercent ?? 0}
                                    strokeWidth={10}
                                    showInfo={false}
                                    status={item.latestStatus === 'RUNNING' ? 'active' : undefined}
                                    strokeColor={getDeploymentProgressColor(item.latestStatus)}
                                    trailColor="#d9e2f1"
                                  />
                                  <div className="deployment-progress-text">
                                    {getDeploymentProgressLabel(item.latestProgressPercent ?? 0, item.latestStatus, item.latestProgressText)}
                                  </div>
                                </div>
                              </div>
                            </div>
                            <div className="pipeline-card-actions-row">
                              <Space size={8} className="pipeline-card-primary-actions">
                                {activeDeployment ? (
                                  renderStopButton(item.pipelineId, activeDeployment.latestDeploymentId!)
                                ) : (
                                  <Button
                                    type="primary"
                                    loading={submittingId === item.pipelineId}
                                    onClick={() => openDeployModal({
                                      id: item.pipelineId,
                                      name: item.pipelineName,
                                      description: item.pipelineDescription || undefined,
                                      defaultBranch: item.defaultBranch || undefined,
                                      tags: item.tags || undefined,
                                      project: item.projectName ? { id: 0, name: item.projectName } : undefined,
                                      template: item.templateType ? { id: 0, name: item.templateType, templateType: item.templateType } : undefined,
                                    } as PipelineSummary).catch(() => message.error('打开部署窗口失败'))}
                                  >
                                    部署
                                  </Button>
                                )}
                                <Button
                                  className="pipeline-card-inline-action"
                                  disabled={!item.latestDeploymentId}
                                  onClick={() => item.latestDeploymentId && navigate(`${deploymentDetailBasePath}/${item.latestDeploymentId}`, {
                                    state: { from: basePath, backLabel: '返回流水线大厅' },
                                  })}
                                >
                                  {item.latestStatus === 'FAILED' ? '查看日志' : '查看'}
                                </Button>
                                <Button className="pipeline-card-inline-action" onClick={() => navigate(`${basePath}/${item.pipelineId}/history`)}>
                                  记录
                                </Button>
                                <Dropdown
                                  menu={{
                                    items: latestDeploymentMenuItems,
                                    onClick: ({ key }) => {
                                      if (key === 'detail' && item.latestDeploymentId) {
                                        navigate(`${deploymentDetailBasePath}/${item.latestDeploymentId}`, {
                                          state: { from: basePath, backLabel: '返回流水线大厅' },
                                        });
                                      }
                                      if (key === 'history') {
                                        navigate(`${basePath}/${item.pipelineId}/history`);
                                      }
                                    },
                                  }}
                                  trigger={['click']}
                                >
                                  <Button className="pipeline-card-more-action" icon={<EllipsisOutlined />}>更多</Button>
                                </Dropdown>
                              </Space>
                              <div className="pipeline-card-duration">
                                耗时 {formatDeploymentElapsed({
                                  startedAt: item.latestStartedAt || undefined,
                                  createdAt: item.latestCreatedAt || undefined,
                                  finishedAt: item.latestFinishedAt || undefined,
                                  status: item.latestStatus || undefined,
                                }, tick)}
                              </div>
                            </div>
                          </Card>
                          );
                        })}
                      </div>
                    ))}
                  </div>
                )
              ) : (
                <Card className="app-card">
                  <Table
                    rowKey="pipelineId"
                    loading={hallLoading && filteredPipelineCards.length > 0}
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
                            <div className="flex items-center gap-1">
                              <div className="truncate font-medium text-slate-900" title={row.pipelineName}>{row.pipelineName}</div>
                              {row.latestDeploymentOrder ? (
                                <div className="shrink-0 text-xs font-semibold text-sky-600">
                                  #{row.latestDeploymentOrder}
                                </div>
                              ) : null}
                            </div>
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
                        const tags = sortTagNames(normalizeTags(row.tags));
                        return tags.length > 0 ? (
                          <Space wrap>
                            {tags.map((tag) => (
                              <Tag
                                key={tag}
                                style={stableTagStyle(tag)}
                                className="app-color-tag !border-0"
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
                      width: 190,
                      render: (_, row) => {
                        const showProgressText = row.latestStatus === 'RUNNING' && row.latestProgressText;
                        const progressText = getDeploymentProgressLabel(row.latestProgressPercent ?? 0, row.latestStatus, row.latestProgressText);
                        return (
                          <div className="space-y-1">
                            <div>
                              <StatusTag status={row.latestStatus || undefined} progress={row.latestProgressPercent} />
                            </div>
                            {showProgressText ? (
                              <div className="text-[11px] leading-4 text-slate-500">
                                {progressText}
                              </div>
                            ) : null}
                          </div>
                        );
                      },
                    },
                    {
                      title: '部署人',
                      width: 140,
                      render: (_, row) => row.latestTriggeredByDisplayName || row.latestTriggeredBy || '-',
                    },
                    {
                      title: '开始时间',
                      width: 176,
                      render: (_, row) => formatDateTime(row.latestStartedAt || row.latestCreatedAt),
                    },
                    {
                      title: '结束时间',
                      width: 176,
                      render: (_, row) => formatDateTime(row.latestFinishedAt),
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
                              renderStopButton(row.pipelineId, activeDeployment.latestDeploymentId!, 'small')
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
                                  tags: row.tags || undefined,
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
                              onClick={() => row.latestDeploymentId && navigate(`${deploymentDetailBasePath}/${row.latestDeploymentId}`, {
                                state: { from: basePath, backLabel: '返回流水线大厅' },
                              })}
                            >
                              查看
                            </Button>
                            <Button size="small" onClick={() => navigate(`${basePath}/${row.pipelineId}/history`)}>
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
          </div>
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
        destroyOnHidden
      >
          <div className="space-y-4">
          {deployingPipeline && hallItems.some((item) => item.pipelineId === deployingPipeline.id && item.latestStatus && ACTIVE_DEPLOYMENT_STATUSES.includes(item.latestStatus)) ? (
            <div className="app-inline-alert app-inline-alert--warning rounded-xl p-3 text-sm">
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
    </div>
  );
}
