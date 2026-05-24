package top.fusb.deploybot.dto;

import java.util.List;

/**
 * 用于前后端展示的部署插件计划摘要。
 *
 * @param pluginId 插件标识
 * @param pluginType 插件类型
 * @param displayName 展示名称
 * @param processLocatorPluginId PID 发现插件标识
 * @param startupJudgePluginId 启动判定插件标识
 * @param children 子计划摘要
 */
public record DeploymentPluginPlanSummary(
        String pluginId,
        String pluginType,
        String displayName,
        String processLocatorPluginId,
        String startupJudgePluginId,
        List<DeploymentPluginPlanSummary> children
) {
}
