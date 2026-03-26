package top.fusb.deploybot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class JsonMapper {

    private final ObjectMapper objectMapper;

    public JsonMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, String> toStringMap(String content) {
        if (content == null || content.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(content, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("JSON content is invalid", ex);
        }
    }

    public List<Map<String, Object>> toObjectList(String content) {
        if (content == null || content.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(content, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("JSON content is invalid", ex);
        }
    }

    public Map<String, Object> toObjectMap(String content) {
        if (content == null || content.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(content, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("JSON content is invalid", ex);
        }
    }

    public String write(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to serialize JSON", ex);
        }
    }

    /**
     * 通用 JSON 反序列化入口，供需要读取复杂配置结构的场景复用。
     */
    public <T> T read(String content, TypeReference<T> typeReference) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("JSON content is blank");
        }
        try {
            return objectMapper.readValue(content, typeReference);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("JSON content is invalid", ex);
        }
    }
}
