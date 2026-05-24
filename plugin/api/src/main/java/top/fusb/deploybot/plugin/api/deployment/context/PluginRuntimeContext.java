package top.fusb.deploybot.plugin.api.deployment.context;

import java.util.List;

/**
 * 描述一次部署在构建与运行阶段依赖的运行时选择结果。
 *
 * @param buildRuntimeTypes 构建阶段运行时类型列表
 * @param targetRuntimeTypes 目标主机运行阶段运行时类型列表
 * @param mavenSettingsFilePath Maven settings.xml 绝对路径
 */
public record PluginRuntimeContext(
        List<String> buildRuntimeTypes,
        List<String> targetRuntimeTypes,
        String mavenSettingsFilePath
) {

    /**
     * 返回最能代表当前服务运行时的首选类型。
     * 优先取目标主机运行时，其次退回构建运行时。
     *
     * @return 首选运行时类型；若都不存在则返回 {@code null}
     */
    public String primaryRuntimeType() {
        if (targetRuntimeTypes != null && !targetRuntimeTypes.isEmpty()) {
            return targetRuntimeTypes.get(0);
        }
        if (buildRuntimeTypes != null && !buildRuntimeTypes.isEmpty()) {
            return buildRuntimeTypes.get(0);
        }
        return null;
    }
}
