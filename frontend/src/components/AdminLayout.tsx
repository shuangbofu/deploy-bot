import { LogoutOutlined, LockOutlined, GithubOutlined, RobotOutlined } from '@ant-design/icons';
import { Avatar, Button, Dropdown, Space } from 'antd';
import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { resolveBackendAssetUrl } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import ChangePasswordModal from './ChangePasswordModal';
import NavIcon from './NavIcon';
import PortalLayoutShell from './PortalLayoutShell';

const GITHUB_REPOSITORY_URL = 'https://github.com/shuangbofu/deploy-bot';

/**
 * 管理端一级菜单配置。
 * 这里保持“仪表盘 -> 资源管理 -> 运行记录”的信息架构顺序。
 */
const menuItems = [
  { key: '/admin/dashboard', icon: <NavIcon name="dashboard" tone="cyan" />, label: '仪表盘', shortLabel: '仪表' },
  { key: '/admin/projects', icon: <NavIcon name="project" tone="amber" />, label: '项目', shortLabel: '项目' },
  { key: '/admin/hosts', icon: <NavIcon name="host" tone="blue" />, label: '主机', shortLabel: '主机' },
  { key: '/admin/plugins', icon: <NavIcon name="plugin" tone="violet" />, label: '插件', shortLabel: '插件' },
  { key: '/admin/pipelines', icon: <NavIcon name="pipeline" tone="emerald" />, label: '流水线', shortLabel: '流水' },
  { key: '/admin/deployments', icon: <NavIcon name="deployment" tone="sky" />, label: '部署记录', shortLabel: '记录' },
  { key: '/admin/services', icon: <NavIcon name="service" tone="teal" />, label: '服务', shortLabel: '服务' },
  { key: '/admin/users', icon: <NavIcon name="user" tone="indigo" />, label: '用户', shortLabel: '用户' },
  { key: '/admin/system-settings', icon: <NavIcon name="settings" tone="slate" />, label: '系统设置', shortLabel: '设置' },
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
  const selectedKey = menuItems.find((item) => location.pathname.startsWith(item.key))?.key || '/admin/dashboard';

  return (
    <>
      <PortalLayoutShell
        title="部署平台"
        menuItems={menuItems}
        selectedKey={selectedKey}
        actions={(
        <Space>
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
        )}
      />
      <ChangePasswordModal open={passwordModalOpen} onClose={() => setPasswordModalOpen(false)} />
    </>
  );
}
