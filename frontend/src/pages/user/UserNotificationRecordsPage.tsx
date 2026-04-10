import { useEffect, useState } from 'react';
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
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });
  const [total, setTotal] = useState(0);

  const loadRecords = async () => {
    setLoading(true);
    try {
      const result = await notificationRecordsApi.listMinePage({
        page: pagination.current,
        pageSize: pagination.pageSize,
        channelType: channelTypeFilter,
        eventType: eventTypeFilter,
      });
      setRecords(result.items);
      setTotal(result.total);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadRecords().catch(() => message.error('加载通知记录失败'));
  }, [pagination.current, pagination.pageSize, channelTypeFilter, eventTypeFilter]);

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
              onChange={(value) => {
                setChannelTypeFilter(value);
                setPagination((previous) => ({ ...previous, current: 1 }));
              }}
            />
            <Select
              allowClear
              value={eventTypeFilter}
              placeholder="筛选通知类型"
              options={eventTypeOptions.map((item) => ({ label: item.label, value: item.value }))}
              onChange={(value) => {
                setEventTypeFilter(value);
                setPagination((previous) => ({ ...previous, current: 1 }));
              }}
            />
            <div className="flex items-center">
              <Button
                onClick={() => {
                  setChannelTypeFilter(undefined);
                  setEventTypeFilter(undefined);
                  setPagination((previous) => ({ ...previous, current: 1 }));
                }}
              >
                重置条件
              </Button>
            </div>
          </div>
          <Table
            rowKey="id"
            loading={loading}
            dataSource={records}
            locale={{ emptyText: <EmptyPane description="还没有通知发送记录。" /> }}
            pagination={{
              current: pagination.current,
              pageSize: pagination.pageSize,
              total,
              showSizeChanger: true,
              showTotal: (total) => `共 ${total} 条`,
              onChange: (current, pageSize) => setPagination({ current, pageSize }),
            }}
            columns={[
              { title: '编号', dataIndex: 'id', width: 90 },
              { title: '通知时间', render: (_, record) => formatDateTime(record.createdAt), width: 200 },
              { title: '通知配置', render: (_, record) => record.channelName || record.channel?.name || '-', width: 180 },
              { title: '通知渠道类型', render: (_, record) => record.channel?.type === 'FEISHU' ? '飞书' : record.channel?.type || '-', width: 140 },
              {
                title: '通知人',
                render: (_, record) => record.deployment?.triggeredByDisplayName || record.deployment?.triggeredBy || '-',
                width: 140,
              },
              { title: '流水线', render: (_, record) => record.pipelineName || record.deployment?.pipeline?.name || '-', width: 180 },
              { title: '通知类型', render: (_, record) => record.eventType === 'DEPLOYMENT_STARTED' ? '开始通知' : '结束通知', width: 140 },
              { title: '结果', render: (_, record) => record.status === 'SUCCESS' ? '成功' : '失败', width: 100 },
              {
                title: '失败详情',
                render: (_, record) => (
                  <div className="whitespace-pre-wrap break-all text-xs text-slate-600">
                    {record.errorMessage || record.responseMessage ? (
                      <>
                        {record.errorMessage ? <div><span className="font-semibold text-slate-700">失败原因：</span>{record.errorMessage}</div> : null}
                        {record.responseMessage ? <div><span className="font-semibold text-slate-700">响应信息：</span>{record.responseMessage}</div> : null}
                      </>
                    ) : '-'}
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
