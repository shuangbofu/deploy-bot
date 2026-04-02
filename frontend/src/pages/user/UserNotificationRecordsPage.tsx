import { useEffect, useMemo, useState } from 'react';
import { Button, Card, Select, Table, message } from 'antd';
import { notificationRecordsApi } from '../../api/notificationRecords';
import type { NotificationDeliveryRecordSummary } from '../../api/types';
import EmptyPane from '../../components/EmptyPane';
import PageHeaderBar from '../../components/PageHeaderBar';
import { formatDateTime } from '../../utils/datetime';

const eventTypeOptions = [
  { label: '开始通知', value: 'DEPLOYMENT_STARTED' },
  { label: '结束通知', value: 'DEPLOYMENT_FINISHED' },
] as const;

export default function UserNotificationRecordsPage() {
  const [records, setRecords] = useState<NotificationDeliveryRecordSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [channelTypeFilter, setChannelTypeFilter] = useState<'FEISHU' | undefined>(undefined);
  const [eventTypeFilter, setEventTypeFilter] = useState<'DEPLOYMENT_STARTED' | 'DEPLOYMENT_FINISHED' | undefined>(undefined);

  const loadRecords = async () => {
    setLoading(true);
    try {
      setRecords(await notificationRecordsApi.listMine());
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadRecords().catch(() => message.error('加载通知记录失败'));
  }, []);

  const filteredRecords = useMemo(() => records.filter((item) => {
    if (channelTypeFilter && item.channel?.type !== channelTypeFilter) {
      return false;
    }
    if (eventTypeFilter && item.eventType !== eventTypeFilter) {
      return false;
    }
    return true;
  }), [records, channelTypeFilter, eventTypeFilter]);

  return (
    <>
      <PageHeaderBar
        title="通知记录"
        description="查看当前账号相关部署的通知发送结果。"
        extra={<Button onClick={() => loadRecords().catch(() => message.error('加载通知记录失败'))}>刷新</Button>}
      />
      <div className="app-page-scroll">
        <Card className="app-card">
          <div className="mb-4 grid grid-cols-1 gap-3 xl:grid-cols-3">
            <Select
              allowClear
              value={channelTypeFilter}
              placeholder="筛选通知渠道类型"
              options={[{ label: '飞书', value: 'FEISHU' }]}
              onChange={setChannelTypeFilter}
            />
            <Select
              allowClear
              value={eventTypeFilter}
              placeholder="筛选通知类型"
              options={eventTypeOptions.map((item) => ({ label: item.label, value: item.value }))}
              onChange={setEventTypeFilter}
            />
            <div className="flex items-center">
              <Button
                onClick={() => {
                  setChannelTypeFilter(undefined);
                  setEventTypeFilter(undefined);
                }}
              >
                重置条件
              </Button>
            </div>
          </div>
          <Table
            rowKey="id"
            loading={loading}
            dataSource={filteredRecords}
            locale={{ emptyText: <EmptyPane description="还没有通知发送记录。" /> }}
            pagination={{ showSizeChanger: true, showTotal: (total) => `共 ${total} 条` }}
            columns={[
              { title: '时间', render: (_, record) => formatDateTime(record.createdAt), width: 170 },
              { title: '通知配置', render: (_, record) => record.channelName || record.channel?.name || '-', width: 180 },
              { title: '通知渠道类型', render: (_, record) => record.channel?.type === 'FEISHU' ? '飞书' : record.channel?.type || '-', width: 140 },
              { title: '流水线', render: (_, record) => record.pipelineName || record.deployment?.pipeline?.name || '-', width: 180 },
              { title: '通知类型', render: (_, record) => record.eventType === 'DEPLOYMENT_STARTED' ? '开始通知' : '结束通知', width: 140 },
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
        </Card>
      </div>
    </>
  );
}
