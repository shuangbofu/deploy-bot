import client from './client';
import type { PipelinePayload, PipelineSummary } from './types';

/**
 * 流水线相关接口。
 */
export const pipelinesApi = {
  list: async () => (await client.get<PipelineSummary[]>('/pipelines')).data,
  getBranches: async (pipelineId: number) => (await client.get<string[]>(`/pipelines/${pipelineId}/branches`)).data,
  create: async (payload: PipelinePayload) => (await client.post<PipelineSummary>('/pipelines', payload)).data,
  update: async (id: number, payload: PipelinePayload) => (await client.put<PipelineSummary>(`/pipelines/${id}`, payload)).data,
  remove: async (id: number) => client.delete(`/pipelines/${id}`),
};
