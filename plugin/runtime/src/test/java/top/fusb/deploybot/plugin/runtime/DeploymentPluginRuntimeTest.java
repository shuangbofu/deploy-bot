package top.fusb.deploybot.plugin.runtime;

import org.junit.jupiter.api.Test;
import top.fusb.deploybot.plugin.api.deployment.DeploymentPluginContext;
import top.fusb.deploybot.plugin.api.deployment.DeploymentPluginPlan;
import top.fusb.deploybot.plugin.api.deployment.context.PluginHostContext;
import top.fusb.deploybot.plugin.api.deployment.context.PluginProjectContext;
import top.fusb.deploybot.plugin.api.deployment.context.PluginRuntimeContext;
import top.fusb.deploybot.plugin.api.deployment.context.PluginServiceContext;
import top.fusb.deploybot.plugin.api.deployment.context.PluginVariableContext;
import top.fusb.deploybot.plugin.api.deployment.definition.DeploymentPluginDefinition;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableAssemblyContext;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableAssemblyPhase;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableAssemblyResult;
import top.fusb.deploybot.plugin.api.process.ProcessLocatorPlugin;
import top.fusb.deploybot.plugin.api.startup.StartupJudgeContext;
import top.fusb.deploybot.plugin.api.startup.StartupJudgeMatchMode;
import top.fusb.deploybot.plugin.api.startup.StartupJudgePlugin;
import top.fusb.deploybot.plugin.api.startup.StartupJudgeResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 覆盖插件运行时最核心的自动发现与能力解析链路。
 */
class DeploymentPluginRuntimeTest {

    @Test
    void shouldLoadBuiltinPluginsThroughServiceLoader() {
        DeploymentPluginRuntime runtime = new DeploymentPluginRuntime();

        List<DeploymentPluginDefinition> definitions = runtime.listDeploymentPluginDefinitions();

        assertFalse(definitions.isEmpty());
        assertTrue(definitions.stream().anyMatch(item -> "springboot-deployment".equals(item.descriptor().pluginId())));
        assertTrue(definitions.stream().anyMatch(item -> "node-static-deployment".equals(item.descriptor().pluginId())));
        assertTrue(definitions.stream().anyMatch(item -> "fullstack-deployment".equals(item.descriptor().pluginId())));
        DeploymentPluginDefinition springBoot = definitions.stream()
                .filter(item -> "springboot-deployment".equals(item.descriptor().pluginId()))
                .findFirst()
                .orElseThrow();
        assertFalse(springBoot.variableMutationRules().isEmpty());
        assertEquals(List.of("JAVA", "MAVEN"), springBoot.runtimeRequirement().buildRuntimeTypes());
        assertEquals(List.of("JAVA"), springBoot.runtimeRequirement().targetRuntimeTypes());
    }

    @Test
    void shouldResolveSpringBootPlanAndCapabilities() {
        DeploymentPluginRuntime runtime = new DeploymentPluginRuntime();
        DeploymentPluginContext context = new DeploymentPluginContext(
                "springboot-deployment",
                new PluginProjectContext("springboot", "Spring Boot 模板", "Deploy Bot", "部署平台", "main"),
                new PluginRuntimeContext(List.of("JAVA", "MAVEN"), List.of("JAVA"), null),
                new PluginHostContext("LOCAL", false, "本机", "/tmp"),
                new PluginVariableContext("{\"jarPath\":\"target/app.jar\"}", Map.of("jarPath", "target/app.jar"), List.of()),
                new PluginServiceContext(null, "java -jar target/app.jar", "Started DemoApplication in", null, null, null),
                "mvn clean package -DskipTests",
                "deploy.sh"
        );

        DeploymentPluginPlan plan = runtime.resolveDeploymentPlan(context);

        assertNotNull(plan);
        assertEquals("springboot-deployment", plan.pluginId());
        assertEquals("springboot-process-locator", plan.processLocatorPluginId());
        assertEquals("springboot-keyword-startup-judge", plan.startupJudgePluginId());

        ProcessLocatorPlugin processLocatorPlugin = runtime.resolveProcessLocator(plan);
        StartupJudgePlugin startupJudgePlugin = runtime.resolveStartupJudge(plan);

        assertNotNull(processLocatorPlugin);
        assertNotNull(startupJudgePlugin);
        assertEquals("springboot-process-locator", processLocatorPlugin.pluginId());
        assertEquals("springboot-keyword-startup-judge", startupJudgePlugin.pluginId());
    }

    @Test
    void shouldUseSpringBootStartedLogPatternWhenKeywordIsBlank() {
        DeploymentPluginRuntime runtime = new DeploymentPluginRuntime();
        DeploymentPluginPlan plan = DeploymentPluginPlan.leaf(
                "springboot-deployment",
                top.fusb.deploybot.plugin.api.deployment.DeploymentPluginType.JAVA_BACKEND_SERVICE,
                "Spring Boot",
                "springboot-process-locator",
                "springboot-keyword-startup-judge"
        );

        StartupJudgePlugin startupJudgePlugin = runtime.resolveStartupJudge(plan);
        StartupJudgeResult result = startupJudgePlugin.judge(new StartupJudgeContext(
                new PluginProjectContext("springboot", "Spring Boot 模板", "Deploy Bot", "部署平台", "main"),
                new PluginRuntimeContext(List.of("JAVA"), List.of("JAVA"), null),
                new PluginServiceContext(null, null, null, null, "/opt/app/app.log", 1001L)
        ));

        assertTrue(result.keywordRequired());
        assertEquals(StartupJudgeMatchMode.REGEX, result.matchMode());
        assertTrue("Started DemoApplication in 2.345 seconds".matches(".*" + result.keyword() + ".*"));
    }

    @Test
    void shouldResolveFullstackCompositePlan() {
        DeploymentPluginRuntime runtime = new DeploymentPluginRuntime();
        DeploymentPluginContext context = new DeploymentPluginContext(
                "fullstack-deployment",
                new PluginProjectContext("springboot_frontend", "前后端一体模板", "Deploy Bot", "部署平台", "main"),
                new PluginRuntimeContext(List.of("NODE", "JAVA", "MAVEN"), List.of("JAVA"), null),
                new PluginHostContext("LOCAL", false, "本机", "/tmp"),
                new PluginVariableContext("{}", Map.of(), List.of()),
                new PluginServiceContext(null, "java -jar app.jar", "Started DemoApplication in", null, null, null),
                "npm install && npm run build && mvn clean package -DskipTests",
                "deploy.sh"
        );

        DeploymentPluginPlan plan = runtime.resolveDeploymentPlan(context);

        assertNotNull(plan);
        assertTrue(plan.composite());
        assertEquals(2, plan.children().size());
        assertEquals("node-static-deployment", plan.children().get(0).pluginId());
        assertEquals("springboot-deployment", plan.children().get(1).pluginId());
    }

    @Test
    void shouldAssembleSpringBootVariables() {
        DeploymentPluginRuntime runtime = new DeploymentPluginRuntime();
        Map<String, String> buildVariables = new LinkedHashMap<>();
        buildVariables.put("buildCommand", "mvn clean package -DskipTests");
        PluginVariableAssemblyResult buildResult = runtime.assembleVariables(new PluginVariableAssemblyContext(
                PluginVariableAssemblyPhase.BUILD,
                "springboot-deployment",
                new PluginProjectContext("springboot", "Spring Boot 模板", "Deploy Bot", "部署平台", "main"),
                new PluginRuntimeContext(List.of("JAVA", "MAVEN"), List.of("JAVA"), "/tmp/maven-settings.xml"),
                new PluginVariableContext(null, buildVariables, List.of()),
                new PluginServiceContext(null, null, null, null, null, null)
        ));

        assertNotNull(buildResult);
        assertTrue(buildResult.variables().get("buildCommand").contains("-s \"/tmp/maven-settings.xml\""));

        Map<String, String> deployVariables = new LinkedHashMap<>();
        deployVariables.put("serviceName", "DeployBot");
        deployVariables.put("targetDir", "/opt/apps/demo");
        deployVariables.put("deploymentId", "42");
        deployVariables.put("deployWorkspaceRoot", "/opt/apps/demo");
        deployVariables.put("javaOpts", "-Xms256m -Xmx512m");
        deployVariables.put("javaSystemProperties", "-Dfile.encoding=UTF-8");
        deployVariables.put("applicationArgs", "--server.port=8080");
        deployVariables.put("pipelineTags", "测试,后端");
        PluginVariableAssemblyResult deployResult = runtime.assembleVariables(new PluginVariableAssemblyContext(
                PluginVariableAssemblyPhase.DEPLOY,
                "springboot-deployment",
                new PluginProjectContext("springboot", "Spring Boot 模板", "Deploy Bot", "部署平台", "main"),
                new PluginRuntimeContext(List.of("JAVA", "MAVEN"), List.of("JAVA"), null),
                new PluginVariableContext(null, deployVariables, List.of()),
                new PluginServiceContext(null, null, null, Map.of(
                        "springProfile", "test",
                        "runtimeConfigYaml", "server:\n  port: 8080"
                ), null, null)
        ));

        assertNotNull(deployResult);
        assertTrue(deployResult.variables().get("springBootArgs").contains("--spring.profiles.active=test"));
        assertTrue(deployResult.variables().get("springBootArgs").contains("--spring.config.additional-location=file:/opt/apps/demo/config/deploy-42.yml"));
        assertTrue(deployResult.variables().get("springBootArgs").contains("--deploybot.deployment-id='42'"));
        assertTrue(deployResult.variables().get("startCommand").contains("nohup java -Xms256m -Xmx512m -Dfile.encoding=UTF-8 -jar 'DeployBot.jar'"));
        assertFalse(deployResult.variables().get("startCommand").contains("LANG="));
        assertFalse(deployResult.variables().get("startCommand").contains("LC_ALL="));
        assertTrue(deployResult.variables().get("startCommand").contains("< /dev/null"));
        assertTrue(deployResult.variables().get("startCommand").contains("--spring.profiles.active=test"));
        assertTrue(deployResult.variables().get("startCommand").contains("--deploybot.pipeline-name='部署平台'"));
        assertTrue(deployResult.variables().get("startCommand").contains("--deploybot.pipeline-tags='测试,后端'"));
        assertTrue(deployResult.variables().get("startCommand").contains("--server.port=8080"));
        assertEquals(deployResult.variables().get("startCommand"), deployResult.variables().get("START_COMMAND"));
    }

    @Test
    void shouldKeepNodeStaticVariablesAsNoOpAssembly() {
        DeploymentPluginRuntime runtime = new DeploymentPluginRuntime();
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("buildCommand", "npm install && npm run build");
        variables.put("distDir", "dist");

        PluginVariableAssemblyResult result = runtime.assembleVariables(new PluginVariableAssemblyContext(
                PluginVariableAssemblyPhase.BUILD,
                "node-static-deployment",
                new PluginProjectContext("react", "前端模板", "Deploy Bot", "部署平台", "main"),
                new PluginRuntimeContext(List.of("NODE"), List.of(), null),
                new PluginVariableContext(null, variables, List.of()),
                new PluginServiceContext(null, null, null, null, null, null)
        ));

        assertNotNull(result);
        assertEquals("npm install && npm run build", result.variables().get("buildCommand"));
        assertTrue(result.notes().isEmpty());
    }
}
