package top.fusb.deploybot.plugin.common.deployment.config;

import java.util.List;

import top.fusb.deploybot.plugin.api.deployment.definition.PluginDescriptor;
import top.fusb.deploybot.plugin.api.deployment.definition.PluginRuntimeRequirement;
import top.fusb.deploybot.plugin.api.deployment.form.PluginFormSchema;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableDefinition;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableMutationRule;

/**
 * 描述单个部署类型插件的静态定义文件。
 *
 * @param descriptor 插件基础描述
 * @param bannerResource 插件启动横幅资源路径
 * @param runtimeRequirement 运行时要求
 * @param pipelineFormSchema 流水线级配置表单
 * @param variableDefinitions 变量定义
 * @param variableMutationRules 自动改写规则
 * @param builtinTemplates 内置模板定义
 */
public record DeploymentPluginConfig(
        PluginDescriptor descriptor,
        String bannerResource,
        PluginRuntimeRequirement runtimeRequirement,
        PluginFormSchema pipelineFormSchema,
        List<PluginVariableDefinition> variableDefinitions,
        List<PluginVariableMutationRule> variableMutationRules,
        List<PluginBuiltinTemplateConfig> builtinTemplates
) {
}
