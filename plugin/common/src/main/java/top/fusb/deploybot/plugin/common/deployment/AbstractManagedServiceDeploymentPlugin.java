package top.fusb.deploybot.plugin.common.deployment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import top.fusb.deploybot.kit.ShellKit;
import top.fusb.deploybot.kit.TextKit;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableAssemblyContext;

/**
 * 有常驻进程的服务类部署插件抽象基类。
 * <p>
 * 该类统一维护平台注入到应用进程里的部署身份参数，后续 Spring Boot、Python、Go
 * 等服务插件只需要决定这些参数应放进哪个启动参数槽位，即可复用同一套 PID 发现策略。
 */
public abstract class AbstractManagedServiceDeploymentPlugin extends AbstractDeploymentPlugin {

    /**
     * 平台用于标记一次部署进程的参数名。
     */
    public static final String DEPLOYMENT_ID_ARGUMENT_NAME = "deploybot.deployment-id";

    /**
     * 将应用启动参数写入当前插件自己的参数槽位。
     *
     * @param variables 当前阶段变量集合，方法实现可按需写回新的变量值
     * @param context 变量组装上下文，包含流水线、项目、服务等部署上下文
     * @param arguments 已组装好的应用启动参数列表，元素已完成 shell 安全转义
     */
    protected abstract void applyApplicationArguments(
            Map<String, String> variables,
            PluginVariableAssemblyContext context,
            List<String> arguments
    );

    /**
     * 追加平台部署身份参数。
     *
     * @param arguments 需要被追加的应用启动参数列表
     * @param context 变量组装上下文
     * @param variables 当前阶段变量集合
     */
    protected void addDeploymentIdentityArguments(
            List<String> arguments,
            PluginVariableAssemblyContext context,
            Map<String, String> variables
    ) {
        addApplicationArgument(arguments, DEPLOYMENT_ID_ARGUMENT_NAME, valueOf(variables, "deploymentId"));
        addApplicationArgument(arguments, "deploybot.pipeline-id", valueOf(variables, "pipelineId"));
        addApplicationArgument(arguments, "deploybot.pipeline-name", context == null || context.projectContext() == null ? null : context.projectContext().pipelineName());
        addApplicationArgument(arguments, "deploybot.pipeline-tags", valueOf(variables, "pipelineTags"));
        addApplicationArgument(arguments, "deploybot.project-id", valueOf(variables, "projectId"));
        addApplicationArgument(arguments, "deploybot.project-name", context == null || context.projectContext() == null ? null : context.projectContext().projectName());
        addApplicationArgument(arguments, "deploybot.service-name", context == null || context.serviceContext() == null ? null : context.serviceContext().serviceName());
    }

    /**
     * 构建并返回平台部署身份参数。
     *
     * @param context 变量组装上下文
     * @param variables 当前阶段变量集合
     * @return 已完成 shell 安全转义的部署身份参数列表
     */
    protected List<String> buildDeploymentIdentityArguments(
            PluginVariableAssemblyContext context,
            Map<String, String> variables
    ) {
        List<String> arguments = new ArrayList<>();
        addDeploymentIdentityArguments(arguments, context, variables);
        return arguments;
    }

    /**
     * 追加一个应用启动参数，空值会被忽略。
     *
     * @param arguments 需要被追加的应用启动参数列表
     * @param key 参数名，不包含前缀 {@code --}
     * @param value 参数值
     */
    protected void addApplicationArgument(List<String> arguments, String key, String value) {
        if (TextKit.isBlank(key) || TextKit.isBlank(value)) {
            return;
        }
        arguments.add("--" + key.trim() + "=" + ShellKit.singleQuote(value.trim()));
    }

    private String valueOf(Map<String, String> variables, String key) {
        return variables == null ? null : variables.get(key);
    }
}
