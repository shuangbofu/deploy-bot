import client from './client';
import type { NotificationChannelSummary, NotificationPayload } from './types';

export const notificationsApi = {
  list: async () => (await client.get<NotificationChannelSummary[]>('/notifications')).data,
  create: async (payload: NotificationPayload) => (await client.post<NotificationChannelSummary>('/notifications', payload)).data,
  update: async (id: number, payload: NotificationPayload) => (await client.put<NotificationChannelSummary>(`/notifications/${id}`, payload)).data,
  remove: async (id: number) => client.delete(`/notifications/${id}`),
};
