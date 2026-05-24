package top.fusb.deploybot.plugin.api.deployment.variable;

/**
 * 描述插件变量拼装所处的阶段。
 */
public enum PluginVariableAssemblyPhase {

    /**
     * 构建阶段变量拼装。
     */
    BUILD,

    /**
     * 发布/启动阶段变量拼装。
     */
    DEPLOY
}
