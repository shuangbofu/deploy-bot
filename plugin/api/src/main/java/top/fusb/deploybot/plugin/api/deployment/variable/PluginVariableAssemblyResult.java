package top.fusb.deploybot.plugin.api.deployment.variable;

import java.util.List;
import java.util.Map;

/**
 * 描述类型插件完成变量拼装后的结果。
 *
 * @param variables 拼装后的变量
 * @param notes 拼装说明
 */
public record PluginVariableAssemblyResult(
        Map<String, String> variables,
        List<String> notes
) {
}
