package top.fusb.deploybot.model.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import top.fusb.deploybot.kit.TextKit;

/**
 * JPA JSON 字段转换基类。
 * <p>
 * 数据库仍保存 JSON 文本，Java 实体层使用明确类型，避免业务代码到处手动读写 {@code xxxJson} 字符串。
 * </p>
 *
 * @param <T> 实体字段类型
 */
public abstract class JsonAttributeConverter<T> implements AttributeConverter<T, String> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    /**
     * 返回字段反序列化目标类型。
     *
     * @return Jackson 类型引用
     */
    protected abstract TypeReference<T> typeReference();

    /**
     * 返回空数据库值对应的 Java 默认值。
     *
     * @return 默认值
     */
    protected abstract T emptyValue();

    /**
     * 将实体字段转换为数据库 JSON 文本。
     *
     * @param attribute 实体字段值
     * @return JSON 文本
     */
    @Override
    public String convertToDatabaseColumn(T attribute) {
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute == null ? emptyValue() : attribute);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("JSON 字段序列化失败。", ex);
        }
    }

    /**
     * 将数据库 JSON 文本转换为实体字段。
     *
     * @param dbData 数据库 JSON 文本
     * @return 实体字段值
     */
    @Override
    public T convertToEntityAttribute(String dbData) {
        if (TextKit.isBlank(dbData)) {
            return emptyValue();
        }
        try {
            return OBJECT_MAPPER.readValue(dbData, typeReference());
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("JSON 字段反序列化失败。", ex);
        }
    }
}
