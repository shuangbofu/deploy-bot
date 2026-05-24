package top.fusb.deploybot.plugin.api.deployment;

import java.util.EnumSet;

import top.fusb.deploybot.plugin.api.deployment.definition.PluginDescriptor;
import top.fusb.deploybot.plugin.api.deployment.definition.PluginRuntimeRequirement;
import top.fusb.deploybot.plugin.api.deployment.template.PluginBuiltinTemplate;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableAssemblyContext;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableAssemblyPhase;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableAssemblyResult;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableDefinition;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableMutationRule;

/**
 * 描述部署类型识别与能力编排的顶层插件接口。
 * <p>
 * 与 {@code ProcessLocatorPlugin}、{@code StartupJudgePlugin} 这类能力插件不同，
 * 该接口负责回答“当前项目/模板属于什么部署类型，以及它该组合哪些能力插件”。
 * </p>
 */
public interface DeploymentPlugin {

    /**
     * 返回插件对外展示用的描述信息。
     *
     * @return 插件描述
     */
    PluginDescriptor descriptor();

    /**
     * 返回当前插件声明的运行时要求。
     *
     * @return 运行时要求
     */
    PluginRuntimeRequirement runtimeRequirement();

    /**
     * 返回当前插件希望前端在流水线配置阶段展示的表单结构。
     *
     * @return 流水线配置表单结构
     */
    default top.fusb.deploybot.plugin.api.deployment.form.PluginFormSchema pipelineFormSchema() {
        return new top.fusb.deploybot.plugin.api.deployment.form.PluginFormSchema(java.util.List.of());
    }

    /**
     * 返回当前插件声明的变量定义。
     *
     * @return 变量定义列表
     */
    java.util.List<PluginVariableDefinition> variableDefinitions();

    /**
     * 返回当前插件会自动改写平台变量的规则说明。
     * <p>
     * 这组规则面向模板作者，帮助他们理解：哪些变量会被插件补充、重写或组合，
     * 从而避免把同一段逻辑又手工写进模板脚本。
     * </p>
     *
     * @return 自动改写规则列表
     */
    default java.util.List<PluginVariableMutationRule> variableMutationRules() {
        return java.util.List.of();
    }

    /**
     * 返回当前插件自带的默认模板定义列表。
     *
     * @return 默认模板定义列表
     */
    java.util.List<PluginBuiltinTemplate> builtinTemplates();

    /**
     * 返回插件启动时展示的横幅文本。
     *
     * @return 横幅文本；若当前插件未提供则返回 {@code null}
     */
    default String bannerText() {
        return null;
    }

    /**
     * 返回当前插件参与变量二次拼装的阶段集合。
     * <p>
     * 平台生命周期是固定的，插件只需要声明自己是否参与构建阶段或发布阶段的变量改写。
     * 例如 Spring Boot 会参与 BUILD 与 DEPLOY，而静态站点通常不参与。
     * </p>
     *
     * @return 支持的变量拼装阶段集合
     */
    default EnumSet<PluginVariableAssemblyPhase> variableAssemblyPhases() {
        return EnumSet.noneOf(PluginVariableAssemblyPhase.class);
    }

    /**
     * 在构建或发布阶段对变量进行插件特有的拼装与补全。
     *
     * @param context 变量拼装上下文
     * @return 变量拼装结果
     */
    PluginVariableAssemblyResult assembleVariables(PluginVariableAssemblyContext context);

    /**
     * 返回部署类型插件唯一标识。
     *
     * @return 插件标识
     */
    String pluginId();

    /**
     * 返回部署类型插件匹配优先级，值越小优先级越高。
     *
     * @return 插件顺序
     */
    int order();

    /**
     * 判断当前部署类型插件是否适用于本次上下文。
     *
     * @param context 部署插件决策上下文
     * @return 适用时返回 {@code true}
     */
    boolean supports(DeploymentPluginContext context);

    /**
     * 生成当前上下文应采用的部署计划。
     *
     * @param context 部署插件决策上下文
     * @return 部署计划
     */
    DeploymentPluginPlan plan(DeploymentPluginContext context);

    /**
     * 返回当前插件携带的 PID 发现能力实现。
     *
     * @return PID 发现插件；若当前类型无需服务接管则返回 {@code null}
     */
    default top.fusb.deploybot.plugin.api.process.ProcessLocatorPlugin processLocatorPlugin() {
        return null;
    }

    /**
     * 返回当前插件携带的启动判定能力实现。
     *
     * @return 启动判定插件；若当前类型无需启动判定则返回 {@code null}
     */
    default top.fusb.deploybot.plugin.api.startup.StartupJudgePlugin startupJudgePlugin() {
        return null;
    }

}
