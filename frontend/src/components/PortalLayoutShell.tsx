import { type ReactNode } from 'react';
import { MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons';
import { Button, Layout, Menu, Typography } from 'antd';
import type { ItemType } from 'antd/es/menu/interface';
import { Outlet, useNavigate } from 'react-router-dom';
import { useLayoutMode } from '../theme/LayoutModeProvider';
import deployBotLogo from '../assets/deploy-bot-logo.svg';

type Props = {
  title: string;
  menuItems: (ItemType & { shortLabel?: string })[];
  selectedKey: string;
  actions: ReactNode;
  children?: ReactNode;
};

/**
 * 管理端和用户端共用布局壳。
 * 根据布局模式渲染顶部导航或左侧菜单，业务页面仍然只通过 Outlet 承载。
 */
export default function PortalLayoutShell({ title, menuItems, selectedKey, actions, children }: Props) {
  const navigate = useNavigate();
  const { isSideMode, sideCollapsed, toggleSideCollapsed } = useLayoutMode();
  const sideMenuItems = menuItems.map((item) => {
    if (!sideCollapsed || !item || !('shortLabel' in item)) {
      return item;
    }
    return {
      ...item,
      icon: null,
      label: (
        <span className="portal-side-nav-collapsed-item">
          {item.icon}
          <span>{item.shortLabel || item.label}</span>
        </span>
      ),
    };
  });

  const brand = (
    <div className="app-brand text-white">
      <div className="app-logo">
        <img src={deployBotLogo} alt="Deploy Bot Logo" className="app-logo-image" />
      </div>
      <div className="min-w-0">
        <Typography.Text className="block text-[11px] uppercase tracking-[0.3em] !text-white/55">
          Deploy Bot
        </Typography.Text>
        <Typography.Title level={4} className="!mb-0 !mt-0 truncate !text-white">
          {title}
        </Typography.Title>
      </div>
    </div>
  );

  const menu = (
    <Menu
      mode={isSideMode ? 'inline' : 'horizontal'}
      theme="dark"
      items={isSideMode ? sideMenuItems : menuItems}
      selectedKeys={[selectedKey]}
      onClick={({ key }) => navigate(key)}
      className={isSideMode ? 'portal-side-nav' : 'admin-nav'}
    />
  );

  if (isSideMode) {
    return (
      <Layout className={`portal-shell portal-shell--side ${sideCollapsed ? 'portal-shell--side-collapsed' : ''} min-h-screen bg-app`}>
        <Layout.Sider width={sideCollapsed ? 88 : 236} className="portal-sider">
          <div className="portal-sider-brand">{brand}</div>
          <Button
            type="text"
            className="portal-sider-collapse-button"
            icon={sideCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={toggleSideCollapsed}
            title={sideCollapsed ? '展开菜单' : '折叠菜单'}
          />
          {menu}
          <div className="portal-sider-actions">{actions}</div>
        </Layout.Sider>
        <Layout className="min-h-screen bg-transparent">
          <Layout.Content className="app-content app-content--side">
            {children}
            <div className="app-page">
              <Outlet />
            </div>
          </Layout.Content>
        </Layout>
      </Layout>
    );
  }

  return (
    <Layout className="portal-shell min-h-screen bg-app">
      <Layout.Header className="app-header px-6">
        {brand}
        {menu}
        {actions}
      </Layout.Header>
      <Layout.Content className="app-content">
        <div className="app-page">
          <Outlet />
        </div>
      </Layout.Content>
    </Layout>
  );
}
