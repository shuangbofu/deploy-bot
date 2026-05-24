import React from 'react';
import ReactDOM from 'react-dom/client';
import { HashRouter } from 'react-router-dom';
import App from './App';
import { AuthProvider } from './auth/AuthContext';
import { AppThemeProvider } from './theme/AppThemeProvider';
import { LayoutModeProvider } from './theme/LayoutModeProvider';
import './index.css';

/**
 * 前端应用启动入口。
 * 这里统一挂载路由、Ant Design 中文化和全局主题配置。
 */
ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <AppThemeProvider>
      <LayoutModeProvider>
        <AuthProvider>
          <HashRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
            <App />
          </HashRouter>
        </AuthProvider>
      </LayoutModeProvider>
    </AppThemeProvider>
  </React.StrictMode>,
);
