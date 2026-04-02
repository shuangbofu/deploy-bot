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
import top.fusb.deploybot.repo.UserRepository;
import top.fusb.deploybot.security.AuthContextHolder;
import top.fusb.deploybot.security.AuthenticatedUser;
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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DeploymentService {
    private static final Logger log = LoggerFactory.getLogger(DeploymentService.class);
    private static final String DEFAULT_TRIGGER_USER = "anonymous";
    private static final String BUILD_ARTIFACT_DIR = "artifacts";
    private static final Pattern REDIRECTION_PATTERN = Pattern.compile("\\s(?:\\d?>>?|>>?)");

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
    private final UserRepository userRepository;
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
            UserRepository userRepository,
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
        this.userRepository = userRepository;
        this.defaultWorkspaceRoot = Path.of(workspaceRoot);
    }

    public List<DeploymentEntity> findAll() {
        requireCurrentUser();
        return enrichTriggeredByDisplayNames(deploymentRepository.findAllByOrderByCreatedAtDesc());
    }

    public DeploymentEntity findById(Long id) {
        DeploymentEntity entity = deploymentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.DEPLOYMENT_NOT_FOUND));
        ensureDeploymentReadable(entity);
        return enrichTriggeredByDisplayName(entity);
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
        variables.put("applicationName", resolveApplicationName(pipeline));
        variables.put("springProfile", pipeline.getSpringProfile() == null ? "" : pipeline.getSpringProfile());
        variables.put("runtimeConfigYaml", pipeline.getRuntimeConfigYaml() == null ? "" : pipeline.getRuntimeConfigYaml());
        variables.put("runtimeConfigYamlBase64", encodeRuntimeConfigYaml(pipeline.getRuntimeConfigYaml()));
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
        entity.setExecutionSnapshotJson(buildExecutionSnapshotJson(pipeline, branch, variables));
        AuthenticatedUser currentUser = requireCurrentUser();
        entity.setTriggeredBy(currentUser.username() == null || currentUser.username().isBlank() ? DEFAULT_TRIGGER_USER : currentUser.username());
        entity.setStatus(DeploymentStatus.PENDING);
        entity.setCreatedAt(LocalDateTime.now());
        deploymentRepository.save(entity);

        // 3. 补齐部署内置变量，并分别渲染构建脚本与发布脚本。
        variables.put("deploymentId", entity.getId().toString());
        Path localArtifactDir = buildWorkspaceRoot.resolve(BUILD_ARTIFACT_DIR).resolve("deploy-" + entity.getId()).toAbsolutePath().normalize();
        Path targetArtifactDir = deployWorkspaceRoot.resolve(BUILD_ARTIFACT_DIR).resolve("deploy-" + entity.getId()).toAbsolutePath().normalize();

        Map<String, String> buildVariables = new LinkedHashMap<>(variables);
        buildVariables.put("artifactDir", localArtifactDir.toString());
        buildVariables.put("workspaceRoot", buildWorkspaceRoot.toAbsolutePath().normalize().toString());

        Map<String, String> deployVariables = new LinkedHashMap<>(variables);
        deployVariables.put("artifactDir", targetArtifactDir.toString());
        deployVariables.put("workspaceRoot", deployWorkspaceRoot.toAbsolutePath().normalize().toString());
        deployVariables.put("runtimeConfigFilePath", deployWorkspaceRoot.resolve("config").resolve("deploy-" + entity.getId() + ".yml").toAbsolutePath().normalize().toString());
        applyDeployRuntimeEnvironmentVariables(deployVariables, pipeline);
        augmentStartCommand(deployVariables);

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

        entity.setArtifactPath(localArtifactDir.toString());
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

        appendGitSshPreamble(lines, pipeline, variables);

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

    private void appendGitSshPreamble(List<String> lines, PipelineEntity pipeline, Map<String, String> variables) {
        if (pipeline == null || pipeline.getProject() == null || pipeline.getProject().getGitAuthType() != GitAuthType.SSH) {
            return;
        }
        String workspaceRoot = valueOf(variables, "workspaceRoot");
        if (workspaceRoot.isBlank()) {
            return;
        }
        String gitSshDir = escapeShell(Path.of(workspaceRoot).resolve("ssh").resolve("git").toString());
        String privateKey = systemSettingsService.get().getGitSshPrivateKey();
        if (privateKey == null || privateKey.isBlank()) {
            return;
        }
        String publicKey = systemSettingsService.get().getGitSshPublicKey();
        String knownHosts = systemSettingsService.get().getGitSshKnownHosts();

        lines.add("# Prepare Git SSH credentials");
        lines.add("mkdir -p \"" + gitSshDir + "\"");
        lines.add("cat > \"" + gitSshDir + "/id_deploybot\" <<'__DEPLOYBOT_GIT_PRIVATE_KEY__'");
        lines.add(privateKey.strip());
        lines.add("__DEPLOYBOT_GIT_PRIVATE_KEY__");
        if (publicKey != null && !publicKey.isBlank()) {
            lines.add("cat > \"" + gitSshDir + "/id_deploybot.pub\" <<'__DEPLOYBOT_GIT_PUBLIC_KEY__'");
            lines.add(publicKey.strip());
            lines.add("__DEPLOYBOT_GIT_PUBLIC_KEY__");
        }
        if (knownHosts != null && !knownHosts.isBlank()) {
            lines.add("cat > \"" + gitSshDir + "/known_hosts\" <<'__DEPLOYBOT_GIT_KNOWN_HOSTS__'");
            lines.add(knownHosts.strip());
            lines.add("__DEPLOYBOT_GIT_KNOWN_HOSTS__");
            lines.add("cat > \"" + gitSshDir + "/git-ssh-wrapper.sh\" <<'__DEPLOYBOT_GIT_SSH_WRAPPER__'");
            lines.add("#!/usr/bin/env bash");
            lines.add("set -e");
            lines.add("exec ssh -i \"" + gitSshDir + "/id_deploybot\" -o IdentitiesOnly=yes -o PreferredAuthentications=publickey -o PasswordAuthentication=no -o KbdInteractiveAuthentication=no -o StrictHostKeyChecking=yes -o UserKnownHostsFile=\"" + gitSshDir + "/known_hosts\" \"$@\"");
            lines.add("__DEPLOYBOT_GIT_SSH_WRAPPER__");
        } else {
            lines.add("cat > \"" + gitSshDir + "/git-ssh-wrapper.sh\" <<'__DEPLOYBOT_GIT_SSH_WRAPPER__'");
            lines.add("#!/usr/bin/env bash");
            lines.add("set -e");
            lines.add("exec ssh -i \"" + gitSshDir + "/id_deploybot\" -o IdentitiesOnly=yes -o PreferredAuthentications=publickey -o PasswordAuthentication=no -o KbdInteractiveAuthentication=no -o StrictHostKeyChecking=no \"$@\"");
            lines.add("__DEPLOYBOT_GIT_SSH_WRAPPER__");
        }
        lines.add("chmod 600 \"" + gitSshDir + "/id_deploybot\" || true");
        lines.add("chmod 700 \"" + gitSshDir + "/git-ssh-wrapper.sh\" || true");
        lines.add("export GIT_SSH=\"" + gitSshDir + "/git-ssh-wrapper.sh\"");
        lines.add("export GIT_TERMINAL_PROMPT=0");
        lines.add("export GIT_ASKPASS=echo");
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
        ensureDeploymentManageable(entity);
        if (entity.getStatus() != DeploymentStatus.PENDING && entity.getStatus() != DeploymentStatus.RUNNING) {
            throw new BusinessException(ErrorSubCode.DEPLOYMENT_NOT_STOPPABLE);
        }
        deploymentRunner.stop(id);
        return deploymentRepository.findById(id).orElse(entity);
    }

    @Transactional
    public DeploymentEntity rollback(Long id) {
        DeploymentEntity source = deploymentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.DEPLOYMENT_NOT_FOUND));
        ensureDeploymentManageable(source);
        if (source.getStatus() == DeploymentStatus.PENDING || source.getStatus() == DeploymentStatus.RUNNING) {
            throw new BusinessException(ErrorSubCode.RUNNING_DEPLOYMENT_CANNOT_ROLLBACK);
        }
        if (source.getStatus() != DeploymentStatus.SUCCESS) {
            throw new BusinessException(ErrorSubCode.DEPLOYMENT_ROLLBACK_ONLY_SUCCESS);
        }
        if (source.getArtifactPath() == null || source.getArtifactPath().isBlank()) {
            throw new BusinessException(ErrorSubCode.DEPLOYMENT_ARTIFACT_MISSING);
        }

        PipelineEntity pipeline = source.getPipeline();
        Map<String, String> variables = new LinkedHashMap<>(jsonMapper.toStringMap(source.getVariablesJson()));
        Path buildWorkspaceRoot = resolveBuildWorkspaceRoot();
        Path deployWorkspaceRoot = resolveDeployWorkspaceRoot(pipeline.getTargetHost(), buildWorkspaceRoot);

        variables.put("branch", source.getBranchName());
        variables.put("gitUrl", gitCredentialService.resolveGitUrl(pipeline.getProject()));
        variables.put("gitRepositoryUrl", pipeline.getProject().getGitUrl());
        variables.put("projectName", pipeline.getProject().getName());
        variables.put("pipelineName", pipeline.getName());
        variables.put("applicationName", resolveApplicationName(pipeline));
        variables.put("springProfile", pipeline.getSpringProfile() == null ? "" : pipeline.getSpringProfile());
        variables.put("runtimeConfigYaml", pipeline.getRuntimeConfigYaml() == null ? "" : pipeline.getRuntimeConfigYaml());
        variables.put("runtimeConfigYamlBase64", encodeRuntimeConfigYaml(pipeline.getRuntimeConfigYaml()));
        variables.put("workspaceRoot", buildWorkspaceRoot.toAbsolutePath().normalize().toString());
        variables.put("buildWorkspaceRoot", buildWorkspaceRoot.toAbsolutePath().normalize().toString());
        variables.put("deployWorkspaceRoot", deployWorkspaceRoot.toAbsolutePath().normalize().toString());
        variables.put("sourceArtifactPath", source.getArtifactPath());
        applyBuildRuntimeEnvironmentVariables(variables, pipeline);

        if (Boolean.TRUE.equals(pipeline.getTemplate().getMonitorProcess())) {
            serviceRepository.findFirstByPipelineId(pipeline.getId())
                    .map(ServiceEntity::getId)
                    .ifPresent(serviceManager::stop);
        }

        DeploymentEntity entity = new DeploymentEntity();
        entity.setPipeline(pipeline);
        entity.setBranchName(source.getBranchName());
        entity.setTriggeredBy(requireCurrentUser().username());
        entity.setStatus(DeploymentStatus.PENDING);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setRollbackFromDeploymentId(source.getId());
        entity.setVariablesJson(jsonMapper.write(variables));
        entity.setExecutionSnapshotJson(buildExecutionSnapshotJson(pipeline, source.getBranchName(), variables));
        deploymentRepository.save(entity);

        variables.put("deploymentId", entity.getId().toString());
        Path localArtifactDir = buildWorkspaceRoot.resolve(BUILD_ARTIFACT_DIR).resolve("deploy-" + entity.getId()).toAbsolutePath().normalize();
        Path targetArtifactDir = deployWorkspaceRoot.resolve(BUILD_ARTIFACT_DIR).resolve("deploy-" + entity.getId()).toAbsolutePath().normalize();
        Map<String, String> buildVariables = new LinkedHashMap<>(variables);
        buildVariables.put("artifactDir", localArtifactDir.toString());
        buildVariables.put("workspaceRoot", buildWorkspaceRoot.toAbsolutePath().normalize().toString());

        Map<String, String> deployVariables = new LinkedHashMap<>(variables);
        deployVariables.put("artifactDir", targetArtifactDir.toString());
        deployVariables.put("workspaceRoot", deployWorkspaceRoot.toAbsolutePath().normalize().toString());
        deployVariables.put("runtimeConfigFilePath", deployWorkspaceRoot.resolve("config").resolve("deploy-" + entity.getId() + ".yml").toAbsolutePath().normalize().toString());
        applyDeployRuntimeEnvironmentVariables(deployVariables, pipeline);
        augmentStartCommand(deployVariables);

        String deployTemplate = resolveDeployScriptTemplate(pipeline);
        String renderedDeployScript = deployTemplate == null ? null : scriptTemplateService.render(deployTemplate, deployVariables);

        entity.setArtifactPath(localArtifactDir.toString());
        entity.setRenderedBuildScript(buildRuntimeEnvironmentPreamble(buildVariables, pipeline, true) + buildReplayScript(buildVariables));
        entity.setRenderedDeployScript(renderedDeployScript == null ? null : buildRuntimeEnvironmentPreamble(deployVariables, pipeline, false) + renderedDeployScript + buildDeployRuntimeDiagnostics(pipeline, deployVariables));
        entity.setVariablesJson(jsonMapper.write(buildVariables));
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
        List<String> builtInKeys = List.of(
                "branch",
                "gitUrl",
                "projectName",
                "pipelineName",
                "applicationName",
                "springProfile",
                "runtimeConfigYaml",
                "runtimeConfigYamlBase64",
                "runtimeConfigFilePath",
                "workspaceRoot",
                "deploymentId",
                "gitRepositoryUrl",
                "rollbackBackupPath"
        );
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
        ensureDeploymentReadable(entity);
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

    private List<DeploymentEntity> enrichTriggeredByDisplayNames(List<DeploymentEntity> deployments) {
        deployments.forEach(this::enrichTriggeredByDisplayName);
        return deployments;
    }

    private DeploymentEntity enrichTriggeredByDisplayName(DeploymentEntity entity) {
        if (entity == null) {
            return null;
        }
        String username = entity.getTriggeredBy();
        if (username == null || username.isBlank()) {
            entity.setTriggeredByDisplayName(null);
            return entity;
        }
        String displayName = userRepository.findByUsername(username)
                .map(user -> user.getDisplayName() == null || user.getDisplayName().isBlank() ? user.getUsername() : user.getDisplayName())
                .orElse(username);
        entity.setTriggeredByDisplayName(displayName);
        return entity;
    }

    private AuthenticatedUser requireCurrentUser() {
        AuthenticatedUser currentUser = AuthContextHolder.get();
        if (currentUser == null) {
            throw new BusinessException(ErrorSubCode.AUTH_REQUIRED);
        }
        return currentUser;
    }

    private void ensureDeploymentReadable(DeploymentEntity entity) {
        requireCurrentUser();
    }

    private void ensureDeploymentManageable(DeploymentEntity entity) {
        AuthenticatedUser currentUser = requireCurrentUser();
        if (currentUser.isAdmin()) {
            return;
        }
        if (!currentUser.username().equals(entity.getTriggeredBy())) {
            throw new BusinessException(ErrorSubCode.AUTH_ADMIN_REQUIRED);
        }
    }

    private String buildReplayScript(Map<String, String> variables) {
        List<String> lines = new ArrayList<>();
        lines.add("#!/usr/bin/env bash");
        lines.add("set -e");
        lines.add("export PS4='+ $(date \"+%Y-%m-%d %H:%M:%S\") '");
        lines.add("set -x");
        lines.add("");
        lines.add("echo \"[回滚 1/3] 准备历史产物目录\"");
        lines.add("if [ ! -d \"{{sourceArtifactPath}}\" ]; then");
        lines.add("  echo \"历史构建产物不存在：{{sourceArtifactPath}}\"");
        lines.add("  exit 1");
        lines.add("fi");
        lines.add("");
        lines.add("echo \"[回滚 2/3] 复制历史构建产物到本次发布包\"");
        lines.add("mkdir -p \"{{artifactDir}}\"");
        lines.add("rsync -a \"{{sourceArtifactPath}}/\" \"{{artifactDir}}/\"");
        lines.add("");
        lines.add("echo \"[回滚 3/3] 历史构建产物已就绪，开始重新发布\"");
        lines.add("");
        return scriptTemplateService.render(String.join("\n", lines), variables);
    }

    /**
     * 启动命令执行完成后，追加一段系统级诊断输出，方便直接在部署日志里确认：
     * 1. 启动关键字是否命中
     * 2. 应用日志是否已经开始写入
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
        }
        String targetDir = variables.get("targetDir");
        if (targetDir != null && !targetDir.isBlank()) {
            String logPath = escapeShell(targetDir + "/" + variables.getOrDefault("applicationName", "application") + ".log");
            lines.add("if [ -f \"" + logPath + "\" ]; then");
            lines.add("  echo \"[系统] 应用日志文件状态：$(ls -l \"" + logPath + "\")\"");
            lines.add("  echo \"[系统] 应用日志最新 20 行：\"");
            lines.add("  tail -n 20 \"" + logPath + "\" || true");
            lines.add("else");
            lines.add("  echo \"[系统] 应用日志尚未生成：" + logPath + "\"");
            lines.add("fi");
        }
        lines.add("");
        return scriptTemplateService.render(String.join("\n", lines), variables);
    }

    private String resolveApplicationName(PipelineEntity pipeline) {
        String configured = pipeline.getApplicationName();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        String fallback = pipeline.getName();
        if (fallback == null || fallback.isBlank()) {
            return "application";
        }
        return fallback.trim()
                .replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
    }

    private String encodeRuntimeConfigYaml(String runtimeConfigYaml) {
        if (runtimeConfigYaml == null || runtimeConfigYaml.isBlank()) {
            return "";
        }
        return Base64.getEncoder().encodeToString(runtimeConfigYaml.getBytes(StandardCharsets.UTF_8));
    }

    private String buildExecutionSnapshotJson(PipelineEntity pipeline, String branch, Map<String, String> variables) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("pipelineName", pipeline.getName());
        snapshot.put("projectName", pipeline.getProject() == null ? null : pipeline.getProject().getName());
        snapshot.put("templateName", pipeline.getTemplate() == null ? null : pipeline.getTemplate().getName());
        snapshot.put("templateType", pipeline.getTemplate() == null ? null : pipeline.getTemplate().getTemplateType());
        snapshot.put("branch", branch);
        snapshot.put("targetHost", pipeline.getTargetHost() == null ? "本机" : pipeline.getTargetHost().getName());
        snapshot.put("targetHostType", pipeline.getTargetHost() == null ? "LOCAL" : pipeline.getTargetHost().getType());
        snapshot.put("applicationName", variables.get("applicationName"));
        snapshot.put("springProfile", variables.get("springProfile"));
        snapshot.put("startupKeyword", pipeline.getStartupKeyword());
        snapshot.put("startupTimeoutSeconds", pipeline.getStartupTimeoutSeconds());
        snapshot.put("javaEnvironment", summarizeRuntimeEnvironment(pipeline.getJavaEnvironment()));
        snapshot.put("nodeEnvironment", summarizeRuntimeEnvironment(pipeline.getNodeEnvironment()));
        snapshot.put("mavenEnvironment", summarizeRuntimeEnvironment(pipeline.getMavenEnvironment()));
        snapshot.put("runtimeJavaEnvironment", summarizeRuntimeEnvironment(pipeline.getRuntimeJavaEnvironment()));

        Map<String, String> importantVariables = new LinkedHashMap<>();
        copySnapshotVariable(importantVariables, variables, "targetDir");
        copySnapshotVariable(importantVariables, variables, "jarPath");
        copySnapshotVariable(importantVariables, variables, "startCommand");
        copySnapshotVariable(importantVariables, variables, "buildCommand");
        copySnapshotVariable(importantVariables, variables, "frontendBuildCommand");
        copySnapshotVariable(importantVariables, variables, "backendBuildCommand");
        copySnapshotVariable(importantVariables, variables, "frontendDir");
        copySnapshotVariable(importantVariables, variables, "backendDir");
        copySnapshotVariable(importantVariables, variables, "distDir");
        snapshot.put("variables", importantVariables);

        String runtimeConfigYaml = pipeline.getRuntimeConfigYaml();
        if (runtimeConfigYaml != null && !runtimeConfigYaml.isBlank()) {
            snapshot.put("runtimeConfigYaml", runtimeConfigYaml);
        }
        return jsonMapper.write(snapshot);
    }

    private Map<String, Object> summarizeRuntimeEnvironment(RuntimeEnvironmentEntity environment) {
        if (environment == null) {
            return null;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", environment.getId());
        summary.put("name", environment.getName());
        summary.put("type", environment.getType());
        summary.put("version", environment.getVersion());
        summary.put("homePath", environment.getHomePath());
        summary.put("binPath", environment.getBinPath());
        if (environment.getHost() != null) {
            summary.put("hostName", environment.getHost().getName());
        }
        return summary;
    }

    private void copySnapshotVariable(Map<String, String> target, Map<String, String> source, String key) {
        String value = source.get(key);
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private void augmentStartCommand(Map<String, String> variables) {
        String startCommand = variables.get("startCommand");
        if (startCommand == null || startCommand.isBlank()) {
            return;
        }
        List<String> springArguments = new ArrayList<>();
        String springProfile = variables.get("springProfile");
        if (springProfile != null && !springProfile.isBlank()) {
            springArguments.add("--spring.profiles.active=" + springProfile.trim());
        }
        String runtimeConfigYamlBase64 = variables.get("runtimeConfigYamlBase64");
        String runtimeConfigFilePath = variables.get("runtimeConfigFilePath");
        if (runtimeConfigYamlBase64 != null && !runtimeConfigYamlBase64.isBlank()
                && runtimeConfigFilePath != null && !runtimeConfigFilePath.isBlank()) {
            springArguments.add("--spring.config.additional-location=file:" + runtimeConfigFilePath.trim());
        }
        if (springArguments.isEmpty()) {
            variables.put("springBootArgs", "");
            return;
        }
        String joinedArguments = String.join(" ", springArguments);
        variables.put("springBootArgs", joinedArguments);
        String trimmedCommand = startCommand.trim();
        if (trimmedCommand.contains("{{springBootArgs}}")) {
            return;
        }
        variables.put("startCommand", injectSpringArguments(trimmedCommand, joinedArguments));
    }

    private String injectSpringArguments(String startCommand, String joinedArguments) {
        if (joinedArguments == null || joinedArguments.isBlank()) {
            return startCommand;
        }

        String trimmedCommand = startCommand.trim();
        String backgroundSuffix = "";
        if (trimmedCommand.endsWith("&")) {
            trimmedCommand = trimmedCommand.substring(0, trimmedCommand.length() - 1).trim();
            backgroundSuffix = " &";
        }

        Matcher redirectionMatcher = REDIRECTION_PATTERN.matcher(trimmedCommand);
        if (redirectionMatcher.find()) {
            int splitIndex = redirectionMatcher.start();
            String commandPart = trimmedCommand.substring(0, splitIndex).trim();
            String redirectPart = trimmedCommand.substring(splitIndex);
            return commandPart + " " + joinedArguments + redirectPart + backgroundSuffix;
        }

        return trimmedCommand + " " + joinedArguments + backgroundSuffix;
    }
}
