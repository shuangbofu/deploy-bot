package top.fusb.deploybot.model.converter;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.List;

/**
 * List<String> JSON 字段转换器。
 */
public class StringListJsonConverter extends JsonAttributeConverter<List<String>> {
    private static final TypeReference<List<String>> TYPE_REFERENCE = new TypeReference<>() {
    };

    /**
     * 返回字符串列表的 Jackson 类型。
     *
     * @return 字符串列表类型引用
     */
    @Override
    protected TypeReference<List<String>> typeReference() {
        return TYPE_REFERENCE;
    }

    /**
     * 返回空字符串列表。
     *
     * @return 空列表
     */
    @Override
    protected List<String> emptyValue() {
        return new ArrayList<>();
    }
}
