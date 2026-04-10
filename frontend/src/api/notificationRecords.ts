import client from './client';
import type { NotificationDeliveryRecordSummary, PageResult } from './types';

export const notificationRecordsApi = {
  list: async () => (await client.get<NotificationDeliveryRecordSummary[]>('/notification-records')).data,
  listMine: async () => (await client.get<NotificationDeliveryRecordSummary[]>('/notification-records/mine')).data,
  listPage: async (params?: {
    page?: number;
    pageSize?: number;
    channelType?: 'FEISHU';
    eventType?: 'DEPLOYMENT_STARTED' | 'DEPLOYMENT_FINISHED';
  }) => (await client.get<PageResult<NotificationDeliveryRecordSummary>>('/notification-records/page', { params })).data,
  listMinePage: async (params?: {
    page?: number;
    pageSize?: number;
    channelType?: 'FEISHU';
    eventType?: 'DEPLOYMENT_STARTED' | 'DEPLOYMENT_FINISHED';
  }) => (await client.get<PageResult<NotificationDeliveryRecordSummary>>('/notification-records/mine/page', { params })).data,
};
