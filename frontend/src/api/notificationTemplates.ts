import client from './client';
import type { NotificationTemplatePayload, NotificationTemplateSummary } from './types';

export const notificationTemplatesApi = {
  list: async () => (await client.get<NotificationTemplateSummary[]>('/notification-templates')).data,
  create: async (payload: NotificationTemplatePayload) => (await client.post<NotificationTemplateSummary>('/notification-templates', payload)).data,
  update: async (id: number, payload: NotificationTemplatePayload) => (await client.put<NotificationTemplateSummary>(`/notification-templates/${id}`, payload)).data,
  remove: async (id: number) => client.delete(`/notification-templates/${id}`),
};
