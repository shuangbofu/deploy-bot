package top.fusb.deploybot.plugin.api.deployment.form;

/**
 * 描述插件表单字段与平台内部数据目标之间的绑定关系。
 *
 * @param scope 绑定范围
 * @param key 目标键名
 */
public record PluginFormFieldBinding(
        PluginFormFieldBindingScope scope,
        String key
) {

    /**
     * 创建一个绑定到插件配置 JSON 的字段绑定。
     *
     * @param key 插件配置键名
     * @return 绑定对象
     */
    public static PluginFormFieldBinding pluginConfig(String key) {
        return new PluginFormFieldBinding(PluginFormFieldBindingScope.PLUGIN_CONFIG, key);
    }

    /**
     * 创建一个绑定到流水线变量池的字段绑定。
     *
     * @param key 流水线变量键名
     * @return 绑定对象
     */
    public static PluginFormFieldBinding pipelineVariable(String key) {
        return new PluginFormFieldBinding(PluginFormFieldBindingScope.PIPELINE_VARIABLE, key);
    }
}
