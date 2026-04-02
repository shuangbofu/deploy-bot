import type { NotificationEventType } from '../types/domain';

export const notificationEventTypeOptions: Array<{ label: string; value: NotificationEventType }> = [
  { label: '开始通知', value: 'DEPLOYMENT_STARTED' },
  { label: '结束通知', value: 'DEPLOYMENT_FINISHED' },
];

export const notificationTemplateVariableOptions = [
  { token: '{{pipelineName}}', label: '流水线名称' },
  { token: '{{projectName}}', label: '项目名称' },
  { token: '{{branch}}', label: '部署分支' },
  { token: '{{eventLabel}}', label: '通知结果' },
  { token: '{{statusLabel}}', label: '部署状态' },
  { token: '{{triggeredByDisplayName}}', label: '部署人' },
  { token: '{{stoppedByDisplayName}}', label: '停止人' },
  { token: '{{hostName}}', label: '目标主机' },
  { token: '{{startedAt}}', label: '开始时间' },
  { token: '{{finishedAt}}', label: '结束时间' },
  { token: '{{duration}}', label: '耗时' },
  { token: '{{errorMessage}}', label: '错误信息' },
  { token: '{{deploymentId}}', label: '部署编号' },
  { token: '{{detailUrl}}', label: '详情链接' },
] as const;

export function getNotificationChannelTypeLabel(type: string) {
  return type === 'FEISHU' ? '飞书' : type;
}

export function getNotificationEventTypeLabel(type: NotificationEventType) {
  return type === 'DEPLOYMENT_STARTED' ? '开始通知' : '结束通知';
}
