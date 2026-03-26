import client from './client';
import type { TemplatePayload, TemplateSummary } from './types';

/**
 * 模板相关接口。
 */
export const templatesApi = {
  list: async () => (await client.get<TemplateSummary[]>('/templates')).data,
  create: async (payload: TemplatePayload) => (await client.post<TemplateSummary>('/templates', payload)).data,
  update: async (id: number, payload: TemplatePayload) => (await client.put<TemplateSummary>(`/templates/${id}`, payload)).data,
  remove: async (id: number) => client.delete(`/templates/${id}`),
};
