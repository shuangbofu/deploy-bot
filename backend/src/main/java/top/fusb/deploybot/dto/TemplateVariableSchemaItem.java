package top.fusb.deploybot.dto;

import java.util.List;

/**
 * 模板变量完整结构定义。
 *
 * @param name 变量名
 * @param label 显示名称
 * @param placeholder 占位提示
 * @param required 是否必填
 * @param pipelineInput 是否允许在流水线层面填写
 * @param phase 所属阶段
 * @param mutationRuleIds 绑定的处理逻辑标识
 * @param binding 变量绑定关系
 */
public record TemplateVariableSchemaItem(
        String name,
        String label,
        String placeholder,
        Boolean required,
        Boolean pipelineInput,
        String phase,
        List<String> mutationRuleIds,
        TemplateVariableSchemaBinding binding
) {
}
