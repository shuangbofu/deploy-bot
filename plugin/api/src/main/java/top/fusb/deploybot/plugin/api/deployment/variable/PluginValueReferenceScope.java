package top.fusb.deploybot.plugin.api.deployment.variable;

/**
 * 描述插件值引用的作用域。
 */
public enum PluginValueReferenceScope {
    /**
     * 来自平台已解析好的上下文字段。
     */
    CONTEXT,
    /**
     * 来自平台定义的标准命令槽位。
     */
    COMMAND_SLOT,
    /**
     * 来自模板/部署变量池。
     */
    VARIABLE
}
