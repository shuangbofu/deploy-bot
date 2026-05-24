package top.fusb.deploybot.model.converter;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Map<String, String> JSON 字段转换器。
 */
public class StringMapJsonConverter extends JsonAttributeConverter<Map<String, String>> {
    private static final TypeReference<Map<String, String>> TYPE_REFERENCE = new TypeReference<>() {
    };

    /**
     * 返回字符串 Map 的 Jackson 类型。
     *
     * @return 字符串 Map 类型引用
     */
    @Override
    protected TypeReference<Map<String, String>> typeReference() {
        return TYPE_REFERENCE;
    }

    /**
     * 返回空字符串 Map。
     *
     * @return 空 Map
     */
    @Override
    protected Map<String, String> emptyValue() {
        return new LinkedHashMap<>();
    }
}
