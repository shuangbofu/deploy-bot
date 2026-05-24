package top.fusb.deploybot.plugin.api.deployment.context;

import java.util.List;

/**
 * 描述模板中一个已暴露变量的结构化定义。
 *
 * @param name 变量名
 * @param phase 所属阶段
 * @param mutationRuleIds 当前变量绑定的自动改写规则标识
 * @param binding 当前变量绑定到的平台标准语义
 */
public record PluginTemplateVariable(
        String name,
        String phase,
        List<String> mutationRuleIds,
        PluginTemplateVariableBinding binding
) {
}
