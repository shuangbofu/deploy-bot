package top.fusb.deploybot.plugin.springboot.process;

import top.fusb.deploybot.kit.ShellKit;
import top.fusb.deploybot.kit.TextKit;
import top.fusb.deploybot.plugin.api.process.ProcessLocatorContext;
import top.fusb.deploybot.plugin.common.process.AbstractProcessLocatorPlugin;

/**
 * 面向 Spring Boot / Java Jar 场景的内置 PID 发现插件。
 */
public class SpringBootProcessLocatorPlugin extends AbstractProcessLocatorPlugin {

    @Override
    public String pluginId() {
        return "springboot-process-locator";
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public boolean supports(ProcessLocatorContext context) {
        if ("JAVA".equalsIgnoreCase(context.runtimeEnvironmentType())) {
            return true;
        }
        String keyword = resolvePidDiscoveryKeyword(context);
        return TextKit.containsIgnoreCase(keyword, "java ")
                || TextKit.containsIgnoreCase(keyword, ".jar")
                || TextKit.containsIgnoreCase(context.templateType(), "springboot");
    }

    @Override
    protected String buildLookupScript(String keyword) {
        String quotedKeyword = ShellKit.singleQuote(keyword);
        StringBuilder script = new StringBuilder();
        script.append("""
                # Spring Boot / Java Jar 优先按完整 java -jar 命令定位，避免误接管其他同名进程。
                PID=$(ps -efww | awk -v kw=%s '($8 ~ /(^|\\/)java$/) && index($0, kw) {print $2; exit}' || true)
                """.formatted(quotedKeyword));
        String jarName = resolveJarName(keyword);
        if (TextKit.isNotBlank(jarName)) {
            script.append("""
                    # 完整命令未命中时，再按 jar 名进行一次兜底。
                    if [ -z "$PID" ]; then
                      PID=$(ps -efww | awk -v jar=%s '($8 ~ /(^|\\/)java$/) && index($0, jar) {print $2; exit}' || true)
                    fi
                    """.formatted(ShellKit.singleQuote(jarName)));
        }
        if (keyword.endsWith(".jar")) {
            String escapedKeyword = ShellKit.escapeDoubleQuoted(keyword);
            script.append("""
                    # jar 名仍未命中时，补一次 pgrep。
                    if [ -z "$PID" ]; then
                      PID=$(pgrep -f "%s" | head -n 1 || true)
                    fi
                    # 最后再用 jps 兜底，方便处理部分 Java 进程列表差异。
                    if [ -z "$PID" ] && command -v jps >/dev/null 2>&1; then
                      PID=$(jps -lv 2>/dev/null | grep "%s" | awk '{print $1}' | head -n 1 || true)
                    fi
                    """.formatted(escapedKeyword, escapedKeyword));
        }
        script.append("if [ -n \"$PID\" ]; then printf '__DEPLOYBOT_PID__%s\\n' \"$PID\"; fi\n");
        return script.toString();
    }

    @Override
    protected String describeSource(boolean keywordAvailable) {
        return keywordAvailable ? "部署唯一标识优先，其次命令推导（Spring Boot 插件）" : "Spring Boot 插件未获得可用进程特征";
    }

    @Override
    protected String normalizeDiagnostics(String output) {
        if (TextKit.isBlank(output)) {
            return "[系统] Spring Boot PID 推导无输出。";
        }
        return output.trim();
    }
}
