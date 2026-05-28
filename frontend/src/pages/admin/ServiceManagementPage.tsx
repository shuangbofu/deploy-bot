import { useEffect, useMemo, useState } from 'react';
import { EllipsisOutlined } from '@ant-design/icons';
import { Button, Card, Dropdown, Input, Modal, Select, Space, Table, Tabs, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import { servicesApi } from '../../api/services';
import EmptyPane from '../../components/EmptyPane';
import PageHeaderBar from '../../components/PageHeaderBar';
import RefreshIconButton from '../../components/RefreshIconButton';
import PipelineNameWithTags from '../../components/PipelineNameWithTags';
import StatusTag from '../../components/StatusTag';
import type { ServicePidHistorySummary, ServiceProcessSummary, ServiceSummary } from '../../api/types';
import { formatDateTime } from '../../utils/datetime';
import { formatDurationSince } from '../../utils/duration';
import { getRequestErrorMessage } from '../../utils/requestError';

const PID_HISTORY_SOURCE_LABELS: Record<string, string> = {
  DEPLOYMENT_CONFIRMED: '部署接管',
  PRE_DEPLOY_STOP: '部署前停止',
  MANUAL_BIND: '手动绑定',
  MANUAL_STOP: '手动停止',
  HEARTBEAT_STOPPED: '心跳判停',
  HEARTBEAT_RECOVERED: '心跳恢复',
};

export default function ServiceManagementPage() {
  const [services, setServices] = useState<ServiceSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [actingId, setActingId] = useState<number>();
  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>();
  const [hostFilter, setHostFilter] = useState<string>();
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });
  const [bindModalOpen, setBindModalOpen] = useState(false);
  const [bindingService, setBindingService] = useState<ServiceSummary | null>(null);
  const [processes, setProcesses] = useState<ServiceProcessSummary[]>([]);
  const [processLoading, setProcessLoading] = useState(false);
  const [processKeyword, setProcessKeyword] = useState('');
  const [selectedPid, setSelectedPid] = useState<number>();
  const [pidHistoryMap, setPidHistoryMap] = useState<Record<number, ServicePidHistorySummary[]>>({});
  const [pidHistoryLoadingMap, setPidHistoryLoadingMap] = useState<Record<number, boolean>>({});
  const [historyModalOpen, setHistoryModalOpen] = useState(false);
  const [historyService, setHistoryService] = useState<ServiceSummary | null>(null);
  const [historyTabKey, setHistoryTabKey] = useState('switches');
  const [switchPagination, setSwitchPagination] = useState({ current: 1, pageSize: 8 });
  const [eventPagination, setEventPagination] = useState({ current: 1, pageSize: 8 });
  const [refreshError, setRefreshError] = useState('');
  const navigate = useNavigate();

  const loadServices = async () => {
    setLoading(true);
    try {
      setServices(await servicesApi.list());
      setRefreshError('');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadServices().catch(() => message.error('加载服务失败'));
  }, []);

  useEffect(() => {
    const timer = window.setInterval(() => {
      loadServices().catch((error) => setRefreshError(getRequestErrorMessage(error, '刷新服务失败')));
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

  const confirmServiceAction = (service: ServiceSummary, action: 'start' | 'stop' | 'restart') => {
    const actionLabelMap = {
      start: '启动',
      stop: '停止',
      restart: '重启',
    };
    const successTextMap = {
      start: '服务启动任务已触发',
      stop: '服务已停止',
      restart: '服务重启任务已触发',
    };
    const actionLabel = actionLabelMap[action];
    Modal.confirm({
      title: `${actionLabel}服务`,
      content: `确认${actionLabel}「${service.pipeline?.name || service.serviceName || `服务 #${service.id}`}」吗？`,
      okText: actionLabel,
      cancelText: '取消',
      okButtonProps: { danger: action === 'stop' },
      onOk: () => triggerAction(
        service.id,
        action,
        successTextMap[action],
      ).catch(() => message.error(`${actionLabel}服务失败`)),
    });
  };

  const handleMoreAction = (service: ServiceSummary, key: string) => {
    if (key === 'history') {
      openHistoryModal(service).catch(() => message.error('加载 PID 轨迹失败'));
      return;
    }
    if (key === 'start' || key === 'restart' || key === 'stop') {
      confirmServiceAction(service, key);
    }
  };

  const openBindProcess = async (service: ServiceSummary) => {
    setBindingService(service);
    setBindModalOpen(true);
    setProcessKeyword('');
    setSelectedPid(service.currentPid || undefined);
    setProcessLoading(true);
    try {
      setProcesses(await servicesApi.listProcesses(service.id));
    } finally {
      setProcessLoading(false);
    }
  };

  const bindProcess = async () => {
    if (!bindingService || !selectedPid) {
      message.warning('请选择要绑定的进程');
      return;
    }
    await servicesApi.bindProcess(bindingService.id, selectedPid);
    setBindModalOpen(false);
    setBindingService(null);
    setSelectedPid(undefined);
    setProcesses([]);
    await loadServices();
    message.success('进程已绑定');
  };

  const loadPidHistory = async (serviceId: number) => {
    if (pidHistoryMap[serviceId] || pidHistoryLoadingMap[serviceId]) {
      return;
    }
    setPidHistoryLoadingMap((previous) => ({ ...previous, [serviceId]: true }));
    try {
      const history = await servicesApi.listPidHistory(serviceId);
      setPidHistoryMap((previous) => ({ ...previous, [serviceId]: history }));
    } finally {
      setPidHistoryLoadingMap((previous) => ({ ...previous, [serviceId]: false }));
    }
  };

  const openHistoryModal = async (service: ServiceSummary) => {
    setHistoryService(service);
    setHistoryModalOpen(true);
    setHistoryTabKey('switches');
    setSwitchPagination({ current: 1, pageSize: 8 });
    setEventPagination({ current: 1, pageSize: 8 });
    await loadPidHistory(service.id);
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

  const filteredProcesses = useMemo(() => {
    const normalizedKeyword = processKeyword.trim().toLowerCase();
    if (!normalizedKeyword) {
      return processes;
    }
    return processes.filter((item) => [item.pid, item.command, item.commandLine]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(normalizedKeyword)));
  }, [processKeyword, processes]);

  const buildSwitchRows = (history: ServicePidHistorySummary[]) => {
    const chronologicalHistory = [...history].sort((left, right) => (
      new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime() || left.id - right.id
    ));
    const pendingStops: ServicePidHistorySummary[] = [];
    const rows: Array<{
      key: string;
      deploymentId: number | null;
      stoppedAt: string;
      attachedAt: string;
      oldPid: number | null;
      newPid: number | null;
      note: string;
    }> = [];
    chronologicalHistory.forEach((item) => {
      if (item.changeSource === 'PRE_DEPLOY_STOP') {
        pendingStops.push(item);
        return;
      }
      if (item.changeSource === 'DEPLOYMENT_CONFIRMED') {
        const stopped = pendingStops.pop();
        if (stopped) {
          rows.push({
            key: `${stopped.id}-${item.id}`,
            deploymentId: item.deploymentId ?? stopped.deploymentId,
            stoppedAt: stopped.createdAt,
            attachedAt: item.createdAt,
            oldPid: stopped.previousPid,
            newPid: item.currentPid ?? null,
            note: '本次部署先停止旧进程，再接管新进程。',
          });
          return;
        }
        rows.push({
          key: `attach-only-${item.id}`,
          deploymentId: item.deploymentId,
          stoppedAt: '',
          attachedAt: item.createdAt,
          oldPid: null,
          newPid: item.currentPid ?? null,
          note: '只记录到新进程接管，前面没有找到可配对的停止记录。',
        });
        return;
      }
    });
    pendingStops.forEach((stopped) => {
      rows.push({
        key: `${stopped.id}-pending`,
        deploymentId: stopped.deploymentId,
        stoppedAt: stopped.createdAt,
        attachedAt: '',
        oldPid: stopped.previousPid,
        newPid: null,
        note: '只记录到停止旧进程，后续还没有看到新的接管记录。',
      });
    });
    return rows.sort((left, right) => (
      new Date(right.stoppedAt || right.attachedAt).getTime() - new Date(left.stoppedAt || left.attachedAt).getTime()
    ));
  };

  const renderHistoryModal = () => {
    if (!historyService) {
      return null;
    }
    const history = pidHistoryMap[historyService.id] || [];
    const switchRows = buildSwitchRows(history);
    return (
      <div className="space-y-3">
        <div className="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 dark:border-slate-700 dark:bg-slate-900/70">
          <div className="text-base font-medium text-slate-800 dark:text-slate-100">
            <PipelineNameWithTags name={historyService.pipeline?.name || historyService.serviceName} importantTags={historyService.pipeline?.importantTags} fallback={`服务 #${historyService.id}`} />
          </div>
          <div className="mt-1 text-sm text-slate-500 dark:text-slate-400">
            {historyService.pipeline?.project?.name || '-'} / <PipelineNameWithTags name={historyService.pipeline?.name} importantTags={historyService.pipeline?.importantTags} /> / {historyService.pipeline?.targetHost?.name || '本机'}
          </div>
        </div>
        <Tabs
          className="app-soft-tabs"
          activeKey={historyTabKey}
          onChange={setHistoryTabKey}
          items={[
            {
              key: 'switches',
              label: '切换记录',
              children: (
                <Table
                  rowKey="key"
                  size="small"
                  loading={pidHistoryLoadingMap[historyService.id]}
                  dataSource={switchRows}
                  scroll={{ x: 860 }}
                  locale={{ emptyText: '当前还没有形成“旧 PID -> 新 PID”的部署切换记录。' }}
                  pagination={{
                    current: switchPagination.current,
                    pageSize: switchPagination.pageSize,
                    total: switchRows.length,
                    showSizeChanger: true,
                    showTotal: (total) => `共 ${total} 条`,
                    onChange: (current, pageSize) => setSwitchPagination({ current, pageSize }),
                  }}
                  columns={[
                    { title: '部署', width: 110, render: (_, row) => row.deploymentId ? `#${row.deploymentId}` : '-' },
                    { title: '停止时间', width: 170, render: (_, row) => formatDateTime(row.stoppedAt) },
                    { title: '接管时间', width: 170, render: (_, row) => row.attachedAt ? formatDateTime(row.attachedAt) : '-' },
                    { title: '旧 PID', width: 110, render: (_, row) => row.oldPid ?? '-' },
                    { title: '新 PID', width: 110, render: (_, row) => row.newPid ?? '-' },
                    { title: '说明', render: (_, row) => row.note },
                  ]}
                />
              ),
            },
            {
              key: 'events',
              label: 'PID 事件流',
              children: (
                <Table<ServicePidHistorySummary>
                  rowKey="id"
                  size="small"
                  loading={pidHistoryLoadingMap[historyService.id]}
                  dataSource={history}
                  scroll={{ x: 980 }}
                  locale={{ emptyText: '当前还没有 PID 变化记录。' }}
                  pagination={{
                    current: eventPagination.current,
                    pageSize: eventPagination.pageSize,
                    total: history.length,
                    showSizeChanger: true,
                    showTotal: (total) => `共 ${total} 条`,
                    onChange: (current, pageSize) => setEventPagination({ current, pageSize }),
                  }}
                  columns={[
                    { title: '时间', width: 170, render: (_, row) => formatDateTime(row.createdAt) },
                    { title: '来源', width: 140, render: (_, row) => PID_HISTORY_SOURCE_LABELS[row.changeSource] || '-' },
                    { title: '部署', width: 110, render: (_, row) => row.deploymentId ? `#${row.deploymentId}` : '-' },
                    { title: '本次 PID', width: 110, render: (_, row) => row.currentPid ?? row.previousPid ?? '-' },
                    { title: '原状态', width: 110, render: (_, row) => row.previousStatus ? <StatusTag status={row.previousStatus} runningLabel="运行中" /> : '-' },
                    { title: '新状态', width: 110, render: (_, row) => <StatusTag status={row.currentStatus} runningLabel="运行中" /> },
                    { title: '说明', render: (_, row) => row.note || '-' },
                  ]}
                />
              ),
            },
          ]}
        />
      </div>
    );
  };

  return (
    <>
      <PageHeaderBar
        title="服务管理"
        description="查看受管服务状态，并执行启动、停止和重启操作。"
        extra={<RefreshIconButton onClick={() => loadServices().catch(() => message.error('刷新失败'))} />}
      />
      <div className="app-page-scroll">
      <Card className="app-card">
        {refreshError ? (
          <div className="app-inline-alert app-inline-alert--warning mb-4 rounded-2xl px-4 py-3 text-sm">
            服务状态刷新异常：{refreshError}
          </div>
        ) : null}
        <div className="app-filter-grid">
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
              { title: '流水线', render: (_, row) => <PipelineNameWithTags name={row.pipeline?.name} importantTags={row.pipeline?.importantTags} /> },
              { title: '服务名', dataIndex: 'serviceName' },
              { title: '项目', render: (_, row) => row.pipeline?.project?.name || '-' },
              { title: '主机', render: (_, row) => row.pipeline?.targetHost?.name || '本机' },
              { title: 'PID', dataIndex: 'currentPid' },
              { title: '状态', render: (_, row) => <StatusTag status={row.status} runningLabel="运行中" /> },
              { title: '活跃时间', render: (_, row) => formatDurationSince(row.activeSince, row.status === 'RUNNING' ? undefined : row.lastHeartbeatAt) },
              { title: '最近心跳', render: (_, row) => formatDateTime(row.lastHeartbeatAt) },
              { title: '最近更新', render: (_, row) => formatDateTime(row.updatedAt) },
              {
                title: '操作',
                width: 198,
                className: 'service-table-actions-cell',
                onHeaderCell: () => ({ className: 'service-table-actions-cell' }),
                onCell: () => ({ className: 'service-table-actions-cell' }),
                render: (_, row) => (
                  <Space className="service-table-actions" size={4}>
                    <Button
                      size="small"
                      onClick={() => openBindProcess(row).catch(() => message.error('加载进程列表失败'))}
                      loading={actingId === row.id}
                    >
                      绑定进程
                    </Button>
                    <Button
                      size="small"
                      disabled={!row.lastDeployment?.id}
                      onClick={() => row.lastDeployment?.id && navigate(`/admin/deployments/${row.lastDeployment.id}?from=services`)}
                    >
                      查看详情
                    </Button>
                    <Dropdown
                      trigger={['click']}
                      menu={{
                        items: [
                          { key: 'history', label: '查看轨迹' },
                          ...(row.status === 'RUNNING'
                            ? [
                              { type: 'divider' as const },
                              { key: 'restart', label: '重启服务' },
                              { key: 'stop', label: '停止服务', danger: true },
                            ]
                            : [
                              { type: 'divider' as const },
                              { key: 'start', label: '启动服务' },
                            ]),
                        ],
                        onClick: ({ key }) => handleMoreAction(row, key),
                      }}
                    >
                      <Button size="small" loading={actingId === row.id} icon={<EllipsisOutlined />} />
                    </Dropdown>
                  </Space>
                ),
              },
          ]}
        />
      </Card>
      </div>
      <Modal
        width={880}
        open={bindModalOpen}
        title={bindingService ? `绑定进程：${bindingService.pipeline?.name || bindingService.serviceName || `服务 #${bindingService.id}`}` : '绑定进程'}
        okText="绑定"
        cancelText="取消"
        onOk={() => bindProcess().catch(() => message.error('绑定进程失败'))}
        onCancel={() => {
          setBindModalOpen(false);
          setBindingService(null);
          setSelectedPid(undefined);
          setProcesses([]);
        }}
      >
        <div className="mb-3 rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600 dark:border-slate-700 dark:bg-slate-900/70 dark:text-slate-300">
          当前拉取主机：{bindingService?.pipeline?.targetHost?.name || '本机'}。请选择这台主机上已经存在的服务进程进行绑定。
        </div>
        <div className="mb-3 flex items-center justify-between gap-3">
          <Input.Search
            allowClear
            value={processKeyword}
            placeholder="搜索 PID / 命令 / 参数"
            onChange={(event) => setProcessKeyword(event.target.value)}
          />
          <RefreshIconButton onClick={() => bindingService && openBindProcess(bindingService).catch(() => message.error('刷新进程列表失败'))} />
        </div>
        <Table
          className="service-process-table"
          rowKey="pid"
          size="small"
          loading={processLoading}
          scroll={{ x: 760, y: 360 }}
          dataSource={filteredProcesses}
          pagination={{ pageSize: 8, showSizeChanger: false, showTotal: (total) => `共 ${total} 个进程` }}
          rowSelection={{
            type: 'radio',
            selectedRowKeys: selectedPid ? [selectedPid] : [],
            onChange: (keys) => setSelectedPid(Number(keys[0])),
          }}
          columns={[
            { title: 'PID', dataIndex: 'pid', width: 100 },
            { title: '命令', dataIndex: 'command', width: 160, render: (value) => value || '-' },
            {
              title: '启动参数',
              dataIndex: 'commandLine',
              render: (value) => <div className="whitespace-pre-wrap break-all font-mono text-xs leading-5 text-slate-600 dark:text-slate-300">{value || '-'}</div>,
            },
          ]}
        />
      </Modal>
      <Modal
        width={1120}
        open={historyModalOpen}
        footer={null}
        title="服务轨迹"
        onCancel={() => {
          setHistoryModalOpen(false);
          setHistoryService(null);
        }}
      >
        {renderHistoryModal()}
      </Modal>
    </>
  );
}
