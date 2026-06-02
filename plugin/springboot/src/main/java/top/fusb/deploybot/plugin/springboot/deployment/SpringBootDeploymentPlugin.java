package top.fusb.deploybot.plugin.springboot.deployment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;

import top.fusb.deploybot.plugin.api.deployment.DeploymentPluginContext;
import top.fusb.deploybot.plugin.api.deployment.DeploymentPluginPlan;
import top.fusb.deploybot.plugin.api.deployment.DeploymentPluginType;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableAssemblyContext;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableAssemblyPhase;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableAssemblyResult;
import top.fusb.deploybot.plugin.api.process.ProcessLocatorPlugin;
import top.fusb.deploybot.plugin.api.startup.StartupJudgePlugin;
import top.fusb.deploybot.plugin.common.deployment.AbstractManagedServiceDeploymentPlugin;
import top.fusb.deploybot.plugin.common.deployment.PluginVariableMutationSupport;
import top.fusb.deploybot.plugin.springboot.process.SpringBootProcessLocatorPlugin;
import top.fusb.deploybot.plugin.springboot.startup.SpringBootKeywordStartupJudgePlugin;
import top.fusb.deploybot.kit.ShellKit;
import top.fusb.deploybot.kit.TextKit;

/**
 * 面向 Spring Boot / Java Jar 服务的内置部署类型插件。
 * <p>
 * 插件流程：
 * 1. 通过插件定义 JSON 暴露 Spring Boot 默认模板、运行配置项和 Java/Maven 组件依赖；
 * 2. 构建阶段根据 Maven settings 配置改写 Maven 命令；
 * 3. 发布阶段生成 Spring Boot 运行参数、运行日志路径和平台部署身份参数；
 * 4. 由插件生成固定语义的 Java Jar 启动命令，用户只填写 JVM / 系统属性 / 应用参数等运行配置；
 * 5. 暴露 Java 进程发现插件和日志关键字启动判定插件，供部署编排层在发布后调用。
 * </p>
 */
public class SpringBootDeploymentPlugin extends AbstractManagedServiceDeploymentPlugin {

    private static final ProcessLocatorPlugin PROCESS_LOCATOR = new SpringBootProcessLocatorPlugin();
    private static final StartupJudgePlugin STARTUP_JUDGE = new SpringBootKeywordStartupJudgePlugin();
    private static final String DEFINITION_RESOURCE = "/plugin-definition/springboot-plugin.json";

    /**
     * Spring Boot 进程发现插件标识。
     */
    public static final String PROCESS_LOCATOR_PLUGIN_ID = "springboot-process-locator";

    /**
     * Spring Boot 启动判定插件标识。
     */
    public static final String STARTUP_JUDGE_PLUGIN_ID = "springboot-keyword-startup-judge";

    public static final String BUILD_MAVEN_SETTINGS_RULE_ID = "springboot.build.maven-settings";
    public static final String DEPLOY_SPRING_ARGS_RULE_ID = "springboot.deploy.spring-args";
    public static final String DEPLOY_START_COMMAND_RULE_ID = "springboot.deploy.start-command";

    /**
     * 返回插件唯一标识，平台会用它绑定默认模板、插件详情和部署计划。
     *
     * @return Spring Boot 部署插件标识
     */
    @Override
    public String pluginId() {
        return "springboot-deployment";
    }

    /**
     * 返回当前插件的资源定义文件，平台会从该 JSON 中读取默认模板、变量规则和运行环境依赖。
     *
     * @return Spring Boot 插件定义资源路径
     */
    @Override
    protected String definitionResourcePath() {
        return DEFINITION_RESOURCE;
    }

    /**
     * 声明该插件会参与变量组装的阶段。
     *
     * @return 构建阶段用于 Maven settings 注入，发布阶段用于启动参数和运行命令注入
     */
    @Override
    public EnumSet<PluginVariableAssemblyPhase> variableAssemblyPhases() {
        return EnumSet.of(PluginVariableAssemblyPhase.BUILD, PluginVariableAssemblyPhase.DEPLOY);
    }

    /**
     * 按当前阶段组装 Spring Boot 部署变量。
     * <p>
     * 构建阶段处理 Maven settings；发布阶段处理 Spring Profile、外部配置文件路径、
     * 运行日志路径和部署身份参数，最后生成受管启动命令。
     * </p>
     *
     * @param context 当前变量组装上下文
     * @return 已按插件规则改写后的变量集合和处理说明
     */
    @Override
    public PluginVariableAssemblyResult assembleVariables(PluginVariableAssemblyContext context) {
        Map<String, String> variables = new LinkedHashMap<>(context == null || context.variables() == null
                ? Map.of()
                : context.variables());
        List<String> notes = new java.util.ArrayList<>();
        if (context == null || context.phase() == null) {
            return new PluginVariableAssemblyResult(variables, List.copyOf(notes));
        }
        var buildMavenSettingsRule = variableMutationRules().stream()
                .filter(item -> BUILD_MAVEN_SETTINGS_RULE_ID.equals(item.ruleId()))
                .findFirst()
                .orElse(null);
        if (buildMavenSettingsRule != null && PluginVariableMutationSupport.matches(buildMavenSettingsRule, context, variables)) {
            String settingsFilePath = TextKit.trimToNull(context.mavenSettingsFilePath());
            if (settingsFilePath != null) {
                variables.put("mavenSettingsFilePath", settingsFilePath);
                variables.put("MAVEN_SETTINGS_FILE_PATH", settingsFilePath);
                rewriteMavenCommandVariable(variables, "buildCommand", settingsFilePath);
                rewriteMavenCommandVariable(variables, "backendBuildCommand", settingsFilePath);
                notes.add("已根据插件规则把 Maven settings.xml 路径注入构建命令。");
            }
        }
        if (context.phase() == PluginVariableAssemblyPhase.DEPLOY) {
            SpringBootPluginConfig pluginConfig = pluginConfig(context, SpringBootPluginConfig.class);
            ensureRuntimeLogPath(variables);
            String runtimeConfigYamlBase64 = encodeRuntimeConfigYaml(pluginConfig.runtimeConfigYaml());
            String runtimeConfigFilePath = resolveRuntimeConfigFilePath(variables, runtimeConfigYamlBase64);
            putIfPresent(variables, "runtimeConfigYamlBase64", runtimeConfigYamlBase64);
            putIfPresent(variables, "RUNTIME_CONFIG_YAML_BASE64", runtimeConfigYamlBase64);
            putIfPresent(variables, "runtimeConfigFilePath", runtimeConfigFilePath);
            putIfPresent(variables, "RUNTIME_CONFIG_FILE_PATH", runtimeConfigFilePath);
            augmentStartCommand(
                    variables,
                    context,
                    pluginConfig.springProfile(),
                    runtimeConfigYamlBase64,
                    runtimeConfigFilePath
            );
            buildManagedStartCommand(variables);
            if (TextKit.isNotBlank(variables.get("springBootArgs"))) {
                notes.add("已根据插件规则生成 Spring Boot 启动参数与受管启动命令。");
            }
        }
        return new PluginVariableAssemblyResult(variables, List.copyOf(notes));
    }

    /**
     * 返回插件匹配优先级。
     *
     * @return 数值越小优先级越高，Spring Boot 插件优先于更通用的前端静态站点插件
     */
    @Override
    public int order() {
        return 100;
    }

    /**
     * 判断当前部署上下文是否适用于 Spring Boot 插件。
     *
     * @param context 部署插件匹配上下文，包含模板类型、启动命令和运行环境类型
     * @return 匹配 Java 运行环境、Spring Boot 模板类型或 java -jar 启动命令时返回 {@code true}
     */
    @Override
    public boolean supports(DeploymentPluginContext context) {
        return runtimeTypeEquals(context, "JAVA")
                || templateContains(context, "springboot")
                || containsIgnoreCase(context.startCommand(), "java -jar");
    }

    /**
     * 生成 Spring Boot 部署计划。
     *
     * @param context 部署插件匹配上下文
     * @return 带 Java 进程发现和日志关键字启动判定能力的叶子计划
     */
    @Override
    public DeploymentPluginPlan plan(DeploymentPluginContext context) {
        return DeploymentPluginPlan.leaf(
                pluginId(),
                DeploymentPluginType.JAVA_BACKEND_SERVICE,
                "Spring Boot 后端服务",
                PROCESS_LOCATOR_PLUGIN_ID,
                STARTUP_JUDGE_PLUGIN_ID
        );
    }

    /**
     * 返回 Spring Boot 服务使用的进程发现插件。
     *
     * @return Java 进程发现插件实例
     */
    @Override
    public ProcessLocatorPlugin processLocatorPlugin() {
        return PROCESS_LOCATOR;
    }

    /**
     * 返回 Spring Boot 服务使用的启动判定插件。
     *
     * @return 日志关键字启动判定插件实例
     */
    @Override
    public StartupJudgePlugin startupJudgePlugin() {
        return STARTUP_JUDGE;
    }

    /**
     * 将 Maven settings.xml 注入 Maven 构建命令。
     *
     * @param variables 当前变量集合
     * @param key 需要改写的命令变量名
     * @param settingsFilePath Maven settings.xml 文件路径
     */
    private void rewriteMavenCommandVariable(Map<String, String> variables, String key, String settingsFilePath) {
        String command = variables.get(key);
        if (TextKit.isBlank(command)) {
            return;
        }
        if (!command.contains("mvn") || command.contains(" -s ") || command.contains("--settings")) {
            return;
        }
        variables.put(key, command.replaceAll("(^|\\s|&&|\\|\\|)(mvn)(?=\\s|$)", "$1$2 -s \"" + Matcher.quoteReplacement(settingsFilePath) + "\""));
    }

    /**
     * 根据发布上下文组装 Spring Boot 运行参数。
     * <p>
     * 这里不会读取用户启动命令，而是先把 Spring Profile、外部配置文件和平台部署身份参数收敛到参数槽位。
     * </p>
     *
     * @param variables 当前变量集合
     * @param context 当前变量组装上下文
     * @param springProfile 用户选择的 Spring Profile
     * @param runtimeConfigYamlBase64 用户填写的运行配置 YAML 内容
     * @param runtimeConfigFilePath 平台在部署时生成的运行配置文件路径
     */
    private void augmentStartCommand(
            Map<String, String> variables,
            PluginVariableAssemblyContext context,
            String springProfile,
            String runtimeConfigYamlBase64,
            String runtimeConfigFilePath
    ) {
        List<String> springArguments = new java.util.ArrayList<>();
        if (TextKit.isNotBlank(springProfile)) {
            springArguments.add("--spring.profiles.active=" + springProfile.trim());
        }
        if (TextKit.isNotBlank(runtimeConfigYamlBase64) && TextKit.isNotBlank(runtimeConfigFilePath)) {
            springArguments.add("--spring.config.additional-location=file:" + runtimeConfigFilePath.trim());
        }
        springArguments.addAll(buildDeploymentIdentityArguments(context, variables));
        if (springArguments.isEmpty()) {
            return;
        }
        applyApplicationArguments(variables, context, springArguments);
    }

    /**
     * 根据插件语义生成最终启动命令。
     * <p>
     * Spring Boot Jar 插件的命令形态固定为 {@code java ... -jar 服务名.jar ...}；
     * JVM 参数、系统属性和应用参数来自流水线运行配置，Spring 参数和部署身份参数由插件生成。
     * </p>
     *
     * @param variables 当前变量集合
     */
    private void buildManagedStartCommand(Map<String, String> variables) {
        String serviceName = TextKit.trimToNull(variables.get("serviceName"));
        String runtimeLogPath = TextKit.trimToNull(variables.get("runtimeLogPath"));
        if (serviceName == null || runtimeLogPath == null) {
            return;
        }
        String javaCommand = ShellKit.joinCommandFragments(
                "env LANG=C.UTF-8 LC_ALL=C.UTF-8 java",
                variables.get("javaOpts"),
                withDefaultEncodingProperties(variables.get("javaSystemProperties")),
                "-jar " + ShellKit.singleQuote(serviceName + ".jar"),
                variables.get("springBootArgs"),
                variables.get("applicationArgs")
        );
        String startCommand = ShellKit.joinCommandFragments(
                "nohup",
                javaCommand,
                "< /dev/null",
                "> " + ShellKit.singleQuote(runtimeLogPath),
                "2>&1 &"
        );
        variables.put("startCommand", startCommand);
        variables.put("START_COMMAND", startCommand);
    }

    private String withDefaultEncodingProperties(String javaSystemProperties) {
        String properties = TextKit.trimToNull(javaSystemProperties);
        java.util.List<String> defaults = new java.util.ArrayList<>();
        if (!containsJvmProperty(properties, "file.encoding")) {
            defaults.add("-Dfile.encoding=UTF-8");
        }
        if (!containsJvmProperty(properties, "sun.jnu.encoding")) {
            defaults.add("-Dsun.jnu.encoding=UTF-8");
        }
        return ShellKit.joinCommandFragments(properties, String.join(" ", defaults));
    }

    private boolean containsJvmProperty(String properties, String key) {
        return properties != null && java.util.regex.Pattern
                .compile("(^|\\s)-D" + java.util.regex.Pattern.quote(key) + "=")
                .matcher(properties)
                .find();
    }

    /**
     * 补齐平台接管运行日志时使用的日志路径。
     *
     * @param variables 当前变量集合
     */
    private void ensureRuntimeLogPath(Map<String, String> variables) {
        if (TextKit.isNotBlank(variables.get("runtimeLogPath"))) {
            return;
        }
        String targetDir = TextKit.trimToNull(variables.get("targetDir"));
        String serviceName = TextKit.trimToNull(variables.get("serviceName"));
        String deploymentId = TextKit.trimToNull(variables.get("deploymentId"));
        if (targetDir == null || serviceName == null) {
            return;
        }
        variables.put("runtimeLogPath", targetDir + "/" + "deploy-" + deploymentId + ".log");
    }

    /**
     * 把 Spring Boot 插件自己的 application.yml 配置转成脚本可写入文件的 Base64 内容。
     *
     * @param runtimeConfigYaml 插件运行配置中的 application.yml 原文
     * @return Base64 后的 YAML 内容；未填写时返回空字符串
     */
    private String encodeRuntimeConfigYaml(String runtimeConfigYaml) {
        return TextKit.isBlank(runtimeConfigYaml)
                ? ""
                : Base64.getEncoder().encodeToString(runtimeConfigYaml.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 为 Spring Boot 外部配置文件生成本次部署专属路径。
     *
     * @param variables 当前变量集合
     * @param runtimeConfigYamlBase64 已编码的 YAML 内容
     * @return 目标主机上的 application.yml 文件路径；不需要生成时返回空字符串
     */
    private String resolveRuntimeConfigFilePath(Map<String, String> variables, String runtimeConfigYamlBase64) {
        if (TextKit.isBlank(runtimeConfigYamlBase64)) {
            return "";
        }
        String deployWorkspaceRoot = TextKit.trimToNull(variables.get("deployWorkspaceRoot"));
        String deploymentId = TextKit.trimToNull(variables.get("deploymentId"));
        if (deployWorkspaceRoot == null || deploymentId == null) {
            return "";
        }
        return Path.of(deployWorkspaceRoot)
                .resolve("config")
                .resolve("deploy-" + deploymentId + ".yml")
                .toAbsolutePath()
                .normalize()
                .toString();
    }

    /**
     * 写入非空变量，并保持插件变量生成逻辑集中在插件内部。
     *
     * @param variables 当前变量集合
     * @param key 变量名
     * @param value 变量值
     */
    private void putIfPresent(Map<String, String> variables, String key, String value) {
        if (TextKit.isNotBlank(value)) {
            variables.put(key, value);
        }
    }

    /**
     * 将通用应用启动参数写入 Spring Boot 专用参数槽位。
     *
     * @param variables 当前变量集合
     * @param context 当前变量组装上下文
     * @param arguments 已完成 shell 安全转义的启动参数列表
     */
    @Override
    protected void applyApplicationArguments(
            Map<String, String> variables,
            PluginVariableAssemblyContext context,
            List<String> arguments
    ) {
        if (arguments == null || arguments.isEmpty()) {
            return;
        }
        variables.put("springBootArgs", String.join(" ", arguments));
    }

}
