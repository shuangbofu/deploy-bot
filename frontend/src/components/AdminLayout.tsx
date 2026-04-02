import { AppstoreOutlined, DeploymentUnitOutlined, FileTextOutlined, FolderOpenOutlined, ProfileOutlined, RadarChartOutlined, SettingOutlined, CloudServerOutlined, TeamOutlined, LogoutOutlined, LockOutlined, GithubOutlined, RobotOutlined, NotificationOutlined } from '@ant-design/icons';
import { Avatar, Button, Dropdown, Layout, Menu, Space, Typography } from 'antd';
import { useState } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { resolveBackendAssetUrl } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import ChangePasswordModal from './ChangePasswordModal';
import deployBotLogo from '../assets/deploy-bot-logo.svg';

const GITHUB_REPOSITORY_URL = 'https://github.com/shuangbofu/deploy-bot';

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
  { key: '/admin/services', icon: <RadarChartOutlined />, label: '服务' },
  { key: '/admin/notifications', icon: <NotificationOutlined />, label: '通知' },
  { key: '/admin/users', icon: <TeamOutlined />, label: '用户' },
  { key: '/admin/system-settings', icon: <SettingOutlined />, label: '系统设置' },
];

/**
 * 管理端整体布局。
 * 负责头部导航、品牌区和子页面内容承载，不处理业务状态。
 */
export default function AdminLayout() {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout } = useAuth();
  const [passwordModalOpen, setPasswordModalOpen] = useState(false);

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
              控制中心
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
          <Link to="/user/pipelines">
            <Button>部署工作台</Button>
          </Link>
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
                <Avatar size={28} src={resolveBackendAssetUrl(user?.avatar)} icon={!user?.avatar ? <RobotOutlined /> : undefined} />
                <span className="user-menu-trigger__text">
                  <span className="user-menu-trigger__display-name">{user?.displayName || '管理员'}</span>
                  <span className="user-menu-trigger__username">@{user?.username || 'admin'}</span>
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
