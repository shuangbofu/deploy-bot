package top.fusb.deploybot.plugin.api.deployment.form;

import java.util.List;

/**
 * 描述插件配置表单中的单个字段。
 *
 * @param key 字段键
 * @param label 字段标题
 * @param type 字段类型
 * @param required 是否必填
 * @param placeholder 占位提示
 * @param helpText 辅助说明
 * @param options 下拉选项列表
 * @param binding 字段值写回平台时的绑定目标
 */
public record PluginFormField(
        String key,
        String label,
        PluginFormFieldType type,
        boolean required,
        String placeholder,
        String helpText,
        List<PluginFormOption> options,
        PluginFormFieldBinding binding
) {

    /**
     * 使用默认绑定规则创建字段定义。
     * <p>
     * 未显式指定绑定时默认写入同名插件配置，平台不需要理解字段的业务含义。
     * </p>
     *
     * @param key 字段键
     * @param label 字段标题
     * @param type 字段类型
     * @param required 是否必填
     * @param placeholder 占位提示
     * @param helpText 辅助说明
     * @param options 下拉选项列表
     */
    public PluginFormField(
            String key,
            String label,
            PluginFormFieldType type,
            boolean required,
            String placeholder,
            String helpText,
            List<PluginFormOption> options
    ) {
        this(key, label, type, required, placeholder, helpText, options, PluginFormFieldBinding.pluginConfig(key));
    }
}
