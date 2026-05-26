import type { CSSProperties } from 'react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { EllipsisOutlined } from '@ant-design/icons';
import { Button, Card, Dropdown, Form, Input, Modal, Select, Space, Steps, Table, Tag, message } from 'antd';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { deploymentPluginsApi } from '../../api/deploymentPlugins';
import { hostsApi } from '../../api/hosts';
import { notificationsApi } from '../../api/notifications';
import { pipelinesApi } from '../../api/pipelines';
import { projectsApi } from '../../api/projects';
import { runtimeEnvironmentsApi } from '../../api/runtimeEnvironments';
import { templatesApi } from '../../api/templates';
import type { PipelinePayload } from '../../api/types';
import EmptyPane from '../../components/EmptyPane';
import CodeEditor from '../../components/CodeEditor';
import PageHeaderBar from '../../components/PageHeaderBar';
import PipelineVariablesEditor from '../../components/PipelineVariablesEditor';
import PipelineIcon from '../../components/PipelineIcon';
import { copyText } from '../../utils/clipboard';
import { getStableTagColor, getStableTagDarkColor, PHASE_LABEL_MAP, sortByPhase, sortTagNames } from '../../utils/tagColors';
import type {
  HostSummary,
  NotificationBinding,
  NotificationChannelSummary,
  DeploymentPluginDefinitionSummary,
  PluginFormFieldBindingSummary,
  PluginFormFieldSummary,
  MavenSettingsSummary,
  PipelineSummary,
  RuntimeEnvironmentSummary,
  TemplateSummary,
  TemplateVariableDefinition,
} from '../../types/domain';

const stableTagStyle = (tag: string): CSSProperties => ({
  '--app-tag-bg': getStableTagColor(tag),
  '--app-tag-bg-dark': getStableTagDarkColor(tag),
  '--app-tag-fg': '#ffffff',
} as CSSProperties);

const phaseClassName = (phase?: string | null) => `app-phase-tag--${phase || 'shared'}`;
const phaseLabelClassName = (phase?: string | null) => `app-phase-label--${phase || 'shared'}`;

const PIPELINE_TABLE_SCROLL_LEFT_KEY = 'deploy-bot:pipeline-table-scroll-left';

interface PipelineFormState {
  name: string;
  description: string;
  tags: string[];
  importantTags: string[];
  projectId?: number;
  templateId?: number;
  templatePluginId?: string;
  builtinTemplateKey?: string;
  targetHostId?: number;
  targetDir: string;
  defaultBranch: string;
  variables: Record<string, string>;
  javaEnvironmentId?: number;
  nodeEnvironmentId?: number;
  mavenEnvironmentId?: number;
  mavenSettingsId?: number;
  runtimeJavaEnvironmentId?: number;
  startupKeyword: string;
  startupTimeoutSeconds?: number;
  pluginConfig: Record<string, string>;
  notificationIds: number[];
}

const emptyPipeline: PipelineFormState = {
  name: '',
  description: '',
  tags: [],
  importantTags: [],
  projectId: undefined,
  templateId: undefined,
  templatePluginId: undefined,
  builtinTemplateKey: undefined,
  targetHostId: undefined,
  targetDir: '',
  defaultBranch: 'main',
  variables: {},
  javaEnvironmentId: undefined,
  nodeEnvironmentId: undefined,
  mavenEnvironmentId: undefined,
  mavenSettingsId: undefined,
  runtimeJavaEnvironmentId: undefined,
  startupKeyword: '',
  startupTimeoutSeconds: undefined,
  pluginConfig: {},
  notificationIds: [],
};

const normalizePluginFieldValue = (value: unknown) => {
  if (value == null) {
    return '';
  }
  return String(value);
};

const resolveFieldBinding = (field: PluginFormFieldSummary): PluginFormFieldBindingSummary => (
  field.binding || { scope: 'PLUGIN_CONFIG', key: field.key }
);

const extractPluginConfig = (
  record: PipelineSummary,
  plugin: DeploymentPluginDefinitionSummary | null,
): Record<string, string> => {
  if (!plugin?.pipelineFormSchema?.sections?.length) {
    return {};
  }
  const variables = normalizeVariables(record.variables);
  const pluginConfigValues = normalizeVariables(record.pluginConfig);
  const config: Record<string, string> = {};
  plugin.pipelineFormSchema.sections.forEach((section) => {
    section.fields.forEach((field) => {
      const binding = resolveFieldBinding(field);
      if (binding.scope === 'PIPELINE_VARIABLE') {
        config[field.key] = normalizePluginFieldValue(variables[binding.key]);
        return;
      }
      config[field.key] = normalizePluginFieldValue(pluginConfigValues[field.key]);
    });
  });
  return config;
};

/**
 * 模板变量可能以字符串或数组返回，这里统一转成结构化数组。
 */
const parseVariablesSchema = (content: unknown): TemplateVariableDefinition[] => {
  if (!content) {
    return [];
  }
  if (Array.isArray(content)) {
    return content;
  }
  try {
    const parsed = JSON.parse(content);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
};

const TEMPLATE_VARIABLE_PATTERN = /\{\{\s*([a-zA-Z_][\w.-]*)\s*}}/g;

const inferVariablesFromScript = (
  content: string | undefined | null,
  phase: 'build' | 'deploy',
  variables: Map<string, TemplateVariableDefinition>,
  plugin?: DeploymentPluginDefinitionSummary | null,
) => {
  if (!content) {
    return;
  }
  const pluginVariableMap = new Map((plugin?.variableDefinitions || []).map((item) => [item.key, item]));
  Array.from(content.matchAll(TEMPLATE_VARIABLE_PATTERN)).forEach((match) => {
    const name = match[1];
    if (!name || variables.has(name)) {
      return;
    }
    const definition = pluginVariableMap.get(name);
    variables.set(name, {
      name,
      label: definition?.label || name,
      required: definition?.required ?? true,
      pipelineInput: true,
      placeholder: definition?.defaultValue,
      phase,
      mutationRuleIds: definition?.mutationRuleIds,
      binding: definition?.binding,
    });
  });
};

const resolveTemplateVariables = (
  template: PipelineTemplateOption | undefined,
  plugin?: DeploymentPluginDefinitionSummary | null,
) => {
  const parsed = parseVariablesSchema(template?.variablesSchema);
  if (parsed.length || !template || template.source !== 'builtin') {
    return parsed;
  }
  const variables = new Map<string, TemplateVariableDefinition>();
  inferVariablesFromScript(template.buildScriptContent, 'build', variables, plugin);
  inferVariablesFromScript(template.deployScriptContent, 'deploy', variables, plugin);
  return Array.from(variables.values());
};

const retainMatchedTemplateVariables = (
  values: Record<string, string>,
  template: PipelineTemplateOption | undefined,
  plugin?: DeploymentPluginDefinitionSummary | null,
) => {
  const names = new Set(resolveTemplateVariables(template, plugin).map((item) => item.name));
  return Object.fromEntries(Object.entries(values || {}).filter(([name]) => names.has(name)));
};

const normalizeVariables = (content: unknown): Record<string, string> => {
  if (!content) {
    return {};
  }
  if (typeof content === 'object') {
    return content;
  }
  try {
    const parsed = JSON.parse(content);
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
};

const stripContextVariables = (values: Record<string, string>) => {
  const next = { ...(values || {}) };
  delete next.targetDir;
  return next;
};

const removePluginConfigVariables = (
  variables: Record<string, string>,
  plugin: DeploymentPluginDefinitionSummary | null,
) => {
  if (!plugin?.pipelineFormSchema?.sections?.length) {
    return variables;
  }
  const next = { ...variables };
  plugin.pipelineFormSchema.sections.forEach((section) => {
    section.fields.forEach((field) => {
      if (field.binding?.scope === 'PLUGIN_CONFIG') {
        delete next[field.binding.key];
        delete next[field.key];
      }
    });
  });
  return next;
};

const renderTemplateOptionLabel = (item: PipelineTemplateOption) => (
  <div className="pipeline-template-option">
    <span className="pipeline-template-option__name">{item.name}</span>
    <span className={`pipeline-template-option__tag ${item.source === 'builtin' ? 'pipeline-template-option__tag--builtin' : ''}`}>
      {item.source === 'builtin' ? '默认模板' : '自定义模板'}
    </span>
  </div>
);

interface PipelineTemplateOption {
  optionId: number;
  source: 'saved' | 'builtin';
  name: string;
  templateType?: string;
  pluginId?: string;
  templateId?: number;
  builtinTemplateKey?: string;
  variablesSchema?: string | TemplateVariableDefinition[];
  buildScriptContent?: string | null;
  deployScriptContent?: string | null;
  monitorProcess?: boolean;
}

const matchesPipelineTemplateOption = (
  option: PipelineTemplateOption,
  item: Pick<PipelineSummary, 'template' | 'templatePluginId' | 'builtinTemplateKey'>,
) => {
  if (item.template?.id != null) {
    return option.source === 'saved' && option.templateId === item.template.id;
  }
  if (item.templatePluginId && item.builtinTemplateKey) {
    return option.source === 'builtin'
      && option.pluginId === item.templatePluginId
      && option.builtinTemplateKey === item.builtinTemplateKey;
  }
  return false;
};

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

const normalizeNotificationBindings = (content: unknown): NotificationBinding[] => {
  if (!content) {
    return [];
  }
  if (Array.isArray(content)) {
    return content.filter((item): item is NotificationBinding => Boolean(item?.notificationId && item?.eventType));
  }
  try {
    const parsed = JSON.parse(String(content));
    return Array.isArray(parsed)
      ? parsed.filter((item): item is NotificationBinding => Boolean(item?.notificationId && item?.eventType))
      : [];
  } catch {
    return [];
  }
};

const deriveNotificationIds = (bindings: NotificationBinding[]) => Array.from(new Set(bindings.map((item) => item.notificationId)));

const buildRuntimeFieldConfigs = [
  {
    type: 'JAVA',
    formKey: 'javaEnvironmentId',
    label: 'Java',
  },
  {
    type: 'NODE',
    formKey: 'nodeEnvironmentId',
    label: 'Node',
  },
  {
    type: 'MAVEN',
    formKey: 'mavenEnvironmentId',
    label: 'Maven',
  },
] as const;

type SelectOptionValue = string | number;
type SelectOptionItem = {
  label: string;
  value: SelectOptionValue;
};

const pickSingleOptionValue = (options: SelectOptionItem[]) => (
  options.length === 1 ? options[0].value : undefined
);

const includesOptionValue = (options: SelectOptionItem[], value?: SelectOptionValue) => (
  value != null && options.some((item) => item.value === value)
);

const buildPipelineFormSnapshot = (value: PipelineFormState) => JSON.stringify({
  ...value,
  tags: value.tags || [],
  importantTags: value.importantTags || [],
  variables: Object.fromEntries(Object.entries(value.variables || {}).sort(([left], [right]) => left.localeCompare(right))),
  pluginConfig: Object.fromEntries(Object.entries(value.pluginConfig || {}).sort(([left], [right]) => left.localeCompare(right))),
  notificationIds: [...(value.notificationIds || [])].sort((left, right) => left - right),
});

const getLocalBuildEnvironmentOptions = (
  items: RuntimeEnvironmentSummary[],
  type: RuntimeEnvironmentSummary['type'],
  localHostId?: number,
) => {
  const candidates = items.filter((item) => {
    if (item?.type !== type || item.enabled === false) {
      return false;
    }
    if (item.host == null) {
      return true;
    }
    if (localHostId && item.host.id === localHostId) {
      return true;
    }
    return item.host.type === 'LOCAL' || item.host.builtIn === true;
  });
  const deduped = new Map();

  candidates.forEach((item) => {
    const key = [item.type, item.name || '', item.version || ''].join('::');
    const existing = deduped.get(key);
    if (!existing) {
      deduped.set(key, item);
      return;
    }
    const currentIsLocal = item.host?.id === localHostId || item.host?.type === 'LOCAL' || item.host?.builtIn === true;
    const existingIsLocal = existing.host?.id === localHostId || existing.host?.type === 'LOCAL' || existing.host?.builtIn === true;
    if (currentIsLocal && !existingIsLocal) {
      deduped.set(key, item);
    }
  });

  return Array.from(deduped.values())
    .sort(sortEnvironmentOptions)
    .map((item) => ({ label: `${item.name}${item.version ? ` (${item.version})` : ''}`, value: item.id }));
};

const sortEnvironmentOptions = (left: RuntimeEnvironmentSummary, right: RuntimeEnvironmentSummary) => {
  const leftAuto = left.name?.includes('自动检测');
  const rightAuto = right.name?.includes('自动检测');
  if (leftAuto !== rightAuto) {
    return leftAuto ? 1 : -1;
  }
  return String(left.name || '').localeCompare(String(right.name || ''), 'zh-CN');
};

const mergeBranchOptions = (branches: string[], currentBranch?: string) => {
  const merged = new Set<string>();
  if (currentBranch?.trim()) {
    merged.add(currentBranch.trim());
  }
  branches.forEach((branch) => {
    if (branch?.trim()) {
      merged.add(branch.trim());
    }
  });
  return Array.from(merged);
};

type PipelinePageMode = 'list' | 'create' | 'edit' | 'view';

export default function PipelineAdminPage({ mode = 'list' }: { mode?: PipelinePageMode }) {
  const navigate = useNavigate();
  const { pipelineId } = useParams();
  const [searchParams] = useSearchParams();
  const routePipelineId = pipelineId ? Number(pipelineId) : undefined;
  const tableWrapRef = useRef<HTMLDivElement | null>(null);
  const initialFormSnapshotRef = useRef(buildPipelineFormSnapshot(emptyPipeline));
  const [projects, setProjects] = useState<PipelineSummary['project'][]>([]);
  const [hosts, setHosts] = useState<HostSummary[]>([]);
  const [templates, setTemplates] = useState<TemplateSummary[]>([]);
  const [notifications, setNotifications] = useState<NotificationChannelSummary[]>([]);
  const [plugins, setPlugins] = useState<DeploymentPluginDefinitionSummary[]>([]);
  const [mavenSettings, setMavenSettings] = useState<MavenSettingsSummary[]>([]);
  const [runtimeEnvironments, setRuntimeEnvironments] = useState<RuntimeEnvironmentSummary[]>([]);
  const [pipelines, setPipelines] = useState<PipelineSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [availableTags, setAvailableTags] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState<PipelineFormState>(emptyPipeline);
  const [branchOptions, setBranchOptions] = useState<string[]>([]);
  const [branchesLoading, setBranchesLoading] = useState(false);
  const [editingId, setEditingId] = useState<number>();
  const [duplicateModalOpen, setDuplicateModalOpen] = useState(false);
  const [duplicateSource, setDuplicateSource] = useState<PipelineSummary>();
  const [duplicateName, setDuplicateName] = useState('');
  const [duplicating, setDuplicating] = useState(false);
  const [currentStep, setCurrentStep] = useState(0);
  const [keyword, setKeyword] = useState('');
  const [projectFilter, setProjectFilter] = useState<number>();
  const [templateFilter, setTemplateFilter] = useState<number>();
  const [hostFilter, setHostFilter] = useState<number>();
  const [tagFilter, setTagFilter] = useState<string[]>();
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });
  const workspaceBackPath = searchParams.get('fromTab') === 'manage' ? '/admin/pipelines?tab=manage' : '/admin/pipelines';

  const updateFormWithSnapshot = (nextForm: PipelineFormState) => {
    initialFormSnapshotRef.current = buildPipelineFormSnapshot(nextForm);
    setForm(nextForm);
  };

  const isFormChanged = () => initialFormSnapshotRef.current !== buildPipelineFormSnapshot(form);

  const cancelEditing = () => {
    if (!isFormChanged()) {
      navigate(workspaceBackPath);
      return;
    }
    Modal.confirm({
      title: '丢弃当前修改？',
      content: '当前流水线配置还没有保存，确认取消后这些修改会丢失。',
      okText: '丢弃修改',
      cancelText: '继续编辑',
      okButtonProps: { danger: true },
      onOk: () => navigate(workspaceBackPath),
    });
  };

  const rememberPipelineTableScrollLeft = () => {
    const scrollBody = tableWrapRef.current?.querySelector('.ant-table-body') as HTMLDivElement | null;
    if (scrollBody) {
      sessionStorage.setItem(PIPELINE_TABLE_SCROLL_LEFT_KEY, String(scrollBody.scrollLeft));
    }
  };

  const loadMeta = async () => {
    const [projectResponse, hostResponse, templateResponse, runtimeEnvironmentsResponse, notificationResponse, tagResponse, pluginResponse] = await Promise.all([
      projectsApi.list(),
      hostsApi.list(true),
      templatesApi.list(),
      runtimeEnvironmentsApi.list(),
      notificationsApi.list(),
      pipelinesApi.listTags(),
      deploymentPluginsApi.list(),
    ]);
    setProjects(projectResponse);
    setHosts(hostResponse);
    setTemplates(templateResponse);
    setRuntimeEnvironments(runtimeEnvironmentsResponse);
    setNotifications(notificationResponse);
    setAvailableTags(sortTagNames(tagResponse));
    setPlugins(pluginResponse);
  };

  const templateOptions = useMemo<PipelineTemplateOption[]>(() => {
    const saved = templates.map((item, index) => ({
      optionId: index + 1,
      source: 'saved' as const,
      name: item.name,
      templateType: item.templateType || undefined,
      pluginId: item.pluginId || undefined,
      templateId: item.id,
      variablesSchema: item.variablesSchema,
      buildScriptContent: item.buildScriptContent,
      deployScriptContent: item.deployScriptContent,
      monitorProcess: item.monitorProcess === true,
    }));
    const builtin = plugins.flatMap((plugin, pluginIndex) => (plugin.builtinTemplates || []).map((item, templateIndex) => ({
      optionId: templates.length + pluginIndex * 1000 + templateIndex + 1,
      source: 'builtin' as const,
      name: item.name,
      templateType: item.templateType || undefined,
      pluginId: plugin.descriptor.pluginId,
      builtinTemplateKey: item.templateKey || undefined,
      variablesSchema: item.variablesSchema || undefined,
      buildScriptContent: item.buildScriptContent,
      deployScriptContent: item.deployScriptContent,
      monitorProcess: item.monitorProcess === true,
    })));
    return [...saved, ...builtin];
  }, [plugins, templates]);

  const loadPipelines = async () => {
    setLoading(true);
    try {
      const result = await pipelinesApi.listPage({
        page: pagination.current,
        pageSize: pagination.pageSize,
        keyword: keyword.trim() || undefined,
        projectId: projectFilter,
        templateId: templateFilter,
        hostId: hostFilter,
        tags: tagFilter && tagFilter.length > 0 ? tagFilter : undefined,
      });
      setPipelines(result.items);
      setTotal(result.total);
    } finally {
      setLoading(false);
    }
  };

  const loadProjectBranches = async (projectId: number, currentBranch?: string) => {
    setBranchesLoading(true);
    try {
      const branches = await projectsApi.getBranches(projectId, currentBranch);
      const mergedBranches = mergeBranchOptions(branches, currentBranch);
      setBranchOptions(mergedBranches);
      return mergedBranches;
    } finally {
      setBranchesLoading(false);
    }
  };

  useEffect(() => {
    loadMeta().catch(() => message.error('加载流水线数据失败'));
  }, []);

  useEffect(() => {
    if (mode !== 'list') {
      return;
    }
    loadPipelines().catch(() => message.error('加载流水线数据失败'));
  }, [pagination.current, pagination.pageSize, keyword, projectFilter, templateFilter, hostFilter, tagFilter]);

  useEffect(() => {
    if (mode === 'list') {
      setBranchOptions([]);
      return;
    }
    if (!form.projectId) {
      setBranchOptions([]);
      return;
    }

    let cancelled = false;
    loadProjectBranches(form.projectId, form.defaultBranch)
      .then((branches) => {
        if (cancelled) {
          return;
        }
        if (!form.defaultBranch && branches.length > 0) {
          setForm((previous) => {
            const next = { ...previous, defaultBranch: branches[0] };
            initialFormSnapshotRef.current = buildPipelineFormSnapshot(next);
            return next;
          });
        }
      })
      .catch(() => {
        if (!cancelled) {
          setBranchOptions(mergeBranchOptions([], form.defaultBranch));
          message.error('加载项目分支失败');
        }
      })

    return () => {
      cancelled = true;
    };
  }, [mode, form.projectId]);

  useEffect(() => {
    const loadScopedMavenSettings = async () => {
      if (!form.mavenEnvironmentId) {
        setMavenSettings([]);
        return;
      }
      const response = await runtimeEnvironmentsApi.listMavenSettings(form.mavenEnvironmentId);
      setMavenSettings(response);
      if (form.mavenSettingsId && !response.some((item) => item.id === form.mavenSettingsId)) {
        setForm((previous) => ({ ...previous, mavenSettingsId: undefined }));
      }
    };
    loadScopedMavenSettings().catch(() => {
      setMavenSettings([]);
      message.error('加载 Maven settings 失败');
    });
  }, [form.mavenEnvironmentId]);

  useEffect(() => {
    if (mode === 'create') {
      setEditingId(undefined);
      updateFormWithSnapshot(emptyPipeline);
      setBranchOptions([]);
      setMavenSettings([]);
      setCurrentStep(0);
      return;
    }
    if ((mode === 'edit' || mode === 'view') && routePipelineId) {
      pipelinesApi.get(routePipelineId)
        .then((record) => {
          setEditingId(mode === 'edit' ? record.id : undefined);
          fillFormFromRecord(record);
          if (record.project?.id) {
            loadProjectBranches(record.project.id, record.defaultBranch || 'main')
              .catch(() => message.error('加载项目分支失败'));
          }
          if (mode === 'edit') {
            setEditingId(record.id);
          }
        })
        .catch(() => message.error('加载流水线详情失败'));
    }
  }, [mode, routePipelineId, plugins.length, templates.length]);

  const openCreate = () => {
    rememberPipelineTableScrollLeft();
    navigate('/admin/pipelines/new?fromTab=manage');
  };

  const fillFormFromRecord = (record: PipelineSummary) => {
    setEditingId(undefined);
    const currentTemplateOption = templateOptions.find((item) => matchesPipelineTemplateOption(item, record));
    const plugin = plugins.find((item) => item.descriptor.pluginId === (currentTemplateOption?.pluginId || record.templatePluginId || record.template?.pluginId)) || null;
    const nextForm = {
      name: record.name || '',
      description: record.description || '',
      tags: normalizeTags(record.tags),
      importantTags: normalizeTags(record.importantTags).slice(0, 2),
      projectId: record.project?.id || undefined,
      templateId: record.template?.id || undefined,
      templatePluginId: record.templatePluginId || undefined,
      builtinTemplateKey: record.builtinTemplateKey || undefined,
      targetHostId: record.targetHost?.id || undefined,
      targetDir: record.targetDir || '',
      defaultBranch: record.defaultBranch || 'main',
      variables: normalizeVariables(record.variables),
      javaEnvironmentId: record.javaEnvironment?.id || undefined,
      nodeEnvironmentId: record.nodeEnvironment?.id || undefined,
      mavenEnvironmentId: record.mavenEnvironment?.id || undefined,
      mavenSettingsId: record.mavenSettings?.id || undefined,
      runtimeJavaEnvironmentId: record.runtimeJavaEnvironment?.id || undefined,
      startupKeyword: record.startupKeyword || '',
      startupTimeoutSeconds: record.startupTimeoutSeconds || undefined,
      pluginConfig: extractPluginConfig(record, plugin),
      notificationIds: deriveNotificationIds(normalizeNotificationBindings(record.notificationBindings)),
    };
    updateFormWithSnapshot(nextForm);
    setBranchOptions(mergeBranchOptions([], record.defaultBranch || 'main'));
    setCurrentStep(0);
  };

  const openEdit = (record: PipelineSummary) => {
    rememberPipelineTableScrollLeft();
    navigate(`/admin/pipelines/${record.id}/edit?fromTab=manage`);
  };

  const openDuplicate = (record: PipelineSummary) => {
    setDuplicateSource(record);
    setDuplicateName(`${record.name} 副本`);
    setDuplicateModalOpen(true);
  };

  const buildPayloadFromRecord = (record: PipelineSummary, name: string): PipelinePayload => ({
    name,
    description: record.description || '',
    projectId: record.project?.id || undefined,
    templateId: record.template?.id || undefined,
    templatePluginId: record.templatePluginId || undefined,
    builtinTemplateKey: record.builtinTemplateKey || undefined,
    targetHostId: record.targetHost?.id || undefined,
    targetDir: record.targetDir || '',
    defaultBranch: record.defaultBranch || 'main',
    variables: stripContextVariables(normalizeVariables(record.variables)),
    tags: normalizeTags(record.tags),
    importantTags: normalizeTags(record.importantTags).slice(0, 2),
    javaEnvironmentId: record.javaEnvironment?.id || undefined,
    nodeEnvironmentId: record.nodeEnvironment?.id || undefined,
    mavenEnvironmentId: record.mavenEnvironment?.id || undefined,
    mavenSettingsId: record.mavenSettings?.id || undefined,
    runtimeJavaEnvironmentId: record.runtimeJavaEnvironment?.id || undefined,
    pluginConfig: normalizeVariables(record.pluginConfig),
    startupKeyword: record.startupKeyword || '',
    startupTimeoutSeconds: record.startupTimeoutSeconds || 30,
    notificationBindings: normalizeNotificationBindings(record.notificationBindings),
  });

  const duplicatePipeline = async () => {
    if (!duplicateSource) {
      return;
    }
    const name = duplicateName.trim();
    if (!name) {
      message.error('请输入复制后的流水线名称');
      return;
    }
    setDuplicating(true);
    try {
      await pipelinesApi.create(buildPayloadFromRecord(duplicateSource, name));
      setDuplicateModalOpen(false);
      setDuplicateSource(undefined);
      setDuplicateName('');
      await Promise.all([loadMeta(), loadPipelines()]);
      message.success('流水线已复制');
    } finally {
      setDuplicating(false);
    }
  };

  const savePipeline = async () => {
    if (!form.name.trim()) {
      setCurrentStep(0);
      message.error('请填写流水线名称');
      return;
    }
    if (!form.projectId) {
      setCurrentStep(0);
      message.error('请选择项目');
      return;
    }
    if (!selectedTemplate) {
      setCurrentStep(0);
      message.error('请选择模板');
      return;
    }
    if (!form.defaultBranch.trim()) {
      setCurrentStep(0);
      message.error('请选择默认分支');
      return;
    }
    const missingBuildRuntime = buildRuntimeFieldConfigs.find((item) => requiredEnvironmentTypes.includes(item.type) && !form[item.formKey]);
    if (missingBuildRuntime) {
      setCurrentStep(1);
      message.error(`请选择本机构建 ${missingBuildRuntime.label} 环境`);
      return;
    }
    if (!form.targetHostId) {
      setCurrentStep(2);
      message.error('请选择目标主机');
      return;
    }
    if (!form.targetDir.trim()) {
      setCurrentStep(2);
      message.error('请填写部署目录');
      return;
    }
    if (requiredRuntimeEnvironmentTypes.includes('JAVA') && !form.runtimeJavaEnvironmentId) {
      setCurrentStep(2);
      message.error('请选择目标主机运行组件环境');
      return;
    }
    const missingVariable = selectedTemplateVariables.find((item) => item.required && !form.variables[item.name]?.trim());
    if (missingVariable) {
      setCurrentStep(stepItems.findIndex((item) => item.key === 'variables'));
      message.error(`请填写变量：${missingVariable.label || missingVariable.name}`);
      return;
    }
    const payloadVariables = removePluginConfigVariables(
      stripContextVariables(form.variables || {}),
      selectedPlugin,
    );
    const payload: PipelinePayload = {
      name: form.name,
      description: form.description,
      projectId: form.projectId,
      templateId: form.templateId,
      templatePluginId: form.templatePluginId,
      builtinTemplateKey: form.builtinTemplateKey,
      targetHostId: form.targetHostId,
      targetDir: form.targetDir,
      defaultBranch: form.defaultBranch,
      variables: payloadVariables,
      tags: form.tags || [],
      importantTags: form.importantTags || [],
      javaEnvironmentId: form.javaEnvironmentId,
      nodeEnvironmentId: form.nodeEnvironmentId,
      mavenEnvironmentId: form.mavenEnvironmentId,
      mavenSettingsId: form.mavenSettingsId,
      runtimeJavaEnvironmentId: form.runtimeJavaEnvironmentId,
      pluginConfig: form.pluginConfig || {},
      startupKeyword: serviceMonitorEnabled ? form.startupKeyword : '',
      startupTimeoutSeconds: serviceMonitorEnabled ? form.startupTimeoutSeconds : undefined,
      notificationBindings: form.notificationIds
          .map((notificationId) => {
            const notification = notifications.find((item) => item.id === notificationId);
            const eventType = notification?.eventType;
            if (!eventType) {
              return null;
            }
            return { notificationId, eventType };
          })
          .filter((item): item is NotificationBinding => Boolean(item)),
    };
    (selectedPlugin?.pipelineFormSchema.sections || []).forEach((section) => {
      section.fields.forEach((field) => {
        const binding = resolveFieldBinding(field);
        const rawValue = form.pluginConfig[field.key];
        const value = rawValue == null ? '' : rawValue;
        if (binding.scope === 'PIPELINE_VARIABLE') {
          if (value) {
            payloadVariables[binding.key] = value;
          } else {
            delete payloadVariables[binding.key];
          }
          return;
        }
      });
    });
    payload.variables = payloadVariables;
    if (editingId) {
      await pipelinesApi.update(editingId, payload);
    } else {
      await pipelinesApi.create(payload);
    }
    setForm(emptyPipeline);
    setEditingId(undefined);
    setCurrentStep(0);
    if (mode === 'list') {
      await Promise.all([loadMeta(), loadPipelines()]);
    } else {
      navigate(workspaceBackPath);
    }
    message.success(editingId ? '流水线已更新' : '流水线已创建');
  };

  const removePipeline = async (id: number) => {
    await pipelinesApi.remove(id);
    await Promise.all([loadMeta(), loadPipelines()]);
    message.success('流水线已删除');
  };

  const selectedTemplate = useMemo(
    () => templateOptions.find((item) => item.source === 'saved'
      ? item.templateId === form.templateId
      : item.pluginId === form.templatePluginId && item.builtinTemplateKey === form.builtinTemplateKey),
    [form.builtinTemplateKey, form.templateId, form.templatePluginId, templateOptions],
  );
  const selectedPlugin = useMemo(
    () => plugins.find((item) => item.descriptor.pluginId === selectedTemplate?.pluginId) || null,
    [plugins, selectedTemplate],
  );
  const serviceMonitorEnabled = selectedTemplate?.monitorProcess === true;
  const localHost = useMemo(
    () => hosts.find((item) => item.builtIn) || hosts.find((item) => item.type === 'LOCAL'),
    [hosts],
  );

const selectedTemplateVariables = useMemo(
    () => resolveTemplateVariables(selectedTemplate, selectedPlugin),
    [selectedPlugin, selectedTemplate],
  );
  const stepItems = useMemo(() => {
    const items = [
      { key: 'basic', title: '基础信息', description: '名称、项目、模板、分支' },
      { key: 'build', title: '构建环境', description: '选择本机构建用环境' },
      { key: 'target', title: '目标主机', description: '选择发布到哪台主机' },
    ];
    if ((selectedPlugin?.pipelineFormSchema.sections || []).length > 0) {
      items.push({ key: 'runtime', title: '运行配置', description: '按插件要求填写运行时与服务配置' });
    }
    items.push(
      { key: 'variables', title: '变量', description: '按阶段填写变量值' },
      { key: 'notifications', title: '通知配置', description: '绑定要使用的通知配置' },
    );
    return items;
  }, [selectedPlugin]);
  const currentStepKey = stepItems[currentStep]?.key;

  const requiredEnvironmentTypes = useMemo(
    () => selectedPlugin?.runtimeRequirement.buildRuntimeTypes || [],
    [selectedPlugin],
  );
  const requiredRuntimeEnvironmentTypes = useMemo(
    () => selectedPlugin?.runtimeRequirement.targetRuntimeTypes || [],
    [selectedPlugin],
  );
  const pipelineServiceFields = useMemo(
    () => (selectedPlugin?.pipelineFormSchema.sections || []).find((section) => section.key === 'service')?.fields || [],
    [selectedPlugin],
  );
  const pipelineRuntimeFields = useMemo(
    () => (selectedPlugin?.pipelineFormSchema.sections || []).find((section) => section.key === 'runtime')?.fields || [],
    [selectedPlugin],
  );

  const tableData = useMemo(() => pipelines.map((item) => ({
    ...item,
    resolvedTemplateOption: templateOptions.find((option) => matchesPipelineTemplateOption(option, item)),
    parsedVariables: normalizeVariables(item.variables),
    parsedTemplateVariables: resolveTemplateVariables(
      templateOptions.find((option) => matchesPipelineTemplateOption(option, item)),
      plugins.find((plugin) => plugin.descriptor.pluginId === (
        templateOptions.find((option) => matchesPipelineTemplateOption(option, item))?.pluginId
        || item.templatePluginId
        || item.template?.pluginId
      )),
    ),
    parsedTags: normalizeTags(item.tags),
  })), [pipelines, plugins, templateOptions]);

  useEffect(() => {
    if (mode !== 'list' || loading) {
      return;
    }
    const scrollLeft = Number(sessionStorage.getItem(PIPELINE_TABLE_SCROLL_LEFT_KEY) || 0);
    if (!scrollLeft) {
      return;
    }
    window.setTimeout(() => {
      const scrollBody = tableWrapRef.current?.querySelector('.ant-table-body') as HTMLDivElement | null;
      if (scrollBody) {
        scrollBody.scrollLeft = scrollLeft;
      }
    }, 0);
  }, [mode, loading, tableData.length]);

  const javaOptions = useMemo(
    () => getLocalBuildEnvironmentOptions(runtimeEnvironments, 'JAVA', localHost?.id),
    [runtimeEnvironments, localHost],
  );
  const nodeOptions = useMemo(
    () => getLocalBuildEnvironmentOptions(runtimeEnvironments, 'NODE', localHost?.id),
    [runtimeEnvironments, localHost],
  );
  const mavenOptions = useMemo(
    () => getLocalBuildEnvironmentOptions(runtimeEnvironments, 'MAVEN', localHost?.id),
    [runtimeEnvironments, localHost],
  );
  const runtimeJavaOptions = useMemo(
    () => runtimeEnvironments
      .filter((item) => item.type === 'JAVA' && item.enabled !== false && item.host?.id === form.targetHostId)
      .sort(sortEnvironmentOptions)
      .map((item) => ({ label: `${item.name}${item.version ? ` (${item.version})` : ''}`, value: item.id })),
    [runtimeEnvironments, form.targetHostId],
  );
  const buildEnvironmentOptionsMap = useMemo(() => ({
    JAVA: javaOptions,
    NODE: nodeOptions,
    MAVEN: mavenOptions,
  }), [javaOptions, nodeOptions, mavenOptions]);
  const mavenSettingsOptions = useMemo(
    () => mavenSettings
      .filter((item) => item.enabled)
      .map((item) => ({ label: item.isDefault ? `${item.name}（默认）` : item.name, value: item.id })),
    [mavenSettings],
  );

  useEffect(() => {
    if (mode !== 'create' && mode !== 'edit') {
      return;
    }
    setForm((previous) => {
      let next = previous;
      buildRuntimeFieldConfigs.forEach((item) => {
        if (!requiredEnvironmentTypes.includes(item.type)) {
          return;
        }
        const options = buildEnvironmentOptionsMap[item.type];
        const currentValue = previous[item.formKey];
        if (includesOptionValue(options, currentValue)) {
          return;
        }
        const singleValue = pickSingleOptionValue(options);
        if (singleValue == null && currentValue == null) {
          return;
        }
        next = {
          ...next,
          [item.formKey]: singleValue,
          ...(item.type === 'MAVEN' ? { mavenSettingsId: undefined } : {}),
        };
      });
      if (next !== previous && initialFormSnapshotRef.current === buildPipelineFormSnapshot(previous)) {
        initialFormSnapshotRef.current = buildPipelineFormSnapshot(next);
      }
      return next;
    });
  }, [buildEnvironmentOptionsMap, mode, requiredEnvironmentTypes]);

  useEffect(() => {
    if (mode !== 'create' && mode !== 'edit' || !requiredRuntimeEnvironmentTypes.includes('JAVA')) {
      return;
    }
    setForm((previous) => {
      if (includesOptionValue(runtimeJavaOptions, previous.runtimeJavaEnvironmentId)) {
        return previous;
      }
      const singleValue = pickSingleOptionValue(runtimeJavaOptions);
      if (singleValue == null && previous.runtimeJavaEnvironmentId == null) {
        return previous;
      }
      const next = {
        ...previous,
        runtimeJavaEnvironmentId: singleValue as number | undefined,
      };
      if (initialFormSnapshotRef.current === buildPipelineFormSnapshot(previous)) {
        initialFormSnapshotRef.current = buildPipelineFormSnapshot(next);
      }
      return next;
    });
  }, [mode, requiredRuntimeEnvironmentTypes, runtimeJavaOptions]);

  useEffect(() => {
    if (mode !== 'create' && mode !== 'edit' || !requiredEnvironmentTypes.includes('MAVEN') || !form.mavenEnvironmentId) {
      return;
    }
    setForm((previous) => {
      if (includesOptionValue(mavenSettingsOptions, previous.mavenSettingsId)) {
        return previous;
      }
      const singleValue = pickSingleOptionValue(mavenSettingsOptions);
      if (singleValue == null && previous.mavenSettingsId == null) {
        return previous;
      }
      const next = {
        ...previous,
        mavenSettingsId: singleValue as number | undefined,
      };
      if (initialFormSnapshotRef.current === buildPipelineFormSnapshot(previous)) {
        initialFormSnapshotRef.current = buildPipelineFormSnapshot(next);
      }
      return next;
    });
  }, [form.mavenEnvironmentId, mavenSettingsOptions, mode, requiredEnvironmentTypes]);

  const renderPluginPipelineField = (field: PluginFormFieldSummary) => {
    const value = form.pluginConfig[field.key] ?? '';
    const updateValue = (nextValue: string) => setForm({
      ...form,
      pluginConfig: {
        ...form.pluginConfig,
        [field.key]: nextValue,
      },
    });
    if (field.type === 'CODE') {
      const lowerKey = field.key.toLowerCase();
      const lowerLabel = field.label.toLowerCase();
      const lowerHelpText = (field.helpText || '').toLowerCase();
      const editorLanguage = lowerKey.includes('yaml') || lowerKey.includes('yml') || lowerLabel.includes('yaml') || lowerLabel.includes('yml')
        ? 'yaml'
        : lowerKey.includes('script') || lowerKey.includes('command') || lowerHelpText.includes('shell')
          ? 'shell'
          : 'text';
      return (
        <CodeEditor
          rows={10}
          language={editorLanguage}
          value={value}
          onChange={updateValue}
          placeholder={field.placeholder || undefined}
        />
      );
    }
    if (field.type === 'TEXTAREA') {
      return (
        <Input.TextArea
          rows={3}
          value={value}
          onChange={(event) => updateValue(event.target.value)}
          placeholder={field.placeholder || undefined}
        />
      );
    }
    if (field.type === 'SELECT') {
      return (
        <Select
          allowClear={!field.required}
          value={value || undefined}
          options={(field.options || []).map((item) => ({ label: item.label, value: item.value }))}
          onChange={(nextValue) => updateValue(nextValue || '')}
          placeholder={field.placeholder || undefined}
        />
      );
    }
    if (field.type === 'SWITCH') {
      return (
        <Select
          value={value || 'false'}
          options={[
            { label: '开启', value: 'true' },
            { label: '关闭', value: 'false' },
          ]}
          onChange={(nextValue) => updateValue(nextValue)}
        />
      );
    }
    return (
      <Input
        value={value}
        onChange={(event) => updateValue(event.target.value)}
        placeholder={field.placeholder || undefined}
      />
    );
  };

  const resolvePluginFieldEditorLanguage = (field: PluginFormFieldSummary) => {
    const lowerKey = field.key.toLowerCase();
    const lowerLabel = field.label.toLowerCase();
    const lowerHelpText = (field.helpText || '').toLowerCase();
    if (lowerKey.includes('yaml') || lowerKey.includes('yml') || lowerLabel.includes('yaml') || lowerLabel.includes('yml')) {
      return 'yaml';
    }
    if (lowerKey.includes('script') || lowerKey.includes('command') || lowerHelpText.includes('shell')) {
      return 'shell';
    }
    return 'text';
  };

  const renderReadonlyPluginConfigValue = (field: PluginFormFieldSummary, value: string) => {
    if (field.type === 'CODE') {
      return (
        <CodeEditor
          rows={Math.max(8, (value || '').split('\n').length)}
          language={resolvePluginFieldEditorLanguage(field)}
          value={value || ''}
          onChange={() => {}}
          readOnly
        />
      );
    }
    return <div className="runtime-config-value">{value || '-'}</div>;
  };

  const updatePipelineTags = (nextTags: string[]) => {
    setForm({
      ...form,
      tags: nextTags,
    });
  };

  const updateImportantTags = (nextImportantTags: string[]) => {
    setForm({
      ...form,
      importantTags: nextImportantTags.slice(0, 2),
    });
  };

  if (mode === 'view') {
    const pluginConfigItems = selectedPlugin
      ? selectedPlugin.pipelineFormSchema.sections
        .flatMap((section) => section.fields.map((field) => ({
          sectionTitle: section.title,
          field,
          value: form.pluginConfig[field.key] || '',
        })))
        .filter(Boolean)
      : [];
    return (
      <>
        <PageHeaderBar
          title="查看流水线"
          description={form.name || '-'}
          extra={<Button onClick={() => navigate(workspaceBackPath)}>返回</Button>}
        />
        <div className="app-page-scroll">
          <div className="mx-auto max-w-6xl space-y-4">
            <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
            <Card title="基础信息" className="app-card">
              <div className="grid grid-cols-1 gap-3 text-sm md:grid-cols-2">
                <div><span className="text-slate-500">名称：</span>{form.name || '-'}</div>
                <div><span className="text-slate-500">默认分支：</span>{form.defaultBranch || '-'}</div>
                <div><span className="text-slate-500">项目：</span>{projects.find((item) => item.id === form.projectId)?.name || '-'}</div>
                <div><span className="text-slate-500">模板：</span>{selectedTemplate?.name || '-'}</div>
                <div className="md:col-span-2"><span className="text-slate-500">描述：</span>{form.description || '-'}</div>
              </div>
            </Card>
            <Card title="目标主机" className="app-card">
              <div className="grid grid-cols-1 gap-3 text-sm md:grid-cols-2">
                <div><span className="text-slate-500">目标主机：</span>{hosts.find((item) => item.id === form.targetHostId)?.name || '-'}</div>
                <div><span className="text-slate-500">部署目录：</span>{form.targetDir || '-'}</div>
                <div><span className="text-slate-500">运行组件：</span>{runtimeEnvironments.find((item) => item.id === form.runtimeJavaEnvironmentId)?.name || '-'}</div>
                <div><span className="text-slate-500">启动超时：</span>{form.startupTimeoutSeconds || '-'} 秒</div>
              </div>
            </Card>
            </div>
            <Card title="构建环境" className="app-card">
              <div className="grid grid-cols-1 gap-3 text-sm md:grid-cols-3">
                {buildRuntimeFieldConfigs.map((item) => (
                  <div key={item.type}>
                    <span className="text-slate-500">{item.label}：</span>
                    {runtimeEnvironments.find((runtime) => runtime.id === form[item.formKey])?.name || '-'}
                  </div>
                ))}
              </div>
            </Card>
            <Card title="运行配置" className="app-card">
              {pluginConfigItems.length ? (
                <div className="runtime-config-list">
                  {pluginConfigItems.map((item: any) => (
                    <div key={item.field.key} className={`runtime-config-item ${item.field.type === 'CODE' ? 'runtime-config-item--code' : ''}`}>
                      <div className="runtime-config-item__header">
                        <span className="runtime-config-item__label">{item.field.label}</span>
                        {item.sectionTitle ? <span className="runtime-config-item__section">{item.sectionTitle}</span> : null}
                      </div>
                      {renderReadonlyPluginConfigValue(item.field, item.value)}
                    </div>
                  ))}
                </div>
              ) : <div className="text-sm text-slate-500">当前流水线没有额外运行配置。</div>}
            </Card>
            <Card title="变量" className="app-card">
              {selectedTemplateVariables.length ? (
                <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
                  {selectedTemplateVariables.map((item) => (
                    <div key={item.name} className="rounded-lg border border-slate-200 px-3 py-2 text-sm">
                      <span className="text-slate-500">{item.label || item.name}：</span>{form.variables[item.name] || '-'}
                    </div>
                  ))}
                </div>
              ) : <div className="text-sm text-slate-500">当前模板没有需要流水线填写的变量。</div>}
            </Card>
          </div>
        </div>
      </>
    );
  }

  if (mode === 'create' || mode === 'edit') {
    return (
      <>
        <PageHeaderBar
          title={mode === 'edit' ? '编辑流水线' : '新建流水线'}
          description="配置项目、模板、环境、运行参数和变量。"
          extra={(
            <Space>
              <Button danger={isFormChanged()} onClick={cancelEditing}>{isFormChanged() ? '取消' : '返回'}</Button>
              <Button onClick={() => updateFormWithSnapshot(JSON.parse(initialFormSnapshotRef.current))}>重置</Button>
              <Button type="primary" onClick={() => savePipeline().catch(() => undefined)}>{mode === 'edit' ? '保存' : '创建'}</Button>
            </Space>
          )}
        />
        <div className="app-page-scroll min-h-0">
          <div className="mx-auto flex h-full min-h-0 max-w-5xl flex-col">
            <div className="sticky top-0 z-20 pb-3">
              <Card className="app-card">
              <Steps size="small" current={currentStep} onChange={setCurrentStep} items={stepItems} />
            </Card>
            </div>
            <div className="min-h-0 flex-1 overflow-auto pb-4">
            {currentStepKey === 'basic' ? <Card title="基础信息" className="app-card">
              <Form layout="vertical">
                <Form.Item label="流水线名称" required>
                  <Input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} placeholder="例如：fusb-app" />
                </Form.Item>
                <Form.Item label="描述">
                  <Input.TextArea rows={3} value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} placeholder="可选。说明这条流水线的用途。" />
                </Form.Item>
                <Form.Item label="标签">
                  <Select mode="tags" value={form.tags} placeholder="输入后回车，可用于业务线、端别、环境等筛选" onChange={updatePipelineTags} />
                </Form.Item>
                <Form.Item label="重要标签">
                  <Select
                    mode="tags"
                    value={form.importantTags}
                    options={form.tags.map((tag) => ({ label: tag, value: tag }))}
                    placeholder="最多 2 个，会显示在标题前"
                    onChange={(value) => updateImportantTags(value.slice(0, 2))}
                  />
                </Form.Item>
                <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
                  <Form.Item label="项目" required>
                    <Select value={form.projectId} options={projects.map((item) => ({ label: item.name, value: item.id }))} onChange={(value) => setForm({ ...form, projectId: value, defaultBranch: '' })} placeholder="请选择项目" />
                  </Form.Item>
                  <Form.Item label="模板" required>
                    <Select
                      value={selectedTemplate?.optionId}
                      options={templateOptions.map((item) => ({ label: renderTemplateOptionLabel(item), value: item.optionId, title: item.name }))}
                      optionLabelProp="title"
                      placeholder="请选择部署模板"
                      onChange={(value) => {
                        const option = templateOptions.find((item) => item.optionId === value);
                        const optionPlugin = plugins.find((item) => item.descriptor.pluginId === option?.pluginId) || null;
                        setForm({
                          ...form,
                          templateId: option?.templateId,
                          templatePluginId: option?.pluginId,
                          builtinTemplateKey: option?.builtinTemplateKey,
                          variables: retainMatchedTemplateVariables(form.variables, option, optionPlugin),
                          javaEnvironmentId: undefined,
                          nodeEnvironmentId: undefined,
                          mavenEnvironmentId: undefined,
                          mavenSettingsId: undefined,
                          runtimeJavaEnvironmentId: undefined,
                          pluginConfig: {},
                          notificationIds: [],
                        });
                      }}
                    />
                  </Form.Item>
                  <Form.Item label="默认分支" required>
                    <Select showSearch loading={branchesLoading} disabled={!form.projectId} value={form.defaultBranch || undefined} placeholder={form.projectId ? '请选择默认分支' : '请先选择项目'} options={branchOptions.map((branch) => ({ label: branch, value: branch }))} onChange={(value) => setForm({ ...form, defaultBranch: value })} />
                  </Form.Item>
                </div>
              </Form>
            </Card> : null}
            {currentStepKey === 'build' ? <Card title="构建环境" className="app-card">
              <Form layout="vertical">
                <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
                  {buildRuntimeFieldConfigs.map((item) => requiredEnvironmentTypes.includes(item.type) ? (
                    <Form.Item key={item.type} label={`本机构建 ${item.label} 环境`} required>
                      <Select
                        allowClear
                        value={form[item.formKey]}
                        options={buildEnvironmentOptionsMap[item.type]}
                        onChange={(value) => setForm({ ...form, [item.formKey]: value, ...(item.type === 'MAVEN' ? { mavenSettingsId: undefined } : {}) })}
                        placeholder={`请选择本机构建用的 ${item.label} 环境`}
                      />
                    </Form.Item>
                  ) : null)}
                </div>
                {requiredEnvironmentTypes.includes('MAVEN') ? <Form.Item label="构建 Maven Settings"><Select allowClear value={form.mavenSettingsId} options={mavenSettingsOptions} onChange={(value) => setForm({ ...form, mavenSettingsId: value })} placeholder={form.mavenEnvironmentId ? '可选。选择后构建时会自动对 mvn 注入 -s settings.xml' : '请先选择 Maven 环境'} disabled={!form.mavenEnvironmentId} /></Form.Item> : null}
              </Form>
            </Card> : null}
            {currentStepKey === 'target' ? <Card title="目标主机" className="app-card">
              <Form layout="vertical">
                <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
                  <Form.Item label="目标主机" required><Select value={form.targetHostId} options={hosts.map((item) => ({ label: item.name, value: item.id }))} placeholder="请选择目标主机" onChange={(value) => setForm({ ...form, targetHostId: value, runtimeJavaEnvironmentId: undefined })} /></Form.Item>
                  <Form.Item label="部署目录" required extra="脚本中可直接使用 $TARGET_DIR。"><Input value={form.targetDir} onChange={(event) => setForm({ ...form, targetDir: event.target.value })} placeholder="例如：/opt/apps/demo" /></Form.Item>
                  {requiredRuntimeEnvironmentTypes.includes('JAVA') ? <Form.Item label="目标主机运行组件" required><Select allowClear value={form.runtimeJavaEnvironmentId} options={runtimeJavaOptions} onChange={(value) => setForm({ ...form, runtimeJavaEnvironmentId: value })} placeholder="请选择目标主机运行应用时使用的组件环境" /></Form.Item> : null}
                </div>
                {serviceMonitorEnabled ? (
                  <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                    <Form.Item label="启动关键字" extra="填写后会读取运行日志，命中该内容即判定启动成功。"><Input.TextArea autoSize={{ minRows: 2, maxRows: 4 }} value={form.startupKeyword} onChange={(event) => setForm({ ...form, startupKeyword: event.target.value })} placeholder="例如：Started / 服务启动成功" /></Form.Item>
                    <Form.Item label="启动超时（秒）"><Input type="number" min={5} value={form.startupTimeoutSeconds} onChange={(event) => setForm({ ...form, startupTimeoutSeconds: event.target.value ? Number(event.target.value) : undefined })} placeholder="例如：30" /></Form.Item>
                  </div>
                ) : null}
              </Form>
            </Card> : null}
            {currentStepKey === 'runtime' && (pipelineRuntimeFields.length || pipelineServiceFields.length) ? (
              <Card title="运行配置" className="app-card">
                <Form layout="vertical">
                  {[...pipelineServiceFields, ...pipelineRuntimeFields].map((field) => (
                    <Form.Item key={field.key} label={field.label} extra={field.helpText || undefined}>{renderPluginPipelineField(field)}</Form.Item>
                  ))}
                </Form>
              </Card>
            ) : null}
            {currentStepKey === 'variables' ? <Card title="变量" className="app-card">
              <PipelineVariablesEditor variables={selectedTemplateVariables} values={form.variables} onChange={(value) => setForm({ ...form, variables: value })} />
            </Card> : null}
            {currentStepKey === 'notifications' ? <Card title="通知配置" className="app-card">
              <Select mode="multiple" allowClear value={form.notificationIds} placeholder="选择这条流水线要绑定的通知配置" options={notifications.filter((item) => item.enabled).map((item) => ({ label: item.name, value: item.id }))} onChange={(value) => setForm({ ...form, notificationIds: value })} className="w-full" />
            </Card> : null}
            </div>
            {stepItems.length > 1 ? (
              <div className="shrink-0 flex justify-end gap-2 border-t border-slate-200/70 px-4 py-3 backdrop-blur-sm">
                {currentStep > 0 ? <Button onClick={() => setCurrentStep((value) => value - 1)}>上一步</Button> : null}
                {currentStep < stepItems.length - 1 ? <Button onClick={() => setCurrentStep((value) => value + 1)}>下一步</Button> : null}
              </div>
            ) : null}
          </div>
        </div>
      </>
    );
  }

  return (
    <>
      <PageHeaderBar
        title="流水线管理"
        description="配置用户可直接部署的流水线，绑定项目、模板、变量和运行环境。构建在本机完成，目标主机负责接收产物并发布。"
        extra={(
          <Space>
            <Button onClick={() => loadPipelines().catch(() => message.error('加载流水线数据失败'))}>刷新</Button>
            <Button type="primary" onClick={openCreate}>新建流水线</Button>
          </Space>
        )}
      />
      <div className="app-page-scroll">
      <Card className="app-card" ref={tableWrapRef}>
        <div className="app-filter-grid">
          <Input
            value={keyword}
            placeholder="搜索流水线名称 / 描述 / 默认分支"
            onChange={(event) => {
              setKeyword(event.target.value);
              setPagination((previous) => ({ ...previous, current: 1 }));
            }}
          />
          <Select
            showSearch
            optionFilterProp="label"
            allowClear
            value={projectFilter}
            placeholder="筛选项目"
            options={projects.map((item) => ({ label: item.name, value: item.id }))}
            onChange={(value) => {
              setProjectFilter(value);
              setPagination((previous) => ({ ...previous, current: 1 }));
            }}
          />
          <Select
            showSearch
            optionFilterProp="label"
            allowClear
            value={templateFilter}
            placeholder="筛选模板"
            options={templates.map((item) => ({ label: item.name, value: item.id }))}
            onChange={(value) => {
              setTemplateFilter(value);
              setPagination((previous) => ({ ...previous, current: 1 }));
            }}
          />
          <Select
            showSearch
            optionFilterProp="label"
            allowClear
            value={hostFilter}
            placeholder="筛选目标主机"
            options={hosts.map((item) => ({ label: item.name, value: item.id }))}
            onChange={(value) => {
              setHostFilter(value);
              setPagination((previous) => ({ ...previous, current: 1 }));
            }}
          />
          <div className="flex items-center">
            <Button
              onClick={() => {
                setKeyword('');
                setProjectFilter(undefined);
                setTemplateFilter(undefined);
                setHostFilter(undefined);
                setTagFilter(undefined);
                setPagination((previous) => ({ ...previous, current: 1 }));
              }}
            >
              重置条件
            </Button>
          </div>
        </div>
        {availableTags.length > 0 ? (
          <div className="mb-4 flex flex-wrap gap-2">
            {availableTags.map((tag) => {
              const active = Boolean(tagFilter?.includes(tag));
              return (
                <Tag
                  key={tag}
                  style={active ? stableTagStyle(tag) : undefined}
                  className={`${active ? 'app-color-tag' : 'app-muted-tag'} cursor-pointer select-none !border-0 !px-3 !py-1`}
                  onClick={() => {
                    setTagFilter((previous) => {
                      const next = previous?.includes(tag)
                        ? previous.filter((item) => item !== tag)
                        : [...(previous || []), tag];
                      setPagination((current) => ({ ...current, current: 1 }));
                      return next.length > 0 ? next : undefined;
                    });
                  }}
                >
                  {tag}
                </Tag>
              );
            })}
          </div>
        ) : null}
        <Table
          rowKey="id"
          loading={loading}
          scroll={{ x: 2200 }}
          dataSource={tableData}
          locale={{ emptyText: <EmptyPane description="还没有流水线，点击右上角先把项目和模板组合起来。" /> }}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`,
            onChange: (current, pageSize) => setPagination({ current, pageSize }),
          }}
          columns={[
              {
                title: '名称',
                width: 220,
                render: (_, row) => (
                  <div className="flex items-center gap-0.5">
                    <div className="scale-[0.82] origin-left">
                      <PipelineIcon type={row.resolvedTemplateOption?.templateType || row.templateTypeSnapshot || row.template?.templateType} />
                    </div>
                    <span>{row.name}</span>
                  </div>
                ),
              },
              { title: '项目', width: 160, render: (_, row) => row.project?.name },
              { title: '目标主机', width: 160, render: (_, row) => row.targetHost?.name || '-' },
              { title: '模板', width: 220, render: (_, row) => row.resolvedTemplateOption?.name || row.templateNameSnapshot || row.template?.name },
              {
                title: '描述',
                dataIndex: 'description',
                width: 280,
                render: (value) => value ? (
                  <div className="py-1 text-sm leading-6 whitespace-normal break-words text-slate-600 line-clamp-2" title={value}>
                    {value}
                  </div>
                ) : '-',
              },
              {
                title: '标签',
                width: 180,
                render: (_, row) => row.parsedTags.length > 0
                  ? (
                    <Space wrap>
                      {sortTagNames(row.parsedTags).map((tag: string) => (
                        <Tag
                          key={tag}
                          style={stableTagStyle(tag)}
                          className="app-color-tag !border-0"
                        >
                          {tag}
                        </Tag>
                      ))}
                    </Space>
                  )
                  : '-',
              },
              { title: '默认分支', dataIndex: 'defaultBranch', width: 120 },
              {
                title: '变量',
                width: 520,
                onCell: () => ({ style: { overflow: 'hidden' } }),
                render: (_, row) => (
                  <div className="w-full overflow-hidden">
                    <Space wrap>
                      {row.parsedTemplateVariables.length === 0 ? <span className="text-slate-400">无</span> : null}
                      {sortByPhase(row.parsedTemplateVariables).map((item) => (
                        <Tag
                          key={item.name}
                          className={`app-phase-tag max-w-full ${phaseClassName(item.phase)} !border-0 !pl-0 !py-0`}
                        >
                          <span
                            className={`app-phase-tag__label ${phaseLabelClassName(item.phase)} mr-1.5 inline-flex items-center rounded-md px-2 py-1 text-xs font-normal text-white`}
                          >
                            <span>[{PHASE_LABEL_MAP[item.phase || 'shared'] || '共用'}]</span>
                            <span className="ml-1">{item.label || item.name}</span>
                          </span>
                          <button
                            type="button"
                            className="cursor-pointer rounded-sm bg-transparent px-1 py-0 text-slate-700 transition-colors hover:text-slate-900"
                            onClick={async () => {
                              const value = row.parsedVariables[item.name] || '-';
                              try {
                                await copyText(value);
                                message.success(`已复制：${value}`);
                              } catch {
                                message.error('复制失败');
                              }
                            }}
                          >
                            {row.parsedVariables[item.name] || '-'}
                          </button>
                        </Tag>
                      ))}
                    </Space>
                  </div>
                ),
              },
              {
                title: '环境版本',
                width: 260,
                render: (_, row) => {
                  const items = [
                    row.javaEnvironment ? `构建组件 ${row.javaEnvironment.type}：${row.javaEnvironment.name}` : null,
                    row.nodeEnvironment ? `构建组件 ${row.nodeEnvironment.type}：${row.nodeEnvironment.name}` : null,
                    row.mavenEnvironment ? `构建组件 ${row.mavenEnvironment.type}：${row.mavenEnvironment.name}` : null,
                    row.runtimeJavaEnvironment ? `运行组件 ${row.runtimeJavaEnvironment.type}：${row.runtimeJavaEnvironment.name}` : null,
                  ].filter(Boolean);
                  return items.length > 0 ? (
                    <div className="space-y-2 py-1 text-sm leading-6 text-slate-600">
                      {items.map((item) => (
                        <div key={item} className="truncate" title={item}>
                          {item}
                        </div>
                      ))}
                    </div>
                  ) : '-';
                },
              },
              {
                title: '操作',
                width: 146,
                fixed: 'right',
                render: (_, record) => (
                  <Space size={6} className="w-full justify-center whitespace-nowrap px-1">
                    <Button size="small" onClick={() => {
                      rememberPipelineTableScrollLeft();
                      navigate(`/admin/pipelines/${record.id}?fromTab=manage`);
                    }}>查看</Button>
                    <Button size="small" onClick={() => openEdit(record)}>编辑</Button>
                    <Dropdown
                      trigger={['click']}
                      menu={{
                        items: [
                          { key: 'duplicate', label: '复制' },
                          { key: 'delete', label: <span className="text-red-500">删除</span> },
                        ],
                        onClick: ({ key }) => {
                          if (key === 'duplicate') {
                            openDuplicate(record);
                          }
                          if (key === 'delete') {
                            Modal.confirm({
                              title: '确认删除这条流水线吗？',
                              okText: '确认',
                              cancelText: '取消',
                              okButtonProps: { danger: true },
                              onOk: () => removePipeline(record.id),
                            });
                          }
                        },
                      }}
                    >
                      <Button size="small" icon={<EllipsisOutlined />} />
                    </Dropdown>
                  </Space>
                ),
              },
          ]}
        />
      </Card>
      </div>
      <Modal
        title="复制流水线"
        open={duplicateModalOpen}
        okText="确认复制"
        cancelText="取消"
        confirmLoading={duplicating}
        onCancel={() => {
          setDuplicateModalOpen(false);
          setDuplicateSource(undefined);
          setDuplicateName('');
        }}
        onOk={() => duplicatePipeline().catch(() => message.error('复制流水线失败'))}
        destroyOnHidden
      >
        <Form layout="vertical">
          <Form.Item label="新流水线名称">
            <Input
              value={duplicateName}
              onChange={(event) => setDuplicateName(event.target.value)}
              placeholder="请输入复制后的流水线名称"
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
