package top.fusb.deploybot.service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import top.fusb.deploybot.dto.DeploymentPluginPlanSummary;
import top.fusb.deploybot.dto.TemplateVariableSchemaBinding;
import top.fusb.deploybot.dto.TemplateVariableSchemaItem;
import top.fusb.deploybot.kit.JsonKit;
import top.fusb.deploybot.kit.ObjectKit;
import top.fusb.deploybot.kit.ProcessKit;
import top.fusb.deploybot.model.DeploymentEntity;
import top.fusb.deploybot.model.HostEntity;
import top.fusb.deploybot.model.HostType;
import top.fusb.deploybot.model.PipelineEntity;
import top.fusb.deploybot.plugin.api.deployment.DeploymentPluginContext;
import top.fusb.deploybot.plugin.api.deployment.DeploymentPluginPlan;
import top.fusb.deploybot.plugin.api.deployment.DeploymentPlugin;
import top.fusb.deploybot.plugin.api.deployment.context.PluginHostContext;
import top.fusb.deploybot.plugin.api.deployment.context.PluginProjectContext;
import top.fusb.deploybot.plugin.api.deployment.context.PluginRuntimeContext;
import top.fusb.deploybot.plugin.api.deployment.context.PluginServiceContext;
import top.fusb.deploybot.plugin.api.deployment.context.PluginTemplateVariable;
import top.fusb.deploybot.plugin.api.deployment.context.PluginTemplateVariableBinding;
import top.fusb.deploybot.plugin.api.deployment.context.PluginTemplateVariableBindingScope;
import top.fusb.deploybot.plugin.api.deployment.context.PluginVariableContext;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableDefinition;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableAssemblyContext;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableAssemblyPhase;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableAssemblyResult;
import top.fusb.deploybot.plugin.api.process.ProcessLocatorContext;
import top.fusb.deploybot.plugin.api.process.ProcessLocatorExecutionBridge;
import top.fusb.deploybot.plugin.api.process.ProcessLocatorPlugin;
import top.fusb.deploybot.plugin.api.process.ProcessLocatorResult;
import top.fusb.deploybot.plugin.api.startup.StartupJudgeContext;
import top.fusb.deploybot.plugin.api.startup.StartupJudgePlugin;
import top.fusb.deploybot.plugin.api.startup.StartupJudgeResult;
import top.fusb.deploybot.plugin.runtime.DeploymentPluginRuntime;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * backend 与插件运行时之间的桥接服务。
 */
@Service
@RequiredArgsConstructor
public class DeploymentPluginBridgeService {
    private static final Logger log = LoggerFactory.getLogger(DeploymentPluginBridgeService.class);

    private final HostService hostService;
    private final DeploymentPluginRuntime pluginRuntime;
    private final PipelineTemplateResolverService pipelineTemplateResolverService;

    /**
     * 为当前部署解析一份部署计划。
     *
     * @param deployment 部署记录
     * @return 匹配到的部署计划；若未命中则返回 {@code null}
     */
    public DeploymentPluginPlan resolvePlan(DeploymentEntity deployment) {
        if (deployment == null) {
            return null;
        }
        PipelineEntity pipeline = deployment.getPipeline();
        if (pipeline == null) {
            return null;
        }
        return resolvePlan(
                pipeline,
                deployment.getVariables() == null ? Map.of() : deployment.getVariables(),
                deployment.getRenderedBuildScript(),
                deployment.getRenderedDeployScript()
        );
    }

    /**
     * 读取指定插件的部署横幅文本。
     *
     * @param pluginId 部署类型插件标识
     * @return 插件横幅文本；未找到插件或未配置横幅时返回 {@code null}
     */
    public String resolveBannerText(String pluginId) {
        DeploymentPlugin plugin = pluginRuntime.resolveDeploymentPlugin(pluginId);
        if (plugin == null) {
            return null;
        }
        return plugin.bannerText();
    }

    /**
     * 返回当前流水线在指定阶段真正需要的组件环境类型。
     * <p>
     * 这里读取插件声明的 runtimeRequirement，而不是读取流水线上曾经选择过哪些环境，
     * 避免 Node 静态站点这类插件把 Java/Maven 环境也带进脚本前置逻辑。
     * </p>
     *
     * @param pipeline 流水线配置
     * @param buildStage 是否为构建阶段
     * @return 插件声明的组件环境类型列表；未命中插件时回退到流水线已选择的环境类型
     */
    public List<String> requiredRuntimeTypes(PipelineEntity pipeline, boolean buildStage) {
        DeploymentPlugin plugin = pluginRuntime.resolveDeploymentPlugin(resolvePluginId(pipeline));
        List<String> declaredTypes = plugin == null || plugin.runtimeRequirement() == null
                ? List.of()
                : (buildStage ? plugin.runtimeRequirement().buildRuntimeTypes() : plugin.runtimeRequirement().targetRuntimeTypes());
        if (declaredTypes != null && !declaredTypes.isEmpty()) {
            return declaredTypes.stream()
                    .filter(item -> item != null && !item.isBlank())
                    .map(item -> item.trim().toUpperCase())
                    .distinct()
                    .toList();
        }
        PluginRuntimeContext context = buildRuntimeContext(pipeline);
        List<String> fallbackTypes = buildStage ? context.buildRuntimeTypes() : context.targetRuntimeTypes();
        return fallbackTypes == null ? List.of() : fallbackTypes.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(item -> item.trim().toUpperCase())
                .distinct()
                .toList();
    }

    /**
     * 为当前流水线解析一份部署计划预览。
     *
     * @param pipeline 流水线配置
     * @return 匹配到的部署计划；若未匹配到则返回 {@code null}
     */
    public DeploymentPluginPlan resolvePlan(PipelineEntity pipeline) {
        if (pipeline == null) {
            return null;
        }
        return resolvePlan(
                pipeline,
                pipeline.getVariables() == null ? Map.of() : pipeline.getVariables(),
                resolveTemplateBuildScript(pipeline),
                resolveTemplateDeployScript(pipeline)
        );
    }

    private DeploymentPluginPlan resolvePlan(
            PipelineEntity pipeline,
            Map<String, String> variables,
            String buildScript,
            String deployScript
    ) {
        DeploymentPluginContext context = new DeploymentPluginContext(
                resolvePluginId(pipeline),
                buildProjectContext(pipeline, null),
                buildRuntimeContext(pipeline),
                buildHostContext(pipeline == null ? null : pipeline.getTargetHost()),
                buildVariableContext(pipeline, variables),
                buildServiceContext(pipeline, null, variables, null, null),
                buildScript,
                deployScript
        );
        DeploymentPluginPlan plan = pluginRuntime.resolveDeploymentPlan(context);
        if (plan == null) {
            log.info("流水线 {} 未命中部署插件，pluginId={}，templateType={}",
                    pipeline == null ? null : pipeline.getId(),
                    context.pluginId(),
                    pipeline == null ? null : pipeline.getTemplateTypeSnapshot());
        } else {
            log.info("流水线 {} 命中部署插件：{} ({})，PID检测={}，启动判定={}，子计划数={}",
                    pipeline == null ? null : pipeline.getId(),
                    plan.displayName(),
                    plan.pluginId(),
                    plan.processLocatorPluginId(),
                    plan.startupJudgePluginId(),
                    plan.children() == null ? 0 : plan.children().size());
        }
        return plan;
    }

    /**
     * 生成当前部署对应的插件计划摘要。
     *
     * @param deployment 部署记录
     * @return 插件计划摘要；若未匹配到计划则返回 {@code null}
     */
    public DeploymentPluginPlanSummary summarizePlan(DeploymentEntity deployment) {
        return toSummary(resolvePlan(deployment));
    }

    /**
     * 生成当前流水线对应的插件计划摘要。
     *
     * @param pipeline 流水线配置
     * @return 插件计划摘要；若未匹配到计划则返回 {@code null}
     */
    public DeploymentPluginPlanSummary summarizePlan(PipelineEntity pipeline) {
        return toSummary(resolvePlan(pipeline));
    }

    /**
     * 按指定阶段为当前流水线执行一次插件变量改写。
     *
     * @param pipeline 流水线配置
     * @param variables 当前变量快照
     * @param phase 变量装配阶段
     * @param mavenSettingsFilePath 构建阶段 Maven settings.xml 的绝对路径；若为空则不额外注入
     * @return 插件变量装配结果；若未匹配到计划或插件则返回 {@code null}
     */
    public PluginVariableAssemblyResult assembleVariables(
            PipelineEntity pipeline,
            Map<String, String> variables,
            PluginVariableAssemblyPhase phase,
            String mavenSettingsFilePath
    ) {
        if (pipeline == null || phase == null) {
            return null;
        }
        Map<String, String> mutableVariables = new LinkedHashMap<>(variables == null ? Map.of() : variables);
        PluginVariableAssemblyContext context = new PluginVariableAssemblyContext(
                phase,
                resolvePluginId(pipeline),
                buildProjectContext(pipeline, null),
                buildRuntimeContext(pipeline, mavenSettingsFilePath),
                buildVariableContext(pipeline, mutableVariables),
                buildServiceContext(pipeline, null, mutableVariables, null, null)
        );
        PluginVariableAssemblyResult result = pluginRuntime.assembleVariables(context);
        if (result == null) {
            log.info("流水线 {} 插件变量装配跳过，未找到插件：{}，阶段={}",
                    pipeline.getId(),
                    context.pluginId(),
                    phase);
            return null;
        }
        log.info("流水线 {} 已执行插件变量装配，插件={}，阶段={}，变量数={}",
                pipeline.getId(),
                context.pluginId(),
                phase,
                result.variables() == null ? 0 : result.variables().size());
        return result;
    }

    /**
     * 返回当前部署在服务接管视角下的有效计划。
     * 复合类型会优先返回第一条具备服务能力的子计划；若不存在则返回原计划本身。
     *
     * @param deployment 部署记录
     * @return 有效服务计划；若完全未命中则返回 {@code null}
     */
    public DeploymentPluginPlan resolveEffectiveServicePlan(DeploymentEntity deployment) {
        DeploymentPluginPlan plan = resolvePlan(deployment);
        if (plan == null) {
            return null;
        }
        DeploymentPluginPlan servicePlan = plan.firstServicePlan();
        return servicePlan == null ? plan : servicePlan;
    }

    /**
     * 使用插件运行时执行一次 PID 发现。
     *
     * @param deployment 部署记录
     * @param targetHost 目标主机
     * @return PID 发现结果；若当前计划未声明 PID 发现插件则返回 {@code null}
     */
    public ProcessLocatorResult locateProcess(DeploymentEntity deployment, HostEntity targetHost) {
        DeploymentPluginPlan plan = resolveEffectiveServicePlan(deployment);
        ProcessLocatorPlugin plugin = pluginRuntime.resolveProcessLocator(plan);
        if (plugin == null) {
            log.info("部署 {} 未解析到 PID 检测插件，计划插件={}",
                    deployment == null ? null : deployment.getId(),
                    plan == null ? null : plan.pluginId());
            return null;
        }
        log.info("部署 {} 调用 PID 检测插件：{}，计划插件={}", deployment.getId(), plugin.pluginId(), plan.pluginId());
        Map<String, String> variables = deployment.getVariables() == null ? Map.of() : deployment.getVariables();
        ProcessLocatorContext context = new ProcessLocatorContext(
                buildProjectContext(deployment.getPipeline(), deployment),
                buildRuntimeContext(deployment.getPipeline()),
                buildVariableContext(deployment.getPipeline(), variables),
                buildServiceContext(deployment.getPipeline(), deployment, variables, null, null),
                targetHost != null && targetHost.getType() == HostType.SSH,
                new BridgeExecutionBridge(targetHost)
        );
        ProcessLocatorResult result = plugin.locate(context);
        log.info("部署 {} PID 检测插件 {} 执行完成，PID={}，来源={}",
                deployment.getId(),
                plugin.pluginId(),
                result == null ? null : result.pid(),
                result == null ? null : result.sourceDescription());
        return result;
    }

    /**
     * 使用插件运行时解析当前部署应采用的启动判定策略。
     *
     * @param deployment 部署记录
     * @param monitoredPid 候选受管 PID
     * @param runtimeLogPath 运行日志路径
     * @return 启动判定结果；若当前计划未声明启动判定插件则返回 {@code null}
     */
    public StartupJudgeResult resolveStartupJudge(DeploymentEntity deployment, Long monitoredPid, String runtimeLogPath) {
        DeploymentPluginPlan plan = resolveEffectiveServicePlan(deployment);
        StartupJudgePlugin plugin = pluginRuntime.resolveStartupJudge(plan);
        if (plugin == null) {
            log.info("部署 {} 未解析到启动判定插件，计划插件={}",
                    deployment == null ? null : deployment.getId(),
                    plan == null ? null : plan.pluginId());
            return null;
        }
        log.info("部署 {} 调用启动判定插件：{}，计划插件={}，PID={}",
                deployment.getId(),
                plugin.pluginId(),
                plan.pluginId(),
                monitoredPid);
        PipelineEntity pipeline = deployment.getPipeline();
        StartupJudgeContext context = new StartupJudgeContext(
                buildProjectContext(pipeline, deployment),
                buildRuntimeContext(pipeline),
                buildServiceContext(
                        pipeline,
                        deployment,
                        deployment.getVariables() == null ? Map.of() : deployment.getVariables(),
                        runtimeLogPath,
                        monitoredPid
                )
        );
        StartupJudgeResult result = plugin.judge(context);
        log.info("部署 {} 启动判定插件 {} 执行完成，keywordRequired={}，keyword={}，策略={}",
                deployment.getId(),
                plugin.pluginId(),
                result == null ? null : result.keywordRequired(),
                result == null ? null : result.keyword(),
                result == null ? null : result.strategyDescription());
        return result;
    }

    private DeploymentPluginPlanSummary toSummary(DeploymentPluginPlan plan) {
        if (plan == null) {
            return null;
        }
        return new DeploymentPluginPlanSummary(
                plan.pluginId(),
                plan.pluginType() == null ? null : plan.pluginType().name(),
                plan.displayName(),
                plan.processLocatorPluginId(),
                plan.startupJudgePluginId(),
                plan.children() == null ? List.of() : plan.children().stream().map(this::toSummary).toList()
        );
    }

    private PluginProjectContext buildProjectContext(PipelineEntity pipeline, DeploymentEntity deployment) {
        return new PluginProjectContext(
                pipeline == null ? null : pipeline.getTemplateTypeSnapshot(),
                pipeline == null ? null : pipeline.getTemplateNameSnapshot(),
                pipeline != null && pipeline.getProject() != null ? pipeline.getProject().getName() : null,
                pipeline == null ? null : pipeline.getName(),
                deployment == null ? (pipeline == null ? null : pipeline.getDefaultBranch()) : deployment.getBranchName()
        );
    }

    private PluginRuntimeContext buildRuntimeContext(PipelineEntity pipeline) {
        return buildRuntimeContext(pipeline, null);
    }

    private PluginRuntimeContext buildRuntimeContext(PipelineEntity pipeline, String mavenSettingsFilePath) {
        List<String> buildRuntimeTypes = java.util.stream.Stream.of(
                        runtimeTypeName(pipeline == null ? null : pipeline.getJavaEnvironment()),
                        runtimeTypeName(pipeline == null ? null : pipeline.getNodeEnvironment()),
                        runtimeTypeName(pipeline == null ? null : pipeline.getMavenEnvironment())
                )
                .filter(item -> item != null && !item.isBlank())
                .toList();
        List<String> targetRuntimeTypes = java.util.stream.Stream.of(
                        runtimeTypeName(pipeline == null ? null : pipeline.getRuntimeJavaEnvironment())
                )
                .filter(item -> item != null && !item.isBlank())
                .toList();
        return new PluginRuntimeContext(
                buildRuntimeTypes,
                targetRuntimeTypes,
                mavenSettingsFilePath
        );
    }

    private PluginHostContext buildHostContext(HostEntity host) {
        return new PluginHostContext(
                host == null || host.getType() == null ? null : host.getType().name(),
                host != null && host.getType() == HostType.SSH,
                host == null ? null : host.getName(),
                host == null ? null : host.getWorkspaceRoot()
        );
    }

    private PluginVariableContext buildVariableContext(PipelineEntity pipeline, Map<String, String> variables) {
        String pluginId = resolvePluginId(pipeline);
        return new PluginVariableContext(
                JsonKit.write(variables == null ? Map.of() : variables),
                variables == null ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(variables)),
                parseTemplateVariables(
                        resolveTemplateVariablesSchema(pipeline),
                        pluginId
                )
        );
    }

    private List<PluginTemplateVariable> parseTemplateVariables(String variablesSchema, String pluginId) {
        if (variablesSchema == null || variablesSchema.isBlank()) {
            return List.of();
        }
        List<TemplateVariableSchemaItem> items = JsonKit.read(
                variablesSchema,
                new com.fasterxml.jackson.core.type.TypeReference<List<TemplateVariableSchemaItem>>() {
                }
        );
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        top.fusb.deploybot.plugin.api.deployment.DeploymentPlugin deploymentPlugin =
                pluginId == null || pluginId.isBlank() ? null : pluginRuntime.resolveDeploymentPlugin(pluginId);
        List<PluginVariableDefinition> definitions = deploymentPlugin == null ? List.of() : deploymentPlugin.variableDefinitions();
        return items.stream().map(item -> new PluginTemplateVariable(
                ObjectKit.stringValue(item.name()),
                ObjectKit.stringValue(item.phase()),
                enrichMutationRuleIds(
                        ObjectKit.stringValue(item.name()),
                        item.mutationRuleIds() == null ? List.of() : item.mutationRuleIds().stream()
                                .map(ObjectKit::stringValue)
                                .filter(value -> value != null && !value.isBlank())
                                .toList(),
                        parseTemplateVariableBinding(item.binding()),
                        definitions
                ),
                resolveBinding(
                        ObjectKit.stringValue(item.name()),
                        parseTemplateVariableBinding(item.binding()),
                        definitions
                )
        )).filter(item -> item.name() != null && !item.name().isBlank()).toList();
    }

    private List<String> enrichMutationRuleIds(
            String variableName,
            List<String> existingRuleIds,
            PluginTemplateVariableBinding binding,
            List<PluginVariableDefinition> definitions
    ) {
        java.util.LinkedHashSet<String> ruleIds = new java.util.LinkedHashSet<>();
        if (existingRuleIds != null) {
            ruleIds.addAll(existingRuleIds);
        }
        if (definitions == null || definitions.isEmpty()) {
            return List.copyOf(ruleIds);
        }
        for (PluginVariableDefinition definition : definitions) {
            if (definition == null) {
                continue;
            }
            if (variableName != null && variableName.equals(definition.key()) && definition.mutationRuleIds() != null) {
                ruleIds.addAll(definition.mutationRuleIds());
            }
            if (binding != null
                    && definition.binding() != null
                    && definition.binding().scope() == binding.scope()
                    && definition.binding().key().equals(binding.key())
                    && definition.mutationRuleIds() != null) {
                ruleIds.addAll(definition.mutationRuleIds());
            }
        }
        return List.copyOf(ruleIds);
    }

    private PluginTemplateVariableBinding resolveBinding(
            String variableName,
            PluginTemplateVariableBinding existingBinding,
            List<PluginVariableDefinition> definitions
    ) {
        if (existingBinding != null) {
            return existingBinding;
        }
        if (definitions == null || definitions.isEmpty() || variableName == null || variableName.isBlank()) {
            return null;
        }
        return definitions.stream()
                .filter(definition -> definition != null && variableName.equals(definition.key()) && definition.binding() != null)
                .map(PluginVariableDefinition::binding)
                .findFirst()
                .map(binding -> new PluginTemplateVariableBinding(binding.scope(), binding.key()))
                .orElse(null);
    }

    private PluginTemplateVariableBinding parseTemplateVariableBinding(TemplateVariableSchemaBinding binding) {
        if (binding == null) {
            return null;
        }
        String scopeValue = ObjectKit.stringValue(binding.scope());
        String key = ObjectKit.stringValue(binding.key());
        if (scopeValue == null || key == null) {
            return null;
        }
        try {
            return new PluginTemplateVariableBinding(PluginTemplateVariableBindingScope.valueOf(scopeValue), key);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private PluginServiceContext buildServiceContext(
            PipelineEntity pipeline,
            DeploymentEntity deployment,
            Map<String, String> variables,
            String runtimeLogPath,
            Long monitoredPid
    ) {
        return new PluginServiceContext(
                variables == null ? null : variables.get("serviceName"),
                variables == null ? null : variables.get("startCommand"),
                pipeline == null ? null : pipeline.getStartupKeyword(),
                pipeline == null || pipeline.getPluginConfig() == null ? Map.of() : pipeline.getPluginConfig(),
                runtimeLogPath,
                monitoredPid == null ? (deployment == null ? null : deployment.getMonitoredPid()) : monitoredPid
        );
    }

    private String runtimeTypeName(top.fusb.deploybot.model.RuntimeEnvironmentEntity environment) {
        if (environment == null || environment.getType() == null) {
            return null;
        }
        return environment.getType().name();
    }

    private String resolvePluginId(PipelineEntity pipeline) {
        return pipelineTemplateResolverService.resolvePluginId(pipeline);
    }

    private String resolveTemplateBuildScript(PipelineEntity pipeline) {
        PipelineTemplateResolverService.ResolvedPipelineTemplate template = pipelineTemplateResolverService.resolve(pipeline);
        return template == null ? null : template.buildScriptContent();
    }

    private String resolveTemplateDeployScript(PipelineEntity pipeline) {
        PipelineTemplateResolverService.ResolvedPipelineTemplate template = pipelineTemplateResolverService.resolve(pipeline);
        return template == null ? null : template.deployScriptContent();
    }

    private String resolveTemplateVariablesSchema(PipelineEntity pipeline) {
        PipelineTemplateResolverService.ResolvedPipelineTemplate template = pipelineTemplateResolverService.resolve(pipeline);
        return template == null ? null : template.variablesSchema();
    }

    private final class BridgeExecutionBridge implements ProcessLocatorExecutionBridge {
        private final HostEntity targetHost;

        private BridgeExecutionBridge(HostEntity targetHost) {
            this.targetHost = targetHost;
        }

        @Override
        public String runScript(boolean remoteHost, String script) {
            try {
                if (remoteHost && targetHost != null && targetHost.getType() == HostType.SSH) {
                    return hostService.executeRemoteScript(targetHost.getId(), script, 8);
                }
                return ProcessKit.runAndCapture(ProcessKit.bash(script)).output();
            } catch (Exception ex) {
                return "";
            }
        }

    }
}
