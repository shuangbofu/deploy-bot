package top.fusb.deploybot.plugin.common.deployment;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import top.fusb.deploybot.kit.TextKit;
import top.fusb.deploybot.plugin.api.deployment.DeploymentPlugin;
import top.fusb.deploybot.plugin.api.deployment.DeploymentPluginContext;
import top.fusb.deploybot.plugin.api.deployment.context.PluginTemplateVariableBinding;
import top.fusb.deploybot.plugin.api.deployment.definition.PluginRuntimeRequirement;
import top.fusb.deploybot.plugin.api.deployment.form.PluginFormSchema;
import top.fusb.deploybot.plugin.api.deployment.template.PluginBuiltinTemplate;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableAssemblyContext;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableAssemblyPhase;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableAssemblyResult;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableDefinition;
import top.fusb.deploybot.plugin.common.deployment.config.DeploymentPluginConfig;
import top.fusb.deploybot.plugin.common.deployment.config.PluginBuiltinTemplateConfig;

/**
 * 内置部署类型插件的抽象基类。
 */
public abstract class AbstractDeploymentPlugin implements DeploymentPlugin {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");
    private static final java.util.Set<String> RESERVED_VARIABLES = java.util.Set.of(
            "branch",
            "gitUrl",
            "gitRepositoryUrl",
            "projectId",
            "projectName",
            "pipelineId",
            "pipelineName",
            "serviceName",
            "targetDir",
            "buildWorkspaceRoot",
            "buildSourceDir",
            "deployWorkspaceRoot",
            "deploymentId",
            "artifactDir",
            "sourceArtifactPath"
    );

    private volatile DeploymentPluginConfig cachedConfig;
    private volatile String cachedBannerText;

    private record BuiltinTemplateVariableSchemaEntry(
            String name,
            String label,
            String placeholder,
            Boolean required,
            Boolean pipelineInput,
            String phase,
            List<String> mutationRuleIds,
            PluginTemplateVariableBinding binding
    ) {
    }

    @Override
    public PluginRuntimeRequirement runtimeRequirement() {
        DeploymentPluginConfig config = pluginConfig();
        return config == null || config.runtimeRequirement() == null
                ? new PluginRuntimeRequirement(List.of(), List.of())
                : config.runtimeRequirement();
    }

    @Override
    public PluginFormSchema pipelineFormSchema() {
        DeploymentPluginConfig config = pluginConfig();
        return config == null || config.pipelineFormSchema() == null
                ? new PluginFormSchema(List.of())
                : config.pipelineFormSchema();
    }

    @Override
    public List<PluginVariableDefinition> variableDefinitions() {
        DeploymentPluginConfig config = pluginConfig();
        return config == null || config.variableDefinitions() == null
                ? List.of()
                : config.variableDefinitions();
    }

    @Override
    public java.util.List<top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableMutationRule> variableMutationRules() {
        DeploymentPluginConfig config = pluginConfig();
        return config == null || config.variableMutationRules() == null
                ? List.of()
                : config.variableMutationRules();
    }

    @Override
    public List<PluginBuiltinTemplate> builtinTemplates() {
        DeploymentPluginConfig config = pluginConfig();
        if (config == null || config.builtinTemplates() == null) {
            return List.of();
        }
        return config.builtinTemplates().stream().map(this::toBuiltinTemplate).toList();
    }

    @Override
    public String bannerText() {
        String local = cachedBannerText;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cachedBannerText == null) {
                DeploymentPluginConfig config = pluginConfig();
                if (config == null || TextKit.isBlank(config.bannerResource())) {
                    cachedBannerText = "";
                    return cachedBannerText;
                }
                String bannerResource = config.bannerResource();
                cachedBannerText = loadOptionalTextResource(bannerResource);
            }
            return cachedBannerText;
        }
    }

    @Override
    public EnumSet<PluginVariableAssemblyPhase> variableAssemblyPhases() {
        return EnumSet.noneOf(PluginVariableAssemblyPhase.class);
    }

    @Override
    public PluginVariableAssemblyResult assembleVariables(PluginVariableAssemblyContext context) {
        return new PluginVariableAssemblyResult(
                context == null || context.variables() == null ? java.util.Map.of() : java.util.Map.copyOf(context.variables()),
                List.of()
        );
    }

    /**
     * 将平台透传的插件配置转换为当前插件自己的强类型配置对象。
     * <p>
     * 平台层只保存和传递 Map，不理解配置字段含义；插件实现通过该方法统一完成 Map 到配置类的转换，
     * 避免每个插件重复编写 {@code config.get("xxx")} 解析逻辑。
     * </p>
     *
     * @param context 当前变量组装上下文
     * @param configType 插件配置类型
     * @param <T> 插件配置泛型
     * @return 插件配置对象
     */
    protected <T> T pluginConfig(PluginVariableAssemblyContext context, Class<T> configType) {
        Map<String, String> config = context == null || context.config() == null ? Map.of() : context.config();
        return OBJECT_MAPPER.convertValue(config, configType);
    }

    @Override
    public top.fusb.deploybot.plugin.api.deployment.definition.PluginDescriptor descriptor() {
        DeploymentPluginConfig config = pluginConfig();
        if (config == null || config.descriptor() == null) {
            throw new IllegalStateException("插件 " + getClass().getName() + " 未提供 descriptor 定义。");
        }
        return config.descriptor();
    }

    /**
     * 返回当前插件的静态定义资源路径。
     *
     * @return 定义 JSON 的 classpath 路径
     */
    protected String definitionResourcePath() {
        return null;
    }

    /**
     * 返回当前插件解析后的静态定义。
     *
     * @return 插件静态定义；若未声明则返回 {@code null}
     */
    protected DeploymentPluginConfig pluginConfig() {
        String resourcePath = definitionResourcePath();
        if (TextKit.isBlank(resourcePath)) {
            return null;
        }
        DeploymentPluginConfig local = cachedConfig;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cachedConfig == null) {
                cachedConfig = loadPluginConfig(resourcePath);
            }
            return cachedConfig;
        }
    }

    /**
     * 从当前插件 jar 包的 resources 中读取静态插件定义。
     *
     * @param resourcePath 插件定义资源路径
     * @return 插件静态定义
     */
    protected DeploymentPluginConfig loadPluginConfig(String resourcePath) {
        try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("未找到插件定义资源：" + resourcePath);
            }
            return OBJECT_MAPPER.readValue(inputStream, DeploymentPluginConfig.class);
        } catch (Exception ex) {
            throw new IllegalStateException("读取插件定义失败：" + resourcePath + "，原因：" + ex.getMessage(), ex);
        }
    }

    private PluginBuiltinTemplate toBuiltinTemplate(PluginBuiltinTemplateConfig template) {
        String buildScriptContent = loadTextResource(template.buildScriptResource());
        String deployScriptContent = loadTextResource(template.deployScriptResource());
        String variablesSchema = enrichVariablesSchema(buildScriptContent, deployScriptContent, template.variablesSchema());
        return new PluginBuiltinTemplate(
                template.templateKey(),
                template.name(),
                template.description(),
                template.templateType(),
                buildScriptContent,
                deployScriptContent,
                variablesSchema,
                template.monitorProcess()
        );
    }

    private String loadTextResource(String resourcePath) {
        if (TextKit.isBlank(resourcePath)) {
            return "";
        }
        String content = loadOptionalTextResource(resourcePath);
        if (content != null) {
            return content;
        }
        throw new IllegalStateException("未找到插件脚本资源：" + resourcePath);
    }

    private String loadOptionalTextResource(String resourcePath) {
        if (TextKit.isBlank(resourcePath)) {
            return null;
        }
        try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                return null;
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("读取插件脚本资源失败：" + resourcePath, ex);
        }
    }

    private String enrichVariablesSchema(String buildScriptContent, String deployScriptContent, String variablesSchema) {
        Map<String, BuiltinTemplateVariableSchemaEntry> schemaMap = parseExistingSchema(variablesSchema);
        Map<String, String> inferredPhases = inferVariablePhases(buildScriptContent, deployScriptContent);
        if (schemaMap.isEmpty() && inferredPhases.isEmpty()) {
            return "[]";
        }
        Map<String, PluginVariableDefinition> pluginVariableMap = new LinkedHashMap<>();
        variableDefinitions().forEach(item -> pluginVariableMap.put(item.key(), item));
        Map<String, BuiltinTemplateVariableSchemaEntry> merged = new LinkedHashMap<>(schemaMap);
        inferredPhases.forEach((name, phase) -> {
            PluginVariableDefinition definition = pluginVariableMap.get(name);
            merged.compute(name, (k, existing) -> new BuiltinTemplateVariableSchemaEntry(
                    name,
                    TextKit.isNotBlank(existing == null ? null : existing.label())
                            ? existing.label()
                            : (definition == null ? name : definition.label()),
                    TextKit.isNotBlank(existing == null ? null : existing.placeholder())
                            ? existing.placeholder()
                            : (definition == null ? null : definition.defaultValue()),
                    existing != null && existing.required() != null
                            ? existing.required()
                            : (definition != null && definition.required()),
                    Boolean.TRUE,
                    TextKit.isNotBlank(existing == null ? null : existing.phase())
                            ? existing.phase()
                            : phase,
                    existing != null && existing.mutationRuleIds() != null && !existing.mutationRuleIds().isEmpty()
                            ? existing.mutationRuleIds()
                            : (definition == null || definition.mutationRuleIds() == null ? List.of() : definition.mutationRuleIds()),
                    existing != null && existing.binding() != null
                            ? existing.binding()
                            : (definition == null ? null : definition.binding())
            ));
        });
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(new ArrayList<>(merged.values()));
        } catch (Exception ex) {
            throw new IllegalStateException("拼装插件默认模板变量定义失败。", ex);
        }
    }

    private Map<String, BuiltinTemplateVariableSchemaEntry> parseExistingSchema(String variablesSchema) {
        if (TextKit.isBlank(variablesSchema)) {
            return Map.of();
        }
        try {
            List<BuiltinTemplateVariableSchemaEntry> entries = OBJECT_MAPPER.readValue(variablesSchema,
                    OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, BuiltinTemplateVariableSchemaEntry.class));
            Map<String, BuiltinTemplateVariableSchemaEntry> map = new LinkedHashMap<>();
            for (BuiltinTemplateVariableSchemaEntry entry : entries) {
                if (entry != null && TextKit.isNotBlank(entry.name())) {
                    map.put(entry.name(), entry);
                }
            }
            return map;
        } catch (Exception ex) {
            throw new IllegalStateException("解析插件默认模板变量定义失败。", ex);
        }
    }

    private Map<String, String> inferVariablePhases(String buildScriptContent, String deployScriptContent) {
        Map<String, String> phases = new LinkedHashMap<>();
        registerVariablePhase(phases, buildScriptContent, "build");
        registerVariablePhase(phases, deployScriptContent, "deploy");
        return phases;
    }

    private void registerVariablePhase(Map<String, String> phases, String content, String phase) {
        if (TextKit.isBlank(content)) {
            return;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(content);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (TextKit.isBlank(name) || RESERVED_VARIABLES.contains(name)) {
                continue;
            }
            String previous = phases.get(name);
            if (previous == null) {
                phases.put(name, phase);
            } else if (!previous.equals(phase)) {
                phases.put(name, "shared");
            }
        }
    }

    /**
     * 判断文本是否包含关键字，忽略大小写。
     *
     * @param source 待检查文本
     * @param candidate 候选关键字
     * @return 包含则返回 {@code true}
     */
    protected boolean containsIgnoreCase(String source, String candidate) {
        if (source == null || candidate == null) {
            return false;
        }
        return source.toLowerCase().contains(candidate.toLowerCase());
    }

    /**
     * 判断上下文中的模板类型是否包含指定关键字。
     *
     * @param context 部署插件决策上下文
     * @param keyword 模板类型关键字
     * @return 包含则返回 {@code true}
     */
    protected boolean templateContains(DeploymentPluginContext context, String keyword) {
        return containsIgnoreCase(context.templateType(), keyword);
    }

    /**
     * 判断上下文中的运行时类型是否等于指定值，忽略大小写。
     *
     * @param context 部署插件决策上下文
     * @param runtimeType 运行时类型
     * @return 相等则返回 {@code true}
     */
    protected boolean runtimeTypeEquals(DeploymentPluginContext context, String runtimeType) {
        if (context.runtimeEnvironmentType() == null || runtimeType == null) {
            return false;
        }
        return context.runtimeEnvironmentType().equalsIgnoreCase(runtimeType);
    }
}
