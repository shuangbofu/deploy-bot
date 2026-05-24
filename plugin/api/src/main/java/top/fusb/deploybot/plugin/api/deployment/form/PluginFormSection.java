package top.fusb.deploybot.plugin.api.deployment.form;

import java.util.List;

/**
 * 描述插件表单中的一个分组。
 *
 * @param key 分组键
 * @param title 分组标题
 * @param description 分组说明
 * @param fields 分组字段
 */
public record PluginFormSection(
        String key,
        String title,
        String description,
        List<PluginFormField> fields
) {
}
