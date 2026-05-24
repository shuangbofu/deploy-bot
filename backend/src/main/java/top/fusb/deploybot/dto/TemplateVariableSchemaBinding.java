package top.fusb.deploybot.dto;

/**
 * 模板变量绑定定义。
 *
 * @param scope 绑定范围
 * @param key 绑定目标键
 */
public record TemplateVariableSchemaBinding(
        String scope,
        String key
) {
}
