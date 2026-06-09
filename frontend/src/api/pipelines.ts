import client from './client';
import type { PipelineBranchOption } from '../types/domain';
import type { PageResult, PipelineHallRunningServiceSummary, PipelineHallSummary, PipelinePayload, PipelineSummary } from './types';

/**
 * 流水线相关接口。
 */
export const pipelinesApi = {
  list: async () => (await client.get<PipelineSummary[]>('/pipelines')).data,
  listHall: async () => (await client.get<PipelineHallSummary[]>('/pipelines/hall')).data,
  listHallPage: async (params: {
    page: number;
    pageSize: number;
    keyword?: string;
    tags?: string[];
    filterMode?: string;
    selectedPipelineId?: number;
    pinActivePipelines?: boolean;
  }) => (await client.get<PageResult<PipelineHallSummary>>('/pipelines/hall/page', { params })).data,
  listHallByIds: async (ids: number[]) => (await client.get<PipelineHallSummary[]>('/pipelines/hall/by-ids', { params: { ids } })).data,
  listRunningServices: async () => (await client.get<PipelineHallRunningServiceSummary[]>('/pipelines/hall/running-services')).data,
  listFavorites: async () => (await client.get<number[]>('/pipelines/favorites')).data,
  favorite: async (id: number) => client.post(`/pipelines/${id}/favorite`),
  unfavorite: async (id: number) => client.delete(`/pipelines/${id}/favorite`),
  listPage: async (params: { page: number; pageSize: number; keyword?: string; projectId?: number; templateId?: number; hostId?: number; tags?: string[] }) =>
    (await client.get<PageResult<PipelineSummary>>('/pipelines/page', { params })).data,
  listTags: async () => (await client.get<string[]>('/pipelines/tags')).data,
  get: async (id: number) => (await client.get<PipelineSummary>(`/pipelines/${id}`)).data,
  getBranches: async (pipelineId: number) => (await client.get<string[]>(`/pipelines/${pipelineId}/branches`)).data,
  getBranchOptions: async (pipelineId: number) => (await client.get<PipelineBranchOption[]>(`/pipelines/${pipelineId}/branch-options`)).data,
  create: async (payload: PipelinePayload) => (await client.post<PipelineSummary>('/pipelines', payload)).data,
  update: async (id: number, payload: PipelinePayload) => (await client.put<PipelineSummary>(`/pipelines/${id}`, payload)).data,
  remove: async (id: number) => client.delete(`/pipelines/${id}`),
};
