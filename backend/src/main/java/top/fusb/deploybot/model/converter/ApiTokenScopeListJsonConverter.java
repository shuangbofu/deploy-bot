package top.fusb.deploybot.model.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import top.fusb.deploybot.model.ApiTokenScope;

import java.util.ArrayList;
import java.util.List;

/**
 * API Token scope 列表 JSON 字段转换器。
 */
public class ApiTokenScopeListJsonConverter extends JsonAttributeConverter<List<ApiTokenScope>> {
    private static final TypeReference<List<ApiTokenScope>> TYPE_REFERENCE = new TypeReference<>() {
    };

    /**
     * 返回 scope 列表的 Jackson 类型。
     *
     * @return API Token scope 列表类型引用
     */
    @Override
    protected TypeReference<List<ApiTokenScope>> typeReference() {
        return TYPE_REFERENCE;
    }

    /**
     * 返回空 scope 列表。
     *
     * @return 空列表
     */
    @Override
    protected List<ApiTokenScope> emptyValue() {
        return new ArrayList<>();
    }
}
