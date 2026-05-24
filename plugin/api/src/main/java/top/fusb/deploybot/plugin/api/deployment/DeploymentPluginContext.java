package top.fusb.deploybot.plugin.api.deployment;

import top.fusb.deploybot.plugin.api.deployment.context.PluginHostContext;
import top.fusb.deploybot.plugin.api.deployment.context.PluginProjectContext;
import top.fusb.deploybot.plugin.api.deployment.context.PluginRuntimeContext;
import top.fusb.deploybot.plugin.api.deployment.context.PluginServiceContext;
import top.fusb.deploybot.plugin.api.deployment.context.PluginVariableContext;

/**
 * 描述部署插件决策阶段所需的中立上下文。
 */
public record DeploymentPluginContext(
        String pluginId,
        PluginProjectContext projectContext,
        PluginRuntimeContext runtimeContext,
        PluginHostContext hostContext,
        PluginVariableContext variableContext,
        PluginServiceContext serviceContext,
        String buildScript,
        String deployScript
) {

    /**
     * 返回首选运行时类型，优先取目标运行时，其次取构建运行时。
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
     * 返回模板名称。
     *
     * @return 模板名称
     */
    public String templateName() {
        return projectContext == null ? null : projectContext.templateName();
    }

    /**
     * 返回原始变量 JSON。
     *
     * @return 变量 JSON
     */
    public String variablesJson() {
        return variableContext == null ? null : variableContext.variablesJson();
    }

    /**
     * 返回启动命令。
     *
     * @return 启动命令
     */
    public String startCommand() {
        return serviceContext == null ? null : serviceContext.startCommand();
    }

    /**
     * 返回启动关键字。
     *
     * @return 启动关键字
     */
    public String startupKeyword() {
        return serviceContext == null ? null : serviceContext.startupKeyword();
    }
}
