package top.fusb.deploybot.plugin.api.deployment.definition;

import java.util.List;

/**
 * 描述部署类型插件的对外元信息。
 *
 * @param pluginId 插件标识
 * @param displayName 展示名称
 * @param category 分类名称，例如“后端服务”“静态站点”“一体化项目”
 * @param templateTypes 当前插件主要对应的模板类型列表
 * @param composite 是否为复合插件
 * @param builtin 是否为系统内置插件
 * @param provider 提供方标识
 * @param description 插件说明
 */
public record PluginDescriptor(
        String pluginId,
        String displayName,
        String category,
        List<String> templateTypes,
        boolean composite,
        boolean builtin,
        String provider,
        String description
) {
}
