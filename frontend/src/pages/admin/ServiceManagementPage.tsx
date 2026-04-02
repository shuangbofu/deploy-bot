import { useEffect, useMemo, useState } from 'react';
import { Button, Card, Input, Select, Space, Table, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import { servicesApi } from '../../api/services';
import EmptyPane from '../../components/EmptyPane';
import PageHeaderBar from '../../components/PageHeaderBar';
import StatusTag from '../../components/StatusTag';
import type { ServiceSummary } from '../../types/domain';
import { formatDateTime } from '../../utils/datetime';
import { formatDurationSince } from '../../utils/duration';

export default function ServiceManagementPage() {
  const [services, setServices] = useState<ServiceSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [actingId, setActingId] = useState<number>();
  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>();
  const [hostFilter, setHostFilter] = useState<string>();
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });
  const navigate = useNavigate();

  const loadServices = async () => {
    setLoading(true);
    try {
      setServices(await servicesApi.list());
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadServices().catch(() => message.error('加载服务失败'));
  }, []);

  useEffect(() => {
    const timer = window.setInterval(() => {
      loadServices().catch(() => message.error('刷新服务失败'));
    }, 15000);
    return () => window.clearInterval(timer);
  }, []);

  const triggerAction = async (id, action, successText) => {
    setActingId(id);
    try {
      const response = await servicesApi.action(id, action);
      await loadServices();
      message.success(successText);
      if ('id' in response && action !== 'stop' && 'pipeline' in response) {
        navigate(`/admin/deployments/${response.id}?from=services`);
      }
    } finally {
      setActingId(undefined);
    }
  };

  const filteredServices = useMemo(() => services.filter((item) => {
    const normalizedKeyword = keyword.trim().toLowerCase();
    if (normalizedKeyword) {
      const matched = [item.serviceName, item.pipeline?.name, item.pipeline?.project?.name]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(normalizedKeyword));
      if (!matched) {
        return false;
      }
    }
    if (statusFilter && item.status !== statusFilter) {
      return false;
    }
    if (hostFilter && (item.pipeline?.targetHost?.name || '本机') !== hostFilter) {
      return false;
    }
    return true;
  }), [services, keyword, statusFilter, hostFilter]);

  const hostOptions = useMemo(() => Array.from(new Set(
    services.map((item) => item.pipeline?.targetHost?.name || '本机'),
  )).map((item) => ({ label: item, value: item })), [services]);

  return (
    <>
      <PageHeaderBar
        title="服务管理"
        description="查看受管服务状态，并执行启动、停止和重启操作。"
        extra={<Button onClick={() => loadServices().catch(() => message.error('刷新失败'))}>刷新</Button>}
      />
      <div className="app-page-scroll">
      <Card className="app-card">
        <div className="mb-4 grid grid-cols-1 gap-3 xl:grid-cols-4">
          <Input
            value={keyword}
            placeholder="搜索服务名 / 流水线 / 项目"
            onChange={(event) => {
              setKeyword(event.target.value);
              setPagination((previous) => ({ ...previous, current: 1 }));
            }}
          />
          <Select
            showSearch
            optionFilterProp="label"
            allowClear
            value={statusFilter}
            placeholder="筛选状态"
            options={[
              { label: '运行中', value: 'RUNNING' },
              { label: '成功', value: 'SUCCESS' },
              { label: '失败', value: 'FAILED' },
              { label: '已停止', value: 'STOPPED' },
              { label: '待执行', value: 'PENDING' },
            ]}
            onChange={(value) => {
              setStatusFilter(value);
              setPagination((previous) => ({ ...previous, current: 1 }));
            }}
          />
          <Select
            showSearch
            optionFilterProp="label"
            allowClear
            value={hostFilter}
            placeholder="筛选主机"
            options={hostOptions}
            onChange={(value) => {
              setHostFilter(value);
              setPagination((previous) => ({ ...previous, current: 1 }));
            }}
          />
          <div className="flex items-center">
            <Button
              onClick={() => {
                setKeyword('');
                setStatusFilter(undefined);
                setHostFilter(undefined);
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
          scroll={{ x: 960 }}
          dataSource={filteredServices}
          locale={{ emptyText: <EmptyPane description="当前没有可管理的服务。只有模板启用了进程监控并成功记录 PID 后，服务才会出现在这里。" /> }}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total: filteredServices.length,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`,
            onChange: (current, pageSize) => setPagination({ current, pageSize }),
          }}
          columns={[
              { title: '服务名', dataIndex: 'serviceName' },
              { title: '流水线', render: (_, row) => row.pipeline?.name || '-' },
              { title: '项目', render: (_, row) => row.pipeline?.project?.name || '-' },
              { title: '主机', render: (_, row) => row.pipeline?.targetHost?.name || '本机' },
              { title: 'PID', dataIndex: 'currentPid' },
              { title: '状态', render: (_, row) => <StatusTag status={row.status} runningLabel="运行中" /> },
              { title: '活跃时间', render: (_, row) => formatDurationSince(row.activeSince, row.status === 'RUNNING' ? undefined : row.lastHeartbeatAt) },
              { title: '最近心跳', render: (_, row) => formatDateTime(row.lastHeartbeatAt) },
              { title: '最近更新', render: (_, row) => formatDateTime(row.updatedAt) },
              {
                title: '操作',
                width: 260,
                render: (_, row) => (
                  <Space wrap>
                    {row.status === 'RUNNING' ? (
                      <>
                        <Button
                          size="small"
                          onClick={() => triggerAction(row.id, 'restart', '服务重启任务已触发').catch(() => message.error('重启服务失败'))}
                          loading={actingId === row.id}
                        >
                          重启
                        </Button>
                        <Button
                          size="small"
                          danger
                          onClick={() => triggerAction(row.id, 'stop', '服务已停止').catch(() => message.error('停止服务失败'))}
                          loading={actingId === row.id}
                        >
                          停止
                        </Button>
                      </>
                    ) : null}
                    {row.status !== 'RUNNING' ? (
                      <Button
                        size="small"
                        onClick={() => triggerAction(row.id, 'start', '服务启动任务已触发').catch(() => message.error('启动服务失败'))}
                        loading={actingId === row.id}
                      >
                        启动
                      </Button>
                    ) : null}
                    <Button
                      size="small"
                      disabled={!row.lastDeployment?.id}
                      onClick={() => row.lastDeployment?.id && navigate(`/admin/deployments/${row.lastDeployment.id}?from=services`)}
                    >
                      查看详情
                    </Button>
                  </Space>
                ),
              },
          ]}
        />
      </Card>
      </div>
    </>
  );
}
