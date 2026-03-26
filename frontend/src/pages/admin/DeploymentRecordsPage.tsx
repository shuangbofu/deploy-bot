import { useEffect, useMemo, useState } from 'react';
import { Button, Card, DatePicker, Input, Select, Space, Table, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import { deploymentsApi } from '../../api/deployments';
import EmptyPane from '../../components/EmptyPane';
import PageHeaderBar from '../../components/PageHeaderBar';
import StatusTag from '../../components/StatusTag';
import { DEPLOYMENT_STATUS_OPTIONS } from '../../constants/deployment';
import type { DeploymentRecordFilters, DeploymentSummary } from '../../types/domain';
import { formatDateTime } from '../../utils/datetime';
import { formatDeploymentElapsed } from '../../utils/deploymentDuration';

const emptyFilters: DeploymentRecordFilters = {
  projectName: undefined,
  pipelineName: undefined,
  triggeredBy: '',
  status: undefined,
  timeRange: undefined,
};

/**
 * 管理端部署记录页。
 * 重点是全局筛选、查看耗时以及进入详情页继续排查。
 */
export default function DeploymentRecordsPage() {
  const navigate = useNavigate();
  const [deployments, setDeployments] = useState<DeploymentSummary[]>([]);
  const [filters, setFilters] = useState<DeploymentRecordFilters>(emptyFilters);
  const [tick, setTick] = useState(() => Date.now());

  /** 从后端读取全部部署记录。 */
  const loadDeployments = async () => {
    setDeployments(await deploymentsApi.list());
  };

  useEffect(() => {
    loadDeployments().catch(() => message.error('加载部署记录失败'));
  }, []);

  useEffect(() => {
    const timer = window.setInterval(() => setTick(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  /** 由当前记录反推项目筛选项，避免额外请求接口。 */
  const projectOptions = useMemo(() => Array.from(new Set(
    deployments.map((item) => item.pipeline?.project?.name).filter(Boolean),
  )).map((item) => ({ label: item, value: item })), [deployments]);

  /** 由当前记录反推流水线筛选项。 */
  const pipelineOptions = useMemo(() => Array.from(new Set(
    deployments.map((item) => item.pipeline?.name).filter(Boolean),
  )).map((item) => ({ label: item, value: item })), [deployments]);

  /** 在前端做轻量筛选，当前数据量下足够满足使用。 */
  const filteredDeployments = useMemo(() => deployments.filter((item) => {
    if (filters.projectName && item.pipeline?.project?.name !== filters.projectName) {
      return false;
    }
    if (filters.pipelineName && item.pipeline?.name !== filters.pipelineName) {
      return false;
    }
    if (filters.status && item.status !== filters.status) {
      return false;
    }
    if (filters.triggeredBy && !(item.triggeredBy || '').toLowerCase().includes(filters.triggeredBy.toLowerCase())) {
      return false;
    }
    if (filters.timeRange?.length === 2) {
      const createdAt = new Date(item.createdAt).getTime();
      const start = filters.timeRange[0]?.startOf('day')?.valueOf?.();
      const end = filters.timeRange[1]?.endOf('day')?.valueOf?.();
      if (start && createdAt < start) {
        return false;
      }
      if (end && createdAt > end) {
        return false;
      }
    }
    return true;
  }), [deployments, filters]);

  return (
    <>
      <PageHeaderBar
        title="部署记录"
        description="这里是部署历史列表。支持按项目、流水线、触发人、状态和时间区间筛选，并直接查看耗时。"
      />
      <Card className="app-card">
        <div className="mb-4 grid grid-cols-1 gap-3 xl:grid-cols-5">
          <Select
            allowClear
            value={filters.projectName}
            options={projectOptions}
            placeholder="筛选项目"
            onChange={(value) => setFilters({ ...filters, projectName: value })}
          />
          <Select
            allowClear
            value={filters.pipelineName}
            options={pipelineOptions}
            placeholder="筛选流水线"
            onChange={(value) => setFilters({ ...filters, pipelineName: value })}
          />
          <Input
            value={filters.triggeredBy}
            placeholder="筛选触发人"
            onChange={(event) => setFilters({ ...filters, triggeredBy: event.target.value })}
          />
            <Select
              allowClear
              value={filters.status}
              options={DEPLOYMENT_STATUS_OPTIONS}
              placeholder="筛选状态"
              onChange={(value) => setFilters({ ...filters, status: value })}
            />
          <DatePicker.RangePicker
            value={filters.timeRange}
            onChange={(value) => setFilters({ ...filters, timeRange: value })}
            placeholder={['开始时间', '结束时间']}
          />
        </div>
        <div className="mb-4">
          <Space>
            <Button onClick={() => setFilters(emptyFilters)}>重置条件</Button>
            <Button onClick={() => loadDeployments().catch(() => message.error('刷新部署记录失败'))}>刷新</Button>
          </Space>
        </div>
        {filteredDeployments.length === 0 ? (
          <EmptyPane description="暂无部署记录。" />
        ) : (
          <Table
            rowKey="id"
            scroll={{ x: 1180 }}
            dataSource={filteredDeployments}
            columns={[
              { title: '编号', dataIndex: 'id' },
              { title: '项目', render: (_, row) => row.pipeline?.project?.name || '-' },
              { title: '流水线', render: (_, row) => row.pipeline?.name },
              { title: '分支', dataIndex: 'branchName' },
              { title: '触发人', dataIndex: 'triggeredBy' },
              { title: '状态', render: (_, row) => <StatusTag status={row.status} /> },
              { title: '创建时间', render: (_, row) => formatDateTime(row.createdAt) },
              { title: '开始时间', render: (_, row) => formatDateTime(row.startedAt) },
              { title: '结束时间', render: (_, row) => formatDateTime(row.finishedAt) },
              { title: '耗时', render: (_, row) => formatDeploymentElapsed(row, tick) },
              {
                title: '操作',
                render: (_, row) => (
                  <Button size="small" type="primary" onClick={() => navigate(`/admin/deployments/${row.id}`)}>
                    查看详情
                  </Button>
                ),
              },
            ]}
          />
        )}
      </Card>
    </>
  );
}
