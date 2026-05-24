package top.fusb.deploybot.plugin.api.deployment.variable;

import java.util.Map;

import top.fusb.deploybot.plugin.api.deployment.context.PluginProjectContext;
import top.fusb.deploybot.plugin.api.deployment.context.PluginRuntimeContext;
import top.fusb.deploybot.plugin.api.deployment.context.PluginServiceContext;
import top.fusb.deploybot.plugin.api.deployment.context.PluginVariableContext;

/**
 * 描述类型插件在变量拼装阶段所需的上下文。
 *
 * @param phase 当前拼装阶段
 * @param pluginId 类型插件标识
 * @param runtimeEnvironmentType 运行时类型
 * @param templateType 模板类型
 * @param variables 当前变量快照
 */
public record PluginVariableAssemblyContext(
        PluginVariableAssemblyPhase phase,
        String pluginId,
        PluginProjectContext projectContext,
        PluginRuntimeContext runtimeContext,
        PluginVariableContext variableContext,
        PluginServiceContext serviceContext
) {

    /**
     * 返回首选运行时类型。
     *
     * @return 首选运行时类型
     */
    public String runtimeEnvironmentType() {
        return runtimeContext == null ? null : runtimeContext.primaryRuntimeType();
    }

    /**
     * 返回模板类型。
     *
     * @return 模板类型
     */
    public String templateType() {
        return projectContext == null ? null : projectContext.templateType();
    }

    /**
     * 返回当前变量快照。
     *
     * @return 当前变量快照
     */
    public Map<String, String> variables() {
        return variableContext == null ? null : variableContext.variables();
    }

    /**
     * 返回 Maven settings.xml 绝对路径。
     *
     * @return Maven settings.xml 绝对路径
     */
    public String mavenSettingsFilePath() {
        return runtimeContext == null ? null : runtimeContext.mavenSettingsFilePath();
    }

    /**
     * 返回插件运行配置值。
     *
     * @param key 插件配置字段 key
     * @return 配置值；不存在则返回 {@code null}
     */
    public String configValue(String key) {
        return serviceContext == null ? null : serviceContext.configValue(key);
    }

    /**
     * 返回插件运行配置 Map。平台不理解其中字段含义，具体插件应转换成自己的配置类后使用。
     *
     * @return 插件运行配置；不存在时返回 {@code null}
     */
    public Map<String, String> config() {
        return serviceContext == null ? null : serviceContext.config();
    }
}
