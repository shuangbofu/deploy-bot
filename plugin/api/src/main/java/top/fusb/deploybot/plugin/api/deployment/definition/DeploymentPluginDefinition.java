package top.fusb.deploybot.plugin.api.deployment.definition;

import java.util.List;

import top.fusb.deploybot.plugin.api.deployment.template.PluginBuiltinTemplate;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableDefinition;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableMutationRule;

/**
 * 描述单个类型插件的完整定义信息。
 *
 * @param descriptor 基础描述
 * @param runtimeRequirement 运行时要求
 * @param pipelineFormSchema 流水线配置表单结构
 * @param variableDefinitions 变量定义
 * @param variableMutationRules 自动改写规则定义
 * @param builtinTemplates 插件自带的默认模板定义
 */
public record DeploymentPluginDefinition(
        PluginDescriptor descriptor,
        PluginRuntimeRequirement runtimeRequirement,
        top.fusb.deploybot.plugin.api.deployment.form.PluginFormSchema pipelineFormSchema,
        List<PluginVariableDefinition> variableDefinitions,
        List<PluginVariableMutationRule> variableMutationRules,
        List<PluginBuiltinTemplate> builtinTemplates
) {
}
