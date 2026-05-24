package top.fusb.deploybot.plugin.api.deployment.variable;

/**
 * 描述插件对平台变量进行二次处理的方式。
 */
public enum PluginVariableMutationType {
    VARIABLE_INJECT,
    COMMAND_REWRITE,
    COMPOSITE_REUSE
}
