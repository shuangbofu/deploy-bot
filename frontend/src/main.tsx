import React from 'react';
import ReactDOM from 'react-dom/client';
import { HashRouter } from 'react-router-dom';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import App from './App';
import './index.css';

/**
 * 前端应用启动入口。
 * 这里统一挂载路由、Ant Design 中文化和全局主题配置。
 */
ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#102542',
          colorInfo: '#102542',
          colorSuccess: '#166534',
          colorWarning: '#b88c4a',
          borderRadius: 14,
        },
      }}
    >
      <HashRouter>
        <App />
      </HashRouter>
    </ConfigProvider>
  </React.StrictMode>,
);
