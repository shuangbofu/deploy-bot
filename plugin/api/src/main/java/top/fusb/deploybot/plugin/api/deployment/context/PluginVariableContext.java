package top.fusb.deploybot.plugin.api.deployment.context;

import java.util.List;
import java.util.Map;

/**
 * 描述平台在进入插件前已经解析好的变量快照。
 *
 * @param variablesJson 原始变量 JSON
 * @param variables 当前变量键值快照
 * @param templateVariables 当前模板对外暴露的变量定义
 */
public record PluginVariableContext(
        String variablesJson,
        Map<String, String> variables,
        List<PluginTemplateVariable> templateVariables
) {

    /**
     * 读取指定变量值。
     *
     * @param key 变量键
     * @return 变量值
     */
    public String value(String key) {
        return variables == null || key == null ? null : variables.get(key);
    }
}
