package top.fusb.deploybot.plugin.api.deployment.variable;

import java.util.List;

/**
 * 描述插件会如何自动改写平台已生成的变量。
 *
 * @param ruleId 规则唯一标识
 * @param phase 发生改写的阶段
 * @param mutationType 改写方式
 * @param sourceReferences 插件读取的结构化输入引用
 * @param targetReferences 直接受影响的结构化目标引用
 * @param generatedReferences 插件会补充生成的结构化引用
 * @param triggerGroups 触发条件组
 */
public record PluginVariableMutationRule(
        String ruleId,
        PluginVariableAssemblyPhase phase,
        PluginVariableMutationType mutationType,
        List<PluginValueReference> sourceReferences,
        List<PluginValueReference> targetReferences,
        List<PluginValueReference> generatedReferences,
        List<PluginVariableTriggerGroup> triggerGroups
) {
}
