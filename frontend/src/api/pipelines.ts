import client from './client';
import type { PageResult, PipelineHallSummary, PipelinePayload, PipelineSummary } from './types';

/**
 * 流水线相关接口。
 */
export const pipelinesApi = {
  list: async () => (await client.get<PipelineSummary[]>('/pipelines')).data,
  listHall: async () => (await client.get<PipelineHallSummary[]>('/pipelines/hall')).data,
  listPage: async (params: { page: number; pageSize: number; keyword?: string; projectId?: number; templateId?: number; hostId?: number; tags?: string[] }) =>
    (await client.get<PageResult<PipelineSummary>>('/pipelines/page', { params })).data,
  listTags: async () => (await client.get<string[]>('/pipelines/tags')).data,
  getBranches: async (pipelineId: number) => (await client.get<string[]>(`/pipelines/${pipelineId}/branches`)).data,
  create: async (payload: PipelinePayload) => (await client.post<PipelineSummary>('/pipelines', payload)).data,
  update: async (id: number, payload: PipelinePayload) => (await client.put<PipelineSummary>(`/pipelines/${id}`, payload)).data,
  remove: async (id: number) => client.delete(`/pipelines/${id}`),
};
