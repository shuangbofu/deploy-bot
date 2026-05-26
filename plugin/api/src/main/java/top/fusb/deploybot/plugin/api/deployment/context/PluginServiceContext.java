package top.fusb.deploybot.plugin.api.deployment.context;

import java.util.Map;

/**
 * 描述服务运行与接管阶段所需的标准化信息。
 *
 * @param serviceName 平台生成的稳定服务标识
 * @param startCommand 启动命令
 * @param startupKeyword 流水线级启动关键字，供启动判定插件决定是否做日志关键字判活
 * @param config 插件运行配置，按插件字段 key 存取
 * @param runtimeLogPath 运行日志路径
 * @param monitoredPid 候选受管 PID；启动观察超时时间由宿主部署编排层控制，不传入插件
 */
public record PluginServiceContext(
        String serviceName,
        String startCommand,
        String startupKeyword,
        Map<String, String> config,
        String runtimeLogPath,
        Long monitoredPid
) {
    public String configValue(String key) {
        return config == null ? null : config.get(key);
    }
}
