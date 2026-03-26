import { useEffect, useState } from 'react';
import { Button, Card, Space, Table, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import { servicesApi } from '../../api/services';
import EmptyPane from '../../components/EmptyPane';
import PageHeaderBar from '../../components/PageHeaderBar';
import StatusTag from '../../components/StatusTag';
import type { ServiceSummary } from '../../types/domain';
import { formatDateTime } from '../../utils/datetime';

export default function ServiceManagementPage() {
  const [services, setServices] = useState<ServiceSummary[]>([]);
  const [actingId, setActingId] = useState<number>();
  const navigate = useNavigate();

  const loadServices = async () => {
    setServices(await servicesApi.list());
  };

  useEffect(() => {
    loadServices().catch(() => message.error('加载服务失败'));
  }, []);

  const triggerAction = async (id, action, successText) => {
    setActingId(id);
    try {
      const response = await servicesApi.action(id, action);
      await loadServices();
      message.success(successText);
      if ('id' in response && action !== 'stop' && 'pipeline' in response) {
        navigate(`/admin/deployments/${response.id}`);
      }
    } finally {
      setActingId(undefined);
    }
  };

  return (
    <>
      <PageHeaderBar
        title="服务管理"
        description="这里管理模板声明为“监控进程”的服务，可以查看运行状态、停止、启动和重启。"
        extra={<Button onClick={() => loadServices().catch(() => message.error('刷新失败'))}>刷新</Button>}
      />
      <Card className="app-card">
        {services.length === 0 ? (
          <EmptyPane description="当前没有可管理的服务。只有模板启用了进程监控并成功记录 PID 后，服务才会出现在这里。" />
        ) : (
          <Table
            rowKey="id"
            scroll={{ x: 960 }}
            dataSource={services}
            columns={[
              { title: '服务名', dataIndex: 'serviceName' },
              { title: '流水线', render: (_, row) => row.pipeline?.name || '-' },
              { title: '项目', render: (_, row) => row.pipeline?.project?.name || '-' },
              { title: '主机', render: (_, row) => row.pipeline?.targetHost?.name || '本机' },
              { title: 'PID', dataIndex: 'currentPid' },
              { title: '状态', render: (_, row) => <StatusTag status={row.status} /> },
              { title: '最近更新', render: (_, row) => formatDateTime(row.updatedAt) },
              {
                title: '操作',
                width: 320,
                render: (_, row) => (
                  <Space wrap>
                    <Button
                      size="small"
                      onClick={() => triggerAction(row.id, 'start', '服务启动任务已触发').catch(() => message.error('启动服务失败'))}
                      loading={actingId === row.id}
                    >
                      启动
                    </Button>
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
                    <Button
                      size="small"
                      disabled={!row.lastDeployment?.id}
                      onClick={() => row.lastDeployment?.id && navigate(`/admin/deployments/${row.lastDeployment.id}`)}
                    >
                      查看详情
                    </Button>
                  </Space>
                ),
              },
            ]}
          />
        )}
      </Card>
    </>
  );
}
