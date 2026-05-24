package top.fusb.deploybot.plugin.api.deployment.template;

/**
 * 描述类型插件自带的一份默认模板定义。
 *
 * @param templateKey 模板键，用于标识插件内部的默认模板
 * @param name 模板名称
 * @param description 模板说明
 * @param templateType 模板类型
 * @param buildScriptContent 构建脚本
 * @param deployScriptContent 发布脚本
 * @param variablesSchema 变量定义 JSON
 * @param monitorProcess 是否需要进程接管
 */
public record PluginBuiltinTemplate(
        String templateKey,
        String name,
        String description,
        String templateType,
        String buildScriptContent,
        String deployScriptContent,
        String variablesSchema,
        boolean monitorProcess
) {
}
