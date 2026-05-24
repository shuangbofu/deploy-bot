package top.fusb.deploybot.kit;

import com.fasterxml.jackson.core.type.TypeReference;
import top.fusb.deploybot.dto.TemplateVariableSchemaItem;

import java.util.List;

/**
 * 模板变量定义 JSON 工具。
 */
public final class TemplateVariableSchemaKit {

    private TemplateVariableSchemaKit() {
    }

    /**
     * 读取模板变量定义列表。
     *
     * @param variablesSchema 模板变量定义 JSON
     * @return 变量定义列表；空文本时返回空列表
     */
    public static List<TemplateVariableSchemaItem> read(String variablesSchema) {
        if (TextKit.isBlank(variablesSchema)) {
            return List.of();
        }
        return JsonKit.read(variablesSchema, new TypeReference<List<TemplateVariableSchemaItem>>() {
        });
    }

    /**
     * 序列化模板变量定义列表。
     *
     * @param items 变量定义列表
     * @return JSON 文本
     */
    public static String write(List<TemplateVariableSchemaItem> items) {
        return JsonKit.write(items == null ? List.of() : items);
    }
}
