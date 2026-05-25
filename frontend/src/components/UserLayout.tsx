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
 * 用户端菜单只保留用户真实需要的三个入口，避免出现配置感。
 */
const menuItems = [
  { key: '/user/dashboard', icon: <NavIcon name="dashboard" tone="cyan" />, label: '仪表盘', shortLabel: '仪表' },
  { key: '/user/pipelines', icon: <NavIcon name="pipeline" tone="emerald" />, label: '流水线大厅', shortLabel: '大厅' },
  { key: '/user/deployments', icon: <NavIcon name="deployment" tone="sky" />, label: '部署记录', shortLabel: '记录' },
  { key: '/user/system-settings', icon: <NavIcon name="settings" tone="slate" />, label: '系统设置', shortLabel: '设置' },
];

/**
 * 用户端整体布局。
 * 用户端只关注部署、进度和记录，因此布局结构比管理端更轻。
 */
export default function UserLayout() {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout } = useAuth();
  const [passwordModalOpen, setPasswordModalOpen] = useState(false);
  const selectedKey = menuItems.find((item) => location.pathname.startsWith(item.key))?.key || '/user/dashboard';

  return (
    <>
      <PortalLayoutShell
        title="部署工作台"
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
        )}
      />
      <ChangePasswordModal open={passwordModalOpen} onClose={() => setPasswordModalOpen(false)} />
    </>
  );
}
