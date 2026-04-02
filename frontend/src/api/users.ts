import client from './client';
import type { AvatarUploadResponse, UserPayload, UserSummary } from './types';

export const usersApi = {
  list: async () => (await client.get<UserSummary[]>('/users')).data,
  create: async (payload: UserPayload) => (await client.post<UserSummary>('/users', payload)).data,
  update: async (id: number, payload: UserPayload) => (await client.put<UserSummary>(`/users/${id}`, payload)).data,
  resetPassword: async (id: number) => client.post(`/users/${id}/reset-password`),
  uploadAvatar: async (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return (await client.post<AvatarUploadResponse>('/users/avatar', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })).data;
  },
  remove: async (id: number) => client.delete(`/users/${id}`),
};
