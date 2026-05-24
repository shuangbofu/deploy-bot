package top.fusb.deploybot.plugin.api.deployment.definition;

import java.util.List;

/**
 * 描述插件对构建端和目标端运行时的要求。
 *
 * @param buildRuntimeTypes 构建阶段依赖的运行时类型
 * @param targetRuntimeTypes 目标主机阶段依赖的运行时类型
 */
public record PluginRuntimeRequirement(
        List<String> buildRuntimeTypes,
        List<String> targetRuntimeTypes
) {
}
