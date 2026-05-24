package top.fusb.deploybot.kit;

/**
 * 文本归一化与常见字符串判断工具，避免在各个 service 内重复书写 trim / isBlank / 换行归一化。
 */
public final class TextKit {

    private TextKit() {
    }

    /**
     * 判断字符串是否为 {@code null}、空串或仅包含空白字符。
     *
     * @param value 待判断的字符串
     * @return 如果字符串为空白则返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 判断字符串是否包含有效内容。
     *
     * @param value 待判断的字符串
     * @return 如果字符串包含非空白内容则返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isNotBlank(String value) {
        return !isBlank(value);
    }

    /**
     * 去除字符串首尾空白；如果结果为空白则统一返回 {@code null}。
     *
     * @param value 原始字符串
     * @return 去除首尾空白后的字符串；如果无有效内容则返回 {@code null}
     */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    /**
     * 归一化多行文本，将 Windows 换行统一成 Unix 换行，并去掉首尾空白。
     *
     * @param value 原始多行文本
     * @return 归一化后的文本；如果无有效内容则返回 {@code null}
     */
    public static String normalizeMultiline(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace("\r\n", "\n").trim();
        return normalized.isBlank() ? null : normalized;
    }

    /**
     * 归一化脚本文本，并保证最终结果以单个换行结尾。
     *
     * @param value 原始脚本文本
     * @return 归一化后且以换行结尾的脚本文本；如果无有效内容则返回 {@code null}
     */
    public static String normalizeScript(String value) {
        String normalized = normalizeMultiline(value);
        return normalized == null ? null : normalized + "\n";
    }

    /**
     * 将空白值统一显示为短横线，便于在界面或通知中兜底展示。
     *
     * @param value 原始值
     * @return 原值；如果为空白则返回 {@code "-"}
     */
    public static String valueOrDash(String value) {
        return isBlank(value) ? "-" : value;
    }

    /**
     * 将 Java 字段名转换为 shell 环境变量常用的大写下划线格式。
     * 例如 {@code runtimeConfigYamlBase64} 会转换为 {@code RUNTIME_CONFIG_YAML_BASE64}。
     *
     * @param value 原始字段名，通常是 camelCase 或已经是下划线格式
     * @return 大写下划线格式；如果原始值为空白则返回 {@code null}
     */
    public static String toUpperSnakeCase(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder(trimmed.length() + 8);
        char previous = 0;
        for (int index = 0; index < trimmed.length(); index++) {
            char current = trimmed.charAt(index);
            if (current == '-' || current == ' ' || current == '.') {
                appendSingleUnderscore(builder);
                previous = '_';
                continue;
            }
            if (current == '_') {
                appendSingleUnderscore(builder);
                previous = '_';
                continue;
            }
            if (Character.isUpperCase(current) && (Character.isLowerCase(previous) || Character.isDigit(previous))) {
                appendSingleUnderscore(builder);
            }
            builder.append(Character.toUpperCase(current));
            previous = current;
        }
        return builder.toString();
    }

    private static void appendSingleUnderscore(StringBuilder builder) {
        if (!builder.isEmpty() && builder.charAt(builder.length() - 1) != '_') {
            builder.append('_');
        }
    }

    /**
     * 忽略大小写判断文本中是否包含关键字。
     *
     * @param value 待检索的原始文本
     * @param keyword 待匹配的关键字
     * @return 如果文本包含关键字则返回 {@code true}，否则返回 {@code false}
     */
    public static boolean containsIgnoreCase(String value, String keyword) {
        if (isBlank(value) || isBlank(keyword)) {
            return false;
        }
        return value.toLowerCase().contains(keyword.trim().toLowerCase());
    }

    /**
     * 提取文本前若干行摘要，常用于日志预览或错误输出压缩。
     *
     * @param value 原始多行文本
     * @param maxLines 最多保留的行数，小于 1 时按 1 处理
     * @return 文本前若干行；如果原文本为空白则返回空串
     */
    public static String summarizeHead(String value, int maxLines) {
        if (isBlank(value)) {
            return "";
        }
        return value.lines()
                .limit(Math.max(1, maxLines))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("")
                .trim();
    }

    /**
     * 提取文本末尾若干行摘要，常用于保留最后的报错上下文。
     *
     * @param value 原始多行文本
     * @param maxLines 最多保留的行数，小于 1 时按 1 处理
     * @return 文本末尾若干行；如果原文本为空白则返回空串
     */
    public static String summarizeTail(String value, int maxLines) {
        if (isBlank(value)) {
            return "";
        }
        String[] lines = value.lines().toArray(String[]::new);
        int start = Math.max(0, lines.length - Math.max(1, maxLines));
        return String.join("\n", java.util.Arrays.copyOfRange(lines, start, lines.length)).trim();
    }
}
