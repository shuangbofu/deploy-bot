package top.fusb.deploybot.kit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * JSON 读写工具，统一封装常用的反序列化与序列化逻辑。
 */
public final class JsonKit {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private JsonKit() {
    }

    /**
     * 将 JSON 文本解析为字符串键值对。
     *
     * @param content JSON 文本
     * @return 解析后的字符串键值对；空文本时返回空 Map
     */
    public static Map<String, String> toStringMap(String content) {
        if (content == null || content.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(content, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            throw new JsonException(JsonErrorType.INVALID, ex);
        }
    }

    /**
     * 将 JSON 文本解析为对象列表。
     *
     * @param content JSON 文本
     * @return 解析后的对象列表；空文本时返回空列表
     */
    public static List<Map<String, Object>> toObjectList(String content) {
        if (content == null || content.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(content, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            throw new JsonException(JsonErrorType.INVALID, ex);
        }
    }

    /**
     * 将 JSON 文本解析为对象键值对。
     *
     * @param content JSON 文本
     * @return 解析后的对象键值对；空文本时返回空 Map
     */
    public static Map<String, Object> toObjectMap(String content) {
        if (content == null || content.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(content, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            throw new JsonException(JsonErrorType.INVALID, ex);
        }
    }

    /**
     * 将任意对象格式化为 JSON 文本。
     *
     * @param value 待序列化对象
     * @return 序列化后的 JSON 文本
     */
    public static String write(Object value) {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new JsonException(JsonErrorType.WRITE_FAILED, ex);
        }
    }

    /**
     * 将任意对象序列化为紧凑 JSON 文本。
     *
     * @param value 待序列化对象
     * @return 紧凑 JSON 文本
     */
    public static String writeCompact(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new JsonException(JsonErrorType.WRITE_FAILED, ex);
        }
    }

    /**
     * 按指定结构读取 JSON 文本。
     *
     * @param content JSON 文本
     * @param typeReference 目标结构类型
     * @param <T> 目标结构泛型
     * @return 解析结果
     */
    public static <T> T read(String content, TypeReference<T> typeReference) {
        if (content == null || content.isBlank()) {
            throw new JsonException(JsonErrorType.BLANK);
        }
        try {
            return OBJECT_MAPPER.readValue(content, typeReference);
        } catch (JsonProcessingException ex) {
            throw new JsonException(JsonErrorType.INVALID, ex);
        }
    }

    /**
     * 读取 JSON 树结构。
     *
     * @param content JSON 文本
     * @return JSON 树
     */
    public static JsonNode readTree(String content) {
        if (content == null || content.isBlank()) {
            throw new JsonException(JsonErrorType.BLANK);
        }
        try {
            return OBJECT_MAPPER.readTree(content);
        } catch (JsonProcessingException ex) {
            throw new JsonException(JsonErrorType.INVALID, ex);
        }
    }

    /**
     * JSON 工具错误类型，供上层应用映射为自己的业务错误码。
     */
    public enum JsonErrorType {
        INVALID("JSON 内容不合法。"),
        BLANK("JSON 内容不能为空。"),
        WRITE_FAILED("JSON 序列化失败。");

        private final String message;

        JsonErrorType(String message) {
            this.message = message;
        }

        /**
         * 获取默认错误信息。
         *
         * @return 默认错误信息
         */
        public String getMessage() {
            return message;
        }
    }

    /**
     * JSON 工具异常，不依赖具体应用层异常体系。
     */
    public static class JsonException extends RuntimeException {
        private final JsonErrorType errorType;

        /**
         * 创建 JSON 工具异常。
         *
         * @param errorType JSON 错误类型
         */
        public JsonException(JsonErrorType errorType) {
            super(errorType.getMessage());
            this.errorType = errorType;
        }

        /**
         * 创建带原始异常的 JSON 工具异常。
         *
         * @param errorType JSON 错误类型
         * @param cause 原始异常
         */
        public JsonException(JsonErrorType errorType, Throwable cause) {
            super(errorType.getMessage(), cause);
            this.errorType = errorType;
        }

        /**
         * 获取 JSON 错误类型。
         *
         * @return JSON 错误类型
         */
        public JsonErrorType getErrorType() {
            return errorType;
        }
    }
}
