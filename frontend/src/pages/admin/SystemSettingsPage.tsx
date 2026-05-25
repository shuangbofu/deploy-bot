import { useEffect, useMemo, useRef, useState } from 'react';
import { Button, Card, Collapse, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Switch, Table, Tabs, Typography, message } from 'antd';
import { notificationTemplatesApi } from '../../api/notificationTemplates';
import { notificationWebhookConfigsApi } from '../../api/notificationWebhookConfigs';
import { systemSettingsApi } from '../../api/systemSettings';
import type {
  NotificationTemplatePayload,
  NotificationTemplateSummary,
  NotificationWebhookConfigPayload,
  NotificationWebhookConfigSummary,
  NotificationTemplateMode,
  SystemSettingsPayload,
} from '../../api/types';
import EmptyPane from '../../components/EmptyPane';
import PageHeaderBar from '../../components/PageHeaderBar';
import { getNotificationChannelTypeLabel, notificationTemplateVariableOptions } from '../../constants/notification';
import { usePipelineHallPreferences } from '../../hooks/usePipelineHallPreferences';
import { useAppTheme } from '../../theme/AppThemeProvider';
import { useLayoutMode } from '../../theme/LayoutModeProvider';
import { copyText } from '../../utils/clipboard';
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

const notificationTemplateModeOptions: { label: string; value: NotificationTemplateMode }[] = [
  { label: '文本', value: 'TEXT' },
  { label: '飞书卡片', value: 'FEISHU_CARD' },
];

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
  const [webhookLoading, setWebhookLoading] = useState(false);
  const [templateLoading, setTemplateLoading] = useState(false);
  const [webhookModalOpen, setWebhookModalOpen] = useState(false);
  const [templateModalOpen, setTemplateModalOpen] = useState(false);
  const [editingWebhookId, setEditingWebhookId] = useState<number>();
  const [editingTemplateId, setEditingTemplateId] = useState<number>();
  const [webhookForm, setWebhookForm] = useState<NotificationWebhookConfigPayload>(emptyWebhookConfig);
  const [templateForm, setTemplateForm] = useState<NotificationTemplatePayload>(emptyTemplate);
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
    });
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

  useEffect(() => {
    if (!adminScope) {
      return;
    }
    loadSettings().catch(() => message.error('加载系统设置失败'));
    loadWebhookConfigs().catch(() => message.error('加载 Webhook 配置失败'));
    loadTemplates().catch(() => message.error('加载通知模板失败'));
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
        extra={adminScope ? <Button type="primary" loading={saving} onClick={() => saveSettings().catch(() => message.error('保存系统设置失败'))}>保存设置</Button> : null}
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
                        <span className="settings-choice__description">导航层级更稳定，适合管理端常驻操作。</span>
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
                            <Button onClick={() => loadWebhookConfigs().catch(() => message.error('加载 Webhook 配置失败'))}>刷新</Button>
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
                              width: 160,
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
                            <Button onClick={() => loadTemplates().catch(() => message.error('加载通知模板失败'))}>刷新</Button>
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
                              width: 160,
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
            <Input value={webhookForm.name} onChange={(event) => setWebhookForm({ ...webhookForm, name: event.target.value })} />
          </Form.Item>
          <Form.Item label="通知渠道类型" required>
            <Select value={webhookForm.type} options={[{ label: '飞书', value: 'FEISHU' }]} onChange={(value) => setWebhookForm({ ...webhookForm, type: value })} />
          </Form.Item>
          <Form.Item label="描述">
            <Input.TextArea rows={2} value={webhookForm.description} onChange={(event) => setWebhookForm({ ...webhookForm, description: event.target.value })} />
          </Form.Item>
          <Form.Item label="Webhook 地址" required>
            <Input value={webhookForm.webhookUrl} onChange={(event) => setWebhookForm({ ...webhookForm, webhookUrl: event.target.value })} />
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
            <Input value={templateForm.name} onChange={(event) => setTemplateForm({ ...templateForm, name: event.target.value })} />
          </Form.Item>
          <Form.Item label="描述">
            <Input.TextArea rows={2} value={templateForm.description} onChange={(event) => setTemplateForm({ ...templateForm, description: event.target.value })} />
          </Form.Item>
          <Form.Item label="模板形式" required>
            <Select
              value={templateForm.templateMode}
              options={notificationTemplateModeOptions}
              onChange={(value) => setTemplateForm({ ...templateForm, templateMode: value })}
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
              <div className="mb-3 rounded-2xl bg-slate-50 px-4 py-3 text-xs leading-6 text-slate-600">
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
            />
          </Form.Item>
        </Form>
      </Modal>
      </>
      ) : null}
    </>
  );
}
