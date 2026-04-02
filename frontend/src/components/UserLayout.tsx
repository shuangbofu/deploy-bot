import { AppstoreOutlined, ProfileOutlined, DeploymentUnitOutlined, LogoutOutlined, LockOutlined, GithubOutlined, RobotOutlined } from '@ant-design/icons';
import { Avatar, Button, Dropdown, Layout, Menu, Space, Typography } from 'antd';
import { useState } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import ChangePasswordModal from './ChangePasswordModal';
import deployBotLogo from '../assets/deploy-bot-logo.svg';

const GITHUB_REPOSITORY_URL = 'https://github.com/shuangbofu/deploy-bot';

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
            <img src={deployBotLogo} alt="Deploy Bot Logo" className="app-logo-image" />
          </div>
          <div>
            <Typography.Text className="block text-[11px] uppercase tracking-[0.3em] !text-white/55">
              Deploy Bot
            </Typography.Text>
            <Typography.Title level={4} className="!mb-0 !mt-0 !text-white">
              部署工作台
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
              <Button>控制中心</Button>
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
            <Button type="primary" className="user-menu-trigger">
              <Space size={10}>
                <Avatar size={28} src={user?.avatar} icon={!user?.avatar ? <RobotOutlined /> : undefined} />
                <span className="user-menu-trigger__text">
                  <span className="user-menu-trigger__display-name">{user?.displayName || '用户'}</span>
                  <span className="user-menu-trigger__username">@{user?.username || 'user'}</span>
                </span>
              </Space>
            </Button>
          </Dropdown>
          <Button
            href={GITHUB_REPOSITORY_URL}
            target="_blank"
            rel="noreferrer"
            icon={<GithubOutlined />}
          />
        </Space>
      </Layout.Header>
      <Layout.Content className="app-content">
        <div className="app-page">
          <Outlet />
        </div>
      </Layout.Content>
      <ChangePasswordModal open={passwordModalOpen} onClose={() => setPasswordModalOpen(false)} />
    </Layout>
  );
}
