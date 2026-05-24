package top.fusb.deploybot.plugin.springboot.startup;

import top.fusb.deploybot.kit.TextKit;
import top.fusb.deploybot.plugin.api.startup.StartupJudgeContext;
import top.fusb.deploybot.plugin.api.startup.StartupJudgeMatchMode;
import top.fusb.deploybot.plugin.api.startup.StartupJudgeResult;
import top.fusb.deploybot.plugin.common.startup.AbstractStartupJudgePlugin;

/**
 * 面向 Spring Boot / Java 服务的日志关键字启动判定插件。
 */
public class SpringBootKeywordStartupJudgePlugin extends AbstractStartupJudgePlugin {

    private static final String SPRING_BOOT_STARTED_PATTERN = "Started\\s+\\S+\\s+in\\s+\\d+(?:\\.\\d+)?\\s+seconds?(?:\\s|$)";

    @Override
    public String pluginId() {
        return "springboot-keyword-startup-judge";
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public boolean supports(StartupJudgeContext context) {
        return "JAVA".equalsIgnoreCase(context.runtimeEnvironmentType())
                || TextKit.containsIgnoreCase(context.templateType(), "springboot")
                || TextKit.containsIgnoreCase(resolveStartupKeyword(context), "started");
    }

    @Override
    public StartupJudgeResult judge(StartupJudgeContext context) {
        String keyword = resolveStartupKeyword(context);
        if (TextKit.isBlank(keyword)) {
            return new StartupJudgeResult(
                    true,
                    SPRING_BOOT_STARTED_PATTERN,
                    StartupJudgeMatchMode.REGEX,
                    "Spring Boot 默认启动日志匹配"
            );
        }
        return new StartupJudgeResult(
                true,
                keyword,
                StartupJudgeMatchMode.CONTAINS,
                "日志关键字命中（Spring Boot 插件）"
        );
    }
}
