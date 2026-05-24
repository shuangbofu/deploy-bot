package top.fusb.deploybot.kit;

/**
 * Shell 相关的通用转义与片段模板工具，集中管理脚本中最常复用的安全转义与 heredoc 片段。
 */
public final class ShellKit {

    private static final String COMMAND_TRACE_PREAMBLE = """
            export PS4='+ $(date "+%Y-%m-%d %H:%M:%S") '
            set -x
            """;

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
     * 拼接多个 shell 命令片段，空片段会被忽略，片段内部连续空白会压缩成单个空格。
     *
     * @param fragments 待拼接的命令片段
     * @return 归一化后的命令文本；如果所有片段都为空则返回空串
     */
    public static String joinCommandFragments(String... fragments) {
        if (fragments == null || fragments.length == 0) {
            return "";
        }
        return java.util.Arrays.stream(fragments)
                .filter(fragment -> fragment != null && !fragment.isBlank())
                .map(fragment -> fragment.trim().replaceAll("\\s+", " "))
                .filter(fragment -> !fragment.isBlank())
                .reduce((left, right) -> left + " " + right)
                .orElse("");
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

    /**
     * 生成“目录必须存在”的 shell 校验片段，常用于阻止运行环境路径失效后静默回退到系统默认命令。
     *
     * @param displayName 日志中展示的目录用途名称
     * @param directoryPath 需要校验的目录路径
     * @return 可直接拼接到脚本中的 shell 校验片段；如果目录路径为空则返回空串
     */
    public static String requireDirectory(String displayName, String directoryPath) {
        if (directoryPath == null || directoryPath.isBlank()) {
            return "";
        }
        return """
                if [ ! -d "%s" ]; then
                  echo "%s 目录不存在：%s" >&2
                  exit 1
                fi
                """.formatted(
                escapeDoubleQuoted(directoryPath),
                escapeDoubleQuoted(displayName),
                escapeDoubleQuoted(directoryPath)
        );
    }

    /**
     * 将脚本中的独立注释行转换为 echo 命令，让步骤说明按真实执行顺序进入部署日志。
     * <p>
     * shebang 行会保持原样；空注释会跳过，避免产生无意义日志。
     *
     * @param script 原始脚本内容
     * @return 已把注释行转换为 echo 的脚本内容；如果原值为空则原样返回
     */
    public static String expandLogComments(String script) {
        if (script == null || script.isBlank()) {
            return script;
        }
        StringBuilder expanded = new StringBuilder();
        String[] lines = script.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            String stripped = line.stripLeading();
            if (stripped.startsWith("#") && !stripped.startsWith("#!")) {
                String indent = line.substring(0, line.length() - stripped.length());
                String message = stripped.substring(1).trim();
                if (!message.isBlank()) {
                    expanded.append(indent).append("echo \"").append(escapeDoubleQuoted(message)).append("\"");
                }
            } else {
                expanded.append(line);
            }
            if (index < lines.length - 1) {
                expanded.append('\n');
            }
        }
        return expanded.toString();
    }

    /**
     * 为脚本启用命令追踪，让部署日志同时包含实际执行的命令和命令输出。
     * <p>
     * 该方法会优先把追踪片段插入到 {@code set -e} 后面，避免脚本头部和平台前置环境变量过早进入追踪日志；
     * 如果脚本已经包含 {@code set -x}，则认为模板作者已自行控制追踪行为并保持原样。
     *
     * @param script 原始脚本内容
     * @return 已插入命令追踪片段的脚本内容；如果原值为空则原样返回
     */
    public static String enableCommandTrace(String script) {
        if (script == null || script.isBlank() || script.lines().anyMatch(line -> line.strip().equals("set -x"))) {
            return script;
        }
        String[] lines = script.split("\\R", -1);
        StringBuilder traced = new StringBuilder();
        boolean inserted = false;
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            traced.append(line);
            if (!inserted && line.strip().equals("set -e")) {
                traced.append('\n').append(COMMAND_TRACE_PREAMBLE.stripTrailing());
                inserted = true;
            }
            if (index < lines.length - 1) {
                traced.append('\n');
            }
        }
        if (inserted) {
            return traced.toString();
        }
        return COMMAND_TRACE_PREAMBLE + script;
    }

}
