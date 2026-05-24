package top.fusb.deploybot.plugin.api.deployment;

import java.util.List;

/**
 * 描述部署类型插件输出的编排计划。
 * <p>
 * 该计划负责把“这是什么类型的部署单元”与“它需要哪些能力插件”绑定起来，
 * 后续主流程只需要消费计划，而不再直接写死 Spring Boot、Node 或 Python 分支。
 * </p>
 *
 * @param pluginId 部署类型插件标识
 * @param pluginType 部署单元类型
 * @param displayName 当前部署计划展示名称
 * @param processLocatorPluginId 负责 PID 发现的能力插件标识，静态站点可为空
 * @param startupJudgePluginId 负责启动判定的能力插件标识，静态站点可为空
 * @param children 子部署计划列表，复合部署场景使用
 */
public record DeploymentPluginPlan(
        String pluginId,
        DeploymentPluginType pluginType,
        String displayName,
        String processLocatorPluginId,
        String startupJudgePluginId,
        List<DeploymentPluginPlan> children
) {

    /**
     * 创建叶子部署计划。
     *
     * @param pluginId 部署类型插件标识
     * @param pluginType 部署单元类型
     * @param displayName 展示名称
     * @param processLocatorPluginId PID 发现插件标识
     * @param startupJudgePluginId 启动判定插件标识
     * @return 叶子部署计划
     */
    public static DeploymentPluginPlan leaf(
            String pluginId,
            DeploymentPluginType pluginType,
            String displayName,
            String processLocatorPluginId,
            String startupJudgePluginId
    ) {
        return new DeploymentPluginPlan(
                pluginId,
                pluginType,
                displayName,
                processLocatorPluginId,
                startupJudgePluginId,
                List.of()
        );
    }

    /**
     * 创建复合部署计划。
     *
     * @param pluginId 部署类型插件标识
     * @param displayName 展示名称
     * @param children 子部署计划
     * @return 复合部署计划
     */
    public static DeploymentPluginPlan composite(
            String pluginId,
            String displayName,
            List<DeploymentPluginPlan> children
    ) {
        return new DeploymentPluginPlan(
                pluginId,
                DeploymentPluginType.COMPOSITE,
                displayName,
                null,
                null,
                children == null ? List.of() : List.copyOf(children)
        );
    }

    /**
     * 判断当前计划是否为复合计划。
     *
     * @return 复合计划返回 {@code true}
     */
    public boolean composite() {
        return pluginType == DeploymentPluginType.COMPOSITE;
    }

    /**
     * 判断当前计划是否直接声明了服务接管相关能力。
     *
     * @return 如果计划具备 PID 发现或启动判定能力则返回 {@code true}
     */
    public boolean serviceCapable() {
        return (processLocatorPluginId != null && !processLocatorPluginId.isBlank())
                || (startupJudgePluginId != null && !startupJudgePluginId.isBlank());
    }

    /**
     * 判断当前计划是否声明了 PID 发现步骤。
     *
     * @return 声明了 PID 发现插件则返回 {@code true}
     */
    public boolean processLocatorEnabled() {
        return processLocatorPluginId != null && !processLocatorPluginId.isBlank();
    }

    /**
     * 判断当前计划是否声明了启动判定步骤。
     *
     * @return 声明了启动判定插件则返回 {@code true}
     */
    public boolean startupJudgeEnabled() {
        return startupJudgePluginId != null && !startupJudgePluginId.isBlank();
    }

    /**
     * 在当前计划树中查找第一条具备服务接管能力的叶子计划。
     *
     * @return 命中的服务计划；若不存在则返回 {@code null}
     */
    public DeploymentPluginPlan firstServicePlan() {
        if (serviceCapable()) {
            return this;
        }
        if (children == null || children.isEmpty()) {
            return null;
        }
        for (DeploymentPluginPlan child : children) {
            DeploymentPluginPlan matched = child == null ? null : child.firstServicePlan();
            if (matched != null) {
                return matched;
            }
        }
        return null;
    }
}
