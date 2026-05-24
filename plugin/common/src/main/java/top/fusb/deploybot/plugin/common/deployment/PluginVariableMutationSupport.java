package top.fusb.deploybot.plugin.common.deployment;

import java.util.List;
import java.util.Map;

import top.fusb.deploybot.kit.TextKit;
import top.fusb.deploybot.plugin.api.deployment.context.PluginTemplateVariable;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableAssemblyContext;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableMutationRule;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginValueReference;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginValueReferenceScope;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginCommandSlot;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableTriggerGroup;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableTriggerMode;

/**
 * 插件变量自动改写规则的匹配支持工具。
 * <p>
 * 该类用于让结构化规则不只停留在展示层，而是直接参与插件的变量改写逻辑判断。
 * </p>
 */
public final class PluginVariableMutationSupport {

    private PluginVariableMutationSupport() {
    }

    /**
     * 判断当前变量拼装上下文是否命中了指定自动改写规则。
     *
     * @param rule 自动改写规则
     * @param context 变量拼装上下文
     * @param variables 当前变量快照
     * @return 命中返回 {@code true}
     */
    public static boolean matches(
            PluginVariableMutationRule rule,
            PluginVariableAssemblyContext context,
            Map<String, String> variables
    ) {
        if (rule == null || context == null || context.phase() == null || rule.phase() != context.phase()) {
            return false;
        }
        if (!isRuleBound(rule, context)) {
            return false;
        }
        List<PluginVariableTriggerGroup> triggerGroups = rule.triggerGroups();
        if (triggerGroups == null || triggerGroups.isEmpty()) {
            return true;
        }
        for (PluginVariableTriggerGroup group : triggerGroups) {
            if (matchesGroup(rule, group, context, variables)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesGroup(
            PluginVariableMutationRule rule,
            PluginVariableTriggerGroup group,
            PluginVariableAssemblyContext context,
            Map<String, String> variables
    ) {
        if (group == null || group.references() == null || group.references().isEmpty()) {
            return false;
        }
        if (group.mode() == PluginVariableTriggerMode.ALL_PRESENT) {
            return group.references().stream().allMatch(reference -> TextKit.isNotBlank(resolveValue(rule, reference, context, variables)));
        }
        return group.references().stream().anyMatch(reference -> TextKit.isNotBlank(resolveValue(rule, reference, context, variables)));
    }

    private static boolean isRuleBound(PluginVariableMutationRule rule, PluginVariableAssemblyContext context) {
        if (TextKit.isBlank(rule.ruleId()) || context.variableContext() == null) {
            return true;
        }
        List<PluginTemplateVariable> templateVariables = context.variableContext().templateVariables();
        if (templateVariables == null || templateVariables.isEmpty()) {
            return true;
        }
        return templateVariables.stream().anyMatch(variable ->
                variable != null
                        && variable.mutationRuleIds() != null
                        && variable.mutationRuleIds().stream().anyMatch(rule.ruleId()::equals)
        );
    }

    private static String resolveValue(
            PluginVariableMutationRule rule,
            PluginValueReference reference,
            PluginVariableAssemblyContext context,
            Map<String, String> variables
    ) {
        if (reference == null || reference.key() == null) {
            return null;
        }
        if (reference.scope() == PluginValueReferenceScope.CONTEXT) {
            return switch (reference.key()) {
                case "mavenSettingsFilePath" -> context.mavenSettingsFilePath();
                default -> context.configValue(reference.key());
            };
        }
        if (reference.scope() == PluginValueReferenceScope.COMMAND_SLOT) {
            return resolveCommandSlot(
                    rule,
                    reference.key(),
                    variables,
                    context.variableContext() == null ? null : context.variableContext().templateVariables()
            );
        }
        return variables == null ? null : variables.get(reference.key());
    }

    private static String resolveCommandSlot(
            PluginVariableMutationRule rule,
            String key,
            Map<String, String> variables,
            List<PluginTemplateVariable> templateVariables
    ) {
        if (variables == null || TextKit.isBlank(key)) {
            return null;
        }
        if (templateVariables != null) {
            for (PluginTemplateVariable variable : templateVariables) {
                if (variable == null || variable.mutationRuleIds() == null || TextKit.isBlank(rule == null ? null : rule.ruleId())) {
                    continue;
                }
                if (variable.mutationRuleIds().stream().anyMatch(rule.ruleId()::equals) && TextKit.isNotBlank(variable.name())) {
                    return variables.get(variable.name());
                }
            }
        }
        try {
            PluginCommandSlot slot = PluginCommandSlot.valueOf(key);
            return switch (slot) {
                case BUILD_COMMAND -> variables.get("buildCommand");
                case BACKEND_BUILD_COMMAND -> variables.get("backendBuildCommand");
                case START_COMMAND -> variables.get("startCommand");
            };
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
