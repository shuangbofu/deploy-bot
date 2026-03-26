import { Navigate, Route, Routes } from 'react-router-dom';
import AdminLayout from './components/AdminLayout';
import UserLayout from './components/UserLayout';
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
import UserDashboardPage from './pages/user/UserDashboardPage';
import UserDeploymentDetailPage from './pages/user/UserDeploymentDetailPage';
import UserDeploymentRecordsPage from './pages/user/UserDeploymentRecordsPage';
import UserPipelineHistoryPage from './pages/user/UserPipelineHistoryPage';
import UserPipelinesPage from './pages/user/UserPipelinesPage';

/**
 * 前端路由总入口。
 * 管理端和用户端共用同一套前端应用，但通过不同布局进行隔离。
 */
export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/user" replace />} />
      <Route path="/admin" element={<AdminLayout />}>
        <Route index element={<Navigate to="dashboard" replace />} />
        <Route path="dashboard" element={<AdminDashboardPage />} />
        <Route path="projects" element={<ProjectAdminPage />} />
        <Route path="hosts" element={<HostManagementPage />} />
        <Route path="hosts/:hostId/environments" element={<RuntimeEnvironmentsPage />} />
        <Route path="templates" element={<TemplateAdminPage />} />
        <Route path="pipelines" element={<PipelineAdminPage />} />
        <Route path="system-settings" element={<SystemSettingsPage />} />
        <Route path="deployments" element={<DeploymentRecordsPage />} />
        <Route path="deployments/:deploymentId" element={<AdminDeploymentDetailPage />} />
        <Route path="services" element={<ServiceManagementPage />} />
      </Route>
      <Route path="/user" element={<UserLayout />}>
        <Route index element={<Navigate to="dashboard" replace />} />
        <Route path="dashboard" element={<UserDashboardPage />} />
        <Route path="pipelines" element={<UserPipelinesPage />} />
        <Route path="deployments" element={<UserDeploymentRecordsPage />} />
        <Route path="pipelines/:pipelineId/history" element={<UserPipelineHistoryPage />} />
        <Route path="deployments/:deploymentId" element={<UserDeploymentDetailPage />} />
      </Route>
    </Routes>
  );
}
