package top.fusb.deploybot.plugin.api.process;

import java.util.Map;

import top.fusb.deploybot.plugin.api.deployment.context.PluginProjectContext;
import top.fusb.deploybot.plugin.api.deployment.context.PluginRuntimeContext;
import top.fusb.deploybot.plugin.api.deployment.context.PluginServiceContext;
import top.fusb.deploybot.plugin.api.deployment.context.PluginVariableContext;

/**
 * PID 发现阶段的中立上下文对象。
 */
public record ProcessLocatorContext(
        PluginProjectContext projectContext,
        PluginRuntimeContext runtimeContext,
        PluginVariableContext variableContext,
        PluginServiceContext serviceContext,
        boolean remoteHost,
        ProcessLocatorExecutionBridge executionBridge
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
     * 返回部署变量快照。
     *
     * @return 部署变量快照
     */
    public Map<String, String> deploymentVariables() {
        return variableContext == null ? null : variableContext.variables();
    }

    /**
     * 读取指定部署变量。
     *
     * @param key 变量键
     * @return 变量值
     */
    public String deploymentVariable(String key) {
        return variableContext == null ? null : variableContext.value(key);
    }
}
