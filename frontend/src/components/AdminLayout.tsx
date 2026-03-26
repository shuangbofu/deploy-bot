import { AppstoreOutlined, DeploymentUnitOutlined, FileTextOutlined, FolderOpenOutlined, ProfileOutlined, RadarChartOutlined, RocketOutlined, SettingOutlined, CloudServerOutlined } from '@ant-design/icons';
import { Button, Layout, Menu, Space, Typography } from 'antd';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';

/**
 * 管理端一级菜单配置。
 * 这里保持“控制台 -> 资源管理 -> 运行记录”的信息架构顺序。
 */
const menuItems = [
  { key: '/admin/dashboard', icon: <AppstoreOutlined />, label: '控制台' },
  { key: '/admin/projects', icon: <FolderOpenOutlined />, label: '项目' },
  { key: '/admin/hosts', icon: <CloudServerOutlined />, label: '主机' },
  { key: '/admin/templates', icon: <FileTextOutlined />, label: '模板' },
  { key: '/admin/pipelines', icon: <DeploymentUnitOutlined />, label: '流水线' },
  { key: '/admin/deployments', icon: <ProfileOutlined />, label: '部署记录' },
  { key: '/admin/services', icon: <RadarChartOutlined />, label: '服务管理' },
  { key: '/admin/system-settings', icon: <SettingOutlined />, label: '系统设置' },
];

/**
 * 管理端整体布局。
 * 负责头部导航、品牌区和子页面内容承载，不处理业务状态。
 */
export default function AdminLayout() {
  const location = useLocation();
  const navigate = useNavigate();

  return (
    <Layout className="min-h-screen bg-app">
      <Layout.Header className="app-header px-6">
        <div className="flex items-center gap-3 text-white">
          <div className="app-logo">
            <RocketOutlined />
          </div>
          <div>
            <Typography.Text className="block text-[11px] uppercase tracking-[0.3em] !text-white/55">
              Deploy Bot
            </Typography.Text>
            <Typography.Title level={4} className="!mb-0 !mt-0 !text-white">
              管理端
            </Typography.Title>
          </div>
        </div>
        <Menu
          mode="horizontal"
          theme="dark"
          items={menuItems}
          selectedKeys={[location.pathname]}
          onClick={({ key }) => navigate(key)}
          className="admin-nav"
        />
        <Space>
          <Link to="/user">
            <Button type="primary">进入用户端</Button>
          </Link>
        </Space>
      </Layout.Header>
      <Layout.Content className="px-6 py-6">
        <Outlet />
      </Layout.Content>
    </Layout>
  );
}
