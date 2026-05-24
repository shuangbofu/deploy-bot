package top.fusb.deploybot.plugin.api.startup;

import top.fusb.deploybot.plugin.api.deployment.context.PluginProjectContext;
import top.fusb.deploybot.plugin.api.deployment.context.PluginRuntimeContext;
import top.fusb.deploybot.plugin.api.deployment.context.PluginServiceContext;

/**
 * 启动判定阶段的中立上下文对象。
 */
public record StartupJudgeContext(
        PluginProjectContext projectContext,
        PluginRuntimeContext runtimeContext,
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
     * 返回启动关键字。
     *
     * @return 启动关键字
     */
    public String startupKeyword() {
        return serviceContext == null ? null : serviceContext.startupKeyword();
    }

    /**
     * 返回运行日志路径。
     *
     * @return 运行日志路径
     */
    public String runtimeLogPath() {
        return serviceContext == null ? null : serviceContext.runtimeLogPath();
    }

    /**
     * 返回候选受管 PID。
     *
     * @return 候选受管 PID
     */
    public Long monitoredPid() {
        return serviceContext == null ? null : serviceContext.monitoredPid();
    }
}
