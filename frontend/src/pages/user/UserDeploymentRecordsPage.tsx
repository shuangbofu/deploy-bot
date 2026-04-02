import { useEffect, useMemo, useState } from 'react';
import { Button, Card, Col, DatePicker, Input, Popconfirm, Row, Select, Space, Table, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import { deploymentsApi } from '../../api/deployments';
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
 * 用户端全局部署记录页。
 * 用于快速回看所有历史部署，而不是只看单条流水线。
 */
export default function UserDeploymentRecordsPage() {
  const navigate = useNavigate();
  const [deployments, setDeployments] = useState<DeploymentSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [filters, setFilters] = useState<DeploymentRecordFilters>(emptyFilters);
  const [tick, setTick] = useState(() => Date.now());
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });

  /** 读取全部部署记录供用户筛选和查看。 */
  const loadDeployments = async () => {
    setLoading(true);
    try {
      setDeployments(await deploymentsApi.list());
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadDeployments().catch(() => message.error('加载部署记录失败'));
  }, []);

  useEffect(() => {
    const timer = window.setInterval(() => setTick(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  /** 从部署记录中提取项目筛选项。 */
  const projectOptions = useMemo(() => Array.from(new Set(
    deployments.map((item) => item.pipeline?.project?.name).filter(Boolean),
  )).map((item) => ({ label: item, value: item })), [deployments]);

  /** 从部署记录中提取流水线筛选项。 */
  const pipelineOptions = useMemo(() => Array.from(new Set(
    deployments.map((item) => item.pipeline?.name).filter(Boolean),
  )).map((item) => ({ label: item, value: item })), [deployments]);

  /** 前端筛选结果。 */
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
    const triggeredBy = item.triggeredByDisplayName || item.triggeredBy || '';
    if (filters.triggeredBy && !triggeredBy.toLowerCase().includes(filters.triggeredBy.toLowerCase())) {
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
        description="查看全部部署记录，并按项目、流水线、状态等条件筛选。"
        extra={<Button onClick={() => loadDeployments().catch(() => message.error('刷新部署记录失败'))}>刷新</Button>}
      />
      <Card className="app-card">
        <Row gutter={[12, 12]} className="mb-4">
          <Col xs={24} md={12} xl={5}>
            <Select
              className="w-full"
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
          </Col>
          <Col xs={24} md={12} xl={5}>
            <Select
              className="w-full"
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
          </Col>
          <Col xs={24} md={12} xl={4}>
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
          <Col xs={24} xl={6}>
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
          dataSource={filteredDeployments}
          locale={{ emptyText: <EmptyPane description="暂无符合条件的部署记录。" /> }}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total: filteredDeployments.length,
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
                render: (_, row) => (
                  <Space wrap>
                    <Button
                      size="small"
                      type="primary"
                      onClick={() => navigate(`/user/deployments/${row.id}`, {
                        state: { from: '/user/deployments', backLabel: '返回部署记录' },
                      })}
                    >
                      查看详情
                    </Button>
                    {row.status === 'SUCCESS' && row.artifactPath ? (
                      <Popconfirm
                        title="确认重新发布"
                        description="会直接复用这次部署保留下来的构建产物重新发布，不会重新执行构建流程。"
                        okText="确认"
                        cancelText="取消"
                        onConfirm={() => deploymentsApi.rollback(row.id).then((response) => {
                          message.success('重新发布任务已创建');
                          navigate(`/user/deployments/${response.id}`, {
                            state: { from: '/user/deployments', backLabel: '返回部署记录' },
                          });
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
    </>
  );
}
