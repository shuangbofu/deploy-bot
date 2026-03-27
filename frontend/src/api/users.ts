import client from './client';
import type { UserPayload, UserSummary } from './types';

export const usersApi = {
  list: async () => (await client.get<UserSummary[]>('/users')).data,
  create: async (payload: UserPayload) => (await client.post<UserSummary>('/users', payload)).data,
  update: async (id: number, payload: UserPayload) => (await client.put<UserSummary>(`/users/${id}`, payload)).data,
  remove: async (id: number) => client.delete(`/users/${id}`),
};
