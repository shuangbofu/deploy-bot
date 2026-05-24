package top.fusb.deploybot.plugin.common.deployment.config;

/**
 * 描述插件资源文件中的一份默认模板配置。
 *
 * @param templateKey 模板唯一标识
 * @param name 模板名称
 * @param description 模板说明
 * @param templateType 模板类型
 * @param buildScriptResource 构建脚本资源路径
 * @param deployScriptResource 发布脚本资源路径
 * @param variablesSchema 额外变量定义 JSON
 * @param monitorProcess 是否需要服务接管
 */
public record PluginBuiltinTemplateConfig(
        String templateKey,
        String name,
        String description,
        String templateType,
        String buildScriptResource,
        String deployScriptResource,
        String variablesSchema,
        boolean monitorProcess
) {
}
