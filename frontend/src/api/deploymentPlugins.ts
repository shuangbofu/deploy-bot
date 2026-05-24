import client from './client';
import type { DeploymentPluginDefinitionSummary, ShellVariableSummary } from '../types/domain';

/**
 * 部署类型插件查询接口。
 */
export const deploymentPluginsApi = {
  list: async () => (await client.get<DeploymentPluginDefinitionSummary[]>('/deployment-plugins')).data,
  shellVariables: async () => (await client.get<ShellVariableSummary[]>('/deployment-plugins/shell-variables')).data,
};
