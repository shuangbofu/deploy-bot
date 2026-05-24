package top.fusb.deploybot.plugin.api.deployment.variable;

import java.util.List;

import top.fusb.deploybot.plugin.api.deployment.context.PluginTemplateVariableBinding;

/**
 * 描述插件推荐或要求的变量定义。
 *
 * @param key 变量键
 * @param label 变量标题
 * @param description 变量说明
 * @param required 是否必填
 * @param secret 是否敏感变量
 * @param pipelineInput 是否允许在流水线层面填写
 * @param defaultValue 默认值
 * @param mutationRuleIds 当前变量默认关联的自动改写规则标识
 * @param binding 当前变量默认绑定的平台标准语义
 */
public record PluginVariableDefinition(
        String key,
        String label,
        String description,
        boolean required,
        boolean secret,
        boolean pipelineInput,
        String defaultValue,
        List<String> mutationRuleIds,
        PluginTemplateVariableBinding binding
) {
}
