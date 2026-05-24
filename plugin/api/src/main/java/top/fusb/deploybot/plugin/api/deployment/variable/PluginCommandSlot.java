package top.fusb.deploybot.plugin.api.deployment.variable;

/**
 * 描述插件在变量装配阶段会读写的标准命令槽位。
 * <p>
 * 这类槽位代表平台流程中的固定命令语义，而不是模板作者自行约定的任意变量名。
 * 例如 Spring Boot 为启动命令追加 Profile / YAML 参数时，
 * 应该命中 {@link #START_COMMAND} 这个槽位，而不是要求模板必须写死某个变量名。
 * </p>
 */
public enum PluginCommandSlot {

    /**
     * 主构建命令槽位。
     */
    BUILD_COMMAND,

    /**
     * 复合插件中的后端构建命令槽位。
     */
    BACKEND_BUILD_COMMAND,

    /**
     * 服务启动命令槽位。
     */
    START_COMMAND
}
