package top.fusb.deploybot.plugin.api.startup;

/**
 * 负责描述启动成功判定策略的能力接口。
 * <p>
 * 该接口通常由单个 {@code DeploymentPlugin} 在内部携带并暴露，
 * 由类型插件统一打包进 jar 后随类型插件一起被加载，而不是作为独立顶层插件直接让前端感知。
 * </p>
 * <p>
 * 启动观察超时时间由宿主部署编排层控制，不进入插件上下文。插件只描述判定方式，例如是否需要日志关键字。
 * </p>
 */
public interface StartupJudgePlugin {

    /**
     * 返回插件唯一标识。
     *
     * @return 插件标识
     */
    String pluginId();

    /**
     * 返回当前插件的匹配优先级，值越小优先级越高。
     *
     * @return 插件顺序
     */
    int order();

    /**
     * 判断当前插件是否适用于本次部署。
     *
     * @param context 启动判定上下文
     * @return 适用则返回 {@code true}
     */
    boolean supports(StartupJudgeContext context);

    /**
     * 生成当前部署应使用的启动判定描述结果。
     *
     * @param context 启动判定上下文
     * @return 启动判定结果
     */
    StartupJudgeResult judge(StartupJudgeContext context);
}
