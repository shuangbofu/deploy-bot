import { useEffect, useMemo, useState } from 'react';
import { Button, Card, Col, DatePicker, Input, Row, Select, Space, Table, message } from 'antd';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { deploymentsApi } from '../../api/deployments';
import { pipelinesApi } from '../../api/pipelines';
import EmptyPane from '../../components/EmptyPane';
import PageHeaderBar from '../../components/PageHeaderBar';
import StatusTag from '../../components/StatusTag';
import { DEPLOYMENT_STATUS_OPTIONS } from '../../constants/deployment';
import type { DeploymentSummary, PipelineHistoryFilters, PipelineSummary } from '../../types/domain';
import { formatDateTime } from '../../utils/datetime';
import { formatDeploymentElapsed } from '../../utils/deploymentDuration';

const emptyFilters: PipelineHistoryFilters = {
  branchName: '',
  triggeredBy: '',
  status: undefined,
  timeRange: undefined,
};

/**
 * 单条流水线的历史记录页。
 * 与全局部署记录不同，这里只关心当前选中的流水线。
 */
export default function UserPipelineHistoryPage() {
  const { pipelineId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const [pipeline, setPipeline] = useState<PipelineSummary>();
  const [deployments, setDeployments] = useState<DeploymentSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [filters, setFilters] = useState<PipelineHistoryFilters>(emptyFilters);
  const [tick, setTick] = useState(() => Date.now());
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });

  const loadPipeline = async () => {
    const pipelineList = await pipelinesApi.list();
    setPipeline(pipelineList.find((item) => String(item.id) === String(pipelineId)));
  };

  const loadDeployments = async () => {
    setLoading(true);
    try {
      const result = await deploymentsApi.listMinePage({
        page: pagination.current,
        pageSize: pagination.pageSize,
        triggeredBy: filters.triggeredBy || undefined,
        branchName: filters.branchName || undefined,
        status: filters.status,
        startTime: filters.timeRange?.[0]?.startOf('day')?.valueOf?.(),
        endTime: filters.timeRange?.[1]?.endOf('day')?.valueOf?.(),
        pipelineId: pipelineId ? Number(pipelineId) : undefined,
      });
      setDeployments(result.items);
      setTotal(result.total);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadPipeline().catch(() => message.error('加载流水线失败'));
  }, [pipelineId]);

  useEffect(() => {
    loadDeployments().catch(() => message.error('加载流水线历史失败'));
  }, [pipelineId, pagination.current, pagination.pageSize, filters]);

  useEffect(() => {
    const timer = window.setInterval(() => setTick(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  /** 页头标题会随着当前流水线变化。 */
  const title = useMemo(
    () => (pipeline ? `${pipeline.name} 的部署记录` : '流水线部署记录'),
    [pipeline],
  );

  return (
    <>
      <PageHeaderBar
        title={title}
        description="查看当前流水线的部署历史和部署结果。"
        extra={[
          <Button key="back" onClick={() => navigate('/user/pipelines')}>返回流水线大厅</Button>,
          <Button key="refresh" type="primary" onClick={() => loadDeployments().catch(() => message.error('刷新失败'))}>刷新</Button>,
        ]}
      />
      <div className="app-page-scroll">
      <Card className="app-card">
        <Row gutter={[12, 12]} className="mb-4">
          <Col xs={24} md={12} xl={6}>
            <Input
              value={filters.branchName}
              placeholder="筛选分支"
              onChange={(event) => {
                setFilters({ ...filters, branchName: event.target.value });
                setPagination((previous) => ({ ...previous, current: 1 }));
              }}
            />
          </Col>
          <Col xs={24} md={12} xl={6}>
            <Input
              value={filters.triggeredBy}
              placeholder="筛选触发人"
              onChange={(event) => {
                setFilters({ ...filters, triggeredBy: event.target.value });
                setPagination((previous) => ({ ...previous, current: 1 }));
              }}
            />
          </Col>
          <Col xs={24} md={12} xl={4}>
            <Select
              className="w-full"
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
          </Col>
          <Col xs={24} md={12} xl={8}>
            <DatePicker.RangePicker
              className="w-full"
              value={filters.timeRange}
              onChange={(value) => {
                setFilters({ ...filters, timeRange: value });
                setPagination((previous) => ({ ...previous, current: 1 }));
              }}
              placeholder={['开始时间', '结束时间']}
            />
          </Col>
        </Row>
        <div className="mb-4 flex items-center">
          <Button onClick={() => setFilters(emptyFilters)}>重置条件</Button>
        </div>
        <Table
          rowKey="id"
          loading={loading}
          scroll={{ x: 1180 }}
          dataSource={deployments}
          locale={{ emptyText: <EmptyPane description="这条流水线还没有符合条件的部署记录。" /> }}
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
              { title: '流水线', render: (_, row) => row.pipeline?.name || '-' },
              { title: '分支', dataIndex: 'branchName' },
              { title: '触发人', render: (_, record) => record.triggeredByDisplayName || record.triggeredBy || '-' },
              { title: '状态', render: (_, row) => <StatusTag status={row.status} /> },
              { title: '创建时间', render: (_, row) => formatDateTime(row.createdAt) },
              { title: '开始时间', render: (_, row) => formatDateTime(row.startedAt) },
              { title: '结束时间', render: (_, row) => formatDateTime(row.finishedAt) },
              { title: '耗时', render: (_, row) => formatDeploymentElapsed(row, tick) },
              {
                title: '操作',
                width: 140,
                render: (_, row) => (
                  <Button
                    size="small"
                    type="primary"
                    onClick={() => navigate(`/user/deployments/${row.id}`, {
                      state: { from: location.pathname, backLabel: '返回部署记录' },
                    })}
                  >
                    查看详情
                  </Button>
                ),
              },
          ]}
        />
      </Card>
      </div>
    </>
  );
}
