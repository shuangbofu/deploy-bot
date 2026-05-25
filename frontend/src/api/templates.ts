import client from './client';
import type { PageResult, TemplatePayload, TemplateSummary } from './types';

/**
 * 模板相关接口。
 */
export const templatesApi = {
  list: async () => (await client.get<TemplateSummary[]>('/templates')).data,
  listPage: async (params: { page: number; pageSize: number; keyword?: string; templateType?: string; pluginId?: string; monitorProcess?: boolean }) =>
    (await client.get<PageResult<TemplateSummary>>('/templates/page', { params })).data,
  get: async (id: number) => (await client.get<TemplateSummary>(`/templates/${id}`)).data,
  create: async (payload: TemplatePayload) => (await client.post<TemplateSummary>('/templates', payload)).data,
  update: async (id: number, payload: TemplatePayload) => (await client.put<TemplateSummary>(`/templates/${id}`, payload)).data,
  remove: async (id: number) => client.delete(`/templates/${id}`),
};
