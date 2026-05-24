package top.fusb.deploybot.kit;

import java.util.List;

/**
 * 面向宽类型对象的轻量转换工具，主要收口从 JSON 反序列化结果里提取字符串值的场景。
 */
public final class ObjectKit {

    private ObjectKit() {
    }

    /**
     * 将任意对象转换为去除首尾空白后的字符串；如果没有有效内容则返回 {@code null}。
     *
     * @param value 原始对象
     * @return 转换后的字符串；若无有效内容则返回 {@code null}
     */
    public static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        return TextKit.trimToNull(String.valueOf(value));
    }

    /**
     * 将对象按字符串列表读取，并自动过滤空白项。
     *
     * @param value 原始对象
     * @return 处理后的字符串列表；若对象不是列表则返回空列表
     */
    public static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(ObjectKit::stringValue)
                .filter(item -> item != null)
                .toList();
    }
}
