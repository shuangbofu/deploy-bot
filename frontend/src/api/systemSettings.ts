import client from './client';
import type { SystemSettingsPayload, SystemSettingsResponse } from './types';

/**
 * 系统设置相关接口。
 */
export const systemSettingsApi = {
  get: async () => (await client.get<SystemSettingsResponse>('/system-settings')).data,
  update: async (payload: SystemSettingsPayload) => (await client.put<SystemSettingsResponse>('/system-settings', payload)).data,
  generateGitKeyPair: async () => (await client.post<SystemSettingsResponse>('/system-settings/generate-ssh-keypair')).data,
  generateHostKeyPair: async () => (await client.post<SystemSettingsResponse>('/system-settings/generate-host-ssh-keypair')).data,
};
