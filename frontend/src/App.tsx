import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom';
import { Spin } from 'antd';
import AdminLayout from './components/AdminLayout';
import UserLayout from './components/UserLayout';
import { useAuth } from './auth/AuthContext';
import AdminDashboardPage from './pages/admin/AdminDashboardPage';
import AdminDeploymentDetailPage from './pages/admin/AdminDeploymentDetailPage';
import DeploymentRecordsPage from './pages/admin/DeploymentRecordsPage';
import HostManagementPage from './pages/admin/HostManagementPage';
import PipelineAdminPage from './pages/admin/PipelineAdminPage';
import ProjectAdminPage from './pages/admin/ProjectAdminPage';
import RuntimeEnvironmentsPage from './pages/admin/RuntimeEnvironmentsPage';
import ServiceManagementPage from './pages/admin/ServiceManagementPage';
import SystemSettingsPage from './pages/admin/SystemSettingsPage';
import TemplateAdminPage from './pages/admin/TemplateAdminPage';
import UserAdminPage from './pages/admin/UserAdminPage';
import LoginPage from './pages/LoginPage';
import UserDashboardPage from './pages/user/UserDashboardPage';
import UserDeploymentDetailPage from './pages/user/UserDeploymentDetailPage';
import UserDeploymentRecordsPage from './pages/user/UserDeploymentRecordsPage';
import UserPipelineHistoryPage from './pages/user/UserPipelineHistoryPage';
import UserPipelinesPage from './pages/user/UserPipelinesPage';

/**
 * 前端路由总入口。
 * 管理端和用户端现在都会经过真实登录态与权限守卫。
 */
function RouteLoading() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-app">
      <Spin size="large" />
    </div>
  );
}

function RequireAuth() {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return <RouteLoading />;
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return <Outlet />;
}

function RequireAdmin() {
  const { user, loading, isAdmin } = useAuth();
  const location = useLocation();

  if (loading) {
    return <RouteLoading />;
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  if (!isAdmin) {
    return <Navigate to="/user/dashboard" replace />;
  }
  return <Outlet />;
}

function DefaultRedirect() {
  const { user, loading, isAdmin } = useAuth();

  if (loading) {
    return <RouteLoading />;
  }
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  return <Navigate to={isAdmin ? '/admin/dashboard' : '/user/dashboard'} replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<DefaultRedirect />} />
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireAdmin />}>
        <Route path="/admin" element={<AdminLayout />}>
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<AdminDashboardPage />} />
          <Route path="projects" element={<ProjectAdminPage />} />
          <Route path="hosts" element={<HostManagementPage />} />
          <Route path="hosts/:hostId/environments" element={<RuntimeEnvironmentsPage />} />
          <Route path="templates" element={<TemplateAdminPage />} />
          <Route path="pipelines" element={<PipelineAdminPage />} />
          <Route path="users" element={<UserAdminPage />} />
          <Route path="system-settings" element={<SystemSettingsPage />} />
          <Route path="deployments" element={<DeploymentRecordsPage />} />
          <Route path="deployments/:deploymentId" element={<AdminDeploymentDetailPage />} />
          <Route path="services" element={<ServiceManagementPage />} />
        </Route>
      </Route>
      <Route element={<RequireAuth />}>
        <Route path="/user" element={<UserLayout />}>
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<UserDashboardPage />} />
          <Route path="pipelines" element={<UserPipelinesPage />} />
          <Route path="deployments" element={<UserDeploymentRecordsPage />} />
          <Route path="pipelines/:pipelineId/history" element={<UserPipelineHistoryPage />} />
          <Route path="deployments/:deploymentId" element={<UserDeploymentDetailPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
