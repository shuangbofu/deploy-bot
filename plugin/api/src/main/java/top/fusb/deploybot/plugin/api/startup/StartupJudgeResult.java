package top.fusb.deploybot.plugin.api.startup;

/**
 * 启动判定结果描述。
 *
 * @param keywordRequired 是否必须命中启动判定表达式
 * @param keyword 需要匹配的启动关键字或正则表达式
 * @param matchMode 匹配模式
 * @param strategyDescription 启动判定策略说明；不包含观察超时时间，超时由宿主部署编排层控制
 */
public record StartupJudgeResult(
        boolean keywordRequired,
        String keyword,
        StartupJudgeMatchMode matchMode,
        String strategyDescription
) {

    /**
     * 使用普通子串匹配创建启动判定结果。
     *
     * @param keywordRequired 是否必须命中启动关键字
     * @param keyword 启动关键字
     * @param strategyDescription 启动判定策略说明
     */
    public StartupJudgeResult(boolean keywordRequired, String keyword, String strategyDescription) {
        this(keywordRequired, keyword, StartupJudgeMatchMode.CONTAINS, strategyDescription);
    }
}
