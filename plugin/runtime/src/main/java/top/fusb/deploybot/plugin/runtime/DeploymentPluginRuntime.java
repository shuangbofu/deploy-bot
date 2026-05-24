package top.fusb.deploybot.plugin.runtime;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

import top.fusb.deploybot.plugin.api.deployment.DeploymentPlugin;
import top.fusb.deploybot.plugin.api.deployment.DeploymentPluginContext;
import top.fusb.deploybot.plugin.api.deployment.DeploymentPluginPlan;
import top.fusb.deploybot.plugin.api.deployment.definition.DeploymentPluginDefinition;
import top.fusb.deploybot.plugin.api.deployment.definition.PluginDescriptor;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableAssemblyContext;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableAssemblyResult;
import top.fusb.deploybot.plugin.api.process.ProcessLocatorPlugin;
import top.fusb.deploybot.plugin.api.startup.StartupJudgePlugin;

/**
 * 插件运行时入口。
 * <p>
 * 该类是 backend 对插件层的唯一稳定入口：
 * 列出类型插件、解析部署计划、并从计划中继续解析底层能力插件。
 * </p>
 */
public class DeploymentPluginRuntime {

    private final List<DeploymentPlugin> deploymentPlugins;

    /**
     * 创建默认插件运行时，并通过 {@link ServiceLoader} 从 classpath 上加载类型插件实现。
     */
    public DeploymentPluginRuntime() {
        this(loadProviders(DeploymentPlugin.class));
    }

    /**
     * 使用指定类型插件列表创建插件运行时。
     *
     * @param deploymentPlugins 部署类型插件
     */
    public DeploymentPluginRuntime(List<DeploymentPlugin> deploymentPlugins) {
        this.deploymentPlugins = deploymentPlugins.stream()
                .sorted(Comparator.comparingInt(DeploymentPlugin::order))
                .toList();
        validateUniquePluginIds(this.deploymentPlugins);
    }

    private static <T> List<T> loadProviders(Class<T> serviceType) {
        ServiceLoader<T> loader = ServiceLoader.load(serviceType);
        return StreamSupport.stream(loader.spliterator(), false).toList();
    }

    private static void validateUniquePluginIds(List<DeploymentPlugin> plugins) {
        Map<String, String> seenPluginIds = new LinkedHashMap<>();
        for (DeploymentPlugin plugin : plugins) {
            if (plugin == null || plugin.pluginId() == null || plugin.pluginId().isBlank()) {
                throw new IllegalStateException("发现未声明 pluginId 的部署类型插件。");
            }
            String previous = seenPluginIds.putIfAbsent(plugin.pluginId(), plugin.getClass().getName());
            if (previous != null) {
                throw new IllegalStateException(
                        "发现重复的部署类型插件标识：" + plugin.pluginId()
                                + "，冲突实现为 " + previous + " 与 " + plugin.getClass().getName()
                );
            }
        }
    }

    /**
     * 返回当前已注册的部署类型插件描述。
     *
     * @return 插件描述列表
     */
    public List<PluginDescriptor> listDeploymentPluginDescriptors() {
        return deploymentPlugins.stream().map(DeploymentPlugin::descriptor).toList();
    }

    /**
     * 返回当前已注册的部署类型插件实例。
     *
     * @return 插件实例列表
     */
    public List<DeploymentPlugin> listDeploymentPlugins() {
        return List.copyOf(deploymentPlugins);
    }

    /**
     * 返回当前已注册的完整类型插件定义。
     *
     * @return 完整插件定义列表
     */
    public List<DeploymentPluginDefinition> listDeploymentPluginDefinitions() {
        return deploymentPlugins.stream()
                .map(plugin -> new DeploymentPluginDefinition(
                        plugin.descriptor(),
                        plugin.runtimeRequirement(),
                        plugin.pipelineFormSchema(),
                        plugin.variableDefinitions(),
                        plugin.variableMutationRules(),
                        plugin.builtinTemplates()
                ))
                .toList();
    }

    /**
     * 解析当前上下文匹配到的部署计划。
     *
     * @param context 部署插件上下文
     * @return 命中的部署计划；若未命中则返回 {@code null}
     */
    public DeploymentPluginPlan resolveDeploymentPlan(DeploymentPluginContext context) {
        if (context != null && context.pluginId() != null && !context.pluginId().isBlank()) {
            DeploymentPlugin explicitPlugin = resolveDeploymentPlugin(context.pluginId());
            if (explicitPlugin != null) {
                return explicitPlugin.plan(context);
            }
        }
        return deploymentPlugins.stream()
                .filter(plugin -> plugin.supports(context))
                .findFirst()
                .map(plugin -> plugin.plan(context))
                .orElse(null);
    }

    /**
     * 根据计划中声明的插件标识解析 PID 发现插件。
     *
     * @param plan 部署计划
     * @return 匹配的 PID 发现插件；若没有则返回 {@code null}
     */
    public ProcessLocatorPlugin resolveProcessLocator(DeploymentPluginPlan plan) {
        if (plan == null) {
            return null;
        }
        DeploymentPlugin plugin = resolveDeploymentPlugin(plan.pluginId());
        if (plugin == null) {
            return null;
        }
        ProcessLocatorPlugin locatorPlugin = plugin.processLocatorPlugin();
        if (locatorPlugin == null || plan.processLocatorPluginId() == null || plan.processLocatorPluginId().isBlank()) {
            return null;
        }
        return locatorPlugin.pluginId().equals(plan.processLocatorPluginId()) ? locatorPlugin : null;
    }

    /**
     * 根据计划中声明的插件标识解析启动判定插件。
     *
     * @param plan 部署计划
     * @return 匹配的启动判定插件；若没有则返回 {@code null}
     */
    public StartupJudgePlugin resolveStartupJudge(DeploymentPluginPlan plan) {
        if (plan == null) {
            return null;
        }
        DeploymentPlugin plugin = resolveDeploymentPlugin(plan.pluginId());
        if (plugin == null) {
            return null;
        }
        StartupJudgePlugin startupJudgePlugin = plugin.startupJudgePlugin();
        if (startupJudgePlugin == null || plan.startupJudgePluginId() == null || plan.startupJudgePluginId().isBlank()) {
            return null;
        }
        return startupJudgePlugin.pluginId().equals(plan.startupJudgePluginId()) ? startupJudgePlugin : null;
    }

    /**
     * 调用指定类型插件完成变量拼装。
     *
     * @param context 变量拼装上下文
     * @return 拼装结果；若未找到对应类型插件则返回 {@code null}
     */
    public PluginVariableAssemblyResult assembleVariables(PluginVariableAssemblyContext context) {
        if (context == null) {
            return null;
        }
        DeploymentPlugin plugin = resolveDeploymentPlugin(context.pluginId());
        if (plugin == null) {
            return null;
        }
        return plugin.assembleVariables(context);
    }

    /**
     * 按插件标识解析类型插件本体。
     *
     * @param pluginId 类型插件标识
     * @return 命中的类型插件；若不存在则返回 {@code null}
     */
    public DeploymentPlugin resolveDeploymentPlugin(String pluginId) {
        if (pluginId == null || pluginId.isBlank()) {
            return null;
        }
        return deploymentPlugins.stream()
                .filter(plugin -> plugin.pluginId().equals(pluginId))
                .findFirst()
                .orElse(null);
    }
}
