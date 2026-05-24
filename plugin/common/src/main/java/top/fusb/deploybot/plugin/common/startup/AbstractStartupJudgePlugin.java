package top.fusb.deploybot.plugin.common.startup;

import top.fusb.deploybot.kit.TextKit;
import top.fusb.deploybot.plugin.api.startup.StartupJudgeContext;
import top.fusb.deploybot.plugin.api.startup.StartupJudgePlugin;

/**
 * 提供启动关键字解析与策略描述等共用能力。
 */
public abstract class AbstractStartupJudgePlugin implements StartupJudgePlugin {

    protected String resolveStartupKeyword(StartupJudgeContext context) {
        return context.startupKeyword();
    }

    protected boolean hasStartupKeyword(StartupJudgeContext context) {
        return TextKit.isNotBlank(resolveStartupKeyword(context));
    }
}
