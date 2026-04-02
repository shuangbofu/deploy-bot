import { useEffect, useMemo, useRef, useState } from 'react';
import { Button, Card, Collapse, Form, Input, Modal, Popconfirm, Select, Space, Switch, Table, Tabs, Typography, message } from 'antd';
import { notificationTemplatesApi } from '../../api/notificationTemplates';
import { notificationWebhookConfigsApi } from '../../api/notificationWebhookConfigs';
import { systemSettingsApi } from '../../api/systemSettings';
import type {
  NotificationTemplatePayload,
  NotificationTemplateSummary,
  NotificationWebhookConfigPayload,
  NotificationWebhookConfigSummary,
  SystemSettingsPayload,
} from '../../api/types';
import EmptyPane from '../../components/EmptyPane';
import PageHeaderBar from '../../components/PageHeaderBar';
import { getNotificationChannelTypeLabel, notificationTemplateVariableOptions } from '../../constants/notification';
import { copyText } from '../../utils/clipboard';

const emptySettings: SystemSettingsPayload = {
  gitExecutable: 'git',
  gitSshPublicKey: '',
  gitSshKnownHosts: '',
  hostSshPublicKey: '',
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
  messageTemplate: '',
  enabled: true,
};

export default function SystemSettingsPage() {
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
    loadSettings().catch(() => message.error('加载系统设置失败'));
    loadWebhookConfigs().catch(() => message.error('加载 Webhook 配置失败'));
    loadTemplates().catch(() => message.error('加载通知模板失败'));
  }, []);

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
        description="集中维护平台级 Git、主机和通知相关配置。"
        extra={<Button type="primary" loading={saving} onClick={() => saveSettings().catch(() => message.error('保存系统设置失败'))}>保存设置</Button>}
      />
      <div className="app-page-scroll">
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
              <div className="text-lg font-semibold text-slate-900">通知</div>
              <div className="mt-1 text-sm text-slate-500">维护可复用的 Webhook 配置和通知模板，通知配置页直接引用这里的基础配置。</div>
            </div>
            <Card className="app-card">
              <Tabs
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
                                  <Button size="small" type="link" onClick={() => openEditWebhook(record)}>编辑</Button>
                                  <Popconfirm title="确认删除这条 Webhook 配置吗？" onConfirm={() => removeWebhook(record.id)}>
                                    <Button size="small" type="link" danger>删除</Button>
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
                            { title: '描述', render: (_, record) => record.description || '-', width: 200 },
                            { title: '模板内容', render: (_, record) => <div className="line-clamp-3 whitespace-pre-wrap text-xs text-slate-600">{record.messageTemplate}</div> },
                            { title: '状态', render: (_, record) => enabledStatus(record.enabled), width: 100 },
                            {
                              title: '操作',
                              width: 160,
                              render: (_, record) => (
                                <Space>
                                  <Button size="small" type="link" onClick={() => openEditTemplate(record)}>编辑</Button>
                                  {record.builtIn ? null : (
                                    <Popconfirm title="确认删除这条通知模板吗？" onConfirm={() => removeTemplate(record.id)}>
                                      <Button size="small" type="link" danger>删除</Button>
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
            <Input.TextArea
              ref={templateTextareaRef}
              rows={12}
              value={templateForm.messageTemplate}
              onChange={(event) => setTemplateForm({ ...templateForm, messageTemplate: event.target.value })}
            />
          </Form.Item>
        </Form>
      </Modal>
      </div>
    </>
  );
}
