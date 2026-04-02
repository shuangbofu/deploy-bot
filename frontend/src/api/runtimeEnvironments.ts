import client from './client';
import type {
  DetectionItem,
  MavenSettingsPayload,
  MavenSettingsSummary,
  PresetItem,
  RuntimeEnvironmentInstallAccepted,
  RuntimeEnvironmentInstallPayload,
  RuntimeEnvironmentInstallTaskStatus,
  RuntimeEnvironmentPayload,
  RuntimeEnvironmentSummary,
} from './types';

/**
 * 运行环境相关接口。
 */
export const runtimeEnvironmentsApi = {
  list: async (hostId?: string | number) => (await client.get<RuntimeEnvironmentSummary[]>('/runtime-environments', {
    params: hostId ? { hostId } : undefined,
  })).data,
  create: async (payload: RuntimeEnvironmentPayload) => (await client.post<RuntimeEnvironmentSummary>('/runtime-environments', payload)).data,
  update: async (id: number, payload: RuntimeEnvironmentPayload) => (await client.put<RuntimeEnvironmentSummary>(`/runtime-environments/${id}`, payload)).data,
  remove: async (id: number) => client.delete(`/runtime-environments/${id}`),
  detections: async (hostId?: string | number) => (await client.get<DetectionItem[]>('/runtime-environments/detections', {
    params: hostId ? { hostId } : undefined,
  })).data,
  presets: async (hostId?: string | number) => (await client.get<PresetItem[]>('/runtime-environments/presets', {
    params: hostId ? { hostId } : undefined,
  })).data,
  installTasks: async (hostId?: string | number) => (await client.get<RuntimeEnvironmentInstallTaskStatus[]>('/runtime-environments/install-tasks', {
    params: hostId ? { hostId } : undefined,
  })).data,
  installPreset: async (payload: RuntimeEnvironmentInstallPayload) => (
    await client.post<RuntimeEnvironmentInstallAccepted>('/runtime-environments/install', payload)
  ).data,
  listMavenSettings: async (runtimeEnvironmentId: number) => (
    await client.get<MavenSettingsSummary[]>(`/runtime-environments/${runtimeEnvironmentId}/maven-settings`)
  ).data,
  createMavenSettings: async (runtimeEnvironmentId: number, payload: MavenSettingsPayload) => (
    await client.post<MavenSettingsSummary>(`/runtime-environments/${runtimeEnvironmentId}/maven-settings`, payload)
  ).data,
  updateMavenSettings: async (runtimeEnvironmentId: number, id: number, payload: MavenSettingsPayload) => (
    await client.put<MavenSettingsSummary>(`/runtime-environments/${runtimeEnvironmentId}/maven-settings/${id}`, payload)
  ).data,
  removeMavenSettings: async (runtimeEnvironmentId: number, id: number) => (
    await client.delete(`/runtime-environments/${runtimeEnvironmentId}/maven-settings/${id}`)
  ),
};
