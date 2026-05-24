package top.fusb.deploybot.plugin.api.deployment.variable;

/**
 * 描述插件规则中引用的一个结构化值位置。
 *
 * @param scope 值所在作用域
 * @param key 作用域内键名
 * @param label 对外展示时使用的可读名称
 */
public record PluginValueReference(
        PluginValueReferenceScope scope,
        String key,
        String label
) {

    /**
     * 创建一个上下文字段引用。
     *
     * @param key 上下文字段键名
     * @param label 对外展示名称
     * @return 上下文字段引用
     */
    public static PluginValueReference context(String key, String label) {
        return new PluginValueReference(PluginValueReferenceScope.CONTEXT, key, label);
    }

    /**
     * 创建一个标准命令槽位引用。
     *
     * @param slot 命令槽位
     * @param label 对外展示名称
     * @return 命令槽位引用
     */
    public static PluginValueReference commandSlot(PluginCommandSlot slot, String label) {
        return new PluginValueReference(PluginValueReferenceScope.COMMAND_SLOT, slot == null ? null : slot.name(), label);
    }

    /**
     * 创建一个变量池字段引用。
     *
     * @param key 变量键名
     * @param label 对外展示名称
     * @return 变量字段引用
     */
    public static PluginValueReference variable(String key, String label) {
        return new PluginValueReference(PluginValueReferenceScope.VARIABLE, key, label);
    }
}
