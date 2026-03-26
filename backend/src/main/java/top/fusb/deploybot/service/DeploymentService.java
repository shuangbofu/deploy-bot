package top.fusb.deploybot.service;

import top.fusb.deploybot.dto.DeploymentRequest;
import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.model.DeploymentEntity;
import top.fusb.deploybot.model.DeploymentStatus;
import top.fusb.deploybot.model.GitAuthType;
import top.fusb.deploybot.model.HostEntity;
import top.fusb.deploybot.model.PipelineEntity;
import top.fusb.deploybot.model.RuntimeEnvironmentEntity;
import top.fusb.deploybot.model.ServiceEntity;
import top.fusb.deploybot.runner.DeploymentRunner;
import top.fusb.deploybot.repo.DeploymentRepository;
import top.fusb.deploybot.repo.PipelineRepository;
import top.fusb.deploybot.repo.ServiceRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class DeploymentService {
    private static final Logger log = LoggerFactory.getLogger(DeploymentService.class);
    private static final String DEFAULT_TRIGGER_USER = "anonymous";
    private static final String BUILD_ARTIFACT_DIR = "artifacts";
    private static final String PID_DIR = "pids";

    private final DeploymentRepository deploymentRepository;
    private final PipelineRepository pipelineRepository;
    private final JsonMapper jsonMapper;
    private final ScriptTemplateService scriptTemplateService;
    private final DeploymentRunner deploymentRunner;
    private final ServiceRepository serviceRepository;
    private final SystemSettingsService systemSettingsService;
    private final GitCredentialService gitCredentialService;
    private final ServiceManager serviceManager;
    private final HostService hostService;
    private final Path defaultWorkspaceRoot;

    public DeploymentService(
            DeploymentRepository deploymentRepository,
            PipelineRepository pipelineRepository,
            JsonMapper jsonMapper,
            ScriptTemplateService scriptTemplateService,
            DeploymentRunner deploymentRunner,
            ServiceRepository serviceRepository,
            SystemSettingsService systemSettingsService,
            GitCredentialService gitCredentialService,
            ServiceManager serviceManager,
            HostService hostService,
            @Value("${deploybot.workspace-root:./runtime}") String workspaceRoot
    ) {
        this.deploymentRepository = deploymentRepository;
        this.pipelineRepository = pipelineRepository;
        this.jsonMapper = jsonMapper;
        this.scriptTemplateService = scriptTemplateService;
        this.deploymentRunner = deploymentRunner;
        this.serviceRepository = serviceRepository;
        this.systemSettingsService = systemSettingsService;
        this.gitCredentialService = gitCredentialService;
        this.serviceManager = serviceManager;
        this.hostService = hostService;
        this.defaultWorkspaceRoot = Path.of(workspaceRoot);
    }

    public List<DeploymentEntity> findAll() {
        return deploymentRepository.findAllByOrderByCreatedAtDesc();
    }

    public DeploymentEntity findById(Long id) {
        return deploymentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.DEPLOYMENT_NOT_FOUND));
    }

    @Transactional
    public DeploymentEntity create(DeploymentRequest request) {
        // 1. 读取并校验流水线。
        PipelineEntity pipeline = pipelineRepository.findById(request.pipelineId())
                .orElseThrow(() -> new BusinessException(ErrorSubCode.PIPELINE_NOT_FOUND));
        log.info("Creating deployment for pipeline {} with requested branch {}.", pipeline.getName(), request.branchName());
        if (Boolean.TRUE.equals(pipeline.getTemplate().getMonitorProcess())) {
            ServiceEntity stoppedService = serviceManager.stopManagedServiceBeforeDeploy(pipeline.getId());
            if (stoppedService != null) {
                log.info(
                        "流水线 '{}' 在新部署前已由系统停止旧服务：serviceId={}。",
                        pipeline.getName(),
                        stoppedService.getId()
                );
            }
        }
        List<DeploymentEntity> activeDeployments = deploymentRepository.findByPipelineIdAndStatusInOrderByCreatedAtDesc(
                pipeline.getId(),
                List.of(DeploymentStatus.PENDING, DeploymentStatus.RUNNING)
        );
        if (!activeDeployments.isEmpty()) {
            if (!Boolean.TRUE.equals(request.replaceRunning())) {
                throw new BusinessException(ErrorSubCode.PIPELINE_HAS_RUNNING_DEPLOYMENT);
            }
            activeDeployments.forEach(item -> deploymentRunner.stop(item.getId()));
        }

        Map<String, String> variables = new LinkedHashMap<>(jsonMapper.toStringMap(pipeline.getVariablesJson()));
        if (request.variableOverrides() != null) {
            variables.putAll(request.variableOverrides());
        }

        // 2. 计算本机构建目录与目标主机发布目录。
        Path buildWorkspaceRoot = resolveBuildWorkspaceRoot();
        Path deployWorkspaceRoot = resolveDeployWorkspaceRoot(pipeline.getTargetHost(), buildWorkspaceRoot);

        String branch = request.branchName() == null || request.branchName().isBlank()
                ? pipeline.getDefaultBranch()
                : request.branchName();

        variables.put("branch", branch);
        variables.put("gitUrl", gitCredentialService.resolveGitUrl(pipeline.getProject()));
        variables.put("gitRepositoryUrl", pipeline.getProject().getGitUrl());
        variables.put("projectName", pipeline.getProject().getName());
        variables.put("pipelineName", pipeline.getName());
        variables.put("workspaceRoot", buildWorkspaceRoot.toAbsolutePath().normalize().toString());
        variables.put("buildWorkspaceRoot", buildWorkspaceRoot.toAbsolutePath().normalize().toString());
        variables.put("deployWorkspaceRoot", deployWorkspaceRoot.toAbsolutePath().normalize().toString());
        applyBuildRuntimeEnvironmentVariables(variables, pipeline);
        log.info(
                "Resolved deployment {} context: buildWorkspaceRoot={}, deployWorkspaceRoot={}, targetHost={}, startupKeyword={}, startupTimeoutSeconds={}",
                pipeline.getName(),
                buildWorkspaceRoot.toAbsolutePath().normalize(),
                deployWorkspaceRoot.toAbsolutePath().normalize(),
                pipeline.getTargetHost() == null ? "本机" : pipeline.getTargetHost().getName(),
                pipeline.getStartupKeyword(),
                pipeline.getStartupTimeoutSeconds()
        );

        DeploymentEntity entity = new DeploymentEntity();
        entity.setPipeline(pipeline);
        entity.setBranchName(branch);
        entity.setVariablesJson(jsonMapper.write(variables));
        entity.setTriggeredBy(request.triggeredBy() == null || request.triggeredBy().isBlank() ? DEFAULT_TRIGGER_USER : request.triggeredBy());
        entity.setStatus(DeploymentStatus.PENDING);
        entity.setCreatedAt(LocalDateTime.now());
        deploymentRepository.save(entity);

        // 3. 补齐部署内置变量，并分别渲染构建脚本与发布脚本。
        variables.put("deploymentId", entity.getId().toString());
        if (requiresPidFileMonitoring(pipeline)) {
            variables.put("pidFilePath", deployWorkspaceRoot.resolve(PID_DIR).resolve("service-" + entity.getId() + ".pid").toAbsolutePath().normalize().toString());
        }
        Path localArtifactDir = buildWorkspaceRoot.resolve(BUILD_ARTIFACT_DIR).resolve("deploy-" + entity.getId()).toAbsolutePath().normalize();
        Path targetArtifactDir = deployWorkspaceRoot.resolve(BUILD_ARTIFACT_DIR).resolve("deploy-" + entity.getId()).toAbsolutePath().normalize();

        Map<String, String> buildVariables = new LinkedHashMap<>(variables);
        buildVariables.put("artifactDir", localArtifactDir.toString());
        buildVariables.put("workspaceRoot", buildWorkspaceRoot.toAbsolutePath().normalize().toString());

        Map<String, String> deployVariables = new LinkedHashMap<>(variables);
        deployVariables.put("artifactDir", targetArtifactDir.toString());
        deployVariables.put("workspaceRoot", deployWorkspaceRoot.toAbsolutePath().normalize().toString());
        applyDeployRuntimeEnvironmentVariables(deployVariables, pipeline);

        String buildTemplate = resolveBuildScriptTemplate(pipeline);
        String deployTemplate = resolveDeployScriptTemplate(pipeline);
        String renderedBuildScript = scriptTemplateService.render(buildTemplate, buildVariables);
        String renderedDeployScript = deployTemplate == null ? null : scriptTemplateService.render(deployTemplate, deployVariables);
        log.info(
                "流水线 '{}' 的脚本渲染完成：存在构建脚本={}，存在发布脚本={}，启用服务监测={}",
                pipeline.getName(),
                renderedBuildScript != null && !renderedBuildScript.isBlank(),
                renderedDeployScript != null && !renderedDeployScript.isBlank(),
                pipeline.getTemplate().getMonitorProcess()
        );

        entity.setRenderedBuildScript(buildRuntimeEnvironmentPreamble(buildVariables, pipeline, true) + renderedBuildScript);
        entity.setRenderedDeployScript(renderedDeployScript == null ? null : buildRuntimeEnvironmentPreamble(deployVariables, pipeline, false) + renderedDeployScript + buildDeployRuntimeDiagnostics(pipeline, deployVariables));
        entity.setVariablesJson(jsonMapper.write(buildVariables));
        entity = deploymentRepository.save(entity);
        log.info(
                "Deployment {} created for pipeline '{}' on host '{}'.",
                entity.getId(),
                pipeline.getName(),
                pipeline.getTargetHost() == null ? "本机" : pipeline.getTargetHost().getName()
        );

        Long deploymentId = entity.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deploymentRunner.runAsync(deploymentId);
            }
        });

        return entity;
    }

    /**
     * 构建始终在本机执行，因此优先读取“本机主机”的工作空间。
     */
    private Path resolveBuildWorkspaceRoot() {
        HostEntity localHost = hostService.ensureLocalHost();
        if (localHost.getWorkspaceRoot() != null && !localHost.getWorkspaceRoot().isBlank()) {
            return Path.of(localHost.getWorkspaceRoot().trim());
        }
        String configured = systemSettingsService.get().getWorkspaceRoot();
        if (configured == null || configured.isBlank()) {
            return defaultWorkspaceRoot;
        }
        return Path.of(configured.trim());
    }

    private Path resolveDeployWorkspaceRoot(HostEntity targetHost, Path buildWorkspaceRoot) {
        if (targetHost != null && targetHost.getWorkspaceRoot() != null && !targetHost.getWorkspaceRoot().isBlank()) {
            return Path.of(targetHost.getWorkspaceRoot().trim());
        }
        return buildWorkspaceRoot;
    }

    /**
     * 流水线中选择的运行环境只注入到本机构建阶段，避免误把远端发布机当成构建机。
     */
    private void applyBuildRuntimeEnvironmentVariables(Map<String, String> variables, PipelineEntity pipeline) {
        putEnvironmentVariables(variables, "JAVA", pipeline.getJavaEnvironment(), "JAVA_HOME");
        putEnvironmentVariables(variables, "NODE", pipeline.getNodeEnvironment(), "NODE_HOME");
        putEnvironmentVariables(variables, "MAVEN", pipeline.getMavenEnvironment(), "MAVEN_HOME");
    }

    private void applyDeployRuntimeEnvironmentVariables(Map<String, String> variables, PipelineEntity pipeline) {
        putEnvironmentVariables(variables, "JAVA", pipeline.getRuntimeJavaEnvironment(), "JAVA_HOME");
    }

    private void putEnvironmentVariables(Map<String, String> variables, String prefix, RuntimeEnvironmentEntity environment, String homeKey) {
        if (environment == null) {
            return;
        }
        variables.put(prefix + "_ENV_NAME", environment.getName());
        if (environment.getVersion() != null && !environment.getVersion().isBlank()) {
            variables.put(prefix + "_VERSION", environment.getVersion());
        }
        if (environment.getHomePath() != null && !environment.getHomePath().isBlank()) {
            variables.put(homeKey, environment.getHomePath());
        }
        if (environment.getBinPath() != null && !environment.getBinPath().isBlank()) {
            variables.put(prefix + "_BIN_PATH", environment.getBinPath());
        }
        if (environment.getActivationScript() != null && !environment.getActivationScript().isBlank()) {
            variables.put(prefix + "_ACTIVATION_SCRIPT", environment.getActivationScript());
        }
        Map<String, Object> extraEnvironment = jsonMapper.toObjectMap(environment.getEnvironmentJson());
        extraEnvironment.forEach((key, value) -> {
            if (value instanceof String stringValue) {
                variables.put(key, stringValue);
                return;
            }
            if (value instanceof Map<?, ?> mapValue) {
                Object rawValue = mapValue.get("value");
                if (rawValue != null) {
                    variables.put(key, Objects.toString(rawValue));
                }
                Object prependPath = mapValue.get("prependPath");
                if (Boolean.TRUE.equals(prependPath) && rawValue != null) {
                    variables.put(key + "__PREPEND_PATH", Objects.toString(rawValue));
                }
            }
        });
    }

    private String buildRuntimeEnvironmentPreamble(Map<String, String> variables, PipelineEntity pipeline, boolean buildStage) {
        List<String> lines = new ArrayList<>();
        lines.add("# Runtime environment preamble generated by Deploy Bot");
        if (buildStage) {
            lines.add("if [ -n \"" + valueOf(variables, "JAVA_HOME") + "\" ]; then");
            lines.add("  export JAVA_HOME=\"" + escapeShell(valueOf(variables, "JAVA_HOME")) + "\"");
            lines.add("fi");
            lines.add("if [ -n \"" + valueOf(variables, "MAVEN_HOME") + "\" ]; then");
            lines.add("  export MAVEN_HOME=\"" + escapeShell(valueOf(variables, "MAVEN_HOME")) + "\"");
            lines.add("fi");
            lines.add("if [ -n \"" + valueOf(variables, "NODE_HOME") + "\" ]; then");
            lines.add("  export NODE_HOME=\"" + escapeShell(valueOf(variables, "NODE_HOME")) + "\"");
            lines.add("fi");
            appendActivationScript(lines, "JAVA", variables);
            appendActivationScript(lines, "NODE", variables);
            appendActivationScript(lines, "MAVEN", variables);
        } else {
            lines.add("if [ -n \"" + valueOf(variables, "JAVA_HOME") + "\" ]; then");
            lines.add("  export JAVA_HOME=\"" + escapeShell(valueOf(variables, "JAVA_HOME")) + "\"");
            lines.add("fi");
            appendActivationScript(lines, "JAVA", variables);
        }

        StringBuilder pathBuilder = new StringBuilder();
        appendPath(pathBuilder, valueOf(variables, "JAVA_BIN_PATH"));
        if (buildStage) {
            appendPath(pathBuilder, valueOf(variables, "MAVEN_BIN_PATH"));
            appendPath(pathBuilder, valueOf(variables, "NODE_BIN_PATH"));
        }

        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String key = entry.getKey();
            if (!key.matches("[A-Z0-9_]+")) {
                continue;
            }
            if (List.of("JAVA_HOME", "MAVEN_HOME", "NODE_HOME", "JAVA_BIN_PATH", "MAVEN_BIN_PATH", "NODE_BIN_PATH").contains(key)) {
                continue;
            }
            if (key.endsWith("__PREPEND_PATH")) {
                pathBuilder.insert(0, escapeShell(entry.getValue()) + ":");
                continue;
            }
            lines.add("export " + key + "=\"" + escapeShell(entry.getValue()) + "\"");
        }

        if (pathBuilder.length() > 0) {
            lines.add("export PATH=\"" + escapeShell(pathBuilder.toString()) + "$PATH\"");
        }

        appendGitSshPreamble(lines, pipeline);

        lines.add("");
        return String.join("\n", lines);
    }

    private void appendPath(StringBuilder pathBuilder, String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        pathBuilder.append(escapeShell(path)).append(":");
    }

    private String valueOf(Map<String, String> variables, String key) {
        return variables.getOrDefault(key, "");
    }

    private String escapeShell(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void appendActivationScript(List<String> lines, String prefix, Map<String, String> variables) {
        String activationScript = valueOf(variables, prefix + "_ACTIVATION_SCRIPT");
        if (activationScript.isBlank()) {
            return;
        }
        lines.add("# Activate " + prefix + " environment");
        lines.add(activationScript);
    }

    private void appendGitSshPreamble(List<String> lines, PipelineEntity pipeline) {
        if (pipeline == null || pipeline.getProject() == null || pipeline.getProject().getGitAuthType() != GitAuthType.SSH) {
            return;
        }
        String privateKey = systemSettingsService.get().getGitSshPrivateKey();
        if (privateKey == null || privateKey.isBlank()) {
            return;
        }
        String publicKey = systemSettingsService.get().getGitSshPublicKey();
        String knownHosts = systemSettingsService.get().getGitSshKnownHosts();

        lines.add("# Prepare Git SSH credentials");
        lines.add("mkdir -p \"{{workspaceRoot}}/ssh/git\"");
        lines.add("cat > \"{{workspaceRoot}}/ssh/git/id_deploybot\" <<'__DEPLOYBOT_GIT_PRIVATE_KEY__'");
        lines.add(privateKey.strip());
        lines.add("__DEPLOYBOT_GIT_PRIVATE_KEY__");
        if (publicKey != null && !publicKey.isBlank()) {
            lines.add("cat > \"{{workspaceRoot}}/ssh/git/id_deploybot.pub\" <<'__DEPLOYBOT_GIT_PUBLIC_KEY__'");
            lines.add(publicKey.strip());
            lines.add("__DEPLOYBOT_GIT_PUBLIC_KEY__");
        }
        if (knownHosts != null && !knownHosts.isBlank()) {
            lines.add("cat > \"{{workspaceRoot}}/ssh/git/known_hosts\" <<'__DEPLOYBOT_GIT_KNOWN_HOSTS__'");
            lines.add(knownHosts.strip());
            lines.add("__DEPLOYBOT_GIT_KNOWN_HOSTS__");
            lines.add("export GIT_SSH_COMMAND=\"ssh -i \\\"{{workspaceRoot}}/ssh/git/id_deploybot\\\" -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes -o UserKnownHostsFile=\\\"{{workspaceRoot}}/ssh/git/known_hosts\\\"\"");
        } else {
            lines.add("export GIT_SSH_COMMAND=\"ssh -i \\\"{{workspaceRoot}}/ssh/git/id_deploybot\\\" -o IdentitiesOnly=yes -o StrictHostKeyChecking=no\"");
        }
        lines.add("chmod 600 \"{{workspaceRoot}}/ssh/git/id_deploybot\" || true");
    }

    private String resolveBuildScriptTemplate(PipelineEntity pipeline) {
        String buildScript = pipeline.getTemplate().getBuildScriptContent();
        if (buildScript == null || buildScript.isBlank()) {
            throw new BusinessException(ErrorSubCode.TEMPLATE_BUILD_SCRIPT_MISSING);
        }
        return buildScript;
    }

    private String resolveDeployScriptTemplate(PipelineEntity pipeline) {
        String deployScript = pipeline.getTemplate().getDeployScriptContent();
        if (deployScript == null || deployScript.isBlank()) {
            return null;
        }
        return deployScript;
    }

    @Transactional
    public DeploymentEntity stop(Long id) {
        DeploymentEntity entity = deploymentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.DEPLOYMENT_NOT_FOUND));
        deploymentRunner.stop(id);
        return deploymentRepository.findById(id).orElse(entity);
    }

    @Transactional
    public DeploymentEntity rollback(Long id) {
        DeploymentEntity source = deploymentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.DEPLOYMENT_NOT_FOUND));
        if (source.getStatus() == DeploymentStatus.PENDING || source.getStatus() == DeploymentStatus.RUNNING) {
            throw new BusinessException(ErrorSubCode.RUNNING_DEPLOYMENT_CANNOT_ROLLBACK);
        }
        if (source.getBackupPath() == null || source.getBackupPath().isBlank()) {
            throw new BusinessException(ErrorSubCode.DEPLOYMENT_BACKUP_MISSING);
        }

        PipelineEntity pipeline = source.getPipeline();
        Map<String, String> variables = new LinkedHashMap<>(jsonMapper.toStringMap(source.getVariablesJson()));
        Path workspaceRoot = resolveBuildWorkspaceRoot();

        variables.put("branch", source.getBranchName());
        variables.put("gitUrl", gitCredentialService.resolveGitUrl(pipeline.getProject()));
        variables.put("gitRepositoryUrl", pipeline.getProject().getGitUrl());
        variables.put("projectName", pipeline.getProject().getName());
        variables.put("pipelineName", pipeline.getName());
        variables.put("workspaceRoot", workspaceRoot.toAbsolutePath().normalize().toString());
        variables.put("rollbackBackupPath", source.getBackupPath());
        applyBuildRuntimeEnvironmentVariables(variables, pipeline);

        if (Boolean.TRUE.equals(pipeline.getTemplate().getMonitorProcess())) {
            serviceRepository.findFirstByPipelineId(pipeline.getId())
                    .map(ServiceEntity::getId)
                    .ifPresent(serviceManager::stop);
        }

        DeploymentEntity entity = new DeploymentEntity();
        entity.setPipeline(pipeline);
        entity.setBranchName(source.getBranchName());
        entity.setTriggeredBy("rollback");
        entity.setStatus(DeploymentStatus.PENDING);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setRollbackFromDeploymentId(source.getId());
        entity.setVariablesJson(jsonMapper.write(variables));
        deploymentRepository.save(entity);

        variables.put("deploymentId", entity.getId().toString());
        if (requiresPidFileMonitoring(pipeline)) {
            variables.put("pidFilePath", workspaceRoot.resolve("pids").resolve("service-" + entity.getId() + ".pid").toAbsolutePath().normalize().toString());
        }

        entity.setRenderedBuildScript(buildRuntimeEnvironmentPreamble(variables, pipeline, true) + buildRollbackScript(pipeline, variables));
        entity.setRenderedDeployScript(null);
        entity.setVariablesJson(jsonMapper.write(variables));
        entity = deploymentRepository.save(entity);

        Long deploymentId = entity.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deploymentRunner.runAsync(deploymentId);
            }
        });
        return entity;
    }

    @Transactional
    public DeploymentEntity startService(Long serviceId) {
        ServiceEntity service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.SERVICE_NOT_FOUND));
        DeploymentEntity source = service.getLastDeployment();
        if (source == null) {
            throw new BusinessException(ErrorSubCode.SERVICE_NO_DEPLOYMENT_TO_START);
        }

        Map<String, String> sourceVariables = new LinkedHashMap<>(jsonMapper.toStringMap(source.getVariablesJson()));
        List<String> builtInKeys = List.of("branch", "gitUrl", "projectName", "pipelineName", "workspaceRoot", "deploymentId", "pidFilePath", "gitRepositoryUrl", "rollbackBackupPath");
        Map<String, String> overrides = new LinkedHashMap<>();
        sourceVariables.forEach((key, value) -> {
            if (!builtInKeys.contains(key)) {
                overrides.put(key, value);
            }
        });

        return create(new DeploymentRequest(
                source.getPipeline().getId(),
                source.getBranchName(),
                "service-manager",
                overrides,
                true
        ));
    }

    public String readLog(Long id) throws IOException {
        DeploymentEntity entity = deploymentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.DEPLOYMENT_NOT_FOUND));
        log.debug("Reading deployment log for deployment {}.", id);
        if (entity.getLogPath() == null) {
            if (entity.getStatus() == DeploymentStatus.PENDING) {
                return "任务还在排队，尚未开始执行。";
            }
            if (entity.getStatus() == DeploymentStatus.STOPPED) {
                return "任务已手动停止，日志文件尚未生成。";
            }
            if (entity.getStatus() == DeploymentStatus.FAILED && entity.getErrorMessage() != null) {
                return "任务执行失败，且尚未生成日志。\n\n错误信息：%s".formatted(entity.getErrorMessage());
            }
            return "暂无日志输出。";
        }
        Path path = Path.of(entity.getLogPath());
        if (!Files.exists(path)) {
            return "日志文件尚未生成，请稍后刷新。";
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private String buildRollbackScript(PipelineEntity pipeline, Map<String, String> variables) {
        List<String> lines = new ArrayList<>();
        lines.add("#!/usr/bin/env bash");
        lines.add("set -e");
        lines.add("export PS4='+ $(date \"+%Y-%m-%d %H:%M:%S\") '");
        lines.add("set -x");
        lines.add("");
        lines.add("echo \"[回滚 1/4] 准备发布目录\"");
        lines.add("mkdir -p \"{{targetDir}}\"");
        lines.add("");
        lines.add("echo \"[回滚 2/4] 从备份恢复发布目录\"");
        lines.add("rsync -av --delete \"{{rollbackBackupPath}}/\" \"{{targetDir}}/\"");
        lines.add("");
        if (Boolean.TRUE.equals(pipeline.getTemplate().getMonitorProcess())) {
            lines.add("echo \"[回滚 3/4] 重新启动服务\"");
            lines.add("cd \"{{targetDir}}\"");
            if (requiresPidFileMonitoring(pipeline)) {
                lines.add("rm -f \"{{pidFilePath}}\" || true");
            }
            lines.add("{{startCommand}}");
            lines.add("");
        } else {
            lines.add("echo \"[回滚 3/4] 当前模板无需重启进程\"");
            lines.add("");
        }
        lines.add("echo \"[回滚 4/4] 回滚完成\"");
        lines.add("");
        return scriptTemplateService.render(String.join("\n", lines), variables);
    }

    private boolean requiresPidFileMonitoring(PipelineEntity pipeline) {
        if (!Boolean.TRUE.equals(pipeline.getTemplate().getMonitorProcess())) {
            return false;
        }
        String deployScript = pipeline.getTemplate().getDeployScriptContent();
        return deployScript != null && deployScript.contains("{{pidFilePath}}");
    }

    /**
     * 启动命令执行完成后，追加一段系统级诊断输出，方便直接在部署日志里确认：
     * 1. 启动关键字是否命中
     * 2. PID 文件是否生成
     * 3. app.log 是否已经开始写入
     */
    private String buildDeployRuntimeDiagnostics(PipelineEntity pipeline, Map<String, String> variables) {
        if (!Boolean.TRUE.equals(pipeline.getTemplate().getMonitorProcess())) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("echo \"[系统] 启动命令已执行，开始进行服务自检。\"");
        lines.add("sleep 1");
        lines.add("echo \"[系统] 当前工作目录：$(pwd)\"");
        String startupKeyword = pipeline.getStartupKeyword();
        if (startupKeyword != null && !startupKeyword.isBlank()) {
            lines.add("echo \"[系统] 启动关键字：" + escapeShell(startupKeyword) + "\"");
            lines.add("pgrep -af \"" + escapeShell(startupKeyword) + "\" || true");
        }
        if (requiresPidFileMonitoring(pipeline)) {
            lines.add("if [ -f \"{{pidFilePath}}\" ]; then");
            lines.add("  echo \"[系统] PID 文件内容：$(cat \"{{pidFilePath}}\")\"");
            lines.add("else");
            lines.add("  echo \"[系统] PID 文件尚未生成：{{pidFilePath}}\"");
            lines.add("fi");
        }
        String targetDir = variables.get("targetDir");
        if (targetDir != null && !targetDir.isBlank()) {
            lines.add("if [ -f \"" + escapeShell(targetDir) + "/app.log\" ]; then");
            lines.add("  echo \"[系统] app.log 文件状态：$(ls -l \"" + escapeShell(targetDir) + "/app.log\")\"");
            lines.add("  echo \"[系统] app.log 最新 20 行：\"");
            lines.add("  tail -n 20 \"" + escapeShell(targetDir) + "/app.log\" || true");
            lines.add("else");
            lines.add("  echo \"[系统] app.log 尚未生成。\"");
            lines.add("fi");
        }
        lines.add("");
        return scriptTemplateService.render(String.join("\n", lines), variables);
    }
}
