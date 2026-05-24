package top.fusb.deploybot.plugin.fullstack.deployment;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import top.fusb.deploybot.plugin.api.deployment.DeploymentPluginContext;
import top.fusb.deploybot.plugin.api.deployment.DeploymentPluginPlan;
import top.fusb.deploybot.plugin.api.deployment.DeploymentPluginType;
import top.fusb.deploybot.plugin.api.deployment.form.PluginFormSection;
import top.fusb.deploybot.plugin.api.deployment.form.PluginFormSchema;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableAssemblyContext;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableAssemblyPhase;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableAssemblyResult;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableDefinition;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableMutationRule;
import top.fusb.deploybot.plugin.nodestatic.deployment.NodeStaticDeploymentPlugin;
import top.fusb.deploybot.plugin.springboot.deployment.SpringBootDeploymentPlugin;
import top.fusb.deploybot.plugin.common.deployment.AbstractDeploymentPlugin;

/**
 * 面向前后端一体项目的内置部署类型插件。
 * <p>
 * 插件流程：
 * 1. 读取一体化插件自己的默认模板与描述信息；
 * 2. 组合 Node 静态站点插件和 Spring Boot 插件的表单项、变量定义和变量改写规则；
 * 3. 变量组装时先执行前端子插件，再把结果交给后端子插件继续处理；
 * 4. 部署计划中声明两个子单元，由编排层按前端构建、后端服务发布的顺序执行。
 * </p>
 * <p>
 * 该插件本身不直接处理 PID 或启动判定，服务进程相关能力来自后端 Spring Boot 子插件。
 * </p>
 */
public class FullstackDeploymentPlugin extends AbstractDeploymentPlugin {

    private static final NodeStaticDeploymentPlugin FRONTEND_PLUGIN = new NodeStaticDeploymentPlugin();
    private static final SpringBootDeploymentPlugin BACKEND_PLUGIN = new SpringBootDeploymentPlugin();
    private static final String DEFINITION_RESOURCE = "/plugin-definition/fullstack-plugin.json";

    /**
     * 返回插件唯一标识，平台会用它绑定默认模板、插件详情和部署计划。
     *
     * @return 前后端一体部署插件标识
     */
    @Override
    public String pluginId() {
        return "fullstack-deployment";
    }

    /**
     * 返回当前插件的资源定义文件，平台会从该 JSON 中读取一体化默认模板和插件基础信息。
     *
     * @return 前后端一体插件定义资源路径
     */
    @Override
    protected String definitionResourcePath() {
        return DEFINITION_RESOURCE;
    }

    /**
     * 合并前端和后端子插件声明的流水线配置项。
     * <p>
     * 相同 key 的配置项只保留一份，避免组合插件重复展示同一类配置。
     * </p>
     *
     * @return 组合后的流水线配置表单结构
     */
    @Override
    public PluginFormSchema pipelineFormSchema() {
        Map<String, PluginFormSection> sections = new LinkedHashMap<>();
        FRONTEND_PLUGIN.pipelineFormSchema().sections().forEach(item -> sections.put(item.key(), item));
        BACKEND_PLUGIN.pipelineFormSchema().sections().forEach(item -> sections.put(item.key(), item));
        return new PluginFormSchema(List.copyOf(sections.values()));
    }

    /**
     * 合并前端、后端和当前插件自身声明的变量定义。
     *
     * @return 去重后的变量定义列表
     */
    @Override
    public List<PluginVariableDefinition> variableDefinitions() {
        Map<String, PluginVariableDefinition> definitions = new LinkedHashMap<>();
        FRONTEND_PLUGIN.variableDefinitions().forEach(item -> definitions.put(item.key(), item));
        BACKEND_PLUGIN.variableDefinitions().forEach(item -> definitions.put(item.key(), item));
        super.variableDefinitions().forEach(item -> definitions.put(item.key(), item));
        return List.copyOf(definitions.values());
    }

    /**
     * 合并前端、后端和当前插件自身声明的变量改写规则。
     *
     * @return 去重后的变量改写规则列表
     */
    @Override
    public List<PluginVariableMutationRule> variableMutationRules() {
        Map<String, PluginVariableMutationRule> rules = new LinkedHashMap<>();
        FRONTEND_PLUGIN.variableMutationRules().forEach(item -> rules.put(item.ruleId(), item));
        BACKEND_PLUGIN.variableMutationRules().forEach(item -> rules.put(item.ruleId(), item));
        super.variableMutationRules().forEach(item -> rules.put(item.ruleId(), item));
        return List.copyOf(rules.values());
    }

    /**
     * 声明该插件会参与变量组装的阶段。
     *
     * @return 构建阶段用于前端子单元变量处理，发布阶段用于后端服务参数注入
     */
    @Override
    public EnumSet<PluginVariableAssemblyPhase> variableAssemblyPhases() {
        return EnumSet.of(PluginVariableAssemblyPhase.BUILD, PluginVariableAssemblyPhase.DEPLOY);
    }

    /**
     * 按子插件顺序组装变量。
     * <p>
     * 前端子插件先处理构建相关变量，后端子插件在这个结果基础上继续注入 Spring Boot 运行参数、
     * 运行日志路径和部署身份参数。
     * </p>
     *
     * @param context 当前变量组装上下文
     * @return 两个子插件顺序处理后的变量组装结果
     */
    @Override
    public PluginVariableAssemblyResult assembleVariables(PluginVariableAssemblyContext context) {
        PluginVariableAssemblyResult frontendResult = FRONTEND_PLUGIN.assembleVariables(context);
        PluginVariableAssemblyContext backendContext = new PluginVariableAssemblyContext(
                context.phase(),
                context.pluginId(),
                context.projectContext(),
                context.runtimeContext(),
                new top.fusb.deploybot.plugin.api.deployment.context.PluginVariableContext(
                        context.variableContext() == null ? null : context.variableContext().variablesJson(),
                        frontendResult == null || frontendResult.variables() == null ? Map.of() : frontendResult.variables(),
                        context.variableContext() == null ? List.of() : context.variableContext().templateVariables()
                ),
                context.serviceContext()
        );
        PluginVariableAssemblyResult backendResult = BACKEND_PLUGIN.assembleVariables(backendContext);
        Map<String, String> variables = backendResult == null || backendResult.variables() == null
                ? Map.of()
                : backendResult.variables();
        List<String> notes = new java.util.ArrayList<>();
        if (frontendResult != null && frontendResult.notes() != null) {
            notes.addAll(frontendResult.notes());
        }
        if (backendResult != null && backendResult.notes() != null) {
            notes.addAll(backendResult.notes());
        }
        return new PluginVariableAssemblyResult(variables, List.copyOf(notes));
    }

    /**
     * 返回插件匹配优先级。
     *
     * @return 数值越小优先级越高，一体化插件需要优先于单独的前端或后端插件匹配
     */
    @Override
    public int order() {
        return 50;
    }

    /**
     * 判断当前部署上下文是否适用于前后端一体插件。
     *
     * @param context 部署插件匹配上下文
     * @return 模板类型显式声明一体化，或脚本同时包含前端构建和 Java 启动特征时返回 {@code true}
     */
    @Override
    public boolean supports(DeploymentPluginContext context) {
        return templateContains(context, "springboot_frontend")
                || templateContains(context, "fullstack")
                || (containsIgnoreCase(context.buildScript(), "npm")
                && containsIgnoreCase(context.startCommand(), "java -jar"));
    }

    /**
     * 生成一体化部署计划。
     * <p>
     * 计划包含前端静态构建子单元和后端服务子单元，后端子单元会携带 PID 发现与启动判定插件标识。
     * </p>
     *
     * @param context 部署插件匹配上下文
     * @return 由两个叶子计划组成的复合计划
     */
    @Override
    public DeploymentPluginPlan plan(DeploymentPluginContext context) {
        DeploymentPluginPlan frontendPlan = DeploymentPluginPlan.leaf(
                "node-static-deployment",
                DeploymentPluginType.NODE_STATIC_SITE,
                "前端静态构建单元",
                null,
                null
        );
        DeploymentPluginPlan backendPlan = DeploymentPluginPlan.leaf(
                "springboot-deployment",
                top.fusb.deploybot.plugin.api.deployment.DeploymentPluginType.JAVA_BACKEND_SERVICE,
                "后端服务单元",
                SpringBootDeploymentPlugin.PROCESS_LOCATOR_PLUGIN_ID,
                SpringBootDeploymentPlugin.STARTUP_JUDGE_PLUGIN_ID
        );
        return DeploymentPluginPlan.composite(
                pluginId(),
                "前后端一体部署",
                List.of(frontendPlan, backendPlan)
        );
    }
}
