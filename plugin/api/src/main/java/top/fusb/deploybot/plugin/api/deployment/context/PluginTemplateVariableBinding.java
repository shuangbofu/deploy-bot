package top.fusb.deploybot.plugin.api.deployment.context;

/**
 * 描述模板变量与平台标准语义之间的绑定关系。
 *
 * @param scope 绑定范围
 * @param key 绑定目标键
 */
public record PluginTemplateVariableBinding(
        PluginTemplateVariableBindingScope scope,
        String key
) {
}
