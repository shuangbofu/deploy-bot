package top.fusb.deploybot.kit;

/**
 * Shell 相关的通用转义与片段模板工具，集中管理脚本中最常复用的安全转义与 heredoc 片段。
 */
public final class ShellKit {

    private ShellKit() {
    }

    /**
     * 转义双引号字符串中会影响 shell 解析的反斜杠和双引号。
     *
     * @param value 原始字符串
     * @return 适合放入双引号中的安全字符串；如果原值为空则返回空串
     */
    public static String escapeDoubleQuoted(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 将字符串包装成单引号 shell 字面量，并处理内部单引号转义。
     *
     * @param value 原始字符串
     * @return 可直接拼入 shell 命令的单引号字面量
     */
    public static String singleQuote(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    /**
     * 生成“值存在时再 export 环境变量”的 shell 片段。
     *
     * @param envName 环境变量名
     * @param envValue 环境变量值
     * @return 可直接拼接到脚本中的 shell 片段
     */
    public static String exportIfPresent(String envName, String envValue) {
        return """
                if [ -n "%s" ]; then
                  export %s="%s"
                fi
                """.formatted(
                escapeDoubleQuoted(envValue),
                envName,
                escapeDoubleQuoted(envValue)
        );
    }

    /**
     * 生成“值存在时再写入 base64 文件”的 shell 片段。
     *
     * @param base64Content base64 编码后的文件内容
     * @param filePath 目标文件路径
     * @return 可直接拼接到脚本中的 shell 片段
     */
    public static String writeBase64FileIfPresent(String base64Content, String filePath) {
        return """
                if [ -n "%s" ]; then
                  mkdir -p "$(dirname "%s")"
                  printf '%%s' '%s' | base64 --decode > "%s"
                fi
                """.formatted(
                escapeDoubleQuoted(base64Content),
                escapeDoubleQuoted(filePath),
                escapeDoubleQuoted(base64Content),
                escapeDoubleQuoted(filePath)
        );
    }

    /**
     * 使用 heredoc 方式生成文字文件写入脚本，适合多行 SSH 密钥或配置文件。
     *
     * @param filePath 目标文件路径
     * @param content 文件内容
     * @param marker heredoc 结束标记
     * @return 可直接拼接到脚本中的 shell 片段
     */
    public static String writeLiteralFile(String filePath, String content, ShellHeredocMarker marker) {
        return """
                cat > "%s" <<'%s'
                %s
                %s
                """.formatted(
                escapeDoubleQuoted(filePath),
                marker.marker(),
                content == null ? "" : content.strip(),
                marker.marker()
        );
    }
}
