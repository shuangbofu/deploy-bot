import client from './client';
import type { LoginPayload, LoginResponse, UserSummary } from './types';

export const authApi = {
  login: async (payload: LoginPayload) => (await client.post<LoginResponse>('/auth/login', payload)).data,
  me: async () => (await client.get<UserSummary>('/auth/me')).data,
  logout: async () => client.post('/auth/logout'),
};
