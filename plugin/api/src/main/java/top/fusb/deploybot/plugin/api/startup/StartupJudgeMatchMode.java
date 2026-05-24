package top.fusb.deploybot.plugin.api.startup;

/**
 * 启动日志判定时使用的文本匹配模式。
 */
public enum StartupJudgeMatchMode {

    /**
     * 普通子串匹配，适合用户手动填写的明确启动关键字。
     */
    CONTAINS,

    /**
     * 正则表达式匹配，适合插件提供的框架默认启动日志特征。
     */
    REGEX
}
