package top.fusb.deploybot.plugin.nodestatic.deployment;

import java.util.LinkedHashMap;
import java.util.Map;

import top.fusb.deploybot.plugin.api.deployment.DeploymentPluginContext;
import top.fusb.deploybot.plugin.api.deployment.DeploymentPluginPlan;
import top.fusb.deploybot.plugin.api.deployment.DeploymentPluginType;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableAssemblyContext;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableAssemblyResult;
import top.fusb.deploybot.plugin.common.deployment.AbstractDeploymentPlugin;

/**
 * 面向 Node 构建静态站点的内置部署类型插件。
 * <p>
 * 插件流程：
 * 1. 通过插件定义 JSON 暴露模板、表单配置和组件环境依赖；
 * 2. 根据构建脚本或模板类型判断是否适用于 React/Vue 等 Node 静态站点；
 * 3. 变量组装阶段保持平台变量原样透传，因为静态站点没有常驻进程、PID 和启动判定；
 * 4. 生成部署计划时明确声明不需要进程发现插件和启动判定插件。
 * </p>
 */
public class NodeStaticDeploymentPlugin extends AbstractDeploymentPlugin {

    private static final String DEFINITION_RESOURCE = "/plugin-definition/node-static-plugin.json";

    /**
     * 返回插件唯一标识，平台会用它绑定默认模板、插件详情和部署计划。
     *
     * @return Node 静态站点部署插件标识
     */
    @Override
    public String pluginId() {
        return "node-static-deployment";
    }

    /**
     * 返回当前插件的资源定义文件，平台会从该 JSON 中读取默认模板、配置项和运行环境依赖。
     *
     * @return Node 静态站点插件定义资源路径
     */
    @Override
    protected String definitionResourcePath() {
        return DEFINITION_RESOURCE;
    }

    /**
     * 组装部署变量。
     * <p>
     * 静态站点只需要构建产物和发布目录，不需要改写启动命令或注入运行参数，所以这里仅复制变量并返回说明。
     * </p>
     *
     * @param context 当前变量组装上下文
     * @return 原样透传后的变量组装结果
     */
    @Override
    public PluginVariableAssemblyResult assembleVariables(PluginVariableAssemblyContext context) {
        Map<String, String> variables = new LinkedHashMap<>(context == null || context.variables() == null
                ? Map.of()
                : context.variables());
        return new PluginVariableAssemblyResult(
                variables,
                java.util.List.of()
        );
    }

    /**
     * 返回插件匹配优先级。
     *
     * @return 数值越小优先级越高，静态站点插件低于一体化和后端服务插件
     */
    @Override
    public int order() {
        return 200;
    }

    /**
     * 判断当前部署上下文是否适用于 Node 静态站点插件。
     *
     * @param context 部署插件匹配上下文，包含模板类型、构建脚本和运行环境类型
     * @return 匹配 Node 运行环境、常见前端模板类型或前端构建命令时返回 {@code true}
     */
    @Override
    public boolean supports(DeploymentPluginContext context) {
        return runtimeTypeEquals(context, "NODE")
                || templateContains(context, "react")
                || templateContains(context, "vue")
                || containsIgnoreCase(context.buildScript(), "npm run build")
                || containsIgnoreCase(context.buildScript(), "pnpm build")
                || containsIgnoreCase(context.buildScript(), "yarn build");
    }

    /**
     * 生成静态站点部署计划。
     *
     * @param context 部署插件匹配上下文
     * @return 不包含 PID 发现和启动判定能力的叶子计划
     */
    @Override
    public DeploymentPluginPlan plan(DeploymentPluginContext context) {
        return DeploymentPluginPlan.leaf(
                pluginId(),
                DeploymentPluginType.NODE_STATIC_SITE,
                "Node 静态站点",
                null,
                null
        );
    }
}
