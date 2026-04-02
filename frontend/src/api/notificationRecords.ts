import client from './client';
import type { NotificationDeliveryRecordSummary } from './types';

export const notificationRecordsApi = {
  list: async () => (await client.get<NotificationDeliveryRecordSummary[]>('/notification-records')).data,
  listMine: async () => (await client.get<NotificationDeliveryRecordSummary[]>('/notification-records/mine')).data,
};
