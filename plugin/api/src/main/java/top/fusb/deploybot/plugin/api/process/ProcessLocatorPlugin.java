package top.fusb.deploybot.plugin.api.process;

/**
 * 负责在部署启动后定位可接管进程的能力接口。
 * <p>
 * 该接口通常由单个 {@code DeploymentPlugin} 在内部携带并暴露，
 * 由类型插件统一打包进 jar 后随类型插件一起被加载，而不是作为独立顶层插件直接让前端感知。
 * </p>
 */
public interface ProcessLocatorPlugin {

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
     * @param context PID 发现上下文
     * @return 适用则返回 {@code true}
     */
    boolean supports(ProcessLocatorContext context);

    /**
     * 执行一次 PID 发现，返回定位结果与诊断信息。
     *
     * @param context PID 发现上下文
     * @return PID 发现结果
     */
    ProcessLocatorResult locate(ProcessLocatorContext context);
}
