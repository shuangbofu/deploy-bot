import { useEffect, useMemo, useRef, useState } from 'react';
import { Button, Card, Collapse, DatePicker, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Switch, Table, Tabs, TimePicker, Typography, message } from 'antd';
import dayjs from 'dayjs';
import { apiTokensApi } from '../../api/apiTokens';
import { notificationTemplatesApi } from '../../api/notificationTemplates';
import { notificationWebhookConfigsApi } from '../../api/notificationWebhookConfigs';
import { pipelinesApi } from '../../api/pipelines';
import { projectsApi } from '../../api/projects';
import { systemSettingsApi } from '../../api/systemSettings';
import { usersApi } from '../../api/users';
import type {
  ApiTokenCreatePayload,
  ApiTokenScope,
  ApiTokenSummary,
  DeploymentRestrictionPolicyConfig,
  DeploymentRestrictionScopeType,
  DeploymentRestrictionWeeklyWindow,
  NotificationTemplatePayload,
  NotificationTemplateSummary,
  NotificationWebhookConfigPayload,
  NotificationWebhookConfigSummary,
  NotificationTemplateMode,
  SystemSettingsPayload,
} from '../../api/types';
import type { PipelineSummary, ProjectSummary, UserSummary } from '../../types/domain';
import EmptyPane from '../../components/EmptyPane';
import PageHeaderBar from '../../components/PageHeaderBar';
import PipelineNameWithTags from '../../components/PipelineNameWithTags';
import RefreshIconButton from '../../components/RefreshIconButton';
import { getNotificationChannelTypeLabel, notificationTemplateVariableOptions } from '../../constants/notification';
import { usePipelineHallPreferences } from '../../hooks/usePipelineHallPreferences';
import { useAppTheme } from '../../theme/AppThemeProvider';
import { useLayoutMode } from '../../theme/LayoutModeProvider';
import { copyText } from '../../utils/clipboard';
import { formatDateTime } from '../../utils/datetime';
import NotificationAdminPage from './NotificationAdminPage';

const emptySettings: SystemSettingsPayload = {
  gitExecutable: 'git',
  gitSshPublicKey: '',
  gitSshKnownHosts: '',
  hostSshPublicKey: '',
  cleanupEnabled: true,
  artifactRetainSuccessCount: 2,
  cleanRunsOnSuccess: true,
  failedRunRetainDays: 0,
  deploymentRestrictionPolicies: [],
};

const emptyWebhookConfig: NotificationWebhookConfigPayload = {
  name: '',
  description: '',
  type: 'FEISHU',
  webhookUrl: '',
  secret: '',
  enabled: true,
};

const emptyTemplate: NotificationTemplatePayload = {
  name: '',
  description: '',
  templateMode: 'TEXT',
  messageTemplate: '',
  enabled: true,
};

const emptyApiTokenForm: ApiTokenCreatePayload = {
  name: '',
  scopes: ['READ', 'PROJECT_WRITE', 'TEMPLATE_WRITE', 'PIPELINE_WRITE', 'DEPLOYMENT_RUN'],
  expiresAt: null,
};

const notificationTemplateModeOptions: { label: string; value: NotificationTemplateMode }[] = [
  { label: '文本', value: 'TEXT' },
  { label: '飞书卡片', value: 'FEISHU_CARD' },
];

const apiTokenScopeOptions: { label: string; value: ApiTokenScope; description: string }[] = [
  { label: '读取资源', value: 'READ', description: '读取项目、主机、环境、插件、流水线和部署记录。' },
  { label: '项目写入', value: 'PROJECT_WRITE', description: '创建或更新项目，并测试 Git 连通性。' },
  { label: '模板写入', value: 'TEMPLATE_WRITE', description: '创建或更新派生模板。' },
  { label: '流水线写入', value: 'PIPELINE_WRITE', description: '创建或更新流水线。' },
  { label: '执行部署', value: 'DEPLOYMENT_RUN', description: '部署预检查、触发部署、停止部署和回滚。' },
  { label: '平台管理', value: 'ADMIN', description: '允许调用全部管理员接口，请谨慎发放。' },
];

const restrictionScopeOptions: { label: string; value: DeploymentRestrictionScopeType }[] = [
  { label: '全部流水线', value: 'GLOBAL' },
  { label: '指定项目', value: 'PROJECT' },
  { label: '指定流水线', value: 'PIPELINE' },
];

const weekdayOptions = [
  { label: '周一', value: 1 },
  { label: '周二', value: 2 },
  { label: '周三', value: 3 },
  { label: '周四', value: 4 },
  { label: '周五', value: 5 },
  { label: '周六', value: 6 },
  { label: '周日', value: 7 },
];

const emptyRestrictionPolicy = (): DeploymentRestrictionPolicyConfig => ({
  id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
  name: '',
  enabled: true,
  scopeType: 'GLOBAL',
  scopeIds: [],
  weeklyWindows: [],
  reason: '',
});

const emptyWeeklyWindow = (): DeploymentRestrictionWeeklyWindow => ({
  daysOfWeek: [],
  startTime: '',
  endTime: '',
  startMinute: null,
  endMinute: null,
});

const restrictionTimeValue = (value?: string | null) => {
  if (!value) {
    return null;
  }
  const parsed = dayjs(value, 'HH:mm', true);
  return parsed.isValid() ? parsed : null;
};

const defaultFeishuCardTemplate = `{
  "config": {
    "wide_screen_mode": true
  },
  "header": {
    "template": "blue",
    "title": {
      "tag": "plain_text",
      "content": "Deploy Bot｜{{pipelineName}} {{eventLabel}}"
    }
  },
  "elements": [
    {
      "tag": "div",
      "fields": [
        {
          "is_short": true,
          "text": {
            "tag": "lark_md",
            "content": "**项目**\\n{{projectName}}"
          }
        },
        {
          "is_short": true,
          "text": {
            "tag": "lark_md",
            "content": "**分支**\\n{{branch}}"
          }
        },
        {
          "is_short": true,
          "text": {
            "tag": "lark_md",
            "content": "**部署人**\\n{{triggeredByDisplayName}}"
          }
        },
        {
          "is_short": true,
          "text": {
            "tag": "lark_md",
            "content": "**目标主机**\\n{{hostName}}"
          }
        },
        {
          "is_short": true,
          "text": {
            "tag": "lark_md",
            "content": "**开始时间**\\n{{startedAt}}"
          }
        },
        {
          "is_short": true,
          "text": {
            "tag": "lark_md",
            "content": "**结束时间**\\n{{finishedAt}}"
          }
        }
      ]
    },
    {
      "tag": "div",
      "text": {
        "tag": "lark_md",
        "content": "**耗时** {{duration}}\\n**错误信息** {{errorMessage}}"
      }
    },
    {
      "tag": "action",
      "actions": [
        {
          "tag": "button",
          "type": "primary",
          "text": {
            "tag": "plain_text",
            "content": "查看部署详情"
          },
          "url": "{{detailUrl}}"
        }
      ]
    }
  ]
}`;

type Props = {
  scope?: 'admin' | 'user';
};

export default function SystemSettingsPage({ scope = 'admin' }: Props) {
  const adminScope = scope === 'admin';
  const { mode: themeMode, followSystem, autoDarkAtNight, setMode: setThemeMode, setFollowSystem, setAutoDarkAtNight } = useAppTheme();
  const { mode: layoutMode, menuIconStyle, setMode: setLayoutMode, setMenuIconStyle } = useLayoutMode();
  const {
    autoOpenDeploymentDetail,
    setAutoOpenDeploymentDetail,
    pinActivePipelines,
    setPinActivePipelines,
    stopConfirmationEnabled,
    setStopConfirmationEnabled,
    showRunningServices,
    setShowRunningServices,
    hideStoppedServices,
    setHideStoppedServices,
  } = usePipelineHallPreferences();
  const [form, setForm] = useState<SystemSettingsPayload>(emptySettings);
  const [saving, setSaving] = useState(false);
  const [previewTitle, setPreviewTitle] = useState('');
  const [previewContent, setPreviewContent] = useState('');
  const [previewOpen, setPreviewOpen] = useState(false);
  const [webhookConfigs, setWebhookConfigs] = useState<NotificationWebhookConfigSummary[]>([]);
  const [templates, setTemplates] = useState<NotificationTemplateSummary[]>([]);
  const [apiTokens, setApiTokens] = useState<ApiTokenSummary[]>([]);
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [projects, setProjects] = useState<ProjectSummary[]>([]);
  const [pipelines, setPipelines] = useState<PipelineSummary[]>([]);
  const [webhookLoading, setWebhookLoading] = useState(false);
  const [templateLoading, setTemplateLoading] = useState(false);
  const [apiTokenLoading, setApiTokenLoading] = useState(false);
  const [webhookModalOpen, setWebhookModalOpen] = useState(false);
  const [templateModalOpen, setTemplateModalOpen] = useState(false);
  const [apiTokenModalOpen, setApiTokenModalOpen] = useState(false);
  const [createdApiToken, setCreatedApiToken] = useState('');
  const [restrictionModalOpen, setRestrictionModalOpen] = useState(false);
  const [editingWebhookId, setEditingWebhookId] = useState<number>();
  const [editingTemplateId, setEditingTemplateId] = useState<number>();
  const [editingRestrictionId, setEditingRestrictionId] = useState<string>();
  const [webhookForm, setWebhookForm] = useState<NotificationWebhookConfigPayload>(emptyWebhookConfig);
  const [templateForm, setTemplateForm] = useState<NotificationTemplatePayload>(emptyTemplate);
  const [apiTokenForm, setApiTokenForm] = useState<ApiTokenCreatePayload>(emptyApiTokenForm);
  const [restrictionForm, setRestrictionForm] = useState<DeploymentRestrictionPolicyConfig>(emptyRestrictionPolicy());
  const templateTextareaRef = useRef<any>(null);

  const hostInstallScript = form.hostSshPublicKey
    ? [
      '#!/usr/bin/env bash',
      'set -euo pipefail',
      'mkdir -p ~/.ssh',
      'chmod 700 ~/.ssh',
      `cat <<'__DEPLOYBOT_HOST_KEY__' >> ~/.ssh/authorized_keys`,
      form.hostSshPublicKey,
      '__DEPLOYBOT_HOST_KEY__',
      'chmod 600 ~/.ssh/authorized_keys',
    ].join('\n')
    : '';

  const openPreview = (title: string, content: string) => {
    setPreviewTitle(title);
    setPreviewContent(content);
    setPreviewOpen(true);
  };

  const loadSettings = async () => {
    const response = await systemSettingsApi.get();
    setForm({
      gitExecutable: response.gitExecutable || 'git',
      gitSshPublicKey: response.gitSshPublicKey || '',
      gitSshKnownHosts: response.gitSshKnownHosts || '',
      hostSshPublicKey: response.hostSshPublicKey || '',
      cleanupEnabled: response.cleanupEnabled ?? true,
      artifactRetainSuccessCount: response.artifactRetainSuccessCount ?? 2,
      cleanRunsOnSuccess: response.cleanRunsOnSuccess ?? true,
      failedRunRetainDays: response.failedRunRetainDays ?? 0,
      deploymentRestrictionPolicies: response.deploymentRestrictionPolicies || [],
    });
  };

  const loadRestrictionMeta = async () => {
    const [projectRows, pipelineRows] = await Promise.all([
      projectsApi.list(),
      pipelinesApi.list(),
    ]);
    setProjects(projectRows);
    setPipelines(pipelineRows);
  };

  const loadWebhookConfigs = async () => {
    setWebhookLoading(true);
    try {
      setWebhookConfigs(await notificationWebhookConfigsApi.list());
    } finally {
      setWebhookLoading(false);
    }
  };

  const loadTemplates = async () => {
    setTemplateLoading(true);
    try {
      setTemplates(await notificationTemplatesApi.list());
    } finally {
      setTemplateLoading(false);
    }
  };

  const loadApiTokens = async () => {
    setApiTokenLoading(true);
    try {
      const [tokenRows, userRows] = await Promise.all([
        apiTokensApi.list(),
        usersApi.list(),
      ]);
      setApiTokens(tokenRows);
      setUsers(userRows);
    } finally {
      setApiTokenLoading(false);
    }
  };

  useEffect(() => {
    if (!adminScope) {
      return;
    }
    loadSettings().catch(() => message.error('加载系统设置失败'));
    loadRestrictionMeta().catch(() => message.error('加载部署限制范围失败'));
    loadWebhookConfigs().catch(() => message.error('加载 Webhook 配置失败'));
    loadTemplates().catch(() => message.error('加载通知模板失败'));
    loadApiTokens().catch(() => message.error('加载 API Token 失败'));
  }, [adminScope]);

  const saveSettings = async () => {
    setSaving(true);
    try {
      await systemSettingsApi.update(form);
      await loadSettings();
      message.success('系统设置已保存');
    } finally {
      setSaving(false);
    }
  };

  const saveSettingsWithPolicies = async (policies: DeploymentRestrictionPolicyConfig[], successMessage: string) => {
    setSaving(true);
    try {
      const nextForm = { ...form, deploymentRestrictionPolicies: policies };
      await systemSettingsApi.update(nextForm);
      setForm(nextForm);
      await loadSettings();
      message.success(successMessage);
    } finally {
      setSaving(false);
    }
  };

  const generateKeyPair = async () => {
    await systemSettingsApi.generateGitKeyPair();
    await loadSettings();
    message.success('Git SSH 密钥对已生成');
  };

  const generateHostKeyPair = async () => {
    await systemSettingsApi.generateHostKeyPair();
    await loadSettings();
    message.success('主机 SSH 密钥对已生成');
  };

  const hasGitKeyPair = Boolean(form.gitSshPublicKey);
  const hasHostKeyPair = Boolean(form.hostSshPublicKey);

  const insertTemplateVariable = (token: string) => {
    const textarea: HTMLTextAreaElement | undefined = templateTextareaRef.current?.resizableTextArea?.textArea;
    if (!textarea) {
      setTemplateForm((current) => ({ ...current, messageTemplate: `${current.messageTemplate || ''}${token}` }));
      return;
    }
    const currentValue = templateForm.messageTemplate || '';
    const start = textarea.selectionStart ?? currentValue.length;
    const end = textarea.selectionEnd ?? start;
    const nextValue = `${currentValue.slice(0, start)}${token}${currentValue.slice(end)}`;
    setTemplateForm((current) => ({ ...current, messageTemplate: nextValue }));
    requestAnimationFrame(() => {
      textarea.focus();
      const cursor = start + token.length;
      textarea.setSelectionRange(cursor, cursor);
    });
  };

  const openCreateWebhook = () => {
    setEditingWebhookId(undefined);
    setWebhookForm(emptyWebhookConfig);
    setWebhookModalOpen(true);
  };

  const openEditWebhook = (record: NotificationWebhookConfigSummary) => {
    setEditingWebhookId(record.id);
    setWebhookForm({
      name: record.name,
      description: record.description || '',
      type: record.type,
      webhookUrl: record.webhookUrl || '',
      secret: '',
      enabled: record.enabled,
    });
    setWebhookModalOpen(true);
  };

  const saveWebhook = async () => {
    if (editingWebhookId) {
      await notificationWebhookConfigsApi.update(editingWebhookId, webhookForm);
    } else {
      await notificationWebhookConfigsApi.create(webhookForm);
    }
    setWebhookModalOpen(false);
    setEditingWebhookId(undefined);
    setWebhookForm(emptyWebhookConfig);
    await loadWebhookConfigs();
    message.success(editingWebhookId ? 'Webhook 配置已更新' : 'Webhook 配置已创建');
  };

  const removeWebhook = async (id: number) => {
    await notificationWebhookConfigsApi.remove(id);
    await loadWebhookConfigs();
    message.success('Webhook 配置已删除');
  };

  const openCreateTemplate = () => {
    setEditingTemplateId(undefined);
    setTemplateForm(emptyTemplate);
    setTemplateModalOpen(true);
  };

  const openEditTemplate = (record: NotificationTemplateSummary) => {
    setEditingTemplateId(record.id);
    setTemplateForm({
      name: record.name,
      description: record.description || '',
      templateMode: record.templateMode || 'TEXT',
      messageTemplate: record.messageTemplate || '',
      enabled: record.enabled,
    });
    setTemplateModalOpen(true);
  };

  const saveTemplate = async () => {
    if (editingTemplateId) {
      await notificationTemplatesApi.update(editingTemplateId, templateForm);
    } else {
      await notificationTemplatesApi.create(templateForm);
    }
    setTemplateModalOpen(false);
    setEditingTemplateId(undefined);
    setTemplateForm(emptyTemplate);
    await loadTemplates();
    message.success(editingTemplateId ? '通知模板已更新' : '通知模板已创建');
  };

  const removeTemplate = async (id: number) => {
    await notificationTemplatesApi.remove(id);
    await loadTemplates();
    message.success('通知模板已删除');
  };

  const openCreateApiToken = () => {
    setApiTokenForm(emptyApiTokenForm);
    setApiTokenModalOpen(true);
  };

  const createApiToken = async () => {
    if (!apiTokenForm.name.trim()) {
      message.error('请填写 Token 名称');
      return;
    }
    if (!apiTokenForm.scopes.length) {
      message.error('请至少选择一个权限范围');
      return;
    }
    const result = await apiTokensApi.create({
      ...apiTokenForm,
      name: apiTokenForm.name.trim(),
    });
    setCreatedApiToken(result.token);
    setApiTokenModalOpen(false);
    setApiTokenForm(emptyApiTokenForm);
    await loadApiTokens();
    message.success('API Token 已创建');
  };

  const toggleApiTokenEnabled = async (record: ApiTokenSummary, enabled: boolean) => {
    await apiTokensApi.update(record.id, { enabled });
    await loadApiTokens();
    message.success(enabled ? 'API Token 已启用' : 'API Token 已停用');
  };

  const revokeApiToken = async (id: number) => {
    await apiTokensApi.revoke(id);
    await loadApiTokens();
    message.success('API Token 已撤销');
  };

  const apiTokenScopeText = (scopes: ApiTokenScope[] = []) => scopes
    .map((scope) => apiTokenScopeOptions.find((item) => item.value === scope)?.label || scope)
    .join('、') || '-';

  const openCreateRestriction = () => {
    setEditingRestrictionId(undefined);
    setRestrictionForm(emptyRestrictionPolicy());
    setRestrictionModalOpen(true);
  };

  const openEditRestriction = (record: DeploymentRestrictionPolicyConfig) => {
    setEditingRestrictionId(record.id);
    setRestrictionForm({
      ...emptyRestrictionPolicy(),
      ...record,
      enabled: record.enabled !== false,
      scopeType: record.scopeType || 'GLOBAL',
      scopeIds: record.scopeIds || [],
      weeklyWindows: record.weeklyWindows || [],
    });
    setRestrictionModalOpen(true);
  };

  const saveRestriction = async () => {
    if (!restrictionForm.name.trim()) {
      message.error('请填写策略名称');
      return;
    }
    if (restrictionForm.scopeType !== 'GLOBAL' && !(restrictionForm.scopeIds || []).length) {
      message.error('请选择策略生效范围');
      return;
    }
    if ((restrictionForm.weeklyWindows || []).some((window) => !window.daysOfWeek?.length)) {
      message.error('请选择每周时间窗口的星期');
      return;
    }
    if ((restrictionForm.weeklyWindows || []).some((window) => (window.startMinute != null || window.endMinute != null) && (window.startMinute == null || window.endMinute == null))) {
      message.error('请完整填写时间段内的分钟窗口');
      return;
    }
    const nextPolicy = {
      ...restrictionForm,
      name: restrictionForm.name.trim(),
      reason: restrictionForm.reason?.trim() || '',
      scopeIds: restrictionForm.scopeType === 'GLOBAL' ? [] : (restrictionForm.scopeIds || []),
    };
    const nextPolicies = editingRestrictionId
      ? (form.deploymentRestrictionPolicies || []).map((item) => item.id === editingRestrictionId ? nextPolicy : item)
      : [...(form.deploymentRestrictionPolicies || []), nextPolicy];
    await saveSettingsWithPolicies(nextPolicies, editingRestrictionId ? '部署限制策略已更新' : '部署限制策略已创建');
    setRestrictionModalOpen(false);
    setEditingRestrictionId(undefined);
    setRestrictionForm(emptyRestrictionPolicy());
  };

  const removeRestriction = async (id: string) => {
    const nextPolicies = (form.deploymentRestrictionPolicies || []).filter((item) => item.id !== id);
    await saveSettingsWithPolicies(nextPolicies, '部署限制策略已删除');
  };

  const toggleRestrictionEnabled = async (id: string, enabled: boolean) => {
    const nextPolicies = (form.deploymentRestrictionPolicies || []).map((item) => item.id === id ? { ...item, enabled } : item);
    await saveSettingsWithPolicies(nextPolicies, enabled ? '部署限制策略已启用' : '部署限制策略已停用');
  };

  const restrictionScopeText = (record: DeploymentRestrictionPolicyConfig) => {
    if (record.scopeType === 'PROJECT') {
      return projects.find((item) => item.id === record.scopeIds?.[0])?.name || '指定项目';
    }
    if (record.scopeType === 'PIPELINE') {
      const selected = (record.scopeIds || [])
        .map((id) => pipelines.find((item) => item.id === id))
        .filter(Boolean);
      if (!selected.length) {
        return '指定流水线';
      }
      return (
        <div className="space-y-1">
          {selected.slice(0, 3).map((item) => (
            <PipelineNameWithTags key={item.id} name={item.name} importantTags={item.importantTags} />
          ))}
          {selected.length > 3 ? <div className="text-xs text-slate-400">等 {selected.length} 条流水线</div> : null}
        </div>
      );
    }
    return '全部流水线';
  };

  const restrictionRuleText = (record: DeploymentRestrictionPolicyConfig) => {
    const parts: string[] = [];
    if (record.weeklyWindows?.length) {
      parts.push(record.weeklyWindows.map((window) => {
        const days = (window.daysOfWeek || []).map((value) => weekdayOptions.find((item) => item.value === value)?.label || value).join('、');
        const minuteText = window.startMinute != null || window.endMinute != null ? `，每小时 ${window.startMinute ?? '-'} 分至 ${window.endMinute ?? '-'} 分` : '';
        return `${days || '未选择星期'} ${window.startTime || '00:00'} 至 ${window.endTime || '23:59'}${minuteText}`;
      }).join('；'));
    }
    return `${parts.length ? parts.join('，且 ') : '不限时间窗口'} 时禁止部署`;
  };

  const updateWeeklyWindow = (index: number, patch: Partial<DeploymentRestrictionWeeklyWindow>) => {
    setRestrictionForm((current) => ({
      ...current,
      weeklyWindows: (current.weeklyWindows || []).map((window, currentIndex) => currentIndex === index ? { ...window, ...patch } : window),
    }));
  };

  const addWeeklyWindow = () => {
    setRestrictionForm((current) => ({
      ...current,
      weeklyWindows: [...(current.weeklyWindows || []), emptyWeeklyWindow()],
    }));
  };

  const removeWeeklyWindow = (index: number) => {
    setRestrictionForm((current) => ({
      ...current,
      weeklyWindows: (current.weeklyWindows || []).filter((_, currentIndex) => currentIndex !== index),
    }));
  };

  const enabledStatus = useMemo(() => (enabled: boolean) => (
    <span className="status-chip">
      <span className={`status-dot ${enabled ? 'status-dot--success' : 'status-dot--pending'}`} />
      <span>{enabled ? '启用' : '停用'}</span>
    </span>
  ), []);

  return (
    <>
      <PageHeaderBar
        title="系统设置"
        description={adminScope ? '集中维护界面偏好、平台基础能力和通知相关配置。' : '设置自己的界面显示偏好。'}
      />
      <Tabs
          className="system-settings-tabs app-fixed-tabs app-soft-tabs"
          items={[
            {
              key: 'appearance',
              label: '界面设置',
              children: (
                <div className="space-y-4">
                  <Card className="app-card">
                    <div className="mb-4">
                      <div className="text-base font-semibold text-slate-800">菜单样式</div>
                      <div className="mt-1 text-sm text-slate-500">选择平台主导航显示在顶部还是左侧。</div>
                    </div>
                    <div className="settings-choice-grid">
                      <button
                        type="button"
                        className={`settings-choice ${layoutMode === 'top' ? 'settings-choice--active' : ''}`}
                        onClick={() => setLayoutMode('top')}
                      >
                        <span className="settings-choice__title">顶部菜单</span>
                        <span className="settings-choice__description">页面横向空间更完整，适合宽屏少菜单场景。</span>
                      </button>
                      <button
                        type="button"
                        className={`settings-choice ${layoutMode === 'side' ? 'settings-choice--active' : ''}`}
                        onClick={() => setLayoutMode('side')}
                      >
                        <span className="settings-choice__title">左侧菜单</span>
                        <span className="settings-choice__description">导航层级更稳定，适合高频配置和日常部署。</span>
                      </button>
                    </div>
                  </Card>
                  <Card className="app-card">
                    <div className="mb-4">
                      <div className="text-base font-semibold text-slate-800">图标样式</div>
                      <div className="mt-1 text-sm text-slate-500">在双色线性和实心两种图标风格之间切换。</div>
                    </div>
                    <div className="settings-choice-grid">
                      <button
                        type="button"
                        className={`settings-choice ${menuIconStyle === 'duotone' ? 'settings-choice--active' : ''}`}
                        onClick={() => setMenuIconStyle('duotone')}
                      >
                        <span className="settings-choice__title">双色线性</span>
                        <span className="settings-choice__description">线条更轻，保留少量双色层次。</span>
                      </button>
                      <button
                        type="button"
                        className={`settings-choice ${menuIconStyle === 'fill' ? 'settings-choice--active' : ''}`}
                        onClick={() => setMenuIconStyle('fill')}
                      >
                        <span className="settings-choice__title">实心图标</span>
                        <span className="settings-choice__description">更明确、更醒目，折叠状态下识别更快。</span>
                      </button>
                    </div>
                  </Card>
                  <Card className="app-card">
                    <div className="mb-4">
                      <div className="text-base font-semibold text-slate-800">主题模式</div>
                      <div className="mt-1 text-sm text-slate-500">控制浅色、深色以及系统外观联动。</div>
                    </div>
                    <div className="settings-choice-grid">
                      <button
                        type="button"
                        className={`settings-choice ${!followSystem && themeMode === 'light' ? 'settings-choice--active' : ''}`}
                        onClick={() => setThemeMode('light')}
                      >
                        <span className="settings-choice__title">浅色</span>
                        <span className="settings-choice__description">固定使用浅色界面。</span>
                      </button>
                      <button
                        type="button"
                        className={`settings-choice ${!followSystem && themeMode === 'dark' ? 'settings-choice--active' : ''}`}
                        onClick={() => setThemeMode('dark')}
                      >
                        <span className="settings-choice__title">深色</span>
                        <span className="settings-choice__description">固定使用深色界面。</span>
                      </button>
                      <button
                        type="button"
                        className={`settings-choice ${followSystem ? 'settings-choice--active' : ''}`}
                        onClick={() => setFollowSystem(true)}
                      >
                        <span className="settings-choice__title">跟随系统</span>
                        <span className="settings-choice__description">跟随操作系统的浅色/深色偏好。</span>
                      </button>
                    </div>
                    <div className="mt-4 rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3">
                      <div className="flex flex-wrap items-center justify-between gap-3">
                        <div>
                          <div className="text-sm font-semibold text-slate-800">晚上自动打开深色模式</div>
                          <div className="mt-1 text-xs text-slate-500">18:00 到次日 06:00 自动使用深色，优先级高于手动和跟随系统。</div>
                        </div>
                        <Switch
                          checked={autoDarkAtNight}
                          checkedChildren="开启"
                          unCheckedChildren="关闭"
                          onChange={setAutoDarkAtNight}
                        />
                      </div>
                    </div>
                  </Card>
                  <Card className="app-card">
                    <div className="mb-4">
                      <div className="text-base font-semibold text-slate-800">流水线大厅</div>
                      <div className="mt-1 text-sm text-slate-500">调整大厅里的部署跳转、列表排序和服务条带。</div>
                    </div>
                    <div className="settings-toggle-list">
                      <button
                        type="button"
                        className="settings-toggle-row"
                        onClick={() => setAutoOpenDeploymentDetail((previous) => !previous)}
                      >
                        <span>
                          <span className="settings-toggle-row__title">部署开始后自动进入详情</span>
                          <span className="settings-toggle-row__description">触发部署后直接打开部署详情。</span>
                        </span>
                        <Switch
                          checked={autoOpenDeploymentDetail}
                          onChange={setAutoOpenDeploymentDetail}
                          onClick={(_, event) => event?.stopPropagation()}
                        />
                      </button>
                      <button
                        type="button"
                        className="settings-toggle-row"
                        onClick={() => setPinActivePipelines((previous) => !previous)}
                      >
                        <span>
                          <span className="settings-toggle-row__title">执行中流水线置顶</span>
                          <span className="settings-toggle-row__description">部署中的流水线优先排在列表前面。</span>
                        </span>
                        <Switch
                          checked={pinActivePipelines}
                          onChange={setPinActivePipelines}
                          onClick={(_, event) => event?.stopPropagation()}
                        />
                      </button>
                      <button
                        type="button"
                        className="settings-toggle-row"
                        onClick={() => {
                          setShowRunningServices((previous) => {
                            const next = !previous;
                            if (!next) {
                              setHideStoppedServices(true);
                            }
                            return next;
                          });
                        }}
                      >
                        <span>
                          <span className="settings-toggle-row__title">显示运行中的服务</span>
                          <span className="settings-toggle-row__description">在大厅顶部展示服务状态条带。</span>
                        </span>
                        <Switch
                          checked={showRunningServices}
                          onChange={(checked) => {
                            setShowRunningServices(checked);
                            if (!checked) {
                              setHideStoppedServices(true);
                            }
                          }}
                          onClick={(_, event) => event?.stopPropagation()}
                        />
                      </button>
                      {showRunningServices ? (
                        <button
                          type="button"
                          className="settings-toggle-row"
                          onClick={() => setHideStoppedServices((previous) => !previous)}
                        >
                          <span>
                            <span className="settings-toggle-row__title">隐藏已停止服务</span>
                            <span className="settings-toggle-row__description">服务条带只保留仍在运行的服务。</span>
                          </span>
                          <Switch
                            checked={hideStoppedServices}
                            onChange={setHideStoppedServices}
                            onClick={(_, event) => event?.stopPropagation()}
                          />
                        </button>
                      ) : null}
                      <button
                        type="button"
                        className="settings-toggle-row"
                        onClick={() => setStopConfirmationEnabled((previous) => !previous)}
                      >
                        <span>
                          <span className="settings-toggle-row__title">停止部署二次确认</span>
                          <span className="settings-toggle-row__description">点击停止前先弹出确认。</span>
                        </span>
                        <Switch
                          checked={stopConfirmationEnabled}
                          onChange={setStopConfirmationEnabled}
                          onClick={(_, event) => event?.stopPropagation()}
                        />
                      </button>
                    </div>
                  </Card>
                </div>
              ),
            },
            ...(adminScope ? [{
              key: 'basic',
              label: '基础设置',
              children: (
        <div className="space-y-6">
          <div className="flex justify-end">
            <Button type="primary" loading={saving} onClick={() => saveSettings().catch(() => message.error('保存基础设置失败'))}>保存基础设置</Button>
          </div>
          <section className="space-y-4">
            <div className="px-1">
              <div className="text-lg font-semibold text-slate-900">Git</div>
              <div className="mt-1 text-sm text-slate-500">维护平台执行 Git 命令时使用的二进制，以及统一的 Git SSH 密钥。</div>
            </div>
            <Card className="app-card">
              <Form layout="vertical">
                <div className="mb-4 text-base font-semibold text-slate-800">执行设置</div>
                <Form.Item label="Git 可执行文件">
                  <Input
                    value={form.gitExecutable}
                    onChange={(event) => setForm({ ...form, gitExecutable: event.target.value })}
                    placeholder="git"
                  />
                </Form.Item>
                <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm leading-7 text-slate-600">
                  项目自己的 Git 认证方式请到“项目管理”里配置；每台机器自己的工作空间请到“主机管理”里配置。这里的 `Git 可执行文件` 只控制平台执行 Git 命令时用哪个二进制。
                </div>
              </Form>
            </Card>
            <Card className="app-card">
              <Form layout="vertical">
                <div className="mb-4 text-base font-semibold text-slate-800">Git SSH 密钥对</div>
          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="min-w-0">
                <div className="text-sm font-medium text-slate-800">系统公钥</div>
                <Typography.Paragraph className="!mb-0 !mt-1 max-w-[560px] font-mono !text-xs !text-slate-500" ellipsis={{ rows: 2 }}>
                  {form.gitSshPublicKey || '点击“生成密钥对”后，公钥会显示在这里。'}
                </Typography.Paragraph>
              </div>
              <Space wrap>
                {hasGitKeyPair ? (
                  <Popconfirm
                    title="确认重新生成 Git SSH 密钥对？"
                    description="重新生成后，系统当前使用的 Git SSH 公私钥会被替换。所有依赖这套 Git 密钥的仓库都需要同步更新平台公钥，否则后续拉代码会失败。"
                    okText="确认生成"
                    cancelText="取消"
                    onConfirm={() => generateKeyPair().catch(() => message.error('生成密钥对失败'))}
                  >
                    <Button type="primary">重新生成密钥对</Button>
                  </Popconfirm>
                ) : (
                  <Button type="primary" onClick={() => generateKeyPair().catch(() => message.error('生成密钥对失败'))}>
                    生成密钥对
                  </Button>
                )}
                <Button disabled={!form.gitSshPublicKey} onClick={() => copyText(form.gitSshPublicKey || '').then(() => message.success('公钥已复制')).catch(() => message.error('复制失败，请点击“查看”后手动复制'))}>
                  复制公钥
                </Button>
                <Button disabled={!form.gitSshPublicKey} onClick={() => openPreview('Git SSH 公钥', form.gitSshPublicKey || '')}>
                  查看
                </Button>
              </Space>
            </div>
          </div>
          <Collapse
            ghost
            className="mt-4"
            items={[
              {
                key: 'known-hosts',
                label: '高级：known_hosts / 主机指纹校验',
                children: (
                  <div className="space-y-3">
                    <div className="text-sm leading-7 text-slate-600">
                      `known_hosts` 用来校验 Git 服务器身份，避免连到被冒充的主机。不填时系统会关闭严格校验；只有你需要固定校验 Git 主机指纹时再展开填写。
                    </div>
                    <Input.TextArea
                      rows={5}
                      value={form.gitSshKnownHosts}
                      onChange={(event) => setForm({ ...form, gitSshKnownHosts: event.target.value })}
                      placeholder="可选。填写后会启用主机指纹校验；不填则默认关闭 StrictHostKeyChecking。"
                    />
                  </div>
                ),
              },
            ]}
          />
          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm leading-7 text-slate-600">
            这是整个系统共用的一套 SSH 密钥对。项目选择“密钥对模式”后，会统一使用这套私钥拉代码；管理员只需要把上面的公钥添加到对应 Git 平台。
          </div>
              </Form>
            </Card>
          </section>

          <section className="space-y-4">
            <div className="px-1">
              <div className="text-lg font-semibold text-slate-900">主机</div>
              <div className="mt-1 text-sm text-slate-500">维护平台登录远程主机时使用的 SSH 密钥，以及快捷安装脚本。</div>
            </div>
            <Card className="app-card">
              <Form layout="vertical">
                <div className="mb-4 text-base font-semibold text-slate-800">主机 SSH 密钥对</div>
          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="min-w-0">
                <div className="text-sm font-medium text-slate-800">主机公钥</div>
                <Typography.Paragraph className="!mb-0 !mt-1 max-w-[560px] font-mono !text-xs !text-slate-500" ellipsis={{ rows: 2 }}>
                  {form.hostSshPublicKey || '点击“生成密钥对”后，公钥会显示在这里。'}
                </Typography.Paragraph>
              </div>
              <Space wrap>
                {hasHostKeyPair ? (
                  <Popconfirm
                    title="确认重新生成主机 SSH 密钥对？"
                    description="重新生成后，系统当前用于登录远程主机的 SSH 公私钥会被替换。所有已配置过这套主机公钥的服务器，都需要重新更新 authorized_keys，否则后续远程部署会失败。"
                    okText="确认生成"
                    cancelText="取消"
                    onConfirm={() => generateHostKeyPair().catch(() => message.error('生成主机密钥对失败'))}
                  >
                    <Button type="primary">重新生成密钥对</Button>
                  </Popconfirm>
                ) : (
                  <Button type="primary" onClick={() => generateHostKeyPair().catch(() => message.error('生成主机密钥对失败'))}>
                    生成密钥对
                  </Button>
                )}
                <Button disabled={!form.hostSshPublicKey} onClick={() => copyText(form.hostSshPublicKey || '').then(() => message.success('公钥已复制')).catch(() => message.error('复制失败，请点击“查看”后手动复制'))}>
                  复制公钥
                </Button>
                <Button disabled={!form.hostSshPublicKey} onClick={() => openPreview('主机 SSH 公钥', form.hostSshPublicKey || '')}>
                  查看
                </Button>
              </Space>
            </div>
          </div>
          <Collapse
            ghost
            className="mt-4"
            items={[
              {
                key: 'host-script',
                label: '快捷脚本：追加到 authorized_keys',
                children: (
                  <div className="space-y-3">
                    <div className="text-sm leading-7 text-slate-600">
                      登录到目标主机后，进入任意目录执行这段脚本即可。它会自动创建 `~/.ssh`，并把当前公钥追加到 `authorized_keys`。
                    </div>
                    <Space wrap>
                      <Button disabled={!hostInstallScript} onClick={() => openPreview('主机 SSH 快捷脚本', hostInstallScript)}>
                        查看脚本
                      </Button>
                      <Button disabled={!hostInstallScript} onClick={() => copyText(hostInstallScript).then(() => message.success('快捷脚本已复制')).catch(() => message.error('复制失败，请点击“查看脚本”后手动复制'))}>
                        复制脚本
                      </Button>
                    </Space>
                  </div>
                ),
              },
            ]}
          />
          <div className="mt-4 rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm leading-7 text-slate-600">
            这套密钥专门用于平台登录远程主机执行部署。请把上面的公钥添加到目标主机的 `authorized_keys`，不要和 Git 仓库的 SSH 密钥混用。
          </div>
              </Form>
            </Card>
          </section>

          <section className="space-y-4">
            <div className="px-1">
              <div className="text-lg font-semibold text-slate-900">清理</div>
              <div className="mt-1 text-sm text-slate-500">控制构建工作区和历史产物的保留策略，避免源码、node_modules、target、dist 长期占用磁盘。</div>
            </div>
            <Card className="app-card">
              <Form layout="vertical">
                <div className="grid gap-4 md:grid-cols-2">
                  <Form.Item label="自动清理">
                    <Switch
                      checked={form.cleanupEnabled ?? true}
                      checkedChildren="开启"
                      unCheckedChildren="关闭"
                      onChange={(value) => setForm({ ...form, cleanupEnabled: value })}
                    />
                  </Form.Item>
                  <Form.Item label="成功后清理 runs">
                    <Switch
                      checked={form.cleanRunsOnSuccess ?? true}
                      checkedChildren="清理"
                      unCheckedChildren="保留"
                      onChange={(value) => setForm({ ...form, cleanRunsOnSuccess: value })}
                    />
                  </Form.Item>
                  <Form.Item label="每条流水线保留成功产物数">
                    <InputNumber
                      min={0}
                      precision={0}
                      value={form.artifactRetainSuccessCount ?? 2}
                      onChange={(value) => setForm({ ...form, artifactRetainSuccessCount: value ?? 0 })}
                      className="!w-full"
                    />
                  </Form.Item>
                  <Form.Item label="失败/停止 runs 保留天数">
                    <InputNumber
                      min={0}
                      precision={0}
                      value={form.failedRunRetainDays ?? 0}
                      onChange={(value) => setForm({ ...form, failedRunRetainDays: value ?? 0 })}
                      className="!w-full"
                    />
                  </Form.Item>
                </div>
                <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm leading-7 text-slate-600">
                  `runs` 是临时构建工作区，源码、`node_modules`、`target`、`dist` 都在里面，默认部署结束后直接删除整个目录。`artifacts` 是可重新发布的构建产物，只按流水线保留最近成功版本。
                </div>
              </Form>
            </Card>
          </section>

        </div>
              ),
            },
            {
              key: 'api-tokens',
              label: 'API Token',
              children: (
        <div className="space-y-6">
          <section className="space-y-4">
            <div className="px-1">
              <div className="text-lg font-semibold text-slate-900">API Token</div>
              <div className="mt-1 text-sm text-slate-500">给 Codex、Claude Code 或后续 CLI 使用的机器调用凭证。明文只在创建后显示一次，请及时复制保存。</div>
            </div>
            <Card className="app-card">
              <div className="mb-4 flex items-center justify-between gap-3">
                <div className="text-sm text-slate-500">Token 按权限范围限制可调用接口，建议只给 Agent 发放接入项目和触发部署所需的最小权限。</div>
                <Space>
                  <RefreshIconButton onClick={() => loadApiTokens().catch(() => message.error('加载 API Token 失败'))} />
                  <Button type="primary" onClick={openCreateApiToken}>新建 Token</Button>
                </Space>
              </div>
              <Table
                rowKey="id"
                loading={apiTokenLoading}
                dataSource={apiTokens}
                locale={{ emptyText: <EmptyPane description="还没有 API Token。" /> }}
                pagination={{ showSizeChanger: true, showTotal: (total) => `共 ${total} 条` }}
                columns={[
                  {
                    title: 'Token',
                    width: 220,
                    render: (_, record) => (
                      <div>
                        <div className="font-semibold text-slate-800 dark:text-slate-100">{record.name}</div>
                        <div className="mt-1 font-mono text-xs text-slate-500 dark:text-slate-400">{record.tokenPrefix}...</div>
                      </div>
                    ),
                  },
                  { title: '归属用户', width: 150, render: (_, record) => record.displayName || record.username || '-' },
                  { title: '权限范围', render: (_, record) => <div className="text-sm text-slate-600 dark:text-slate-300">{apiTokenScopeText(record.scopes)}</div> },
                  { title: '过期时间', width: 170, render: (_, record) => formatDateTime(record.expiresAt) },
                  { title: '最近使用', width: 190, render: (_, record) => (
                    <div>
                      <div>{formatDateTime(record.lastUsedAt)}</div>
                      <div className="mt-1 text-xs text-slate-400">{record.lastUsedIp || ''}</div>
                    </div>
                  ) },
                  {
                    title: '状态',
                    width: 100,
                    render: (_, record) => record.revokedAt ? (
                      <span className="status-chip">
                        <span className="status-dot status-dot--failed" />
                        <span>已撤销</span>
                      </span>
                    ) : (
                      <Switch
                        size="small"
                        checked={record.enabled}
                        checkedChildren="启用"
                        unCheckedChildren="停用"
                        onChange={(checked) => toggleApiTokenEnabled(record, checked).catch(() => message.error('更新 API Token 失败'))}
                      />
                    ),
                  },
                  {
                    title: '操作',
                    width: 88,
                    render: (_, record) => record.revokedAt ? '-' : (
                      <Popconfirm title="确认撤销这个 API Token 吗？" description="撤销后无法恢复，正在使用它的 Agent 或 CLI 会立即失效。" onConfirm={() => revokeApiToken(record.id).catch(() => message.error('撤销 API Token 失败'))}>
                        <Button size="small" danger>撤销</Button>
                      </Popconfirm>
                    ),
                  },
                ]}
              />
            </Card>
          </section>
        </div>
              ),
            },
            {
              key: 'deployment-restrictions',
              label: '部署限制策略',
              children: (
        <div className="space-y-6">
          <section className="space-y-4">
            <div className="px-1">
              <div className="text-lg font-semibold text-slate-900">部署限制策略</div>
              <div className="mt-1 text-sm text-slate-500">统一控制限制部署的时间，支持全局、项目和流水线范围。</div>
            </div>
            <Card className="app-card">
              <div className="mb-4 flex justify-end">
                <Button type="primary" onClick={openCreateRestriction}>新建限制策略</Button>
              </div>
              <Table
                rowKey="id"
                dataSource={form.deploymentRestrictionPolicies || []}
                locale={{ emptyText: <EmptyPane description="还没有部署限制策略。" /> }}
                pagination={false}
                columns={[
                  {
                    title: '策略',
                    width: 220,
                    render: (_, record) => (
                      <div>
                        <div className="font-semibold text-slate-800 dark:text-slate-100">{record.name || '-'}</div>
                        <div className="mt-1 text-xs text-slate-500 dark:text-slate-400">{record.reason || '未填写限制原因'}</div>
                      </div>
                    ),
                  },
                  { title: '范围', width: 160, render: (_, record) => restrictionScopeText(record) },
                  { title: '规则', render: (_, record) => restrictionRuleText(record) },
                  {
                    title: '状态',
                    width: 100,
                    render: (_, record) => (
                      <Switch
                        checked={record.enabled !== false}
                        checkedChildren="启用"
                        unCheckedChildren="停用"
                        loading={saving}
                        onChange={(checked) => toggleRestrictionEnabled(record.id, checked).catch(() => message.error('更新部署限制策略失败'))}
                      />
                    ),
                  },
                  {
                    title: '操作',
                    width: 136,
                    render: (_, record) => (
                      <Space>
                        <Button size="small" onClick={() => openEditRestriction(record)}>编辑</Button>
                        <Popconfirm title="确认删除这条限制策略吗？" onConfirm={() => removeRestriction(record.id).catch(() => message.error('删除部署限制策略失败'))}>
                          <Button size="small" danger>删除</Button>
                        </Popconfirm>
                      </Space>
                    ),
                  },
                ]}
              />
            </Card>
          </section>
        </div>
              ),
            },
            {
              key: 'notification',
              label: '通知设置',
              children: (
        <div className="space-y-6">
          <section className="space-y-4">
            <div className="px-1">
              <div className="text-lg font-semibold text-slate-900">通知</div>
              <div className="mt-1 text-sm text-slate-500">维护通知配置、Webhook 和通知模板，流水线绑定通知配置后会按部署事件发送消息。</div>
            </div>
            <NotificationAdminPage embedded showRecords={false} />
            <Card className="app-card">
              <Tabs
                className="app-soft-tabs"
                items={[
                  {
                    key: 'webhooks',
                    label: 'Webhook 配置',
                    children: (
                      <>
                        <div className="mb-4 flex items-center justify-between gap-3">
                          <div className="text-sm text-slate-500">集中维护通知要使用的 Webhook 地址和签名密钥，后续创建通知配置时直接选择。</div>
                          <Space>
                            <RefreshIconButton onClick={() => loadWebhookConfigs().catch(() => message.error('加载 Webhook 配置失败'))} />
                            <Button type="primary" onClick={openCreateWebhook}>新建 Webhook 配置</Button>
                          </Space>
                        </div>
                        <Table
                          rowKey="id"
                          loading={webhookLoading}
                          dataSource={webhookConfigs}
                          locale={{ emptyText: <EmptyPane description="还没有 Webhook 配置，先新建一个飞书 Webhook。" /> }}
                          pagination={{ showSizeChanger: true, showTotal: (total) => `共 ${total} 条` }}
                          columns={[
                            { title: '名称', dataIndex: 'name', width: 180 },
                            { title: '通知渠道类型', render: (_, record) => getNotificationChannelTypeLabel(record.type), width: 140 },
                            { title: '描述', render: (_, record) => record.description || '-', width: 200 },
                            { title: 'Webhook 地址', render: (_, record) => <div className="truncate max-w-[360px]">{record.webhookUrl || '-'}</div> },
                            { title: '状态', render: (_, record) => enabledStatus(record.enabled), width: 100 },
                            {
                              title: '操作',
                              width: 136,
                              render: (_, record) => (
                                <Space>
                                  <Button size="small" onClick={() => openEditWebhook(record)}>编辑</Button>
                                  <Popconfirm title="确认删除这条 Webhook 配置吗？" onConfirm={() => removeWebhook(record.id)}>
                                    <Button size="small" danger>删除</Button>
                                  </Popconfirm>
                                </Space>
                              ),
                            },
                          ]}
                        />
                      </>
                    ),
                  },
                  {
                    key: 'templates',
                    label: '通知模板',
                    children: (
                      <>
                        <div className="mb-4 flex items-center justify-between gap-3">
                          <div className="text-sm text-slate-500">维护通知消息的默认内容。通知配置选中模板后会自动带出内容，有特殊需求再做覆盖。</div>
                          <Space>
                            <RefreshIconButton onClick={() => loadTemplates().catch(() => message.error('加载通知模板失败'))} />
                            <Button type="primary" onClick={openCreateTemplate}>新建通知模板</Button>
                          </Space>
                        </div>
                        <Table
                          rowKey="id"
                          loading={templateLoading}
                          dataSource={templates}
                          locale={{ emptyText: <EmptyPane description="还没有通知模板。" /> }}
                          pagination={{ showSizeChanger: true, showTotal: (total) => `共 ${total} 条` }}
                          columns={[
                            { title: '名称', dataIndex: 'name', width: 180 },
                            {
                              title: '模板形式',
                              width: 120,
                              render: (_, record) => record.templateMode === 'FEISHU_CARD' ? '飞书卡片' : '文本',
                            },
                            { title: '描述', render: (_, record) => record.description || '-', width: 200 },
                            { title: '模板内容', render: (_, record) => <div className="line-clamp-3 whitespace-pre-wrap text-xs text-slate-600">{record.messageTemplate}</div> },
                            { title: '状态', render: (_, record) => enabledStatus(record.enabled), width: 100 },
                            {
                              title: '操作',
                              width: 136,
                              render: (_, record) => (
                                <Space>
                                  <Button size="small" onClick={() => openEditTemplate(record)}>编辑</Button>
                                  {record.builtIn ? null : (
                                    <Popconfirm title="确认删除这条通知模板吗？" onConfirm={() => removeTemplate(record.id)}>
                                      <Button size="small" danger>删除</Button>
                                    </Popconfirm>
                                  )}
                                </Space>
                              ),
                            },
                          ]}
                        />
                      </>
                    ),
                  },
                ]}
              />
            </Card>
          </section>
        </div>
              ),
            }] : []),
          ]}
        />
      {adminScope ? (
      <>
        <Modal
        title={previewTitle}
        open={previewOpen}
        onCancel={() => setPreviewOpen(false)}
        footer={(
          <Space>
            <Button onClick={() => setPreviewOpen(false)}>关闭</Button>
            <Button type="primary" onClick={() => copyText(previewContent).then(() => message.success('内容已复制')).catch(() => message.error('复制失败，请手动复制'))}>
              复制内容
            </Button>
          </Space>
        )}
      >
        <Input.TextArea readOnly rows={8} value={previewContent} className="font-mono" />
      </Modal>
      <Modal
        open={restrictionModalOpen}
        title={editingRestrictionId ? '编辑部署限制策略' : '新建部署限制策略'}
        okText="保存策略"
        cancelText="取消"
        onCancel={() => {
          setRestrictionModalOpen(false);
          setEditingRestrictionId(undefined);
          setRestrictionForm(emptyRestrictionPolicy());
        }}
        confirmLoading={saving}
        onOk={() => saveRestriction().catch(() => message.error('保存部署限制策略失败'))}
        width={720}
        destroyOnHidden
      >
        <Form layout="vertical">
          <div className="grid gap-3 md:grid-cols-2">
            <Form.Item label="策略名称" required className="!mb-0">
              <Input
                value={restrictionForm.name}
                onChange={(event) => setRestrictionForm({ ...restrictionForm, name: event.target.value })}
                placeholder="请输入策略名称"
              />
            </Form.Item>
            <Form.Item label="生效范围" required className="!mb-0">
              <Select
                value={restrictionForm.scopeType}
                options={restrictionScopeOptions}
                onChange={(value) => setRestrictionForm({ ...restrictionForm, scopeType: value, scopeIds: [] })}
              />
            </Form.Item>
            {restrictionForm.scopeType === 'PROJECT' ? (
              <Form.Item label="项目" required className="!mb-0">
                <Select
                  showSearch
                  value={restrictionForm.scopeIds?.[0]}
                  optionFilterProp="label"
                  options={projects.map((item) => ({ label: item.name, value: item.id }))}
                  onChange={(value) => setRestrictionForm({ ...restrictionForm, scopeIds: value ? [value] : [] })}
                  placeholder="请选择项目"
                />
              </Form.Item>
            ) : null}
            {restrictionForm.scopeType === 'PIPELINE' ? (
              <Form.Item label="流水线" required className="!mb-0 md:col-span-2">
                <Select
                  mode="multiple"
                  showSearch
                  value={restrictionForm.scopeIds || []}
                  optionLabelProp="label"
                  filterOption={(input, option) => {
                    const keyword = input.trim().toLowerCase();
                    if (!keyword) {
                      return true;
                    }
                    const label = String(option?.label || '').toLowerCase();
                    const tags = ((option?.importantTags || []) as string[]).join(' ').toLowerCase();
                    return label.includes(keyword) || tags.includes(keyword);
                  }}
                  options={pipelines.map((item) => ({
                    label: item.name,
                    value: item.id,
                    importantTags: item.importantTags || [],
                  }))}
                  optionRender={(option) => {
                    const tags = (option.data.importantTags || []) as string[];
                    return (
                      <PipelineNameWithTags name={String(option.data.label || '')} importantTags={tags} />
                    );
                  }}
                  onChange={(value) => setRestrictionForm({ ...restrictionForm, scopeIds: value })}
                  placeholder="请选择流水线"
                />
              </Form.Item>
            ) : null}
            <div className="md:col-span-2 rounded-2xl border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-900/70">
              <div className="mb-3 flex items-center justify-between gap-3">
                <div>
                  <div className="text-sm font-semibold text-slate-800 dark:text-slate-100">每周时间段</div>
                  <div className="mt-1 text-xs text-slate-500 dark:text-slate-400">可添加多个时间段，多个时间段之间为“或”。不添加则不限制星期和时段。</div>
                </div>
                <Button size="small" onClick={addWeeklyWindow}>添加时间段</Button>
              </div>
              <div className="space-y-3">
                {(restrictionForm.weeklyWindows || []).map((window, index) => (
                  <div key={`${index}-${window.startTime || ''}-${window.endTime || ''}`} className="rounded-xl border border-slate-200 bg-white p-3 dark:border-slate-700 dark:bg-slate-950/40">
                    <div className="mb-3 flex items-center justify-between gap-3">
                      <span className="text-xs font-semibold text-slate-500 dark:text-slate-400">时间段 {index + 1}</span>
                      <Button size="small" danger onClick={() => removeWeeklyWindow(index)}>删除</Button>
                    </div>
                    <div className="grid gap-3 md:grid-cols-2">
                      <Form.Item label="星期" required className="!mb-0 md:col-span-3">
                        <Select
                          mode="multiple"
                          value={window.daysOfWeek || []}
                          options={weekdayOptions}
                          onChange={(value) => updateWeeklyWindow(index, { daysOfWeek: value })}
                          placeholder="请选择星期"
                        />
                      </Form.Item>
                      <Form.Item label="开始时间" className="!mb-0">
                        <TimePicker
                          format="HH:mm"
                          value={restrictionTimeValue(window.startTime)}
                          className="!w-full"
                          placeholder="不选则为 00:00"
                          onChange={(value) => updateWeeklyWindow(index, { startTime: value ? value.format('HH:mm') : '' })}
                        />
                      </Form.Item>
                      <Form.Item label="结束时间" className="!mb-0">
                        <TimePicker
                          format="HH:mm"
                          value={restrictionTimeValue(window.endTime)}
                          className="!w-full"
                          placeholder="不选则为 23:59"
                          onChange={(value) => updateWeeklyWindow(index, { endTime: value ? value.format('HH:mm') : '' })}
                        />
                      </Form.Item>
                      <div className="md:col-span-2 rounded-xl border border-slate-200 bg-slate-50 p-3 dark:border-slate-700 dark:bg-slate-900/60">
                        <div className="flex flex-wrap items-center justify-between gap-3">
                          <div>
                            <div className="text-xs font-semibold text-slate-600 dark:text-slate-300">每小时分钟窗口</div>
                            <div className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                              {window.startMinute == null && window.endMinute == null ? '未设置，表示该时间段内 0-59 分钟都禁止部署。' : '仅在该时间段内匹配指定分钟范围。'}
                            </div>
                          </div>
                          {window.startMinute == null && window.endMinute == null ? (
                            <Button size="small" onClick={() => updateWeeklyWindow(index, { startMinute: 0, endMinute: 59 })}>设置分钟窗口</Button>
                          ) : (
                            <Button size="small" onClick={() => updateWeeklyWindow(index, { startMinute: null, endMinute: null })}>匹配整小时</Button>
                          )}
                        </div>
                        {window.startMinute != null || window.endMinute != null ? (
                          <div className="mt-3 grid gap-3 md:grid-cols-2">
                            <Form.Item label="开始分钟" className="!mb-0">
                              <InputNumber
                                min={0}
                                max={59}
                                precision={0}
                                value={window.startMinute}
                                className="!w-full"
                                onChange={(value) => updateWeeklyWindow(index, { startMinute: value })}
                              />
                            </Form.Item>
                            <Form.Item label="结束分钟" className="!mb-0">
                              <InputNumber
                                min={0}
                                max={59}
                                precision={0}
                                value={window.endMinute}
                                className="!w-full"
                                onChange={(value) => updateWeeklyWindow(index, { endMinute: value })}
                              />
                            </Form.Item>
                          </div>
                        ) : null}
                      </div>
                    </div>
                  </div>
                ))}
                {(restrictionForm.weeklyWindows || []).length === 0 ? (
                  <div className="rounded-xl border border-dashed border-slate-300 px-4 py-5 text-center text-sm text-slate-500 dark:border-slate-700 dark:text-slate-400">
                    未添加每周时间段，策略不限制星期和时段。
                  </div>
                ) : null}
              </div>
            </div>
          </div>
          <Form.Item label="限制原因" className="!mt-3">
            <Input.TextArea
              rows={3}
              value={restrictionForm.reason || ''}
              maxLength={500}
              showCount
              onChange={(event) => setRestrictionForm({ ...restrictionForm, reason: event.target.value })}
              placeholder="请输入命中限制时展示的原因"
            />
          </Form.Item>
          <Form.Item label="启用">
            <Switch
              checked={restrictionForm.enabled !== false}
              checkedChildren="启用"
              unCheckedChildren="停用"
              onChange={(checked) => setRestrictionForm({ ...restrictionForm, enabled: checked })}
            />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        open={apiTokenModalOpen}
        title="新建 API Token"
        okText="创建 Token"
        cancelText="取消"
        confirmLoading={apiTokenLoading}
        onCancel={() => {
          setApiTokenModalOpen(false);
          setApiTokenForm(emptyApiTokenForm);
        }}
        onOk={() => createApiToken().catch(() => message.error('创建 API Token 失败'))}
        destroyOnHidden
      >
        <Form layout="vertical">
          <Form.Item label="Token 名称" required>
            <Input
              value={apiTokenForm.name}
              onChange={(event) => setApiTokenForm({ ...apiTokenForm, name: event.target.value })}
              placeholder="例如：Codex 本地接入"
            />
          </Form.Item>
          <Form.Item label="归属用户">
            <Select
              allowClear
              showSearch
              value={apiTokenForm.userId}
              optionFilterProp="label"
              options={users.map((item) => ({
                label: `${item.displayName || item.username}（${item.username}）`,
                value: item.id,
              }))}
              onChange={(value) => setApiTokenForm({ ...apiTokenForm, userId: value })}
              placeholder="默认使用当前管理员"
            />
          </Form.Item>
          <Form.Item label="权限范围" required>
            <Select
              mode="multiple"
              value={apiTokenForm.scopes}
              optionLabelProp="label"
              options={apiTokenScopeOptions.map((item) => ({ label: item.label, value: item.value, description: item.description }))}
              optionRender={(option) => (
                <div>
                  <div className="text-sm font-medium text-slate-800 dark:text-slate-100">{option.data.label}</div>
                  <div className="mt-1 text-xs text-slate-500 dark:text-slate-400">{option.data.description}</div>
                </div>
              )}
              onChange={(value) => setApiTokenForm({ ...apiTokenForm, scopes: value })}
              placeholder="请选择权限范围"
            />
          </Form.Item>
          <Form.Item label="过期时间">
            <DatePicker
              showTime
              className="!w-full"
              value={apiTokenForm.expiresAt ? dayjs(apiTokenForm.expiresAt) : null}
              onChange={(value) => setApiTokenForm({ ...apiTokenForm, expiresAt: value ? value.format('YYYY-MM-DDTHH:mm:ss') : null })}
              placeholder="不选表示不过期"
            />
          </Form.Item>
          <div className="rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm leading-6 text-amber-800 dark:border-amber-500/30 dark:bg-amber-500/10 dark:text-amber-100">
            创建后只会显示一次完整 Token。后续列表只保留前缀，平台也不会保存明文。
          </div>
        </Form>
      </Modal>
      <Modal
        open={Boolean(createdApiToken)}
        title="API Token 已创建"
        okText="我已保存"
        cancelButtonProps={{ style: { display: 'none' } }}
        onOk={() => setCreatedApiToken('')}
        onCancel={() => setCreatedApiToken('')}
      >
        <div className="space-y-3">
          <div className="text-sm leading-6 text-slate-600 dark:text-slate-300">
            请现在复制保存。关闭后将无法再次查看完整 Token，只能撤销后重新创建。
          </div>
          <Input.TextArea readOnly rows={4} value={createdApiToken} className="font-mono" />
          <Button type="primary" onClick={() => copyText(createdApiToken).then(() => message.success('Token 已复制')).catch(() => message.error('复制失败，请手动复制'))}>
            复制 Token
          </Button>
        </div>
      </Modal>
      <Modal
        open={webhookModalOpen}
        title={editingWebhookId ? '编辑 Webhook 配置' : '新建 Webhook 配置'}
        onCancel={() => {
          setWebhookModalOpen(false);
          setEditingWebhookId(undefined);
          setWebhookForm(emptyWebhookConfig);
        }}
        onOk={saveWebhook}
      >
        <Form layout="vertical">
          <Form.Item label="名称" required>
            <Input value={webhookForm.name} onChange={(event) => setWebhookForm({ ...webhookForm, name: event.target.value })} placeholder="例如：研发群飞书机器人" />
          </Form.Item>
          <Form.Item label="通知渠道类型" required>
            <Select value={webhookForm.type} options={[{ label: '飞书', value: 'FEISHU' }]} onChange={(value) => setWebhookForm({ ...webhookForm, type: value })} placeholder="请选择通知渠道类型" />
          </Form.Item>
          <Form.Item label="描述">
            <Input.TextArea rows={2} value={webhookForm.description} onChange={(event) => setWebhookForm({ ...webhookForm, description: event.target.value })} placeholder="可选。说明 Webhook 对应的群或接收人。" />
          </Form.Item>
          <Form.Item label="Webhook 地址" required>
            <Input value={webhookForm.webhookUrl} onChange={(event) => setWebhookForm({ ...webhookForm, webhookUrl: event.target.value })} placeholder="请输入飞书机器人 Webhook 地址" />
          </Form.Item>
          <Form.Item label="签名密钥">
            <Input.Password
              value={webhookForm.secret}
              placeholder={editingWebhookId ? '留空则保持不变' : ''}
              onChange={(event) => setWebhookForm({ ...webhookForm, secret: event.target.value })}
            />
          </Form.Item>
          <Form.Item label="启用">
            <Switch checked={webhookForm.enabled} onChange={(checked) => setWebhookForm({ ...webhookForm, enabled: checked })} />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        open={templateModalOpen}
        title={editingTemplateId ? '编辑通知模板' : '新建通知模板'}
        onCancel={() => {
          setTemplateModalOpen(false);
          setEditingTemplateId(undefined);
          setTemplateForm(emptyTemplate);
        }}
        onOk={saveTemplate}
        width={820}
      >
        <Form layout="vertical">
          <Form.Item label="模板名称" required>
            <Input value={templateForm.name} onChange={(event) => setTemplateForm({ ...templateForm, name: event.target.value })} placeholder="例如：部署结束文本模板" />
          </Form.Item>
          <Form.Item label="描述">
            <Input.TextArea rows={2} value={templateForm.description} onChange={(event) => setTemplateForm({ ...templateForm, description: event.target.value })} placeholder="可选。说明模板适用的通知场景。" />
          </Form.Item>
          <Form.Item label="模板形式" required>
            <Select
              value={templateForm.templateMode}
              options={notificationTemplateModeOptions}
              onChange={(value) => setTemplateForm({ ...templateForm, templateMode: value })}
              placeholder="请选择模板形式"
            />
          </Form.Item>
          <Form.Item label="启用">
            <Switch checked={templateForm.enabled} onChange={(checked) => setTemplateForm({ ...templateForm, enabled: checked })} />
          </Form.Item>
          <Form.Item label="可用变量">
            <div className="flex flex-wrap gap-2">
              {notificationTemplateVariableOptions.map((item) => (
                <Button key={item.token} size="small" onClick={() => insertTemplateVariable(item.token)}>
                  {item.label}
                </Button>
              ))}
            </div>
          </Form.Item>
          <Form.Item label="消息模板" required>
            {templateForm.templateMode === 'FEISHU_CARD' ? (
              <div className="app-inline-alert app-inline-alert--warning mb-3 rounded-2xl px-4 py-3 text-xs leading-6">
                <div>卡片模式请直接填写飞书 `interactive` 卡片的 JSON 内容。</div>
                <div>
                  变量仍然可以照常写成 <code>{'{{pipelineName}}'}</code>、<code>{'{{detailUrl}}'}</code> 这样的占位符。
                </div>
                <div className="mt-2">
                  <Button size="small" onClick={() => setTemplateForm((current) => ({ ...current, messageTemplate: defaultFeishuCardTemplate }))}>
                    填入卡片示例
                  </Button>
                </div>
              </div>
            ) : null}
            <Input.TextArea
              ref={templateTextareaRef}
              rows={12}
              value={templateForm.messageTemplate}
              onChange={(event) => setTemplateForm({ ...templateForm, messageTemplate: event.target.value })}
              placeholder="请输入消息模板内容，可使用上方变量。"
            />
          </Form.Item>
        </Form>
      </Modal>
      </>
      ) : null}
    </>
  );
}
