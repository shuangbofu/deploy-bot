import { useEffect, useMemo, useState } from 'react';
import { Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Switch, Table, Tabs, message } from 'antd';
import { notificationRecordsApi } from '../../api/notificationRecords';
import { notificationsApi } from '../../api/notifications';
import { notificationWebhookConfigsApi } from '../../api/notificationWebhookConfigs';
import { notificationTemplatesApi } from '../../api/notificationTemplates';
import type {
  NotificationChannelSummary,
  NotificationDeliveryRecordSummary,
  NotificationPayload,
  NotificationTemplateSummary,
  NotificationWebhookConfigSummary,
} from '../../api/types';
import EmptyPane from '../../components/EmptyPane';
import PageHeaderBar from '../../components/PageHeaderBar';
import {
  getNotificationChannelTypeLabel,
  getNotificationEventTypeLabel,
  notificationEventTypeOptions,
} from '../../constants/notification';
import { formatDateTime } from '../../utils/datetime';

const emptyChannel: NotificationPayload = {
  name: '',
  description: '',
  type: 'FEISHU',
  eventType: 'DEPLOYMENT_FINISHED',
  webhookConfigId: undefined,
  templateId: undefined,
  messageTemplate: '',
  enabled: true,
};

function renderEnabledStatus(enabled: boolean) {
  return (
    <span className="status-chip">
      <span className={`status-dot ${enabled ? 'status-dot--success' : 'status-dot--pending'}`} />
      <span>{enabled ? '启用' : '停用'}</span>
    </span>
  );
}

export default function NotificationAdminPage() {
  const [templates, setTemplates] = useState<NotificationTemplateSummary[]>([]);
  const [webhookConfigs, setWebhookConfigs] = useState<NotificationWebhookConfigSummary[]>([]);
  const [notifications, setNotifications] = useState<NotificationChannelSummary[]>([]);
  const [records, setRecords] = useState<NotificationDeliveryRecordSummary[]>([]);
  const [channelsLoading, setChannelsLoading] = useState(false);
  const [recordsLoading, setRecordsLoading] = useState(false);

  const [channelModalOpen, setChannelModalOpen] = useState(false);
  const [editingChannelId, setEditingChannelId] = useState<number>();
  const [channelForm, setChannelForm] = useState<NotificationPayload>(emptyChannel);

  const [channelKeyword, setChannelKeyword] = useState('');
  const [channelTypeFilter, setChannelTypeFilter] = useState<'FEISHU' | undefined>(undefined);
  const [channelEventTypeFilter, setChannelEventTypeFilter] = useState<'DEPLOYMENT_STARTED' | 'DEPLOYMENT_FINISHED' | undefined>(undefined);
  const [channelEnabledFilter, setChannelEnabledFilter] = useState<boolean | undefined>(undefined);

  const [recordChannelTypeFilter, setRecordChannelTypeFilter] = useState<'FEISHU' | undefined>(undefined);
  const [recordEventTypeFilter, setRecordEventTypeFilter] = useState<'DEPLOYMENT_STARTED' | 'DEPLOYMENT_FINISHED' | undefined>(undefined);

  const loadTemplates = async () => {
    setTemplates(await notificationTemplatesApi.list());
  };

  const loadWebhookConfigs = async () => {
    setWebhookConfigs(await notificationWebhookConfigsApi.list());
  };

  const loadNotifications = async () => {
    setChannelsLoading(true);
    try {
      setNotifications(await notificationsApi.list());
    } finally {
      setChannelsLoading(false);
    }
  };

  const loadRecords = async () => {
    setRecordsLoading(true);
    try {
      setRecords(await notificationRecordsApi.list());
    } finally {
      setRecordsLoading(false);
    }
  };

  useEffect(() => {
    loadTemplates().catch(() => message.error('加载通知模板失败'));
    loadWebhookConfigs().catch(() => message.error('加载 Webhook 配置失败'));
    loadNotifications().catch(() => message.error('加载通知配置失败'));
    loadRecords().catch(() => message.error('加载通知记录失败'));
  }, []);

  const filteredNotifications = useMemo(() => notifications.filter((item) => {
    const normalizedKeyword = channelKeyword.trim().toLowerCase();
    if (normalizedKeyword) {
      const matched = [
        item.name,
        item.description,
        item.webhookConfig?.name,
        getNotificationChannelTypeLabel(item.type),
        getNotificationEventTypeLabel(item.eventType),
      ].some((value) => String(value || '').toLowerCase().includes(normalizedKeyword));
      if (!matched) {
        return false;
      }
    }
    if (channelTypeFilter && item.type !== channelTypeFilter) {
      return false;
    }
    if (channelEventTypeFilter && item.eventType !== channelEventTypeFilter) {
      return false;
    }
    if (channelEnabledFilter !== undefined && item.enabled !== channelEnabledFilter) {
      return false;
    }
    return true;
  }), [notifications, channelKeyword, channelTypeFilter, channelEventTypeFilter, channelEnabledFilter]);

  const filteredRecords = useMemo(() => records.filter((item) => {
    if (recordChannelTypeFilter && item.channel?.type !== recordChannelTypeFilter) {
      return false;
    }
    if (recordEventTypeFilter && item.eventType !== recordEventTypeFilter) {
      return false;
    }
    return true;
  }), [records, recordChannelTypeFilter, recordEventTypeFilter]);

  const openCreateChannel = () => {
    setEditingChannelId(undefined);
    setChannelForm(emptyChannel);
    setChannelModalOpen(true);
  };

  const openEditChannel = (record: NotificationChannelSummary) => {
    setEditingChannelId(record.id);
    setChannelForm({
      name: record.name,
      description: record.description || '',
      type: record.type,
      eventType: record.eventType,
      webhookConfigId: record.webhookConfig?.id,
      templateId: record.template?.id,
      messageTemplate: record.messageTemplate || '',
      enabled: record.enabled,
    });
    setChannelModalOpen(true);
  };

  const saveChannel = async () => {
    if (editingChannelId) {
      await notificationsApi.update(editingChannelId, channelForm);
    } else {
      await notificationsApi.create(channelForm);
    }
    setChannelModalOpen(false);
    setEditingChannelId(undefined);
    setChannelForm(emptyChannel);
    await loadNotifications();
    message.success(editingChannelId ? '通知配置已更新' : '通知配置已创建');
  };

  const removeChannel = async (id: number) => {
    await notificationsApi.remove(id);
    await loadNotifications();
    message.success('通知配置已删除');
  };

  const selectedTemplate = useMemo(
    () => templates.find((item) => item.id === channelForm.templateId),
    [templates, channelForm.templateId],
  );

  return (
    <>
      <PageHeaderBar
        title="通知"
        description="管理通知配置，以及每次发送的成功或失败记录。Webhook 配置和通知模板请到系统设置里维护。"
        extra={(
          <Button
            onClick={() => {
              loadTemplates().catch(() => message.error('加载通知模板失败'));
              loadWebhookConfigs().catch(() => message.error('加载 Webhook 配置失败'));
              loadNotifications().catch(() => message.error('加载通知配置失败'));
              loadRecords().catch(() => message.error('加载通知记录失败'));
            }}
          >
            刷新
          </Button>
        )}
      />
      <div className="app-page-scroll">
        <Card className="app-card">
          <Tabs
            items={[
              {
                key: 'configurations',
                label: '通知配置',
                children: (
                  <>
                    <div className="mb-4 grid grid-cols-1 gap-3 xl:grid-cols-5">
            <Input value={channelKeyword} placeholder="搜索名称 / 描述 / Webhook 配置 / 类型" onChange={(event) => setChannelKeyword(event.target.value)} />
            <Select
              allowClear
              value={channelTypeFilter}
              placeholder="筛选通知渠道类型"
              options={[{ label: '飞书', value: 'FEISHU' }]}
              onChange={setChannelTypeFilter}
            />
            <Select
              allowClear
              value={channelEventTypeFilter}
              placeholder="筛选通知类型"
              options={notificationEventTypeOptions}
              onChange={setChannelEventTypeFilter}
            />
            <Select
              allowClear
              value={channelEnabledFilter}
              placeholder="筛选状态"
              options={[{ label: '启用', value: true }, { label: '停用', value: false }]}
              onChange={setChannelEnabledFilter}
            />
            <div className="flex items-center gap-2">
              <Button onClick={() => {
                setChannelKeyword('');
                setChannelTypeFilter(undefined);
                setChannelEventTypeFilter(undefined);
                setChannelEnabledFilter(undefined);
              }}
              >
                重置条件
              </Button>
              <Button type="primary" onClick={openCreateChannel}>新建通知配置</Button>
            </div>
                    </div>
                    <Table
            rowKey="id"
            loading={channelsLoading}
            dataSource={filteredNotifications}
            locale={{ emptyText: <EmptyPane description="还没有通知配置，先新建一个开始或结束通知。" /> }}
            pagination={{ showSizeChanger: true, showTotal: (total) => `共 ${total} 条` }}
            columns={[
              { title: '名称', dataIndex: 'name', width: 180 },
              { title: '通知渠道类型', render: (_, record) => getNotificationChannelTypeLabel(record.type), width: 140 },
              { title: '通知类型', render: (_, record) => getNotificationEventTypeLabel(record.eventType), width: 140 },
              { title: 'Webhook 配置', render: (_, record) => record.webhookConfig?.name || '-', width: 180 },
              { title: '描述', render: (_, record) => record.description || '-', width: 220 },
              {
                title: '消息内容',
                render: (_, record) => (
                  record.messageTemplate
                    ? <div className="line-clamp-3 whitespace-pre-wrap text-xs text-slate-600">{record.messageTemplate}</div>
                    : <span className="text-slate-400">使用通知模板默认内容</span>
                ),
              },
              { title: '状态', render: (_, record) => renderEnabledStatus(record.enabled), width: 100 },
              {
                title: '操作',
                width: 160,
                render: (_, record) => (
                  <Space>
                    <Button size="small" type="link" onClick={() => openEditChannel(record)}>编辑</Button>
                    <Popconfirm title="确认删除这条通知配置吗？" onConfirm={() => removeChannel(record.id)}>
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
                key: 'records',
                label: '通知记录',
                children: (
                  <>
                    <div className="mb-4 grid grid-cols-1 gap-3 xl:grid-cols-3">
            <Select
              allowClear
              value={recordChannelTypeFilter}
              placeholder="筛选通知渠道类型"
              options={[{ label: '飞书', value: 'FEISHU' }]}
              onChange={setRecordChannelTypeFilter}
            />
            <Select
              allowClear
              value={recordEventTypeFilter}
              placeholder="筛选通知类型"
              options={notificationEventTypeOptions}
              onChange={setRecordEventTypeFilter}
            />
            <div className="flex items-center">
              <Button onClick={() => {
                setRecordChannelTypeFilter(undefined);
                setRecordEventTypeFilter(undefined);
              }}
              >
                重置条件
              </Button>
            </div>
                    </div>
                    <Table
            rowKey="id"
            loading={recordsLoading}
            dataSource={filteredRecords}
            locale={{ emptyText: <EmptyPane description="还没有通知记录。" /> }}
            pagination={{ showSizeChanger: true, showTotal: (total) => `共 ${total} 条` }}
            columns={[
              { title: '时间', render: (_, record) => formatDateTime(record.createdAt), width: 170 },
              { title: '通知配置', render: (_, record) => record.channelName || record.channel?.name || '-', width: 180 },
              { title: '通知渠道类型', render: (_, record) => record.channel?.type ? getNotificationChannelTypeLabel(record.channel.type) : '-', width: 140 },
              { title: '通知类型', render: (_, record) => getNotificationEventTypeLabel(record.eventType), width: 140 },
              { title: '流水线', render: (_, record) => record.pipelineName || record.deployment?.pipeline?.name || '-', width: 180 },
              { title: '结果', render: (_, record) => record.status === 'SUCCESS' ? '成功' : '失败', width: 100 },
              {
                title: '失败原因 / 响应',
                render: (_, record) => (
                  <div className="whitespace-pre-wrap break-all text-xs text-slate-600">
                    {record.errorMessage || record.responseMessage || '-'}
                  </div>
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
      </div>

      <Modal
        open={channelModalOpen}
        title={editingChannelId ? '编辑通知配置' : '新建通知配置'}
        onCancel={() => {
          setChannelModalOpen(false);
          setEditingChannelId(undefined);
          setChannelForm(emptyChannel);
        }}
        onOk={saveChannel}
        width={820}
      >
        <Form layout="vertical">
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <Form.Item label="名称" required>
              <Input value={channelForm.name} onChange={(event) => setChannelForm({ ...channelForm, name: event.target.value })} />
            </Form.Item>
            <Form.Item label="通知渠道类型" required>
              <Select value={channelForm.type} options={[{ label: '飞书', value: 'FEISHU' }]} onChange={(value) => setChannelForm({ ...channelForm, type: value })} />
            </Form.Item>
          </div>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <Form.Item label="通知类型" required>
              <Select
                value={channelForm.eventType}
                options={notificationEventTypeOptions}
                onChange={(value) => setChannelForm({ ...channelForm, eventType: value })}
              />
            </Form.Item>
            <Form.Item label="Webhook 配置" required>
              <Select
                value={channelForm.webhookConfigId}
                placeholder="选择一个已配置好的 Webhook"
                options={webhookConfigs
                  .filter((item) => item.enabled)
                  .map((item) => ({
                    label: (
                      <div className="flex items-center gap-2 py-0.5">
                        <span className="font-medium text-slate-800">{item.name}</span>
                        <span
                          className="rounded-full px-2 py-0.5 text-xs font-medium text-white"
                          style={{ backgroundColor: '#0f766e' }}
                        >
                          {getNotificationChannelTypeLabel(item.type)}
                        </span>
                      </div>
                    ),
                    value: item.id,
                  }))}
                onChange={(value) => setChannelForm({ ...channelForm, webhookConfigId: value })}
              />
            </Form.Item>
          </div>
          <Form.Item label="描述">
            <Input.TextArea rows={2} value={channelForm.description} onChange={(event) => setChannelForm({ ...channelForm, description: event.target.value })} />
          </Form.Item>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <Form.Item label="通知模板">
              <Select
                allowClear
                value={channelForm.templateId}
                placeholder="选择一个模板快速带出内容"
                options={templates.map((item) => ({
                  label: item.name,
                  value: item.id,
                }))}
                onChange={(value) => setChannelForm({ ...channelForm, templateId: value })}
              />
            </Form.Item>
            <Form.Item label="启用">
              <Switch checked={channelForm.enabled} onChange={(checked) => setChannelForm({ ...channelForm, enabled: checked })} />
            </Form.Item>
          </div>
          {selectedTemplate ? (
            <Form.Item label="当前模板内容">
              <div className="rounded-xl border border-slate-200 bg-slate-50 p-3 whitespace-pre-wrap text-xs text-slate-600">
                {selectedTemplate.messageTemplate}
              </div>
            </Form.Item>
          ) : null}
          <Form.Item label="消息内容覆盖">
            <Input.TextArea
              rows={10}
              value={channelForm.messageTemplate}
              placeholder="留空则直接使用上面选择的通知模板内容；如果这里填写了内容，会优先使用这里。"
              onChange={(event) => setChannelForm({ ...channelForm, messageTemplate: event.target.value })}
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
