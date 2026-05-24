package top.fusb.deploybot.plugin.api.deployment.form;

/**
 * 描述插件配置表单中单个可选项。
 *
 * @param value 选项值
 * @param label 选项展示名称
 */
public record PluginFormOption(
        String value,
        String label
) {
}
