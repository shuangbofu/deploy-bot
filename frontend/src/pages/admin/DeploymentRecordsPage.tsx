import { useEffect, useState } from 'react';
import { Button, Card, DatePicker, Input, Popconfirm, Select, Space, Table, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import { deploymentsApi } from '../../api/deployments';
import { pipelinesApi } from '../../api/pipelines';
import { projectsApi } from '../../api/projects';
import EmptyPane from '../../components/EmptyPane';
import PageHeaderBar from '../../components/PageHeaderBar';
import StatusTag from '../../components/StatusTag';
import { ACTIVE_DEPLOYMENT_STATUSES } from '../../constants/deployment';
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
  const [projectOptions, setProjectOptions] = useState<Array<{ label: string; value: string }>>([]);
  const [pipelineOptions, setPipelineOptions] = useState<Array<{ label: string; value: string }>>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [filters, setFilters] = useState<DeploymentRecordFilters>(emptyFilters);
  const [tick, setTick] = useState(() => Date.now());
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });

  const loadDeployments = async () => {
    setLoading(true);
    try {
      const result = await deploymentsApi.listPage({
        page: pagination.current,
        pageSize: pagination.pageSize,
        projectName: filters.projectName,
        pipelineName: filters.pipelineName,
        triggeredBy: filters.triggeredBy || undefined,
        status: filters.status,
        startTime: filters.timeRange?.[0]?.startOf('day')?.valueOf?.(),
        endTime: filters.timeRange?.[1]?.endOf('day')?.valueOf?.(),
      });
      setDeployments(result.items);
      setTotal(result.total);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    Promise.all([projectsApi.list(), pipelinesApi.list()])
      .then(([projects, pipelines]) => {
        setProjectOptions(projects.map((item) => ({ label: item.name, value: item.name })));
        setPipelineOptions(pipelines.map((item) => ({ label: item.name, value: item.name })));
      })
      .catch(() => message.error('加载筛选项失败'));
  }, []);

  useEffect(() => {
    loadDeployments().catch(() => message.error('加载部署记录失败'));
  }, [pagination.current, pagination.pageSize, filters]);

  useEffect(() => {
    const timer = window.setInterval(() => setTick(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  return (
    <>
      <PageHeaderBar
        title="部署记录"
        description="查看全部部署记录，并按项目、流水线、触发人、状态和时间筛选。"
        extra={<Button onClick={() => loadDeployments().catch(() => message.error('刷新部署记录失败'))}>刷新</Button>}
      />
      <div className="app-page-scroll">
      <Card className="app-card">
        <div className="mb-4 grid grid-cols-1 gap-3 xl:grid-cols-5">
          <Select
            showSearch
            optionFilterProp="label"
            allowClear
            value={filters.projectName}
            options={projectOptions}
            placeholder="筛选项目"
            onChange={(value) => {
              setFilters({ ...filters, projectName: value });
              setPagination((previous) => ({ ...previous, current: 1 }));
            }}
          />
          <Select
            showSearch
            optionFilterProp="label"
            allowClear
            value={filters.pipelineName}
            options={pipelineOptions}
            placeholder="筛选流水线"
            onChange={(value) => {
              setFilters({ ...filters, pipelineName: value });
              setPagination((previous) => ({ ...previous, current: 1 }));
            }}
          />
          <Input
            value={filters.triggeredBy}
            placeholder="筛选触发人"
            onChange={(event) => {
              setFilters({ ...filters, triggeredBy: event.target.value });
              setPagination((previous) => ({ ...previous, current: 1 }));
            }}
          />
            <Select
              showSearch
              optionFilterProp="label"
              allowClear
              value={filters.status}
              options={DEPLOYMENT_STATUS_OPTIONS}
              placeholder="筛选状态"
              onChange={(value) => {
                setFilters({ ...filters, status: value });
                setPagination((previous) => ({ ...previous, current: 1 }));
              }}
            />
          <DatePicker.RangePicker
            value={filters.timeRange}
            onChange={(value) => {
              setFilters({ ...filters, timeRange: value });
              setPagination((previous) => ({ ...previous, current: 1 }));
            }}
            placeholder={['开始时间', '结束时间']}
          />
        </div>
        <div className="mb-4 flex items-center">
          <Button onClick={() => setFilters(emptyFilters)}>重置条件</Button>
        </div>
        <Table
          rowKey="id"
          loading={loading}
          scroll={{ x: 1180 }}
          dataSource={deployments}
          locale={{ emptyText: <EmptyPane description="暂无部署记录。" /> }}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`,
            onChange: (current, pageSize) => setPagination({ current, pageSize }),
          }}
          columns={[
              { title: '编号', dataIndex: 'id' },
              { title: '项目', render: (_, row) => row.pipeline?.project?.name || '-' },
              { title: '流水线', render: (_, row) => row.pipeline?.name },
              { title: '分支', dataIndex: 'branchName' },
              { title: '触发人', render: (_, record) => record.triggeredByDisplayName || record.triggeredBy || '-' },
              { title: '状态', render: (_, row) => <StatusTag status={row.status} /> },
              { title: '创建时间', render: (_, row) => formatDateTime(row.createdAt) },
              { title: '开始时间', render: (_, row) => formatDateTime(row.startedAt) },
              { title: '结束时间', render: (_, row) => formatDateTime(row.finishedAt) },
              { title: '耗时', render: (_, row) => formatDeploymentElapsed(row, tick) },
              {
                title: '操作',
                render: (_, row) => (
                  <Space wrap>
                    <Button size="small" type="primary" onClick={() => navigate(`/admin/deployments/${row.id}`)}>
                      查看详情
                    </Button>
                    {row.status === 'SUCCESS' && row.artifactPath ? (
                      <Popconfirm
                        title="确认重新发布"
                        description="会直接复用这次部署保留下来的构建产物重新发布，不会重新走构建流程。"
                        okText="确认"
                        cancelText="取消"
                        onConfirm={() => deploymentsApi.rollback(row.id).then((response) => {
                          message.success('重新发布任务已创建');
                          navigate(`/admin/deployments/${response.id}`);
                        }).catch(() => message.error('创建回滚任务失败'))}
                      >
                        <Button size="small">回滚</Button>
                      </Popconfirm>
                    ) : null}
                    {row.status && ACTIVE_DEPLOYMENT_STATUSES.includes(row.status) ? (
                      <Button
                        size="small"
                        danger
                        onClick={() => deploymentsApi.stop(row.id).then(() => {
                          message.success('部署已停止');
                          return loadDeployments();
                        }).catch(() => message.error('停止部署失败'))}
                      >
                        停止
                      </Button>
                    ) : null}
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
