package top.fusb.deploybot.plugin.api.deployment.variable;

import java.util.List;

/**
 * 描述插件自动改写规则的一组触发条件。
 *
 * @param mode 触发模式
 * @param references 参与判断的结构化值引用
 */
public record PluginVariableTriggerGroup(
        PluginVariableTriggerMode mode,
        List<PluginValueReference> references
) {
}
