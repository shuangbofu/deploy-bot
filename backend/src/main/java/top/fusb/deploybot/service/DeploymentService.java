package top.fusb.deploybot.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import top.fusb.deploybot.dto.DeploymentDetailSummary;
import top.fusb.deploybot.dto.DeploymentRequest;
import top.fusb.deploybot.dto.DeploymentPrecheckResult;
import top.fusb.deploybot.dto.DeploymentPrecheckMissingItem;
import top.fusb.deploybot.dto.DeploymentStreamStatus;
import top.fusb.deploybot.dto.DeploymentFilterOptions;
import top.fusb.deploybot.dto.DeploymentListSummary;
import top.fusb.deploybot.dto.DeploymentRestrictionEvaluationResult;
import top.fusb.deploybot.dto.PageResult;
import top.fusb.deploybot.dto.TemplateVariableSchemaItem;
import top.fusb.deploybot.dto.UserRecentPipelineSummary;
import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.kit.JsonKit;
import top.fusb.deploybot.kit.ObjectKit;
import top.fusb.deploybot.kit.ShellHeredocMarker;
import top.fusb.deploybot.kit.ShellKit;
import top.fusb.deploybot.kit.TemplateVariableSchemaKit;
import top.fusb.deploybot.kit.TextKit;
import top.fusb.deploybot.kit.TimeKit;
import top.fusb.deploybot.model.DeploymentEntity;
import top.fusb.deploybot.model.DeploymentStatus;
import top.fusb.deploybot.model.GitAuthType;
import top.fusb.deploybot.model.HostEntity;
import top.fusb.deploybot.model.PipelineEntity;
import top.fusb.deploybot.model.RuntimeEnvironmentEntity;
import top.fusb.deploybot.model.ServiceEntity;
import top.fusb.deploybot.runner.DeploymentRunner;
import top.fusb.deploybot.notification.model.NotificationEventType;
import top.fusb.deploybot.notification.service.DeploymentNotificationAsyncService;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableAssemblyPhase;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableAssemblyResult;
import top.fusb.deploybot.repo.DeploymentRepository;
import top.fusb.deploybot.repo.PipelineRepository;
import top.fusb.deploybot.repo.ServiceRepository;
import top.fusb.deploybot.repo.UserRepository;
import top.fusb.deploybot.security.AuthContextHolder;
import top.fusb.deploybot.security.AuthenticatedUser;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class DeploymentService {
    private static final Logger log = LoggerFactory.getLogger(DeploymentService.class);
    private static final String DEFAULT_TRIGGER_USER = "anonymous";
    private static final String BUILD_ARTIFACT_DIR = "artifacts";
    private static final long LOG_READ_TIMEOUT_MILLIS = 1500L;
    private static final int LOG_CHUNK_MAX_BYTES = 64 * 1024;
    private static final List<RuntimeEnvironmentBinding> BUILD_RUNTIME_BINDINGS = List.of(
            new RuntimeEnvironmentBinding(
                    "JAVA",
                    "JAVA_HOME",
                    "Java",
                    "Java bin",
                    PipelineEntity::getJavaEnvironment,
                    List.of("echo \"[环境] java=$(command -v java 2>/dev/null || true) version=$(java -version 2>&1 | head -n 1 || true)\"")
            ),
            new RuntimeEnvironmentBinding(
                    "MAVEN",
                    "MAVEN_HOME",
                    "Maven",
                    "Maven bin",
                    PipelineEntity::getMavenEnvironment,
                    List.of("echo \"[环境] mvn=$(command -v mvn 2>/dev/null || true) version=$(mvn -v 2>/dev/null | head -n 1 || true)\"")
            ),
            new RuntimeEnvironmentBinding(
                    "NODE",
                    "NODE_HOME",
                    "Node",
                    "Node bin",
                    PipelineEntity::getNodeEnvironment,
                    List.of(
                            "echo \"[环境] node=$(command -v node 2>/dev/null || true) version=$(node -v 2>/dev/null || true)\"",
                            "echo \"[环境] npm=$(command -v npm 2>/dev/null || true) version=$(npm -v 2>/dev/null || true)\""
                    )
            )
    );
    private static final List<RuntimeEnvironmentBinding> DEPLOY_RUNTIME_BINDINGS = List.of(
            new RuntimeEnvironmentBinding(
                    "JAVA",
                    "JAVA_HOME",
                    "Java",
                    "Java bin",
                    PipelineEntity::getRuntimeJavaEnvironment,
                    List.of("echo \"[环境] java=$(command -v java 2>/dev/null || true) version=$(java -version 2>&1 | head -n 1 || true)\"")
            )
    );
    private static final Set<String> RUNTIME_ENVIRONMENT_PREFIXES = java.util.stream.Stream
            .concat(BUILD_RUNTIME_BINDINGS.stream(), DEPLOY_RUNTIME_BINDINGS.stream())
            .map(RuntimeEnvironmentBinding::prefix)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final Set<String> RUNTIME_ENVIRONMENT_INTERNAL_KEYS = java.util.stream.Stream
            .concat(BUILD_RUNTIME_BINDINGS.stream(), DEPLOY_RUNTIME_BINDINGS.stream())
            .flatMap(binding -> java.util.stream.Stream.of(
                    binding.homeKey(),
                    binding.prefix() + "_BIN_PATH"
            ))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    private static final ExecutorService LOG_READ_EXECUTOR = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "deployment-log-reader");
        thread.setDaemon(true);
        return thread;
    });
    private final DeploymentRepository deploymentRepository;
    private final PipelineRepository pipelineRepository;
    private final ScriptTemplateService scriptTemplateService;
    private final DeploymentRunner deploymentRunner;
    private final ServiceRepository serviceRepository;
    private final SystemSettingsService systemSettingsService;
    private final GitCredentialService gitCredentialService;
    private final ServiceManager serviceManager;
    private final HostService hostService;
    private final UserRepository userRepository;
    private final DeploymentNotificationAsyncService deploymentNotificationAsyncService;
    private final DeploymentCleanupService deploymentCleanupService;
    private final DeploymentPluginBridgeService deploymentPluginBridgeService;
    private final PipelineTemplateResolverService pipelineTemplateResolverService;
    private final ShellVariableService shellVariableService;
    private final DeploymentRestrictionPolicyService deploymentRestrictionPolicyService;
    private final PipelineHallEventService pipelineHallEventService;
    @Value("${deploybot.workspace-root:./runtime}")
    private String workspaceRoot;
    private Path defaultWorkspaceRoot;

    @PostConstruct
    public void initDefaultWorkspaceRoot() {
        this.defaultWorkspaceRoot = Path.of(workspaceRoot);
    }

    public List<DeploymentEntity> findAll() {
        requireCurrentUser();
        return enrichTriggeredByDisplayNames(deploymentRepository.findAllByOrderByCreatedAtDesc());
    }

    @Transactional
    public void failInterruptedDeploymentsOnStartup() {
        List<DeploymentEntity> interruptedDeployments = deploymentRepository.findByStatusInOrderByCreatedAtDesc(
                List.of(DeploymentStatus.PENDING, DeploymentStatus.RUNNING)
        );
        if (interruptedDeployments.isEmpty()) {
            return;
        }
        log.warn("检测到 {} 条部署在系统重启前未正常结束，开始补偿为失败状态。", interruptedDeployments.size());
        Path buildWorkspaceRoot = resolveBuildWorkspaceRoot();
        interruptedDeployments.forEach(deployment -> {
            try {
                appendSystemLog(resolveOrCreateLogFile(deployment, buildWorkspaceRoot), "系统重启，当前部署任务已中断并自动标记为失败。");
            } catch (Exception ex) {
                log.warn("为部署 {} 追加系统重启日志失败：{}", deployment.getId(), ex.getMessage());
            }
            deployment.setStatus(DeploymentStatus.FAILED);
            deployment.setFinishedAt(LocalDateTime.now());
            deployment.setErrorMessage("部署平台重启，任务已中断。");
            deploymentRepository.save(deployment);
            deploymentCleanupService.cleanupAfterDeployment(deployment, buildWorkspaceRoot);
            deploymentNotificationAsyncService.notifyAsync(deployment.getId(), NotificationEventType.DEPLOYMENT_FINISHED);
        });
    }

    public DeploymentFilterOptions findMineFilterOptions() {
        AuthenticatedUser currentUser = requireCurrentUser();
        return new DeploymentFilterOptions(
                deploymentRepository.findDistinctProjectNamesByTriggeredBy(currentUser.username()),
                deploymentRepository.findDistinctPipelineNamesByTriggeredBy(currentUser.username())
        );
    }

    public List<UserRecentPipelineSummary> findMyRecentPipelines() {
        AuthenticatedUser currentUser = requireCurrentUser();
        return deploymentRepository.findTop30ByTriggeredByOrderByCreatedAtDesc(currentUser.username()).stream()
                .filter(item -> item.getPipeline() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        DeploymentEntity::getPipeline,
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ))
                .entrySet()
                .stream()
                .map(entry -> new UserRecentPipelineSummary(
                        entry.getKey().getId(),
                        entry.getKey().getName(),
                        entry.getKey().getProject() == null ? null : entry.getKey().getProject().getName(),
                        entry.getKey().getDefaultBranch(),
                        entry.getKey().getTemplateTypeSnapshot(),
                        entry.getValue().size(),
                        entry.getValue().stream()
                                .map(DeploymentEntity::getCreatedAt)
                                .filter(Objects::nonNull)
                                .max(LocalDateTime::compareTo)
                                .orElse(null)
                ))
                .sorted(
                        Comparator.comparingLong(UserRecentPipelineSummary::count).reversed()
                                .thenComparing(
                                        UserRecentPipelineSummary::latestDeploymentAt,
                                        Comparator.nullsLast(Comparator.reverseOrder())
                                )
                )
                .toList();
    }

    public PageResult<DeploymentListSummary> findPage(
            int page,
            int pageSize,
            String projectName,
            String pipelineName,
            String triggeredBy,
            DeploymentStatus status,
            Long startTime,
            Long endTime,
            Long pipelineId
    ) {
        requireCurrentUser();
        Page<DeploymentEntity> result = deploymentRepository.findAll((root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            var pipelineJoin = root.join("pipeline", jakarta.persistence.criteria.JoinType.LEFT);
            var projectJoin = pipelineJoin.join("project", jakarta.persistence.criteria.JoinType.LEFT);
            if (TextKit.isNotBlank(projectName)) {
                predicates.add(cb.equal(projectJoin.get("name"), projectName));
            }
            if (TextKit.isNotBlank(pipelineName)) {
                predicates.add(cb.equal(pipelineJoin.get("name"), pipelineName));
            }
            if (pipelineId != null) {
                predicates.add(cb.equal(pipelineJoin.get("id"), pipelineId));
            }
            if (TextKit.isNotBlank(triggeredBy)) {
                String pattern = "%" + triggeredBy.trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("triggeredBy")), pattern));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), TimeKit.fromEpochMillis(startTime)));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), TimeKit.fromEpochMillis(endTime)));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, PageRequest.of(Math.max(0, page - 1), Math.max(1, Math.min(100, pageSize)), Sort.by(Sort.Order.desc("createdAt"))));
        return PageResult.of(result.map(this::toDeploymentListSummary));
    }

    public PageResult<DeploymentListSummary> findMinePage(
            int page,
            int pageSize,
            String projectName,
            String pipelineName,
            String triggeredBy,
            String branchName,
            DeploymentStatus status,
            Long startTime,
            Long endTime,
            Long pipelineId
    ) {
        AuthenticatedUser currentUser = requireCurrentUser();
        Page<DeploymentEntity> result = deploymentRepository.findAll((root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            var pipelineJoin = root.join("pipeline", jakarta.persistence.criteria.JoinType.LEFT);
            var projectJoin = pipelineJoin.join("project", jakarta.persistence.criteria.JoinType.LEFT);
            predicates.add(cb.equal(root.get("triggeredBy"), currentUser.username()));
            if (TextKit.isNotBlank(projectName)) {
                predicates.add(cb.equal(projectJoin.get("name"), projectName));
            }
            if (TextKit.isNotBlank(pipelineName)) {
                predicates.add(cb.equal(pipelineJoin.get("name"), pipelineName));
            }
            if (pipelineId != null) {
                predicates.add(cb.equal(pipelineJoin.get("id"), pipelineId));
            }
            if (TextKit.isNotBlank(triggeredBy)) {
                String pattern = "%" + triggeredBy.trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("triggeredBy")), pattern));
            }
            if (TextKit.isNotBlank(branchName)) {
                predicates.add(cb.like(cb.lower(root.get("branchName")), "%" + branchName.trim().toLowerCase() + "%"));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), TimeKit.fromEpochMillis(startTime)));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), TimeKit.fromEpochMillis(endTime)));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, PageRequest.of(Math.max(0, page - 1), Math.max(1, Math.min(100, pageSize)), Sort.by(Sort.Order.desc("createdAt"))));
        return PageResult.of(result.map(this::toDeploymentListSummary));
    }

    public DeploymentEntity findById(Long id) {
        DeploymentEntity entity = deploymentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.DEPLOYMENT_NOT_FOUND));
        ensureDeploymentReadable(entity);
        return enrichTriggeredByDisplayName(entity);
    }

    public DeploymentDetailSummary findDetail(Long id) {
        return toDeploymentDetailSummary(findById(id));
    }

    public DeploymentStreamStatus findStreamStatus(Long id) {
        DeploymentEntity entity = deploymentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.DEPLOYMENT_NOT_FOUND));
        ensureDeploymentReadable(entity);
        DeploymentEntity.ProgressSnapshot snapshot = entity.readProgressSnapshot();
        return new DeploymentStreamStatus(
                entity.getId(),
                entity.getStatus(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getErrorMessage(),
                entity.getMonitoredPid(),
                entity.getCommitSha(),
                entity.progressPercent(snapshot),
                entity.progressStage(snapshot),
                entity.progressCurrent(snapshot),
                entity.progressTotal(snapshot)
        );
    }

    @Transactional
    public DeploymentEntity create(DeploymentRequest request) {
        // 1. 读取并校验流水线。
        PipelineEntity pipeline = pipelineRepository.findById(request.pipelineId())
                .orElseThrow(() -> new BusinessException(ErrorSubCode.PIPELINE_NOT_FOUND));
        if (pipeline.getMavenSettings() != null && Boolean.TRUE.equals(pipeline.getMavenSettings().getDeleted())) {
            throw new BusinessException(ErrorSubCode.MAVEN_SETTINGS_NOT_FOUND, "当前流水线绑定的 Maven settings.xml 已被删除，请重新选择。");
        }
        validatePipelineReadyForDeployment(pipeline);
        AuthenticatedUser currentUser = requireCurrentUser();
        log.info("Creating deployment for pipeline {} with requested branch {}.", pipeline.getName(), request.branchName());
        List<DeploymentEntity> activeDeployments = deploymentRepository.findByPipelineIdAndStatusInOrderByCreatedAtDesc(
                pipeline.getId(),
                List.of(DeploymentStatus.PENDING, DeploymentStatus.RUNNING)
        );
        if (!activeDeployments.isEmpty()) {
            if (!Boolean.TRUE.equals(request.replaceRunning())) {
                throw new BusinessException(ErrorSubCode.PIPELINE_HAS_RUNNING_DEPLOYMENT);
            }
            activeDeployments.forEach(item -> deploymentRunner.stop(item.getId(), currentUser.username()));
        }

        Map<String, String> variables = new LinkedHashMap<>(pipeline.getVariables() == null ? Map.of() : pipeline.getVariables());
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
        variables.put("projectId", pipeline.getProject().getId() == null ? "" : pipeline.getProject().getId().toString());
        variables.put("projectName", pipeline.getProject().getName());
        variables.put("pipelineId", pipeline.getId() == null ? "" : pipeline.getId().toString());
        variables.put("pipelineName", pipeline.getName());
        variables.put("pipelineTags", serializePipelineTags(pipeline));
        variables.put("serviceName", resolveServiceName(pipeline));
        variables.put("targetDir", resolveTargetDir(pipeline));
        putPluginConfigVariables(variables, pipeline);
        variables.put("workspaceRoot", buildWorkspaceRoot.toAbsolutePath().normalize().toString());
        variables.put("buildWorkspaceRoot", buildWorkspaceRoot.toAbsolutePath().normalize().toString());
        variables.put("deployWorkspaceRoot", deployWorkspaceRoot.toAbsolutePath().normalize().toString());
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
        entity.setPipelineName(pipeline.getName());
        entity.setPipelineImportantTags(pipeline.getImportantTags() == null ? List.of() : pipeline.getImportantTags());
        entity.setProjectName(pipeline.getProject() == null ? null : pipeline.getProject().getName());
        entity.setVariables(variables);
        entity.setExecutionSnapshot(buildExecutionSnapshot(pipeline, branch, variables));
        entity.setTriggeredBy(currentUser.username() == null || currentUser.username().isBlank() ? DEFAULT_TRIGGER_USER : currentUser.username());
        entity.setStatus(DeploymentStatus.PENDING);
        entity.setCreatedAt(LocalDateTime.now());
        deploymentRepository.save(entity);

        // 3. 补齐部署内置变量，并分别渲染构建脚本与发布脚本。
        variables.put("deploymentId", entity.getId().toString());
        Path localArtifactDir = buildWorkspaceRoot.resolve(BUILD_ARTIFACT_DIR).resolve("deploy-" + entity.getId()).toAbsolutePath().normalize();
        Path targetArtifactDir = deployWorkspaceRoot.resolve(BUILD_ARTIFACT_DIR).resolve("deploy-" + entity.getId()).toAbsolutePath().normalize();
        Path buildSourceDir = buildWorkspaceRoot.resolve("runs").resolve(entity.getId().toString()).toAbsolutePath().normalize();
        variables.put("buildSourceDir", buildSourceDir.toString());

        Map<String, String> buildVariables = new LinkedHashMap<>(variables);
        buildVariables.put("artifactDir", localArtifactDir.toString());
        buildVariables.put("workspaceRoot", buildWorkspaceRoot.toAbsolutePath().normalize().toString());
        buildVariables.put("buildSourceDir", buildSourceDir.toString());
        applyBuildRuntimeEnvironmentVariables(buildVariables, pipeline);
        applyPluginVariableAssembly(
                pipeline,
                buildVariables,
                PluginVariableAssemblyPhase.BUILD,
                resolveMavenSettingsFilePath(pipeline, buildWorkspaceRoot, entity.getId())
        );
        buildVariables.put("buildWorkspaceRoot", buildWorkspaceRoot.toAbsolutePath().normalize().toString());
        buildVariables.put("buildSourceDir", buildSourceDir.toString());

        Map<String, String> deployVariables = new LinkedHashMap<>(variables);
        deployVariables.put("artifactDir", targetArtifactDir.toString());
        deployVariables.put("workspaceRoot", deployWorkspaceRoot.toAbsolutePath().normalize().toString());
        deployVariables.put("buildSourceDir", buildSourceDir.toString());
        applyDeployRuntimeEnvironmentVariables(deployVariables, pipeline);
        applyPluginVariableAssembly(pipeline, deployVariables, PluginVariableAssemblyPhase.DEPLOY, null);
        deployVariables.put("buildWorkspaceRoot", buildWorkspaceRoot.toAbsolutePath().normalize().toString());
        deployVariables.put("buildSourceDir", buildSourceDir.toString());
        putManagedStartScriptPath(deployVariables, entity.getId());

        String buildTemplate = resolveBuildScriptTemplate(pipeline);
        String deployTemplate = resolveDeployScriptTemplate(pipeline);
        String renderedBuildScript = renderDeploymentScriptTemplate(buildTemplate, buildVariables);
        String renderedDeployScript = deployTemplate == null ? null : renderDeploymentScriptTemplate(deployTemplate, deployVariables);
        log.info(
                "流水线 '{}' 的脚本渲染完成：存在构建脚本={}，存在发布脚本={}，启用服务监测={}",
                pipeline.getName(),
                renderedBuildScript != null && !renderedBuildScript.isBlank(),
                renderedDeployScript != null && !renderedDeployScript.isBlank(),
                pipeline.getTemplateMonitorProcess()
        );

        entity.setArtifactPath(localArtifactDir.toString());
        entity.setRenderedBuildScript(buildRuntimeEnvironmentPreamble(buildVariables, pipeline, true) + renderedBuildScript);
        entity.setRenderedDeployScript(renderedDeployScript == null ? null : buildRuntimeEnvironmentPreamble(deployVariables, pipeline, false) + renderedDeployScript);
        entity.setBuildStepTotal(entity.extractDeclaredStepTotal(renderedBuildScript));
        entity.setDeployStepTotal(entity.extractDeclaredStepTotal(renderedDeployScript));
        entity.setVariables(deployVariables);
        entity.setExecutionSnapshot(buildExecutionSnapshot(pipeline, branch, deployVariables));
        entity = deploymentRepository.save(entity);
        log.info(
                "Deployment {} created for pipeline '{}' on host '{}'.",
                entity.getId(),
                pipeline.getName(),
                pipeline.getTargetHost() == null ? "本机" : pipeline.getTargetHost().getName()
        );

        Long deploymentId = entity.getId();
        Long pipelineId = pipeline.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                pipelineHallEventService.publishPipelineChange(pipelineId);
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

    private Path resolveOrCreateLogFile(DeploymentEntity deployment, Path buildWorkspaceRoot) throws IOException {
        if (deployment.getLogPath() != null && !deployment.getLogPath().isBlank()) {
            Path existingPath = Path.of(deployment.getLogPath());
            if (existingPath.getParent() != null) {
                Files.createDirectories(existingPath.getParent());
            }
            deployment.setLogPath(existingPath.toAbsolutePath().normalize().toString());
            return existingPath;
        }
        Path fallbackLogsDir = buildWorkspaceRoot.resolve("logs");
        Files.createDirectories(fallbackLogsDir);
        Path fallbackLogFile = fallbackLogsDir.resolve("deploy-" + deployment.getId() + ".log");
        deployment.setLogPath(fallbackLogFile.toAbsolutePath().normalize().toString());
        return fallbackLogFile;
    }

    private void appendSystemLog(Path logFile, String message) throws IOException {
        Files.writeString(
                logFile,
                "[系统] " + TimeKit.formatDateTime(LocalDateTime.now()) + " " + message + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    /**
     * 流水线中选择的运行环境只注入到本机构建阶段，避免误把远端发布机当成构建机。
     */
    private void applyBuildRuntimeEnvironmentVariables(Map<String, String> variables, PipelineEntity pipeline) {
        applyRuntimeEnvironmentVariables(variables, pipeline, true);
    }

    private void applyDeployRuntimeEnvironmentVariables(Map<String, String> variables, PipelineEntity pipeline) {
        applyRuntimeEnvironmentVariables(variables, pipeline, false);
    }

    private void applyRuntimeEnvironmentVariables(Map<String, String> variables, PipelineEntity pipeline, boolean buildStage) {
        Set<String> requiredTypes = requiredRuntimeTypeSet(pipeline, buildStage);
        runtimeBindings(buildStage).stream()
                .filter(binding -> requiredTypes.contains(binding.prefix()))
                .forEach(binding -> putEnvironmentVariables(variables, binding.prefix(), binding.environment(pipeline), binding.homeKey()));
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
        Map<String, Object> extraEnvironment = environment.getEnvironment() == null ? Map.of() : environment.getEnvironment();
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

    public DeploymentPrecheckResult precheck(DeploymentRequest request) {
        PipelineEntity pipeline = pipelineRepository.findById(request.pipelineId())
                .orElseThrow(() -> new BusinessException(ErrorSubCode.PIPELINE_NOT_FOUND));
        List<DeploymentPrecheckMissingItem> missingItems = collectPipelineMissingItems(pipeline);
        boolean hasRunning = !deploymentRepository.findByPipelineIdAndStatusInOrderByCreatedAtDesc(
                pipeline.getId(),
                List.of(DeploymentStatus.PENDING, DeploymentStatus.RUNNING)
        ).isEmpty();
        if (hasRunning && !Boolean.TRUE.equals(request.replaceRunning())) {
            missingItems.add(new DeploymentPrecheckMissingItem("RUNNING_DEPLOYMENT", null, null));
        }
        return new DeploymentPrecheckResult(missingItems.isEmpty(), missingItems);
    }

    private void validatePipelineReadyForDeployment(PipelineEntity pipeline) {
        List<DeploymentPrecheckMissingItem> missingItems = collectPipelineMissingItems(pipeline);
        if (!missingItems.isEmpty()) {
            throw new BusinessException(
                    ErrorSubCode.PIPELINE_CONFIG_INCOMPLETE,
                    resolvePipelineUnavailableMessage(missingItems)
            );
        }
    }

    private String resolvePipelineUnavailableMessage(List<DeploymentPrecheckMissingItem> missingItems) {
        return missingItems.stream()
                .filter(item -> "DEPLOYMENT_RESTRICTED".equals(item.code()))
                .findFirst()
                .map(item -> TextKit.isBlank(item.label()) ? "当前时间不允许部署。" : "当前时间不允许部署：" + item.label())
                .orElseGet(() -> "流水线配置不完整，请先补充：" + missingItems.stream()
                        .map(DeploymentPrecheckMissingItem::code)
                        .collect(java.util.stream.Collectors.joining("、")));
    }

    private List<DeploymentPrecheckMissingItem> collectPipelineMissingItems(PipelineEntity pipeline) {
        List<DeploymentPrecheckMissingItem> missingItems = new ArrayList<>();
        if (pipeline == null) {
            missingItems.add(new DeploymentPrecheckMissingItem("PIPELINE", null, null));
        } else {
            DeploymentRestrictionEvaluationResult restriction = deploymentRestrictionPolicyService.evaluate(pipeline, LocalDateTime.now());
            if (!restriction.allowed()) {
                missingItems.add(new DeploymentPrecheckMissingItem("DEPLOYMENT_RESTRICTED", restriction.policyName(), restriction.reason()));
            }
            if (pipeline.getProject() == null) {
                missingItems.add(new DeploymentPrecheckMissingItem("PROJECT", null, null));
            }
            PipelineTemplateResolverService.ResolvedPipelineTemplate resolvedTemplate = pipelineTemplateResolverService.resolve(pipeline);
            if (resolvedTemplate == null) {
                missingItems.add(new DeploymentPrecheckMissingItem("TEMPLATE", null, null));
            }
            if (pipeline.getTargetHost() == null) {
                missingItems.add(new DeploymentPrecheckMissingItem("TARGET_HOST", null, null));
            }
            if (TextKit.isBlank(pipeline.getTargetDir())) {
                missingItems.add(new DeploymentPrecheckMissingItem("TARGET_DIR", null, null));
            }
            if (TextKit.isBlank(pipeline.getDefaultBranch())) {
                missingItems.add(new DeploymentPrecheckMissingItem("DEFAULT_BRANCH", null, null));
            }
            validateRequiredRuntimeEnvironments(pipeline, missingItems);
            if (resolvedTemplate != null) {
                validateRequiredTemplateVariables(pipeline, resolvedTemplate.variablesSchema(), missingItems);
            }
        }
        return missingItems;
    }

    private void validateRequiredRuntimeEnvironments(PipelineEntity pipeline, List<DeploymentPrecheckMissingItem> missingItems) {
        Set<String> buildTypes = requiredRuntimeTypeSet(pipeline, true);
        BUILD_RUNTIME_BINDINGS.stream()
                .filter(binding -> buildTypes.contains(binding.prefix()))
                .filter(binding -> binding.environment(pipeline) == null)
                .forEach(binding -> missingItems.add(new DeploymentPrecheckMissingItem("BUILD_RUNTIME_ENVIRONMENT", binding.prefix(), binding.label())));
        Set<String> targetTypes = requiredRuntimeTypeSet(pipeline, false);
        DEPLOY_RUNTIME_BINDINGS.stream()
                .filter(binding -> targetTypes.contains(binding.prefix()))
                .filter(binding -> binding.environment(pipeline) == null)
                .forEach(binding -> missingItems.add(new DeploymentPrecheckMissingItem("TARGET_RUNTIME_ENVIRONMENT", binding.prefix(), binding.label())));
    }

    private void validateRequiredTemplateVariables(PipelineEntity pipeline, String variablesSchema, List<DeploymentPrecheckMissingItem> missingItems) {
        Map<String, String> variables = pipeline.getVariables() == null ? Map.of() : pipeline.getVariables();
        for (TemplateVariableSchemaItem item : TemplateVariableSchemaKit.read(variablesSchema)) {
            if (item == null || !Boolean.TRUE.equals(item.required()) || Boolean.FALSE.equals(item.pipelineInput())) {
                continue;
            }
            String name = ObjectKit.stringValue(item.name());
            if (name == null) {
                continue;
            }
            if (TextKit.isBlank(variables.get(name))) {
                missingItems.add(new DeploymentPrecheckMissingItem("TEMPLATE_VARIABLE", name, TextKit.trimToNull(item.label())));
            }
        }
    }

    private String buildRuntimeEnvironmentPreamble(Map<String, String> variables, PipelineEntity pipeline, boolean buildStage) {
        StringBuilder script = new StringBuilder();
        Set<String> requiredTypes = requiredRuntimeTypeSet(pipeline, buildStage);
        script.append("# Runtime environment preamble generated by Deploy Bot\n");
        script.append("set -e\n");
        putScriptEnvironmentAliases(variables, buildStage);
        List<RuntimeEnvironmentBinding> runtimeBindings = runtimeBindings(buildStage);
        if (buildStage) {
            script.append("# Build stage runtime exports\n");
            runtimeBindings.forEach(binding -> script.append(exportRuntimeHomeIfRequired(requiredTypes, binding.prefix(), binding.homeKey(), variables)));
            String settingsXmlBase64 = encodeMavenSettingsXml(pipeline);
            if (requiredTypes.contains("MAVEN")
                    && TextKit.isNotBlank(settingsXmlBase64)
                    && TextKit.isNotBlank(valueOf(variables, "MAVEN_SETTINGS_FILE_PATH"))) {
                script.append("# Prepare Maven settings.xml for build stage\n");
                script.append(ShellKit.writeBase64FileIfPresent(settingsXmlBase64, valueOf(variables, "MAVEN_SETTINGS_FILE_PATH")));
            }
        } else {
            script.append("# Deploy stage runtime exports\n");
            runtimeBindings.forEach(binding -> script.append(exportRuntimeHomeIfRequired(requiredTypes, binding.prefix(), binding.homeKey(), variables)));
        }

        StringBuilder pathBuilder = new StringBuilder();
        runtimeBindings.forEach(binding -> appendRuntimePath(pathBuilder, requiredTypes, binding.prefix(), binding.prefix() + "_BIN_PATH", variables));

        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String key = entry.getKey();
            if (!key.matches("[A-Z0-9_]+")) {
                continue;
            }
            if (isRuntimeScopedEnvironmentKey(key) && !requiredTypes.contains(runtimePrefix(key))) {
                continue;
            }
            if (RUNTIME_ENVIRONMENT_INTERNAL_KEYS.contains(key)) {
                continue;
            }
            if (key.endsWith("__PREPEND_PATH")) {
                pathBuilder.insert(0, ShellKit.escapeDoubleQuoted(entry.getValue()) + ":");
                continue;
            }
            script.append("export ")
                    .append(key)
                    .append("=\"")
                    .append(ShellKit.escapeDoubleQuoted(entry.getValue()))
                    .append("\"\n");
        }

        if (pathBuilder.length() > 0) {
            script.append("export PATH=\"")
                    .append(ShellKit.escapeDoubleQuoted(pathBuilder.toString()))
                    .append("$PATH\"\n");
        }

        if (buildStage) {
            runtimeBindings.forEach(binding -> script.append(requireRuntimeBinDirectory(requiredTypes, binding.prefix(), binding.displayName(), variables)));
            runtimeBindings.forEach(binding -> script.append(buildActivationScriptBlock(requiredTypes, binding.prefix(), variables)));
            script.append(buildRuntimeCommandDiagnostics(requiredTypes, true));
        } else {
            runtimeBindings.forEach(binding -> script.append(requireRuntimeBinDirectory(requiredTypes, binding.prefix(), binding.displayName(), variables)));
            runtimeBindings.forEach(binding -> script.append(buildActivationScriptBlock(requiredTypes, binding.prefix(), variables)));
            script.append(buildRuntimeCommandDiagnostics(requiredTypes, false));
        }

        script.append("\n");
        return script.toString();
    }

    /**
     * 部署脚本里只有模板变量继续使用 {{name}}。平台上下文、插件配置和派生值统一在脚本执行前注入为 shell 环境变量。
     */
    private String renderDeploymentScriptTemplate(String template, Map<String, String> variables) {
        Map<String, String> templateVariables = new LinkedHashMap<>();
        variables.forEach((key, value) -> {
            if (shellVariableService.contextKeys().contains(key)) {
                return;
            }
            if (key != null && key.matches("[A-Z0-9_]+")) {
                return;
            }
            templateVariables.put(key, replaceScriptEnvPlaceholders(value));
        });
        String renderedScript = scriptTemplateService.render(replaceScriptEnvPlaceholders(template), templateVariables);
        return ShellKit.enableCommandTrace(ShellKit.expandLogComments(renderedScript));
    }

    private String replaceScriptEnvPlaceholders(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String result = value;
        for (String key : shellVariableService.contextKeys()) {
            String placeholder = shellVariableService.placeholderForContextKey(key);
            result = result.replace("{{" + key + "}}", placeholder);
            result = result.replace("{{ " + key + " }}", placeholder);
        }
        return result;
    }

    private void putScriptEnvironmentAliases(Map<String, String> variables, boolean buildStage) {
        List<String> stageKeys = buildStage
                ? List.of(
                "branch",
                "gitUrl",
                "gitRepositoryUrl",
                "projectId",
                "projectName",
                "pipelineId",
                "pipelineName",
                "pipelineTags",
                "serviceName",
                "deploymentId",
                "buildWorkspaceRoot",
                "buildSourceDir",
                "artifactDir",
                "sourceArtifactPath"
        )
                : List.of(
                "branch",
                "projectId",
                "projectName",
                "pipelineId",
                "pipelineName",
                "pipelineTags",
                "serviceName",
                "targetDir",
                "deploymentId",
                "deployWorkspaceRoot",
                "artifactDir",
                "sourceArtifactPath"
        );
        stageKeys.forEach(key -> putEnvAlias(variables, shellEnvName(key), key));
    }

    private String shellEnvName(String sourceKey) {
        return TextKit.toUpperSnakeCase(sourceKey);
    }

    private void putEnvAlias(Map<String, String> variables, String envKey, String sourceKey) {
        String value = variables.get(sourceKey);
        if (TextKit.isNotBlank(value)) {
            variables.put(envKey, value);
        }
    }

    private void putManagedStartScriptPath(Map<String, String> variables, Long deploymentId) {
        if (variables == null || deploymentId == null || TextKit.isBlank(variables.get("START_COMMAND"))) {
            return;
        }
        String targetDir = TextKit.trimToNull(variables.get("targetDir"));
        if (targetDir == null) {
            targetDir = TextKit.trimToNull(variables.get("TARGET_DIR"));
        }
        if (targetDir == null) {
            return;
        }
        variables.put("MANAGED_START_SCRIPT", Path.of(targetDir).resolve(".deploybot-start-" + deploymentId + ".sh").toString());
    }

    private String resolveMavenSettingsFilePath(PipelineEntity pipeline, Path workspaceRoot, Long deploymentId) {
        if (pipeline == null || pipeline.getMavenSettings() == null || workspaceRoot == null || deploymentId == null) {
            return null;
        }
        if (TextKit.isBlank(pipeline.getMavenSettings().getContentXml())) {
            return null;
        }
        return workspaceRoot.resolve("config")
                .resolve("maven-settings-" + deploymentId + ".xml")
                .toAbsolutePath()
                .normalize()
                .toString();
    }

    /**
     * 将部署流程中的类型差异变量改写委托给插件桥接层处理，平台主流程只保留统一的执行编排。
     */
    private void applyPluginVariableAssembly(
            PipelineEntity pipeline,
            Map<String, String> variables,
            PluginVariableAssemblyPhase phase,
            String mavenSettingsFilePath
    ) {
        if (pipeline == null || variables == null || phase == null) {
            return;
        }
        PluginVariableAssemblyResult result = deploymentPluginBridgeService.assembleVariables(
                pipeline,
                variables,
                phase,
                mavenSettingsFilePath
        );
        if (result == null || result.variables() == null || result.variables().isEmpty()) {
            return;
        }
        // 插件变量装配只应该改写自己关心的变量，不能把宿主已注入的运行环境变量整包清空。
        // 否则发布阶段会丢失 JAVA_HOME / *_BIN_PATH / *_ACTIVATION_SCRIPT，导致远端环境前导脚本失效。
        variables.putAll(result.variables());
        if (result.notes() != null) {
            result.notes().stream()
                    .filter(TextKit::isNotBlank)
                    .forEach(note -> log.info("插件变量改写[{}][{}]：{}", pipeline.getName(), phase.name(), note));
        }
    }

    private void appendPath(StringBuilder pathBuilder, String path) {
        if (TextKit.isBlank(path)) {
            return;
        }
        pathBuilder.append(ShellKit.escapeDoubleQuoted(path)).append(":");
    }

    private Set<String> requiredRuntimeTypeSet(PipelineEntity pipeline, boolean buildStage) {
        return deploymentPluginBridgeService.requiredRuntimeTypes(pipeline, buildStage).stream()
                .filter(TextKit::isNotBlank)
                .map(item -> item.trim().toUpperCase())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private String exportRuntimeHomeIfRequired(Set<String> requiredTypes, String prefix, String envName, Map<String, String> variables) {
        return requiredTypes.contains(prefix) ? ShellKit.exportIfPresent(envName, valueOf(variables, envName)) : "";
    }

    private boolean isRuntimeScopedEnvironmentKey(String key) {
        return key != null && RUNTIME_ENVIRONMENT_PREFIXES.stream().anyMatch(prefix -> key.startsWith(prefix + "_"));
    }

    private String runtimePrefix(String key) {
        if (key == null) {
            return "";
        }
        int splitIndex = key.indexOf('_');
        return splitIndex < 0 ? key : key.substring(0, splitIndex);
    }

    private void appendRuntimePath(StringBuilder pathBuilder, Set<String> requiredTypes, String prefix, String pathKey, Map<String, String> variables) {
        if (requiredTypes.contains(prefix)) {
            appendPath(pathBuilder, valueOf(variables, pathKey));
        }
    }

    private String requireRuntimeBinDirectory(Set<String> requiredTypes, String prefix, String displayName, Map<String, String> variables) {
        if (!requiredTypes.contains(prefix)) {
            return "";
        }
        if (TextKit.isNotBlank(valueOf(variables, prefix + "_ACTIVATION_SCRIPT"))) {
            return "";
        }
        return ShellKit.requireDirectory(displayName, valueOf(variables, prefix + "_BIN_PATH"));
    }

    private String buildRuntimeCommandDiagnostics(Set<String> requiredTypes, boolean buildStage) {
        StringBuilder script = new StringBuilder();
        runtimeBindings(buildStage).stream()
                .filter(binding -> requiredTypes.contains(binding.prefix()))
                .flatMap(binding -> binding.diagnosticCommands().stream())
                .forEach(command -> script.append(command).append("\n"));
        return script.toString();
    }

    private List<RuntimeEnvironmentBinding> runtimeBindings(boolean buildStage) {
        return buildStage ? BUILD_RUNTIME_BINDINGS : DEPLOY_RUNTIME_BINDINGS;
    }

    private String valueOf(Map<String, String> variables, String key) {
        return variables.getOrDefault(key, "");
    }

    /**
     * 运行环境提供的激活脚本会原样嵌入到部署脚本中，并在前面补一行注释说明作用。
     */
    private String buildActivationScriptBlock(Set<String> requiredTypes, String prefix, Map<String, String> variables) {
        if (!requiredTypes.contains(prefix)) {
            return "";
        }
        String activationScript = valueOf(variables, prefix + "_ACTIVATION_SCRIPT");
        if (TextKit.isBlank(activationScript)) {
            return "";
        }
        return """
                # Activate %s environment
                %s
                """.formatted(prefix, activationScript);
    }

    private String resolveBuildScriptTemplate(PipelineEntity pipeline) {
        String buildScript = pipelineTemplateResolverService.resolveRequired(pipeline).buildScriptContent();
        if (buildScript == null || buildScript.isBlank()) {
            throw new BusinessException(ErrorSubCode.TEMPLATE_BUILD_SCRIPT_MISSING);
        }
        return buildScript;
    }

    private String resolveDeployScriptTemplate(PipelineEntity pipeline) {
        String deployScript = pipelineTemplateResolverService.resolveRequired(pipeline).deployScriptContent();
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
        deploymentRunner.stop(id, requireCurrentUser().username());
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
        validatePipelineReadyForDeployment(pipeline);
        if (pipeline.getMavenSettings() != null && Boolean.TRUE.equals(pipeline.getMavenSettings().getDeleted())) {
            throw new BusinessException(ErrorSubCode.MAVEN_SETTINGS_NOT_FOUND, "当前流水线绑定的 Maven settings.xml 已被删除，请重新选择。");
        }
        Map<String, String> variables = new LinkedHashMap<>(source.getVariables() == null ? Map.of() : source.getVariables());
        Path buildWorkspaceRoot = resolveBuildWorkspaceRoot();
        Path deployWorkspaceRoot = resolveDeployWorkspaceRoot(pipeline.getTargetHost(), buildWorkspaceRoot);

        variables.put("branch", source.getBranchName());
        variables.put("gitUrl", gitCredentialService.resolveGitUrl(pipeline.getProject()));
        variables.put("gitRepositoryUrl", pipeline.getProject().getGitUrl());
        variables.put("projectId", pipeline.getProject().getId() == null ? "" : pipeline.getProject().getId().toString());
        variables.put("projectName", pipeline.getProject().getName());
        variables.put("pipelineId", pipeline.getId() == null ? "" : pipeline.getId().toString());
        variables.put("pipelineName", pipeline.getName());
        variables.put("pipelineTags", serializePipelineTags(pipeline));
        variables.put("serviceName", resolveServiceName(pipeline));
        variables.put("targetDir", resolveTargetDir(pipeline));
        putPluginConfigVariables(variables, pipeline);
        variables.put("workspaceRoot", buildWorkspaceRoot.toAbsolutePath().normalize().toString());
        variables.put("buildWorkspaceRoot", buildWorkspaceRoot.toAbsolutePath().normalize().toString());
        variables.put("deployWorkspaceRoot", deployWorkspaceRoot.toAbsolutePath().normalize().toString());
        variables.put("sourceArtifactPath", source.getArtifactPath());

        if (Boolean.TRUE.equals(pipeline.getTemplateMonitorProcess())) {
            serviceRepository.findByPipelineId(pipeline.getId())
                    .map(ServiceEntity::getId)
                    .ifPresent(serviceManager::stop);
        }

        DeploymentEntity entity = new DeploymentEntity();
        entity.setPipeline(pipeline);
        entity.setBranchName(source.getBranchName());
        entity.setPipelineName(pipeline.getName());
        entity.setPipelineImportantTags(pipeline.getImportantTags() == null ? List.of() : pipeline.getImportantTags());
        entity.setProjectName(pipeline.getProject() == null ? null : pipeline.getProject().getName());
        entity.setTriggeredBy(requireCurrentUser().username());
        entity.setStatus(DeploymentStatus.PENDING);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setRollbackFromDeploymentId(source.getId());
        entity.setVariables(variables);
        entity.setExecutionSnapshot(buildExecutionSnapshot(pipeline, source.getBranchName(), variables));
        deploymentRepository.save(entity);

        variables.put("deploymentId", entity.getId().toString());
        Path localArtifactDir = buildWorkspaceRoot.resolve(BUILD_ARTIFACT_DIR).resolve("deploy-" + entity.getId()).toAbsolutePath().normalize();
        Path targetArtifactDir = deployWorkspaceRoot.resolve(BUILD_ARTIFACT_DIR).resolve("deploy-" + entity.getId()).toAbsolutePath().normalize();
        Path buildSourceDir = buildWorkspaceRoot.resolve("runs").resolve(entity.getId().toString()).toAbsolutePath().normalize();
        variables.put("buildSourceDir", buildSourceDir.toString());
        Map<String, String> buildVariables = new LinkedHashMap<>(variables);
        buildVariables.put("artifactDir", localArtifactDir.toString());
        buildVariables.put("workspaceRoot", buildWorkspaceRoot.toAbsolutePath().normalize().toString());
        buildVariables.put("buildSourceDir", buildSourceDir.toString());
        applyBuildRuntimeEnvironmentVariables(buildVariables, pipeline);
        applyPluginVariableAssembly(
                pipeline,
                buildVariables,
                PluginVariableAssemblyPhase.BUILD,
                resolveMavenSettingsFilePath(pipeline, buildWorkspaceRoot, entity.getId())
        );
        buildVariables.put("buildWorkspaceRoot", buildWorkspaceRoot.toAbsolutePath().normalize().toString());
        buildVariables.put("buildSourceDir", buildSourceDir.toString());

        Map<String, String> deployVariables = new LinkedHashMap<>(variables);
        deployVariables.put("artifactDir", targetArtifactDir.toString());
        deployVariables.put("workspaceRoot", deployWorkspaceRoot.toAbsolutePath().normalize().toString());
        deployVariables.put("buildSourceDir", buildSourceDir.toString());
        applyDeployRuntimeEnvironmentVariables(deployVariables, pipeline);
        applyPluginVariableAssembly(pipeline, deployVariables, PluginVariableAssemblyPhase.DEPLOY, null);
        deployVariables.put("buildWorkspaceRoot", buildWorkspaceRoot.toAbsolutePath().normalize().toString());
        deployVariables.put("buildSourceDir", buildSourceDir.toString());
        putManagedStartScriptPath(deployVariables, entity.getId());

        String deployTemplate = resolveDeployScriptTemplate(pipeline);
        String renderedDeployScript = deployTemplate == null ? null : renderDeploymentScriptTemplate(deployTemplate, deployVariables);
        String renderedBuildScript = buildReplayScript(buildVariables);

        entity.setArtifactPath(localArtifactDir.toString());
        entity.setRenderedBuildScript(buildRuntimeEnvironmentPreamble(buildVariables, pipeline, true) + renderedBuildScript);
        entity.setRenderedDeployScript(renderedDeployScript == null ? null : buildRuntimeEnvironmentPreamble(deployVariables, pipeline, false) + renderedDeployScript);
        entity.setBuildStepTotal(entity.extractDeclaredStepTotal(renderedBuildScript));
        entity.setDeployStepTotal(entity.extractDeclaredStepTotal(renderedDeployScript));
        entity.setVariables(deployVariables);
        entity.setExecutionSnapshot(buildExecutionSnapshot(pipeline, source.getBranchName(), deployVariables));
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

        Map<String, String> sourceVariables = new LinkedHashMap<>(source.getVariables() == null ? Map.of() : source.getVariables());
        List<String> builtInKeys = List.of(
                "branch",
                "gitUrl",
                "projectName",
                "pipelineName",
                "serviceName",
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
        return readLogWithTimeout(path);
    }

    public LogChunk readAuthorizedLogChunk(Long id, long offset) throws IOException {
        DeploymentEntity entity = deploymentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.DEPLOYMENT_NOT_FOUND));
        if (entity.getLogPath() == null) {
            return new LogChunk("", Math.max(0L, offset), isDeploymentFinished(entity));
        }
        Path path = Path.of(entity.getLogPath());
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            return new LogChunk("", Math.max(0L, offset), isDeploymentFinished(entity));
        }
        long safeOffset = Math.max(0L, offset);
        long size = Files.size(path);
        if (size < safeOffset) {
            safeOffset = 0L;
        }
        if (size == safeOffset) {
            return new LogChunk("", size, isDeploymentFinished(entity));
        }
        int chunkSize = (int) Math.min(LOG_CHUNK_MAX_BYTES, size - safeOffset);
        byte[] buffer = new byte[chunkSize];
        try (var inputStream = Files.newInputStream(path)) {
            long skipped = inputStream.skip(safeOffset);
            if (skipped < safeOffset) {
                return new LogChunk("", skipped, isDeploymentFinished(entity));
            }
            int read = inputStream.read(buffer);
            if (read <= 0) {
                return new LogChunk("", safeOffset, isDeploymentFinished(entity));
            }
            long nextOffset = safeOffset + read;
            String content = new String(buffer, 0, read, StandardCharsets.UTF_8);
            return new LogChunk(content, nextOffset, isDeploymentFinished(entity) && nextOffset >= size);
        }
    }

    private boolean isDeploymentFinished(DeploymentEntity entity) {
        return entity.getStatus() == DeploymentStatus.SUCCESS
                || entity.getStatus() == DeploymentStatus.FAILED
                || entity.getStatus() == DeploymentStatus.STOPPED;
    }

    public record LogChunk(String content, long offset, boolean finished) {
    }

    private String readLogWithTimeout(Path path) {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            return "[系统] 日志路径不是普通文件，已跳过读取：" + path;
        }
        Future<String> future = LOG_READ_EXECUTOR.submit(() -> Files.readString(path, StandardCharsets.UTF_8));
        try {
            return future.get(LOG_READ_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            return "[系统] 日志读取超时，可能被其他进程占用或底层文件状态异常，请稍后刷新。";
        } catch (Exception ex) {
            return "[系统] 日志读取失败：" + ex.getMessage();
        }
    }

    private DeploymentListSummary toDeploymentListSummary(DeploymentEntity entity) {
        enrichTriggeredByDisplayName(entity);
        String pipelineName = resolveDeploymentPipelineName(entity);
        String projectName = resolveDeploymentProjectName(entity);
        return new DeploymentListSummary(
                entity.getId(),
                entity.getBranchName(),
                entity.getTriggeredBy(),
                entity.getTriggeredByDisplayName(),
                entity.getStoppedBy(),
                entity.getStoppedByDisplayName(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getLogPath(),
                entity.getErrorMessage(),
                pipelineName,
                resolveDeploymentPipelineImportantTags(entity),
                projectName,
                toPipelineRef(entity),
                entity.getArtifactPath(),
                entity.getRollbackFromDeploymentId(),
                entity.getMonitoredPid(),
                entity.getCommitSha(),
                entity.getGitDiffSnapshot()
        );
    }

    private DeploymentDetailSummary toDeploymentDetailSummary(DeploymentEntity entity) {
        enrichTriggeredByDisplayName(entity);
        DeploymentEntity.ProgressSnapshot snapshot = entity.readProgressSnapshot();
        return new DeploymentDetailSummary(
                entity.getId(),
                entity.getBranchName(),
                entity.getTriggeredBy(),
                entity.getTriggeredByDisplayName(),
                entity.getStoppedBy(),
                entity.getStoppedByDisplayName(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getLogPath(),
                entity.getErrorMessage(),
                entity.progressPercent(snapshot),
                entity.progressStage(snapshot),
                entity.progressCurrent(snapshot),
                entity.progressTotal(snapshot),
                resolveDeploymentPipelineName(entity),
                resolveDeploymentPipelineImportantTags(entity),
                resolveDeploymentProjectName(entity),
                toPipelineRef(entity),
                entity.getArtifactPath(),
                entity.getExecutionSnapshot(),
                entity.getRollbackFromDeploymentId(),
                entity.getMonitoredPid(),
                entity.getCommitSha(),
                entity.getGitDiffSnapshot()
        );
    }

    private DeploymentListSummary.PipelineRef toPipelineRef(DeploymentEntity entity) {
        if (entity.getPipeline() == null) {
            return null;
        }
        return new DeploymentListSummary.PipelineRef(
                entity.getPipeline().getId(),
                entity.getPipeline().getName(),
                entity.getPipeline().getImportantTags() == null ? List.of() : entity.getPipeline().getImportantTags(),
                entity.getPipeline().getProject() == null ? null : new DeploymentListSummary.ProjectRef(
                        entity.getPipeline().getProject().getId(),
                        entity.getPipeline().getProject().getName()
                ),
                entity.getPipeline().getTemplatePluginId(),
                entity.getPipeline().getTemplate() == null ? null : new DeploymentListSummary.TemplateRef(
                        entity.getPipeline().getTemplate().getId(),
                        entity.getPipeline().getTemplate().getName(),
                        entity.getPipeline().getTemplate().getPluginId()
                )
        );
    }

    private List<DeploymentEntity> enrichTriggeredByDisplayNames(List<DeploymentEntity> deployments) {
        deployments.forEach(this::enrichTriggeredByDisplayName);
        return deployments;
    }

    private DeploymentEntity enrichTriggeredByDisplayName(DeploymentEntity entity) {
        if (entity == null) {
            return null;
        }
        entity.setTriggeredByDisplayName(resolveDisplayName(entity.getTriggeredBy()));
        entity.setStoppedByDisplayName(resolveDisplayName(entity.getStoppedBy()));
        entity.setPipelineName(resolveDeploymentPipelineName(entity));
        entity.setPipelineImportantTags(resolveDeploymentPipelineImportantTags(entity));
        entity.setProjectName(resolveDeploymentProjectName(entity));
        return entity;
    }

    private String resolveDeploymentPipelineName(DeploymentEntity entity) {
        if (entity == null) {
            return null;
        }
        if (entity.getPipelineName() != null && !entity.getPipelineName().isBlank()) {
            return entity.getPipelineName();
        }
        if (entity.getPipeline() != null && entity.getPipeline().getName() != null && !entity.getPipeline().getName().isBlank()) {
            return entity.getPipeline().getName();
        }
        return readSnapshotText(entity.getExecutionSnapshot(), "pipelineName");
    }

    private List<String> resolveDeploymentPipelineImportantTags(DeploymentEntity entity) {
        if (entity == null) {
            return List.of();
        }
        if (entity.getPipelineImportantTags() != null && !entity.getPipelineImportantTags().isEmpty()) {
            return entity.getPipelineImportantTags();
        }
        if (entity.getPipeline() != null && entity.getPipeline().getImportantTags() != null && !entity.getPipeline().getImportantTags().isEmpty()) {
            return entity.getPipeline().getImportantTags();
        }
        Object snapshotValue = entity.getExecutionSnapshot() == null ? null : entity.getExecutionSnapshot().get("pipelineImportantTags");
        if (snapshotValue instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .filter(TextKit::isNotBlank)
                    .toList();
        }
        return List.of();
    }

    private String resolveDeploymentProjectName(DeploymentEntity entity) {
        if (entity == null) {
            return null;
        }
        if (entity.getProjectName() != null && !entity.getProjectName().isBlank()) {
            return entity.getProjectName();
        }
        if (entity.getPipeline() != null
                && entity.getPipeline().getProject() != null
                && entity.getPipeline().getProject().getName() != null
                && !entity.getPipeline().getProject().getName().isBlank()) {
            return entity.getPipeline().getProject().getName();
        }
        return readSnapshotText(entity.getExecutionSnapshot(), "projectName");
    }

    private String readSnapshotText(Map<String, Object> snapshot, String key) {
        if (snapshot == null || snapshot.isEmpty()) {
            return null;
        }
        try {
            Object value = snapshot.get(key);
            return value == null ? null : String.valueOf(value);
        } catch (Exception ex) {
            log.debug("读取部署快照字段 {} 失败：{}", key, ex.getMessage());
            return null;
        }
    }

    private String resolveDisplayName(String username) {
        if (TextKit.isBlank(username)) {
            return null;
        }
        return userRepository.findByUsername(username)
                .map(user -> TextKit.isBlank(user.getDisplayName()) ? user.getUsername() : user.getDisplayName())
                .orElse(username);
    }

    private AuthenticatedUser requireCurrentUser() {
        AuthenticatedUser currentUser = AuthContextHolder.get();
        if (currentUser == null) {
            throw new BusinessException(ErrorSubCode.AUTH_REQUIRED);
        }
        return currentUser;
    }

    private boolean matchesProject(DeploymentEntity item, String projectName) {
        return TextKit.isBlank(projectName)
                || Objects.equals(item.getPipeline() == null || item.getPipeline().getProject() == null ? null : item.getPipeline().getProject().getName(), projectName);
    }

    private boolean matchesPipeline(DeploymentEntity item, String pipelineName) {
        return TextKit.isBlank(pipelineName)
                || Objects.equals(item.getPipeline() == null ? null : item.getPipeline().getName(), pipelineName);
    }

    private boolean matchesTriggeredBy(DeploymentEntity item, String triggeredBy) {
        if (TextKit.isBlank(triggeredBy)) {
            return true;
        }
        String normalized = triggeredBy.trim().toLowerCase();
        String displayName = resolveDisplayName(item.getTriggeredBy());
        if (displayName == null) {
            displayName = "";
        }
        displayName = displayName.toLowerCase();
        String username = item.getTriggeredBy() == null ? "" : item.getTriggeredBy().toLowerCase();
        return displayName.contains(normalized) || username.contains(normalized);
    }

    private boolean matchesBranch(DeploymentEntity item, String branchName) {
        if (TextKit.isBlank(branchName)) {
            return true;
        }
        return item.getBranchName() != null && item.getBranchName().toLowerCase().contains(branchName.trim().toLowerCase());
    }

    private boolean matchesTimeRange(DeploymentEntity item, Long startTime, Long endTime) {
        if (item.getCreatedAt() == null) {
            return startTime == null && endTime == null;
        }
        if (startTime != null) {
            LocalDateTime start = TimeKit.fromEpochMillis(startTime);
            if (item.getCreatedAt().isBefore(start)) {
                return false;
            }
        }
        if (endTime != null) {
            LocalDateTime end = TimeKit.fromEpochMillis(endTime);
            if (item.getCreatedAt().isAfter(end)) {
                return false;
            }
        }
        return true;
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
        String script = """
                #!/usr/bin/env bash
                set -e

                # 校验历史产物目录仍然存在，避免创建一条注定失败的回滚任务。
                echo "[回滚 1/3] 准备历史产物目录"
                if [ ! -d "$SOURCE_ARTIFACT_PATH" ]; then
                  echo "历史构建产物不存在：$SOURCE_ARTIFACT_PATH"
                  exit 1
                fi

                # 将历史构建产物复制到本次发布产物目录，后续仍然复用标准发布脚本。
                echo "[回滚 2/3] 复制历史构建产物到本次发布包"
                mkdir -p "$ARTIFACT_DIR"
                rsync -a "$SOURCE_ARTIFACT_PATH/" "$ARTIFACT_DIR/"

                echo "[回滚 3/3] 历史构建产物已就绪，开始重新发布"
                """;
        return renderDeploymentScriptTemplate(script, variables);
    }

    private String resolveServiceName(PipelineEntity pipeline) {
        if (pipeline == null || pipeline.getId() == null) {
            return "service";
        }
        return "pipeline-" + pipeline.getId();
    }

    private String resolveTargetDir(PipelineEntity pipeline) {
        String targetDir = TextKit.trimToNull(pipeline == null ? null : pipeline.getTargetDir());
        return targetDir == null ? "" : targetDir;
    }

    private String serializePipelineTags(PipelineEntity pipeline) {
        List<String> tags = pipeline == null || pipeline.getTags() == null ? List.of() : pipeline.getTags();
        return String.join(",", tags.stream()
                .filter(TextKit::isNotBlank)
                .map(String::trim)
                .toList());
    }

    private void putPluginConfigVariables(Map<String, String> variables, PipelineEntity pipeline) {
        Map<String, String> pluginConfig = pipeline == null || pipeline.getPluginConfig() == null ? Map.of() : pipeline.getPluginConfig();
        pluginConfig.forEach((key, value) -> {
            if (TextKit.isNotBlank(key)) {
                variables.put(key, value == null ? "" : value);
            }
        });
    }

    private String encodeMavenSettingsXml(PipelineEntity pipeline) {
        if (pipeline.getMavenSettings() == null || pipeline.getMavenSettings().getContentXml() == null || pipeline.getMavenSettings().getContentXml().isBlank()) {
            return "";
        }
        return Base64.getEncoder().encodeToString(pipeline.getMavenSettings().getContentXml().getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, Object> buildExecutionSnapshot(PipelineEntity pipeline, String branch, Map<String, String> variables) {
        PipelineTemplateResolverService.ResolvedPipelineTemplate resolvedTemplate = pipelineTemplateResolverService.resolveRequired(pipeline);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("pipelineName", pipeline.getName());
        snapshot.put("pipelineImportantTags", pipeline.getImportantTags() == null ? List.of() : pipeline.getImportantTags());
        snapshot.put("projectName", pipeline.getProject() == null ? null : pipeline.getProject().getName());
        snapshot.put("pluginId", resolvedTemplate.pluginId());
        snapshot.put("templateName", resolvedTemplate.name());
        snapshot.put("templateType", resolvedTemplate.templateType());
        snapshot.put("branch", branch);
        snapshot.put("targetHost", pipeline.getTargetHost() == null ? "本机" : pipeline.getTargetHost().getName());
        snapshot.put("targetHostType", pipeline.getTargetHost() == null ? "LOCAL" : pipeline.getTargetHost().getType());
        snapshot.put("targetDir", variables.get("targetDir"));
        snapshot.put("serviceName", variables.get("serviceName"));
        snapshot.put("pluginConfig", pipeline.getPluginConfig() == null ? Map.of() : pipeline.getPluginConfig());
        snapshot.put("startupKeyword", pipeline.getStartupKeyword());
        snapshot.put("startupTimeoutSeconds", pipeline.getStartupTimeoutSeconds());
        snapshot.put("javaEnvironment", summarizeRuntimeEnvironment(pipeline.getJavaEnvironment()));
        snapshot.put("nodeEnvironment", summarizeRuntimeEnvironment(pipeline.getNodeEnvironment()));
        snapshot.put("mavenEnvironment", summarizeRuntimeEnvironment(pipeline.getMavenEnvironment()));
        snapshot.put("mavenSettings", summarizeMavenSettings(pipeline));
        snapshot.put("runtimeJavaEnvironment", summarizeRuntimeEnvironment(pipeline.getRuntimeJavaEnvironment()));
        snapshot.put("buildRuntimeEnvironments", summarizeRuntimeEnvironments(pipeline, true));
        snapshot.put("targetRuntimeEnvironments", summarizeRuntimeEnvironments(pipeline, false));

        snapshot.put("variables", buildExecutionSnapshotVariables(pipeline, variables));

        return snapshot;
    }

    private List<Map<String, Object>> summarizeRuntimeEnvironments(PipelineEntity pipeline, boolean buildStage) {
        Set<String> requiredTypes = requiredRuntimeTypeSet(pipeline, buildStage);
        return runtimeBindings(buildStage).stream()
                .filter(binding -> requiredTypes.contains(binding.prefix()))
                .map(binding -> summarizeRuntimeEnvironment(binding.environment(pipeline)))
                .filter(Objects::nonNull)
                .toList();
    }

    private List<Map<String, Object>> buildExecutionSnapshotVariables(PipelineEntity pipeline, Map<String, String> variables) {
        String variablesSchema = pipelineTemplateResolverService.resolveRequired(pipeline).variablesSchema();
        List<Map<String, Object>> result = new ArrayList<>();
        for (TemplateVariableSchemaItem item : TemplateVariableSchemaKit.read(variablesSchema)) {
            String name = item == null ? null : ObjectKit.stringValue(item.name());
            if (name == null) {
                continue;
            }
            addExecutionSnapshotVariable(result, item, variables, name);
        }
        return result;
    }

    private void addExecutionSnapshotVariable(
            List<Map<String, Object>> result,
            TemplateVariableSchemaItem schemaItem,
            Map<String, String> variables,
            String key
    ) {
        String value = variables.get(key);
        if (value == null || value.isBlank()) {
            return;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", key);
        item.put("label", TextKit.isNotBlank(schemaItem == null ? null : schemaItem.label()) ? schemaItem.label() : key);
        item.put("value", value);
        result.add(item);
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

    private Map<String, Object> summarizeMavenSettings(PipelineEntity pipeline) {
        if (pipeline.getMavenSettings() == null) {
            return null;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("name", pipeline.getMavenSettings().getName());
        summary.put("enabled", pipeline.getMavenSettings().getEnabled());
        summary.put("description", pipeline.getMavenSettings().getDescription());
        return summary;
    }

    private record RuntimeEnvironmentBinding(
            String prefix,
            String homeKey,
            String label,
            String displayName,
            Function<PipelineEntity, RuntimeEnvironmentEntity> environmentResolver,
            List<String> diagnosticCommands
    ) {
        private RuntimeEnvironmentEntity environment(PipelineEntity pipeline) {
            return pipeline == null ? null : environmentResolver.apply(pipeline);
        }
    }

}
