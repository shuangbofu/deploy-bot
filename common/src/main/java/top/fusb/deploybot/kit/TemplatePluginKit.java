package top.fusb.deploybot.kit;

import java.util.List;

/**
 * 模板类型与内置部署插件之间的映射工具。
 * <p>
 * 这份映射目前只服务于系统内置模板与插件的兜底归属判断，
 * 统一放在这里，避免在多个 service 中各自手写一份 switch。
 * </p>
 */
public final class TemplatePluginKit {

    private TemplatePluginKit() {
    }

    /**
     * 根据模板类型解析对应的内置部署插件标识。
     *
     * @param templateType 模板类型
     * @return 插件标识；若当前模板类型没有内置映射则返回 {@code null}
     */
    public static String resolvePluginIdByTemplateType(String templateType) {
        String normalizedTemplateType = TextKit.trimToNull(templateType);
        if (normalizedTemplateType == null) {
            return null;
        }
        return switch (normalizedTemplateType) {
            case "springboot" -> "springboot-deployment";
            case "react", "vue" -> "node-static-deployment";
            case "springboot_frontend" -> "fullstack-deployment";
            default -> null;
        };
    }

    /**
     * 根据插件标识返回它默认承接的模板类型列表。
     *
     * @param pluginId 插件标识
     * @return 模板类型列表；若当前插件没有内置映射则返回空列表
     */
    public static List<String> resolveTemplateTypesByPluginId(String pluginId) {
        String normalizedPluginId = TextKit.trimToNull(pluginId);
        if (normalizedPluginId == null) {
            return List.of();
        }
        return switch (normalizedPluginId) {
            case "springboot-deployment" -> List.of("springboot");
            case "node-static-deployment" -> List.of("react", "vue");
            case "fullstack-deployment" -> List.of("springboot_frontend");
            default -> List.of();
        };
    }
}
