import { EllipsisOutlined } from '@ant-design/icons';
import { Heart, LockSimple, UserCircle } from '@phosphor-icons/react';
import type { CSSProperties } from 'react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Avatar, Button, Card, Dropdown, Input, Modal, Popconfirm, Progress, Segmented, Select, Space, Table, Tag, Typography, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import { deploymentsApi } from '../../api/deployments';
import { pipelinesApi } from '../../api/pipelines';
import type { DeploymentPrecheckMissingItem, DeploymentSummary } from '../../api/types';
import { resolveBackendAssetUrl } from '../../api/client';
import EmptyPane from '../../components/EmptyPane';
import HallSwitchIcon from '../../components/HallSwitchIcon';
import PageHeaderBar from '../../components/PageHeaderBar';
import RefreshIconButton from '../../components/RefreshIconButton';
import PipelineNameWithTags from '../../components/PipelineNameWithTags';
import PipelineIcon from '../../components/PipelineIcon';
import ShortDateTime from '../../components/ShortDateTime';
import StatusTag from '../../components/StatusTag';
import { ACTIVE_DEPLOYMENT_STATUSES } from '../../constants/deployment';
import { usePipelineHallPreferences, type PipelineHallFilterMode } from '../../hooks/usePipelineHallPreferences';
import { usePipelineHallStream } from '../../hooks/usePipelineHallStream';
import { useSseStream } from '../../hooks/useSseStream';
import type { PipelineBranchOption, PipelineHallRunningServiceSummary, PipelineHallSummary, PipelineSummary, UserRecentPipelineSummary } from '../../types/domain';
import { formatDateTime } from '../../utils/datetime';
import { formatDeploymentElapsed } from '../../utils/deploymentDuration';
import { getDeploymentProgressColor, getDeploymentProgressLabel, hasDeploymentProgressStep } from '../../utils/deploymentProgress';
import { formatDurationSince } from '../../utils/duration';
import { filterDisplayTags } from '../../utils/pipelineDisplay';
import { getStableTagColor, getStableTagDarkColor, sortTagNames } from '../../utils/tagColors';

type HallView = PipelineHallFilterMode;

const DEPLOYMENT_PRECHECK_LABEL: Record<string, string> = {
  DEPLOYMENT_RESTRICTED: '当前时间不允许部署',
  PIPELINE: '流水线',
  PROJECT: '项目',
  TEMPLATE: '模板',
  TARGET_HOST: '目标主机',
  TARGET_DIR: '部署目录',
  DEFAULT_BRANCH: '默认分支',
  RUNNING_DEPLOYMENT: '当前流水线已有运行中的部署',
};

const deploymentPrecheckItemLabel = (item: DeploymentPrecheckMissingItem) => {
  if (item.code === 'DEPLOYMENT_RESTRICTED') {
    return item.name ? `命中策略“${item.name}”${item.label ? `，原因：${item.label}` : ''}` : (item.label || '当前时间不允许部署');
  }
  if (item.code === 'BUILD_RUNTIME_ENVIRONMENT') {
    return `本机构建 ${item.label || item.name || ''} 环境`.trim();
  }
  if (item.code === 'TARGET_RUNTIME_ENVIRONMENT') {
    return `目标主机运行 ${item.label || item.name || ''} 环境`.trim();
  }
  if (item.code === 'TEMPLATE_VARIABLE') {
    return item.label || item.name || '模板变量';
  }
  return DEPLOYMENT_PRECHECK_LABEL[item.code] || item.label || item.name || item.code;
};

const deploymentPrecheckMessage = (missingItems?: DeploymentPrecheckMissingItem[]) => {
  if (!missingItems?.length) {
    return '部署前检查未通过';
  }
  return `部署前检查未通过：${missingItems.map(deploymentPrecheckItemLabel).join('、')}`;
};

const showRestrictedDeploymentModal = (item: DeploymentPrecheckMissingItem) => {
  Modal.warning({
    title: '当前时间不允许部署',
    content: (
      <div className="space-y-2">
        <div>{item.name ? `命中策略：${item.name}` : '当前时间命中部署限制策略。'}</div>
        {item.label ? <div className="text-sm text-slate-500 dark:text-slate-400">原因：{item.label}</div> : null}
      </div>
    ),
    okText: '知道了',
  });
};

const stableTagStyle = (tag: string): CSSProperties => ({
  '--app-tag-bg': getStableTagColor(tag),
  '--app-tag-bg-dark': getStableTagDarkColor(tag),
  '--app-tag-fg': '#ffffff',
} as CSSProperties);

const deploymentUserName = (item: Pick<PipelineHallSummary, 'latestTriggeredBy' | 'latestTriggeredByDisplayName'>) => (
  item.latestTriggeredByDisplayName || item.latestTriggeredBy || '-'
);

function DeploymentUser({ item }: { item: Pick<PipelineHallSummary, 'latestTriggeredBy' | 'latestTriggeredByDisplayName' | 'latestTriggeredByAvatar'> }) {
  const name = deploymentUserName(item);
  const avatarUrl = resolveBackendAssetUrl(item.latestTriggeredByAvatar);
  return (
    <span className="deployment-user-inline">
      <Avatar size={20} src={avatarUrl} icon={!avatarUrl ? <UserCircle size={14} weight="fill" /> : undefined} />
      <span className="truncate">{name}</span>
    </span>
  );
}

function PipelineRestrictionIcon({ name, reason }: { name?: string | null; reason?: string | null }) {
  const title = [name ? `命中策略：${name}` : '当前命中部署限制策略', reason ? `原因：${reason}` : undefined].filter(Boolean).join('｜');
  return (
    <span className="pipeline-lock-icon" title={title}>
      <LockSimple size={14} weight="fill" />
    </span>
  );
}

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
 * 流水线大厅。
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
  const [tableItems, setTableItems] = useState<PipelineHallSummary[]>([]);
  const [tableTotal, setTableTotal] = useState(0);
  const [tablePagination, setTablePagination] = useState({ current: 1, pageSize: 10 });
  const [recentPipelines, setRecentPipelines] = useState<UserRecentPipelineSummary[]>([]);
  const [runningServices, setRunningServices] = useState<PipelineHallRunningServiceSummary[]>([]);
  const [hallLoading, setHallLoading] = useState(false);
  const [tableLoading, setTableLoading] = useState(false);
  const [recentLoading, setRecentLoading] = useState(false);
  const [runningLoading, setRunningLoading] = useState(false);
  const [submittingId, setSubmittingId] = useState<number>();
  const [deployModalOpen, setDeployModalOpen] = useState(false);
  const [deployingPipeline, setDeployingPipeline] = useState<PipelineSummary>();
  const [branchOptions, setBranchOptions] = useState<PipelineBranchOption[]>([]);
  const [selectedBranch, setSelectedBranch] = useState<string>();
  const [branchesLoading, setBranchesLoading] = useState(false);
  const [tick, setTick] = useState(() => Date.now());
  const [keyword, setKeyword] = useState('');
  const [tagFilter, setTagFilter] = useState<string[]>();
  const [selectedPipelineId, setSelectedPipelineId] = useState<number>();
  const [cardColumnLimit, setCardColumnLimit] = useState(1);
  const [hallStreamVersion, setHallStreamVersion] = useState<number>();
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

  const loadHallItems = async (silent = false) => {
    if (!silent) {
      setHallLoading(true);
    }
    try {
      const items = await pipelinesApi.listHall();
      setHallItems(items);
      setHallStreamVersion(items.reduce((max, item) => Math.max(max, item.version || item.latestDeploymentId || item.pipelineId || 0), 0));
    } finally {
      if (!silent) {
        setHallLoading(false);
      }
    }
  };

  const loadTablePage = async (silent = false, page = tablePagination.current, pageSize = tablePagination.pageSize) => {
    if (!silent) {
      setTableLoading(true);
    }
    try {
      const result = await pipelinesApi.listHallPage({
        page,
        pageSize,
        keyword: keyword.trim() || undefined,
        tags: tagFilter,
        filterMode: hallView,
        selectedPipelineId,
        pinActivePipelines,
      });
      setTableItems(result.items);
      setTableTotal(result.total);
      setTablePagination({ current: result.page, pageSize: result.pageSize });
    } finally {
      if (!silent) {
        setTableLoading(false);
      }
    }
  };

  const mergeHallStreamItems = (items: PipelineHallSummary[], full?: boolean) => {
    if (!items.length && !full) {
      return;
    }
    setHallItems((current) => {
      if (full) {
        return items;
      }
      const nextById = new Map(current.map((item) => [item.pipelineId, item]));
      items.forEach((item) => nextById.set(item.pipelineId, item));
      return current.map((item) => nextById.get(item.pipelineId) || item);
    });
    setTableItems((current) => {
      if (!current.length) {
        return current;
      }
      const nextById = new Map(items.map((item) => [item.pipelineId, item]));
      return current.map((item) => nextById.get(item.pipelineId) || item);
    });
  };

  const mergeCreatedDeployment = (deployment: DeploymentSummary, fallbackPipelineId?: number) => {
    const pipelineId = deployment.pipeline?.id || fallbackPipelineId;
    if (!pipelineId) {
      return;
    }
    const updateItem = (item: PipelineHallSummary): PipelineHallSummary => {
      if (item.pipelineId !== pipelineId) {
        return item;
      }
      return {
        ...item,
        latestDeploymentId: deployment.id,
        latestStatus: deployment.status || 'PENDING',
        latestBranchName: deployment.branchName || item.defaultBranch,
        latestTriggeredBy: deployment.triggeredBy || item.latestTriggeredBy,
        latestTriggeredByDisplayName: deployment.triggeredByDisplayName || item.latestTriggeredByDisplayName,
        latestCreatedAt: deployment.createdAt || item.latestCreatedAt,
        latestStartedAt: deployment.startedAt || item.latestStartedAt,
        latestFinishedAt: deployment.finishedAt || undefined,
        latestProgressPercent: deployment.progressPercent ?? 0,
        latestProgressStage: deployment.progressStage || null,
        latestProgressCurrent: deployment.progressCurrent ?? null,
        latestProgressTotal: deployment.progressTotal ?? null,
      };
    };
    setHallItems((current) => current.map(updateItem));
    setTableItems((current) => current.map(updateItem));
  };

  /** 首次加载大厅卡片和最近部署统计，后续变化由 SSE 推送。 */
  const loadData = async (silent = false) => {
    if (!silent) {
      setRecentLoading(true);
      if (showRunningServices) {
        setRunningLoading(true);
      }
    }
    const tasks: Promise<unknown>[] = [
      loadHallItems(silent),
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

  const refreshData = async () => {
    const tasks = [loadData()];
    if (viewMode === 'table') {
      tasks.push(loadTablePage());
    }
    await Promise.allSettled(tasks);
  };

  const hasLiveRunningServices = runningServices.some((item) => item.status === 'RUNNING');

  useEffect(() => {
    loadData().catch(() => message.error('加载流水线失败'));
  }, [showRunningServices]);

  useEffect(() => {
    setTablePagination((current) => (current.current === 1 ? current : { ...current, current: 1 }));
  }, [hallView, keyword, pinActivePipelines, selectedPipelineId, tagFilter]);

  useEffect(() => {
    if (viewMode !== 'table') {
      return;
    }
    loadTablePage().catch(() => message.error('加载流水线失败'));
  }, [hallView, keyword, pinActivePipelines, selectedPipelineId, tagFilter, tablePagination.current, tablePagination.pageSize, viewMode]);

  usePipelineHallStream({
    enabled: true,
    version: hallStreamVersion,
    onUpdate: (payload) => {
      setHallStreamVersion(payload.version);
      mergeHallStreamItems(payload.items || [], payload.full);
    },
    onError: () => {},
  });

  useSseStream<PipelineHallRunningServiceSummary[]>({
    enabled: showRunningServices,
    path: '/pipelines/hall/running-services/stream',
    eventName: 'running-services',
    onData: setRunningServices,
    onError: () => {},
  });

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
    setTableItems((current) => current.map((item) => item.pipelineId === pipelineId ? { ...item, favorited: !favorited } : item));
    setRecentPipelines((current) => [...current]);
  };

  /** 打开部署弹窗前先做限制预检，通过后再拉取可选分支。 */
  const openDeployModal = async (pipeline: PipelineSummary) => {
    const activePipeline = hallItems.find((item) => item.pipelineId === pipeline.id);
    const precheckPayload = {
      pipelineId: pipeline.id,
      branchName: pipeline.defaultBranch,
      triggeredBy: 'user',
      replaceRunning: Boolean(activePipeline?.latestStatus && ACTIVE_DEPLOYMENT_STATUSES.includes(activePipeline.latestStatus)),
    };
    const precheck = await deploymentsApi.precheck(precheckPayload);
    if (!precheck.passed) {
      const restrictedItem = precheck.missingItems.find((item) => item.code === 'DEPLOYMENT_RESTRICTED');
      if (restrictedItem) {
        showRestrictedDeploymentModal(restrictedItem);
        return;
      }
      message.error(deploymentPrecheckMessage(precheck.missingItems));
      return;
    }
    setDeployingPipeline(pipeline);
    setSelectedBranch(pipeline.defaultBranch);
    setDeployModalOpen(true);
    setBranchesLoading(true);
    try {
      const branches = await pipelinesApi.getBranches(pipeline.id);
      const options = await pipelinesApi.getBranchOptions(pipeline.id).catch(() => (
        branches.map((name) => ({
          name,
          defaultBranch: name === pipeline.defaultBranch,
          recent: false,
        }))
      ));
      setBranchOptions(options);
      if (options.length > 0) {
        const optionNames = options.map((item) => item.name);
        setSelectedBranch(optionNames.includes(pipeline.defaultBranch || '') ? pipeline.defaultBranch : optionNames[0]);
      }
    } catch {
      setBranchOptions(pipeline.defaultBranch ? [{ name: pipeline.defaultBranch, defaultBranch: true, recent: false }] : []);
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
    const payload = {
      pipelineId: deployingPipeline.id,
      branchName: selectedBranch,
      triggeredBy: 'user',
      replaceRunning: Boolean(activePipeline?.latestStatus && ACTIVE_DEPLOYMENT_STATUSES.includes(activePipeline.latestStatus)),
    };
    setSubmittingId(deployingPipeline.id);
    try {
      const precheck = await deploymentsApi.precheck(payload);
      if (!precheck.passed) {
        const restrictedItem = precheck.missingItems.find((item) => item.code === 'DEPLOYMENT_RESTRICTED');
        if (restrictedItem) {
          showRestrictedDeploymentModal(restrictedItem);
          return;
        }
        message.error(deploymentPrecheckMessage(precheck.missingItems));
        return;
      }
      const deployment = await deploymentsApi.create(payload);
      mergeCreatedDeployment(deployment, deployingPipeline.id);
      setDeployModalOpen(false);
      setDeployingPipeline(undefined);
      setBranchOptions([]);
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
            <RefreshIconButton onClick={() => refreshData().catch(() => message.error('加载流水线失败'))} />
            <Segmented
              className="pipeline-hall-header-switch pipeline-hall-view-switch"
              value={hallView}
              onChange={(value) => {
                setHallView(value as HallView);
                setSelectedPipelineId(undefined);
              }}
              options={[
                { value: 'all', icon: <HallSwitchIcon name="all" tone="blue" />, label: `全部 ${hallViewStats.all}` },
                { value: 'favorites', icon: <HallSwitchIcon name="favorites" tone="rose" />, label: `收藏 ${hallViewStats.favorites}` },
                { value: 'running', icon: <HallSwitchIcon name="running" tone="emerald" />, label: `运行中 ${hallViewStats.running}` },
                { value: 'failed', icon: <HallSwitchIcon name="failed" tone="amber" />, label: `失败 ${hallViewStats.failed}` },
                { value: 'recent', icon: <HallSwitchIcon name="recent" tone="cyan" />, label: `最近 ${hallViewStats.recent}` },
              ]}
            />
            <Segmented
              className="pipeline-hall-header-switch pipeline-hall-view-switch"
              value={viewMode}
              onChange={(value) => setViewMode(value as 'card' | 'table')}
              options={[
                { value: 'card', icon: <HallSwitchIcon name="card" tone="violet" />, label: '卡片' },
                { value: 'table', icon: <HallSwitchIcon name="table" tone="slate" />, label: '表格' },
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
                          <div className="pipeline-hall-running-item-name">
                            <PipelineNameWithTags name={item.pipelineName || item.serviceName} importantTags={item.importantTags} fallback={`服务 #${item.serviceId}`} />
                          </div>
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
                  <EmptyPane description="当前没有可部署流水线，请先创建或分配流水线。" />
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
                          const tags = filterDisplayTags(item.tags, item.importantTags);
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
                                    <Typography.Text className="pipeline-project-kicker block">
                                      {item.projectName || 'Project'}
                                    </Typography.Text>
                                    <div className="pipeline-card-title-meta-row">
                                      {item.deploymentRestricted ? <PipelineRestrictionIcon name={item.restrictionName} reason={item.restrictionReason} /> : null}
                                      <button
                                        type="button"
                                        className={`pipeline-hall-favorite-button${item.favorited ? ' pipeline-hall-favorite-button--active' : ''}`}
                                        onClick={() => toggleFavorite(item.pipelineId, item.favorited).catch(() => message.error(item.favorited ? '取消收藏失败' : '收藏失败'))}
                                      >
                                        <Heart weight={item.favorited ? 'fill' : 'regular'} />
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
                            <Typography.Title level={4} className="pipeline-title-text pipeline-card-title !m-0" title={item.pipelineName}>
                              <PipelineNameWithTags name={item.pipelineName} importantTags={item.importantTags} />
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
                                  <span><DeploymentUser item={item} /></span>
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
                                    {getDeploymentProgressLabel(
                                      item.latestProgressPercent ?? 0,
                                      item.latestStatus,
                                      item.latestProgressStage,
                                      item.latestProgressCurrent,
                                      item.latestProgressTotal,
                                    )}
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
	                    className="pipeline-hall-table"
	                    rowKey="pipelineId"
	                    loading={tableLoading && tableItems.length > 0}
	                    dataSource={tableItems}
	                    rowClassName={(row) => row.latestStatus === 'FAILED' ? 'pipeline-hall-table-row--failed' : ''}
	                    locale={{ emptyText: <EmptyPane description="当前筛选条件下没有可部署流水线。" /> }}
                    pagination={{
                      current: tablePagination.current,
                      pageSize: tablePagination.pageSize,
                      total: tableTotal,
                      showSizeChanger: true,
                      showTotal: (total) => `共 ${total} 条`,
                      onChange: (page, pageSize) => setTablePagination({ current: page, pageSize }),
                    }}
                    columns={[
                    {
                      title: '名称',
                      width: 260,
                      render: (_, row) => {
                        return (
                          <div className="flex items-center gap-2">
                            <button
                              type="button"
                              className={`pipeline-hall-favorite-button shrink-0${row.favorited ? ' pipeline-hall-favorite-button--active' : ''}`}
                              onClick={() => toggleFavorite(row.pipelineId, row.favorited).catch(() => message.error(row.favorited ? '取消收藏失败' : '收藏失败'))}
                            >
                              <Heart weight={row.favorited ? 'fill' : 'regular'} />
                            </button>
                            <PipelineIcon type={row.templateType} />
                            <div className="pipeline-table-name-block min-w-0">
                              <div className="pipeline-table-title-row">
                                {row.deploymentRestricted ? <PipelineRestrictionIcon name={row.restrictionName} reason={row.restrictionReason} /> : null}
                                {row.latestDeploymentOrder ? (
                                  <div className="pipeline-table-order shrink-0 text-xs font-semibold text-sky-600">
                                    #{row.latestDeploymentOrder}
                                  </div>
                                ) : null}
                                <div className="pipeline-title-text pipeline-table-title truncate">
                                  <PipelineNameWithTags name={row.pipelineName} importantTags={row.importantTags} />
                                </div>
                              </div>
                              <div className="pipeline-project-kicker truncate" title={row.projectName || ''}>{row.projectName || '-'}</div>
                            </div>
                          </div>
                        );
                      },
                    },
                    {
                      title: '描述',
                      render: (_, row) => row.pipelineDescription ? (
                        <div className="line-clamp-2 text-sm leading-6 text-slate-600" title={row.pipelineDescription}>
                          {row.pipelineDescription}
                        </div>
                      ) : '-',
                    },
                    {
                      title: '标签',
                      width: 168,
                      render: (_, row) => {
                        const tags = filterDisplayTags(row.tags, row.importantTags);
                        return tags.length > 0 ? (
                          <Space className="pipeline-table-tags" size={[4, 4]} wrap>
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
                      width: 76,
                      className: 'pipeline-table-status-cell',
                      onHeaderCell: () => ({ className: 'pipeline-table-status-cell' }),
                      onCell: () => ({ className: 'pipeline-table-status-cell' }),
                      render: (_, row) => {
                        const showProgressText = row.latestStatus === 'RUNNING' || hasDeploymentProgressStep(
                          row.latestProgressStage,
                          row.latestProgressCurrent,
                          row.latestProgressTotal,
                        );
                        const progressText = getDeploymentProgressLabel(
                          row.latestProgressPercent ?? 0,
                          row.latestStatus,
                          row.latestProgressStage,
                          row.latestProgressCurrent,
                          row.latestProgressTotal,
                        );
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
                      render: (_, row) => <DeploymentUser item={row} />,
                    },
                    {
                      title: '开始时间',
                      width: 138,
                      render: (_, row) => <ShortDateTime value={row.latestStartedAt || row.latestCreatedAt} />,
                    },
                    {
                      title: '结束时间',
                      width: 138,
                      render: (_, row) => <ShortDateTime value={row.latestFinishedAt} />,
                    },
                    {
                      title: '最近耗时',
                      width: 74,
                      className: 'pipeline-table-duration-cell',
                      onHeaderCell: () => ({ className: 'pipeline-table-duration-cell' }),
                      onCell: () => ({ className: 'pipeline-table-duration-cell' }),
                      render: (_, row) => (
                        <span className="pipeline-table-duration">
                          {formatDeploymentElapsed({
                            startedAt: row.latestStartedAt || undefined,
                            createdAt: row.latestCreatedAt || undefined,
                            finishedAt: row.latestFinishedAt || undefined,
                            status: row.latestStatus || undefined,
                          }, tick)}
                        </span>
                      ),
                    },
                    {
                      title: '操作',
                      width: 154,
                      render: (_, row) => {
                        const activeDeployment = row.latestStatus && ACTIVE_DEPLOYMENT_STATUSES.includes(row.latestStatus)
                          ? row
                          : undefined;
                        return (
                          <Space className="pipeline-table-actions" size={6}>
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
            options={branchOptions.map((item) => ({
              label: (
                <div className="pipeline-branch-option">
                  <span>{item.name}</span>
                  <span className="pipeline-branch-option__tags">
                    {item.defaultBranch ? <span className="pipeline-branch-option__tag">默认</span> : null}
                    {item.recent ? <span className="pipeline-branch-option__tag pipeline-branch-option__tag--recent">最近</span> : null}
                  </span>
                </div>
              ),
              value: item.name,
              title: item.name,
            }))}
            onChange={setSelectedBranch}
            placeholder="请选择分支"
            showSearch
            optionFilterProp="value"
          />
        </div>
      </Modal>
    </div>
  );
}
