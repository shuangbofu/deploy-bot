import { AppstoreOutlined, ProfileOutlined, RocketOutlined, DeploymentUnitOutlined, LogoutOutlined, LockOutlined } from '@ant-design/icons';
import { Button, Dropdown, Layout, Menu, Space, Typography } from 'antd';
import { useState } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import ChangePasswordModal from './ChangePasswordModal';

/**
 * 用户端菜单只保留用户真实需要的三个入口，避免出现配置感。
 */
const menuItems = [
  { key: '/user/dashboard', icon: <AppstoreOutlined />, label: '控制台' },
  { key: '/user/pipelines', icon: <DeploymentUnitOutlined />, label: '流水线大厅' },
  { key: '/user/deployments', icon: <ProfileOutlined />, label: '部署记录' },
];

/**
 * 用户端整体布局。
 * 用户端只关注部署、进度和记录，因此布局结构比管理端更轻。
 */
export default function UserLayout() {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, isAdmin, logout } = useAuth();
  const [passwordModalOpen, setPasswordModalOpen] = useState(false);
  const selectedKey = menuItems.find((item) => location.pathname.startsWith(item.key))?.key || '/user/dashboard';

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
              用户端
            </Typography.Title>
          </div>
        </div>
        <Menu
          mode="horizontal"
          theme="dark"
          items={menuItems}
          selectedKeys={[selectedKey]}
          onClick={({ key }) => navigate(key)}
          className="admin-nav"
        />
        <Space>
          {isAdmin ? (
            <Link to="/admin/dashboard">
              <Button>管理端</Button>
            </Link>
          ) : null}
          <Dropdown
            menu={{
              items: [
                {
                  key: 'change-password',
                  icon: <LockOutlined />,
                  label: '修改密码',
                  onClick: () => setPasswordModalOpen(true),
                },
                {
                  key: 'logout',
                  icon: <LogoutOutlined />,
                  label: '退出登录',
                  onClick: () => {
                    logout().finally(() => navigate('/login', { replace: true }));
                  },
                },
              ],
            }}
          >
            <Button type="primary">
              {user?.displayName || user?.username || '用户'}
            </Button>
          </Dropdown>
        </Space>
      </Layout.Header>
      <Layout.Content className="px-4 py-4">
        <Outlet />
      </Layout.Content>
      <ChangePasswordModal open={passwordModalOpen} onClose={() => setPasswordModalOpen(false)} />
    </Layout>
  );
}
