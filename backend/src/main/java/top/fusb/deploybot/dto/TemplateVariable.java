package top.fusb.deploybot.dto;

/**
 * 模板变量定义。
 */
public record TemplateVariable(
        /** 变量名。 */
        String name,
        /** 显示名称。 */
        String label,
        /** 是否必填。 */
        Boolean required,
        /** 输入占位提示。 */
        String placeholder
) {
}
