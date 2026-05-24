package top.fusb.deploybot.model.converter;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Map<String, Object> JSON 字段转换器。
 */
public class ObjectMapJsonConverter extends JsonAttributeConverter<Map<String, Object>> {
    private static final TypeReference<Map<String, Object>> TYPE_REFERENCE = new TypeReference<>() {
    };

    /**
     * 返回对象 Map 的 Jackson 类型。
     *
     * @return 对象 Map 类型引用
     */
    @Override
    protected TypeReference<Map<String, Object>> typeReference() {
        return TYPE_REFERENCE;
    }

    /**
     * 返回空对象 Map。
     *
     * @return 空 Map
     */
    @Override
    protected Map<String, Object> emptyValue() {
        return new LinkedHashMap<>();
    }
}
