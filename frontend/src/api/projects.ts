import client from './client';
import type { ProjectConnectionTestResult, ProjectPayload, ProjectSummary } from './types';

/**
 * 项目相关接口。
 */
export const projectsApi = {
  list: async () => (await client.get<ProjectSummary[]>('/projects')).data,
  create: async (payload: ProjectPayload) => (await client.post<ProjectSummary>('/projects', payload)).data,
  update: async (id: number, payload: ProjectPayload) => (await client.put<ProjectSummary>(`/projects/${id}`, payload)).data,
  testConnection: async (payload: ProjectPayload) => (await client.post<ProjectConnectionTestResult>('/projects/test-connection', payload)).data,
  remove: async (id: number) => client.delete(`/projects/${id}`),
};
