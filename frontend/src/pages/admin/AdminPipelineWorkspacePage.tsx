import { Tabs } from 'antd';
import { useSearchParams } from 'react-router-dom';
import PipelineAdminPage from './PipelineAdminPage';
import UserPipelinesPage from '../user/UserPipelinesPage';

/**
 * 流水线工作区。
 * 管理员在同一个入口里切换“使用视角”和“配置视角”，避免在多个流水线入口之间来回跳。
 */
export default function AdminPipelineWorkspacePage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const activeTab = searchParams.get('tab') === 'manage' ? 'manage' : 'hall';

  return (
    <Tabs
      className="pipeline-workspace-tabs"
      activeKey={activeTab}
      onChange={(key) => setSearchParams(key === 'manage' ? { tab: 'manage' } : {})}
      items={[
        {
          key: 'hall',
          label: '部署大厅',
          children: (
            <UserPipelinesPage
              basePath="/admin/pipelines"
              deploymentDetailBasePath="/admin/deployments"
              title="流水线"
              description="选择流水线发起部署，查看最近状态和执行进度。"
            />
          ),
        },
        {
          key: 'manage',
          label: '流水线管理',
          children: <PipelineAdminPage />,
        },
      ]}
    />
  );
}
