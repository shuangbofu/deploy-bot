package top.fusb.deploybot.plugin.api.deployment.form;

import java.util.List;

/**
 * 描述插件对外暴露的配置表单结构。
 *
 * @param sections 表单分组列表
 */
public record PluginFormSchema(
        List<PluginFormSection> sections
) {
}
