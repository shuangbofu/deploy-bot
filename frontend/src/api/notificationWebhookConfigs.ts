import client from './client';
import type { NotificationWebhookConfigPayload, NotificationWebhookConfigSummary } from './types';

export const notificationWebhookConfigsApi = {
  list: async () => (await client.get<NotificationWebhookConfigSummary[]>('/notification-webhook-configs')).data,
  create: async (payload: NotificationWebhookConfigPayload) =>
    (await client.post<NotificationWebhookConfigSummary>('/notification-webhook-configs', payload)).data,
  update: async (id: number, payload: NotificationWebhookConfigPayload) =>
    (await client.put<NotificationWebhookConfigSummary>(`/notification-webhook-configs/${id}`, payload)).data,
  remove: async (id: number) => client.delete(`/notification-webhook-configs/${id}`),
};
