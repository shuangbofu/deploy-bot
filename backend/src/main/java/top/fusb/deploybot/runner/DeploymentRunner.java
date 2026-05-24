package top.fusb.deploybot.runner;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.kit.ProcessKit;
import top.fusb.deploybot.kit.ShellKit;
import top.fusb.deploybot.kit.TextKit;
import top.fusb.deploybot.kit.TimeKit;
import top.fusb.deploybot.model.DeploymentEntity;
import top.fusb.deploybot.model.DeploymentStatus;
import top.fusb.deploybot.model.HostEntity;
import top.fusb.deploybot.model.HostSshAuthType;
import top.fusb.deploybot.model.HostType;
import top.fusb.deploybot.model.ServiceEntity;
import top.fusb.deploybot.notification.model.NotificationEventType;
import top.fusb.deploybot.repo.DeploymentRepository;
import top.fusb.deploybot.notification.service.DeploymentNotificationAsyncService;
import top.fusb.deploybot.service.GitCredentialService;
import top.fusb.deploybot.service.HostService;
import top.fusb.deploybot.service.DeploymentCleanupService;
import top.fusb.deploybot.service.DeploymentPluginBridgeService;
import top.fusb.deploybot.service.ServiceManager;
import top.fusb.deploybot.service.SystemSettingsService;
import top.fusb.deploybot.plugin.api.process.ProcessLocatorResult;
import top.fusb.deploybot.plugin.api.deployment.DeploymentPluginPlan;
import top.fusb.deploybot.plugin.api.startup.StartupJudgeResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Component
@RequiredArgsConstructor
public class DeploymentRunner {
    private static final Logger log = LoggerFactory.getLogger(DeploymentRunner.class);
    private static final String LOG_DIR = "logs";
    private static final String SCRIPT_DIR = "scripts";
    private static final String SSH_DIR = "ssh";
    private static final String ARTIFACT_DIR = "artifacts";
    private static final long PID_DISCOVERY_TIMEOUT_MILLIS = 30_000L;
    private static final long DEFAULT_STARTUP_TIMEOUT_MILLIS = 30_000L;
    private static final long MONITOR_INTERVAL_MILLIS = 2_000L;
    private static final long PROCESS_STABLE_OBSERVE_MILLIS = 3_000L;
    private static final int STARTUP_LOG_BUFFER_LIMIT = 256 * 1024;

    private final DeploymentRepository deploymentRepository;
    private final ServiceManager serviceManager;
    private final SystemSettingsService systemSettingsService;
    private final GitCredentialService gitCredentialService;
    private final HostService hostService;
    private final DeploymentCleanupService deploymentCleanupService;
    private final DeploymentNotificationAsyncService deploymentNotificationAsyncService;
    private final DeploymentPluginBridgeService deploymentPluginBridgeService;
    private final Map<Long, Process> runningProcesses = new ConcurrentHashMap<>();
    private final Map<Long, String> stopRequests = new ConcurrentHashMap<>();
    @Value("${deploybot.workspace-root:./runtime}")
    private String workspaceRoot;
    private Path defaultWorkspaceRoot;

    private record StartupLogCursor(String runtimeLogPath, long nextOffset) {
    }

    private record StartupLogReadResult(StartupLogCursor cursor, String content) {
    }

    @PostConstruct
    public void initDefaultWorkspaceRoot() {
        this.defaultWorkspaceRoot = Path.of(workspaceRoot);
    }

    @Async
    public void runAsync(Long deploymentId) {
        Path logFile = null;
        boolean deployStageExecuted = false;
        try {
            // 1. 读取部署与主机上下文，准备运行时目录。
            DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
                    .orElseThrow(() -> new BusinessException(ErrorSubCode.DEPLOYMENT_NOT_FOUND));
            if (deployment.getStatus() == DeploymentStatus.STOPPED) {
                log.info("部署 {} 已经处于停止状态，跳过执行。", deploymentId);
                return;
            }
            HostEntity targetHost = deployment.getPipeline().getTargetHost();
            log.info(
                    "Deployment {} started. pipeline='{}', project='{}', template='{}', host='{}'.",
                    deploymentId,
                    deployment.getPipeline().getName(),
                    deployment.getPipeline().getProject() == null ? "-" : deployment.getPipeline().getProject().getName(),
                    deployment.getPipeline().getTemplateNameSnapshot() == null ? "-" : deployment.getPipeline().getTemplateNameSnapshot(),
                    targetHost == null ? "本机" : targetHost.getName()
            );
            Path buildWorkspaceRoot = resolveLocalWorkspaceRoot();
            Path deployWorkspaceRoot = resolveTargetWorkspaceRoot(targetHost, buildWorkspaceRoot);
            Files.createDirectories(buildWorkspaceRoot);
            Path logsDir = buildWorkspaceRoot.resolve(LOG_DIR);
            Path scriptsDir = buildWorkspaceRoot.resolve(SCRIPT_DIR);
            Path sshDir = buildWorkspaceRoot.resolve(SSH_DIR).resolve("deploy-" + deploymentId);
            Path artifactsDir = buildWorkspaceRoot.resolve(ARTIFACT_DIR).resolve("deploy-" + deploymentId);
            Files.createDirectories(logsDir);
            Files.createDirectories(scriptsDir);
            Files.createDirectories(sshDir);
            Files.createDirectories(artifactsDir);

            logFile = logsDir.resolve("deploy-" + deploymentId + ".log");
            Path buildScriptFile = scriptsDir.resolve("deploy-" + deploymentId + "-build.sh");
            Path deployScriptFile = scriptsDir.resolve("deploy-" + deploymentId + "-deploy.sh");
            Files.writeString(buildScriptFile, deployment.getRenderedBuildScript(), StandardCharsets.UTF_8);
            buildScriptFile.toFile().setExecutable(true);
            log.info("Deployment {} build script written to {}.", deploymentId, buildScriptFile.toAbsolutePath().normalize());
            if (deployment.getRenderedDeployScript() != null && !deployment.getRenderedDeployScript().isBlank()) {
                Files.writeString(deployScriptFile, deployment.getRenderedDeployScript(), StandardCharsets.UTF_8);
                deployScriptFile.toFile().setExecutable(true);
                log.info("Deployment {} deploy script written to {}.", deploymentId, deployScriptFile.toAbsolutePath().normalize());
            }

            deployment.setStatus(DeploymentStatus.RUNNING);
            deployment.setStartedAt(LocalDateTime.now());
            deployment.setLogPath(logFile.toAbsolutePath().toString());
            deploymentRepository.save(deployment);
            deploymentNotificationAsyncService.notifyAsync(deployment.getId(), NotificationEventType.DEPLOYMENT_STARTED);
            appendSystemLog(logFile, "部署任务已开始，日志文件：" + logFile.toAbsolutePath().normalize());
            appendDeploymentPluginLog(logFile, deployment);
            log.info("Deployment {} log file initialized at {}.", deploymentId, logFile.toAbsolutePath().normalize());

            // 2. 每次部署都会保留构建产物，后续重新发布历史版本时直接复用该产物。
            appendSystemLog(logFile, "本次部署将保留构建产物，供后续重新发布。");
            log.info("部署 {} 本次将保留构建产物，供后续重新发布使用。", deploymentId);

            appendSystemLog(logFile, "开始本机构建阶段。");
            log.info("部署 {} 构建阶段开始。", deploymentId);
            int exitCode = runProcess(
                    buildLocalBuildProcessBuilder(deployment, buildWorkspaceRoot, buildScriptFile, sshDir),
                    null,
                    logFile,
                    deploymentId
            );
            log.info("部署 {} 构建阶段结束，退出码={}.", deploymentId, exitCode);
            deployment = refreshDeploymentState(deployment);
            if (isStopRequested(deploymentId) || deployment.getStatus() == DeploymentStatus.STOPPED) {
                deployment.setStatus(DeploymentStatus.STOPPED);
                if (deployment.getFinishedAt() == null) {
                    deployment.setFinishedAt(LocalDateTime.now());
                }
                appendSystemLog(logFile, "部署已停止。");
                deploymentRepository.save(deployment);
                deploymentCleanupService.cleanupAfterDeployment(deployment, buildWorkspaceRoot);
                return;
            }
            if (exitCode == 0 && deployment.getRenderedDeployScript() != null && !deployment.getRenderedDeployScript().isBlank()) {
                if (Boolean.TRUE.equals(deployment.getPipeline().getTemplateMonitorProcess())) {
                    ServiceEntity stoppedService = serviceManager.stopManagedServiceBeforeDeploy(deployment);
                    if (stoppedService != null) {
                        appendSystemLog(logFile, "本次发布前已停止旧服务，serviceId=" + stoppedService.getId() + "，pid=" + (stoppedService.getCurrentPid() == null ? "-" : stoppedService.getCurrentPid()) + "。");
                        log.info(
                                "流水线 '{}' 在构建成功后、发布开始前已由系统停止旧服务：serviceId={}，pid={}。",
                                deployment.getPipeline().getName(),
                                stoppedService.getId(),
                                stoppedService.getCurrentPid()
                        );
                    }
                }
                appendSystemLog(logFile, "本机构建完成，开始发布阶段。");
                log.info("部署 {} 发布阶段开始。", deploymentId);
                deployStageExecuted = true;
                if (targetHost != null && targetHost.getType() == HostType.SSH) {
                    Path remoteArtifactDir = deployWorkspaceRoot.resolve(ARTIFACT_DIR).resolve("deploy-" + deploymentId).toAbsolutePath().normalize();
                    prepareRemoteExecutionDirectories(targetHost, remoteArtifactDir, sshDir, logFile, deploymentId);
                    syncArtifactsToRemote(targetHost, artifactsDir, remoteArtifactDir, sshDir, logFile, deploymentId);
                    log.info("部署 {} 构建产物同步完成。目标主机='{}'，远端产物目录='{}'。", deploymentId, targetHost.getHostname(), remoteArtifactDir);
                    exitCode = runProcess(
                            buildRemoteDeployProcessBuilder(targetHost, sshDir),
                            deployment.getRenderedDeployScript(),
                            logFile,
                            deploymentId
                    );
                } else {
                    exitCode = runProcess(
                        buildLocalDeployProcessBuilder(buildWorkspaceRoot, deployScriptFile),
                        null,
                        logFile,
                        deploymentId
                    );
                }
                log.info("部署 {} 发布阶段结束，退出码={}.", deploymentId, exitCode);
                deployment = refreshDeploymentState(deployment);
                if (isStopRequested(deploymentId) || deployment.getStatus() == DeploymentStatus.STOPPED) {
                    deployment.setStatus(DeploymentStatus.STOPPED);
                    if (deployment.getFinishedAt() == null) {
                        deployment.setFinishedAt(LocalDateTime.now());
                    }
                    appendSystemLog(logFile, "部署已停止。");
                    deploymentRepository.save(deployment);
                    deploymentCleanupService.cleanupAfterDeployment(deployment, buildWorkspaceRoot);
                    return;
                }
            }
            runningProcesses.remove(deploymentId);
            LocalDateTime finishedAt = LocalDateTime.now();
            deployment = refreshDeploymentState(deployment);
            deployment.setFinishedAt(finishedAt);
            if (isStopRequested(deploymentId) || deployment.getStatus() == DeploymentStatus.STOPPED) {
                deployment.setStatus(DeploymentStatus.STOPPED);
                appendSystemLog(logFile, "部署已停止。");
                deploymentRepository.save(deployment);
                deploymentCleanupService.cleanupAfterDeployment(deployment, buildWorkspaceRoot);
                return;
            }
            if (exitCode == 0) {
                if (Boolean.TRUE.equals(deployment.getPipeline().getTemplateMonitorProcess())) {
                    appendSystemLog(logFile, "启动命令已执行，开始进行服务自检。");
                    log.info(
                            "Deployment {} service monitoring started. startupTimeoutSeconds={}.",
                            deploymentId,
                            deployment.getPipeline().getStartupTimeoutSeconds()
                    );
                    ServiceVerificationResult verificationResult = verifyMonitoredProcess(deployment, targetHost, deploymentId, logFile);
                    deployment = refreshDeploymentState(deployment);
                    deployment.setFinishedAt(finishedAt);
                    if (isStopRequested(deploymentId) || deployment.getStatus() == DeploymentStatus.STOPPED) {
                        deployment.setStatus(DeploymentStatus.STOPPED);
                        appendSystemLog(logFile, "部署已停止。");
                        deploymentRepository.save(deployment);
                        deploymentCleanupService.cleanupAfterDeployment(deployment, buildWorkspaceRoot);
                        return;
                    }
                    if (verificationResult == null || !verificationResult.success()) {
                        deployment.setStatus(DeploymentStatus.FAILED);
                        deployment.setErrorMessage(ErrorSubCode.DEPLOYMENT_MONITORED_PROCESS_NOT_RUNNING.getMessage());
                        appendSystemLog(logFile, "部署失败：服务启动后未检测到可用进程。");
                        if (deployStageExecuted) {
                            stopRuntimeProcessQuietly(deployment, logFile, "启动观察未通过，正在清理本次启动出来的进程。");
                        }
                        log.warn("部署 {} 在服务监测阶段失败，未确认到稳定可接管的进程。", deploymentId);
                    } else {
                        if (verificationResult.managedService() && verificationResult.monitoredPid() != null) {
                            deployment.setMonitoredPid(verificationResult.monitoredPid());
                            serviceManager.updateFromDeployment(deployment, verificationResult.monitoredPid());
                        }
                        deployment.setStatus(DeploymentStatus.SUCCESS);
                        appendSystemLog(logFile, "发布阶段执行完成。");
                        appendSystemLog(logFile, "部署完成。");
                        log.info(
                                "部署 {} 成功完成，服务接管={}，受管进程 PID={}。",
                                deploymentId,
                                verificationResult.managedService(),
                                verificationResult.monitoredPid()
                        );
                    }
                } else {
                    DeploymentPluginPlan plan = deploymentPluginBridgeService.resolveEffectiveServicePlan(deployment);
                    deployment.setStatus(DeploymentStatus.SUCCESS);
                    appendSystemLog(logFile, "发布阶段执行完成。");
                    if (plan != null && !plan.processLocatorEnabled() && !plan.startupJudgeEnabled()) {
                        appendSystemLog(logFile, "当前插件未声明 PID 检测与启动判定能力，已跳过服务检测步骤。");
                    } else {
                        appendSystemLog(logFile, "当前模板未启用服务监测，已跳过 PID 检测与启动判定。");
                    }
                    appendSystemLog(logFile, "部署完成。");
                    log.info(
                            "部署 {} 成功完成，本次未启用服务监测。processLocatorEnabled={}，startupJudgeEnabled={}",
                            deploymentId,
                            plan != null && plan.processLocatorEnabled(),
                            plan != null && plan.startupJudgeEnabled()
                    );
                }
            } else {
                if (isStopRequested(deploymentId)) {
                    deployment.setStatus(DeploymentStatus.STOPPED);
                    deployment.setErrorMessage(null);
                    appendSystemLog(logFile, "部署已停止。");
                    log.info("部署 {} 在收到手动停止请求后退出，脚本退出码={} 按停止处理。", deploymentId, exitCode);
                } else {
                    deployment.setStatus(DeploymentStatus.FAILED);
                    deployment.setErrorMessage("Script exited with code " + exitCode);
                    appendSystemLog(logFile, "部署失败，脚本退出码：" + exitCode);
                    if (deployStageExecuted) {
                        stopRuntimeProcessQuietly(deployment, logFile, "发布脚本执行异常，正在清理本次启动出来的进程。");
                    }
                    log.warn("部署 {} 失败，脚本进程退出码={}。", deploymentId, exitCode);
                }
            }
            deploymentRepository.save(deployment);
            deploymentCleanupService.cleanupAfterDeployment(deployment, buildWorkspaceRoot);
            deploymentNotificationAsyncService.notifyAsync(deployment.getId(), NotificationEventType.DEPLOYMENT_FINISHED);
        } catch (Exception ex) {
            runningProcesses.remove(deploymentId);
            log.error("部署 {} 在完成前发生未预期异常。", deploymentId, ex);
            final Path capturedLogFile = logFile;
            final boolean deployStageExecutedFinal = deployStageExecuted;
            deploymentRepository.findById(deploymentId).ifPresent(deployment -> {
                if (isStopRequested(deploymentId) || deployment.getStatus() == DeploymentStatus.STOPPED) {
                    deployment.setStatus(DeploymentStatus.STOPPED);
                    Path resolvedStoppedLogFile = resolveOrCreateLogFile(capturedLogFile, deployment.getLogPath(), deploymentId);
                    if (resolvedStoppedLogFile != null) {
                        try {
                            appendSystemLog(resolvedStoppedLogFile, "任务已手动停止。");
                        } catch (Exception ignored) {
                        }
                    }
                    if (deployment.getFinishedAt() == null) {
                        deployment.setFinishedAt(LocalDateTime.now());
                    }
                    deploymentRepository.save(deployment);
                    deploymentCleanupService.cleanupAfterDeployment(deployment, resolveLocalWorkspaceRoot());
                    return;
                }
                Path resolvedLogFile = resolveOrCreateLogFile(capturedLogFile, deployment.getLogPath(), deploymentId);
                if (resolvedLogFile != null) {
                    try {
                        appendSystemErrorLog(resolvedLogFile, ex);
                        appendSystemLog(resolvedLogFile, "部署失败。");
                        deployment.setLogPath(resolvedLogFile.toAbsolutePath().toString());
                    } catch (Exception ignored) {
                    }
                }
                deployment.setFinishedAt(LocalDateTime.now());
                deployment.setStatus(DeploymentStatus.FAILED);
                deployment.setErrorMessage(ex.getMessage());
                if (deployStageExecutedFinal) {
                    stopRuntimeProcessQuietly(deployment, resolvedLogFile, "部署异常结束，正在清理本次启动出来的进程。");
                }
                deploymentRepository.save(deployment);
                deploymentCleanupService.cleanupAfterDeployment(deployment, resolveLocalWorkspaceRoot());
                deploymentNotificationAsyncService.notifyAsync(deployment.getId(), NotificationEventType.DEPLOYMENT_FINISHED);
            });
        } finally {
            stopRequests.remove(deploymentId);
        }
    }

    /**
     * 远程主机只负责接收产物与执行发布脚本，因此先准备远端产物目录。
     */
    private void prepareRemoteExecutionDirectories(
            HostEntity targetHost,
            Path remoteArtifactDir,
            Path sshDir,
            Path logFile,
            Long deploymentId
    ) throws Exception {
        log.info("Preparing remote directories for deployment {} on host {}.", deploymentId, targetHost.getHostname());
        StringBuilder script = new StringBuilder();
        String escapedArtifactDir = remoteArtifactDir.toString().replace("\"", "\\\"");
        script.append("set -e\n");
        script.append("rm -rf \"").append(escapedArtifactDir).append("\"\n");
        script.append("mkdir -p \"").append(escapedArtifactDir).append("\"\n");

        appendSystemLog(logFile, "准备远程目录：" + remoteArtifactDir);
        int prepareExitCode = runProcess(
                ProcessKit.mergedBuilder(buildSshCommand(targetHost, sshDir)),
                script.toString(),
                logFile,
                deploymentId
        );
        log.info("Remote directory preparation finished for deployment {} with exit code {}.", deploymentId, prepareExitCode);
        if (prepareExitCode != 0) {
            throw new BusinessException(ErrorSubCode.REMOTE_DIRECTORY_PREPARE_FAILED);
        }
    }

    public void stop(Long deploymentId, String stoppedBy) {
        log.info("收到部署 {} 的手动停止请求。", deploymentId);
        stopRequests.put(deploymentId, stoppedBy == null ? "" : stoppedBy);
        deploymentRepository.findById(deploymentId).ifPresent(deployment -> {
            if (deployment.getStatus() != DeploymentStatus.PENDING && deployment.getStatus() != DeploymentStatus.RUNNING) {
                return;
            }
            deployment.setStatus(DeploymentStatus.STOPPED);
            deployment.setStoppedBy(stoppedBy);
            deployment.setFinishedAt(LocalDateTime.now());
            deployment.setErrorMessage(null);
            Path logFile = resolveOrCreateLogFile(null, deployment.getLogPath(), deploymentId);
            if (logFile != null) {
                try {
                    appendSystemLog(logFile, "任务已手动停止。");
                    deployment.setLogPath(logFile.toAbsolutePath().toString());
                } catch (Exception ignored) {
                }
            }
            deploymentRepository.saveAndFlush(deployment);
            runningProcesses.computeIfPresent(deploymentId, (id, process) -> {
                process.destroy();
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
                return process;
            });
            try {
                stopRuntimeProcessIfPresent(deployment);
            } catch (Exception ex) {
                log.warn("部署 {} 在手动停止时清理运行中服务失败：{}", deploymentId, ex.getMessage());
            }
            deploymentCleanupService.cleanupAfterDeployment(deployment, resolveLocalWorkspaceRoot());
            deploymentNotificationAsyncService.notifyAsync(deployment.getId(), NotificationEventType.DEPLOYMENT_FINISHED);
        });
        runningProcesses.remove(deploymentId);
        log.info("部署 {} 手动停止流程结束。", deploymentId);
    }

    private Path resolveOrCreateLogFile(Path preferredLogFile, String existingLogPath, Long deploymentId) {
        try {
            if (preferredLogFile != null) {
                Files.createDirectories(preferredLogFile.getParent());
                return preferredLogFile;
            }
            if (existingLogPath != null && !existingLogPath.isBlank()) {
                Path existingPath = Path.of(existingLogPath);
                Files.createDirectories(existingPath.getParent());
                return existingPath;
            }
            Path fallbackLogsDir = resolveLocalWorkspaceRoot().resolve("logs");
            Files.createDirectories(fallbackLogsDir);
            return fallbackLogsDir.resolve("deploy-" + deploymentId + ".log");
        } catch (Exception ignored) {
            return null;
        }
    }

    private Path resolveLocalWorkspaceRoot() {
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

    private Path resolveTargetWorkspaceRoot(HostEntity targetHost, Path localWorkspaceRoot) {
        if (targetHost != null && targetHost.getWorkspaceRoot() != null && !targetHost.getWorkspaceRoot().isBlank()) {
            return Path.of(targetHost.getWorkspaceRoot().trim());
        }
        return localWorkspaceRoot;
    }

    private ProcessBuilder buildLocalBuildProcessBuilder(DeploymentEntity deployment, Path workspaceRoot, Path scriptFile, Path sshDir) throws Exception {
        ProcessBuilder processBuilder = ProcessKit.mergedBuilder("bash", scriptFile.toAbsolutePath().toString())
                .directory(workspaceRoot.toFile());
        GitCredentialService.GitProcessConfig processConfig = gitCredentialService.buildProcessConfig(
                deployment.getPipeline().getProject(),
                sshDir
        );
        processBuilder.environment().putAll(processConfig.environment());
        processBuilder.environment().put("GIT_TERMINAL_PROMPT", "0");
        processBuilder.environment().put("GIT_ASKPASS", "echo");
        processBuilder.environment().put("DEPLOYBOT_GIT_EXECUTABLE", gitCredentialService.getGitExecutable());
        log.info("已准备部署 {} 的本机构建进程，工作目录={}。", deployment.getId(), workspaceRoot.toAbsolutePath().normalize());
        return processBuilder;
    }

    private ProcessBuilder buildLocalDeployProcessBuilder(Path workspaceRoot, Path scriptFile) {
        ProcessBuilder processBuilder = ProcessKit.mergedBuilder("bash", scriptFile.toAbsolutePath().toString())
                .directory(workspaceRoot.toFile());
        processBuilder.environment().put("GIT_TERMINAL_PROMPT", "0");
        processBuilder.environment().put("GIT_ASKPASS", "echo");
        processBuilder.environment().put("DEPLOYBOT_GIT_EXECUTABLE", gitCredentialService.getGitExecutable());
        log.info("已准备本地发布进程，工作目录={}，脚本={}。", workspaceRoot.toAbsolutePath().normalize(), scriptFile.toAbsolutePath().normalize());
        return processBuilder;
    }

    private ProcessBuilder buildRemoteDeployProcessBuilder(HostEntity targetHost, Path sshDir) throws Exception {
        ProcessBuilder processBuilder = ProcessKit.mergedBuilder(buildSshCommand(targetHost, sshDir));
        processBuilder.environment().put("GIT_TERMINAL_PROMPT", "0");
        processBuilder.environment().put("GIT_ASKPASS", "echo");
        processBuilder.environment().put("DEPLOYBOT_GIT_EXECUTABLE", gitCredentialService.getGitExecutable());
        log.info("已准备远程发布进程，目标主机={}。", targetHost.getHostname());
        return processBuilder;
    }

    private java.util.List<String> buildSshCommand(HostEntity targetHost, Path sshDir) throws Exception {
        var settings = systemSettingsService.get();
        Files.createDirectories(sshDir);
        Path privateKey = sshDir.resolve("id_host");
        Path knownHosts = sshDir.resolve("known_hosts");
        HostSshAuthType authType = targetHost.getSshAuthType() == null ? HostSshAuthType.SYSTEM_KEY_PAIR : targetHost.getSshAuthType();
        prepareHostKey(targetHost, settings, authType, privateKey);
        if (targetHost.getSshKnownHosts() != null && !targetHost.getSshKnownHosts().isBlank()) {
            Files.writeString(knownHosts, targetHost.getSshKnownHosts().trim() + "\n", StandardCharsets.UTF_8);
        }

        java.util.List<String> command = new java.util.ArrayList<>();
        if (authType == HostSshAuthType.PASSWORD) {
            command.add("sshpass");
            command.add("-p");
            command.add(targetHost.getSshPassword() == null ? "" : targetHost.getSshPassword());
        }
        command.add("ssh");
        command.add("-o");
        command.add(authType == HostSshAuthType.PASSWORD ? "BatchMode=no" : "BatchMode=yes");
        if (Files.exists(privateKey)) {
            command.add("-i");
            command.add(privateKey.toAbsolutePath().toString());
            command.add("-o");
            command.add("IdentitiesOnly=yes");
        }
        if (targetHost.getPort() != null) {
            command.add("-p");
            command.add(String.valueOf(targetHost.getPort()));
        }
        if (Files.exists(knownHosts)) {
            command.add("-o");
            command.add("StrictHostKeyChecking=yes");
            command.add("-o");
            command.add("UserKnownHostsFile=" + knownHosts.toAbsolutePath());
        } else {
            command.add("-o");
            command.add("StrictHostKeyChecking=no");
        }
        String userAtHost = (targetHost.getUsername() == null || targetHost.getUsername().isBlank())
                ? targetHost.getHostname()
                : targetHost.getUsername().trim() + "@" + targetHost.getHostname().trim();
        command.add(userAtHost);
        command.add("bash -s");
        log.info("已构建 SSH 命令，目标主机={}，认证方式={}。", targetHost.getHostname(), authType);
        return command;
    }

    private java.util.List<String> buildArtifactSyncCommand(HostEntity targetHost, Path sshDir, Path localSource, String remoteTarget) throws Exception {
        var settings = systemSettingsService.get();
        Files.createDirectories(sshDir);
        Path privateKey = sshDir.resolve("id_host");
        Path knownHosts = sshDir.resolve("known_hosts");
        HostSshAuthType authType = targetHost.getSshAuthType() == null ? HostSshAuthType.SYSTEM_KEY_PAIR : targetHost.getSshAuthType();
        prepareHostKey(targetHost, settings, authType, privateKey);
        if (targetHost.getSshKnownHosts() != null && !targetHost.getSshKnownHosts().isBlank()) {
            Files.writeString(knownHosts, targetHost.getSshKnownHosts().trim() + "\n", StandardCharsets.UTF_8);
        }

        String userAtHost = (targetHost.getUsername() == null || targetHost.getUsername().isBlank())
                ? targetHost.getHostname()
                : targetHost.getUsername().trim() + "@" + targetHost.getHostname().trim();
        StringBuilder sshCommand = new StringBuilder();
        if (authType == HostSshAuthType.PASSWORD) {
            sshCommand.append("sshpass -p ")
                    .append(ShellKit.singleQuote(targetHost.getSshPassword() == null ? "" : targetHost.getSshPassword()))
                    .append(" ");
        }
        sshCommand.append("ssh -o BatchMode=")
                .append(authType == HostSshAuthType.PASSWORD ? "no" : "yes")
                .append(" -o ConnectTimeout=30 ");
        if (Files.exists(privateKey)) {
            sshCommand.append("-i ").append(ShellKit.singleQuote(privateKey.toAbsolutePath().toString())).append(" ");
            sshCommand.append("-o IdentitiesOnly=yes ");
        }
        if (targetHost.getPort() != null) {
            sshCommand.append("-p ").append(targetHost.getPort()).append(" ");
        }
        if (Files.exists(knownHosts)) {
            sshCommand.append("-o StrictHostKeyChecking=yes ");
            sshCommand.append("-o UserKnownHostsFile=").append(ShellKit.singleQuote(knownHosts.toAbsolutePath().toString())).append(" ");
        } else {
            sshCommand.append("-o StrictHostKeyChecking=no ");
        }
        sshCommand.append(ShellKit.singleQuote(userAtHost)).append(" ");
        sshCommand.append(ShellKit.singleQuote("mkdir -p " + ShellKit.singleQuote(remoteTarget) + " && tar -xf - -C " + ShellKit.singleQuote(remoteTarget)));
        String shellCommand = "tar -cf - -C "
                + ShellKit.singleQuote(localSource.toAbsolutePath().normalize().toString())
                + " . | "
                + sshCommand;
        log.info("Built artifact sync command to stream {} to {}:{}", localSource.toAbsolutePath().normalize(), userAtHost, remoteTarget);
        return java.util.List.of("bash", "-lc", shellCommand);
    }

    private void prepareHostKey(HostEntity targetHost, top.fusb.deploybot.model.SystemSettingsEntity settings, HostSshAuthType authType, Path privateKey) throws Exception {
        String keyContent = null;
        if (authType == HostSshAuthType.PRIVATE_KEY) {
            keyContent = targetHost.getSshPrivateKey();
        } else if (authType == HostSshAuthType.SYSTEM_KEY_PAIR) {
            keyContent = settings.getHostSshPrivateKey();
        }
        if (keyContent == null || keyContent.isBlank()) {
            return;
        }
        Files.writeString(privateKey, keyContent.replace("\r\n", "\n").trim() + "\n", StandardCharsets.UTF_8);
        try {
            Files.setPosixFilePermissions(privateKey, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        } catch (Exception ignored) {
        }
    }

    private void appendSystemLog(Path logFile, String message) throws Exception {
        Files.writeString(
                logFile,
                "[系统] " + TimeKit.formatDateTime(LocalDateTime.now()) + " " + message + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    private void appendDeploymentPluginLog(Path logFile, DeploymentEntity deployment) {
        try {
            DeploymentPluginPlan plan = deploymentPluginBridgeService.resolvePlan(deployment);
            if (plan == null) {
                appendSystemLog(logFile, "未命中部署插件，将按基础部署流程执行。");
                return;
            }
            appendDeploymentPluginBanners(logFile, plan);
            appendSystemLog(logFile, "使用部署插件：" + plan.displayName() + "（" + plan.pluginId() + "）。");
            if (plan.children() != null && !plan.children().isEmpty()) {
                String children = plan.children().stream()
                        .map(item -> item.displayName() + "（" + item.pluginId() + "）")
                        .collect(java.util.stream.Collectors.joining(" / "));
                appendSystemLog(logFile, "组合插件子计划：" + children + "。");
            }
            if (plan.processLocatorPluginId() != null && !plan.processLocatorPluginId().isBlank()) {
                appendSystemLog(logFile, "PID 检测插件：" + plan.processLocatorPluginId() + "。");
            }
            if (plan.startupJudgePluginId() != null && !plan.startupJudgePluginId().isBlank()) {
                appendSystemLog(logFile, "启动判定插件：" + plan.startupJudgePluginId() + "。");
            }
        } catch (Exception ex) {
            log.warn("部署 {} 写入插件使用日志失败：{}", deployment == null ? null : deployment.getId(), ex.getMessage(), ex);
        }
    }

    private void appendDeploymentPluginBanners(Path logFile, DeploymentPluginPlan plan) {
        Set<String> pluginIds = new LinkedHashSet<>();
        collectPlanPluginIds(plan, pluginIds);
        for (String pluginId : pluginIds) {
            String bannerText = deploymentPluginBridgeService.resolveBannerText(pluginId);
            if (bannerText == null || bannerText.isBlank()) {
                continue;
            }
            appendRawLog(logFile, bannerText.stripTrailing() + System.lineSeparator());
        }
    }

    private void collectPlanPluginIds(DeploymentPluginPlan plan, Set<String> pluginIds) {
        if (plan == null || plan.pluginId() == null || plan.pluginId().isBlank()) {
            return;
        }
        pluginIds.add(plan.pluginId());
        if (plan.children() == null) {
            return;
        }
        plan.children().forEach(child -> collectPlanPluginIds(child, pluginIds));
    }

    private void appendRawLog(Path logFile, String content) {
        try {
            Files.writeString(
                    logFile,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (Exception ignored) {
        }
    }

    private void appendSystemErrorLog(Path logFile, Exception ex) throws Exception {
        Files.writeString(
                logFile,
                "[系统] 部署执行异常：" + ex.getMessage() + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
        for (StackTraceElement element : ex.getStackTrace()) {
            Files.writeString(
                    logFile,
                    "    at " + element + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        }
    }

    private Long readMonitoredPid(DeploymentEntity deployment, HostEntity targetHost, Long deploymentId) {
        try {
            ProcessLocatorResult pluginResult = deploymentPluginBridgeService.locateProcess(deployment, targetHost);
            if (pluginResult != null) {
                if (pluginResult.pid() != null) {
                    log.info(
                            "部署 {} 通过插件运行时定位到受管 PID={}，来源={}。",
                            deploymentId,
                            pluginResult.pid(),
                            pluginResult.sourceDescription()
                    );
                    return pluginResult.pid();
                }
                if (TextKit.isNotBlank(pluginResult.diagnostics())) {
                    log.info(
                            "部署 {} 插件运行时未命中 PID。来源={}，诊断={}",
                            deploymentId,
                            pluginResult.sourceDescription(),
                            pluginResult.diagnostics()
                    );
                }
                return null;
            }
            log.info("部署 {} 当前没有可用的 PID 检测插件。", deploymentId);
            return null;
        } catch (Exception ex) {
            log.warn("部署 {} 读取受管 PID 失败：{}", deploymentId, ex.getMessage());
            return null;
        }
    }

    private ServiceVerificationResult verifyMonitoredProcess(DeploymentEntity deployment, HostEntity targetHost, Long deploymentId, Path logFile) {
        DeploymentPluginPlan servicePlan = deploymentPluginBridgeService.resolveEffectiveServicePlan(deployment);
        if (servicePlan != null && !servicePlan.processLocatorEnabled() && !servicePlan.startupJudgeEnabled()) {
            try {
                appendSystemLog(logFile, "当前插件未声明 PID 检测与启动判定能力，跳过服务检测。");
            } catch (Exception ignored) {
            }
            log.info("部署 {} 当前插件计划 '{}' 未声明服务检测步骤，直接跳过 PID 检测与启动判定。", deploymentId, servicePlan.pluginId());
            return ServiceVerificationResult.skipped();
        }
        if (servicePlan != null && !servicePlan.processLocatorEnabled()) {
            try {
                appendSystemLog(logFile, "当前插件未声明 PID 检测能力，跳过服务检测。");
            } catch (Exception ignored) {
            }
            log.info("部署 {} 当前插件计划 '{}' 未声明 PID 检测能力，跳过服务检测。", deploymentId, servicePlan.pluginId());
            return ServiceVerificationResult.skipped();
        }
        Long monitoredPid = waitForMonitoredPid(deployment, targetHost, deploymentId, logFile);
        if (monitoredPid == null) {
            try {
                appendSystemLog(logFile, "服务检测超时，未能获取到可接管的进程 PID。");
            } catch (Exception ignored) {
            }
            return ServiceVerificationResult.failed();
        }
        deployment.setMonitoredPid(monitoredPid);
        deploymentRepository.save(deployment);
        log.info("部署 {} 已提前记录候选受管进程 PID={}，后续即使启动观察失败也可用于清理。", deploymentId, monitoredPid);
        if (servicePlan != null && !servicePlan.startupJudgeEnabled()) {
            try {
                appendSystemLog(logFile, "当前插件未声明启动判定能力，已确认 PID 后跳过启动观察。");
            } catch (Exception ignored) {
            }
            log.info("部署 {} 当前插件计划 '{}' 未声明启动判定能力，确认 PID={} 后跳过启动观察。", deploymentId, servicePlan.pluginId(), monitoredPid);
            return ServiceVerificationResult.managed(monitoredPid);
        }
        Long verifiedPid = observeStartupWindow(deployment, targetHost, monitoredPid, logFile);
        if (verifiedPid == null) {
            return ServiceVerificationResult.failed();
        }
        return ServiceVerificationResult.managed(verifiedPid);
    }

    private Long waitForMonitoredPid(DeploymentEntity deployment, HostEntity targetHost, Long deploymentId, Path logFile) {
        long startedAt = System.currentTimeMillis();
        long deadline = startedAt + PID_DISCOVERY_TIMEOUT_MILLIS;
        int attempt = 0;
        try {
            appendSystemLog(logFile, "开始检测服务进程。");
        } catch (Exception ignored) {
        }
        log.info("部署 {} 开始检测服务 PID。{}", deploymentId, buildPidDiscoveryBanner(deployment, targetHost));
        while (System.currentTimeMillis() <= deadline) {
            if (isStopRequested(deploymentId)) {
                try {
                    appendSystemLog(logFile, "已收到手动停止请求，结束服务 PID 检测。");
                } catch (Exception ignored) {
                }
                return null;
            }
            attempt++;
            Long monitoredPid = readMonitoredPid(deployment, targetHost, deploymentId);
            if (monitoredPid != null) {
                try {
                    appendSystemLog(logFile, "检测到候选进程 PID " + monitoredPid + "，开始进入启动观察窗口。");
                } catch (Exception ignored) {
                }
                log.info("部署 {} 在第 {} 次 PID 检测时成功获取到 PID={}。", deploymentId, attempt, monitoredPid);
                return monitoredPid;
            }
            try {
                long elapsedSeconds = (System.currentTimeMillis() - startedAt) / 1000;
                appendSystemLog(logFile, "第 " + attempt + " 次服务检测未命中 PID，已等待 " + elapsedSeconds + " 秒。");
            } catch (Exception ignored) {
            }
            log.info(
                    "部署 {} 第 {} 次 PID 检测未命中，已等待 {} 秒。来源={}",
                    deploymentId,
                    attempt,
                    (System.currentTimeMillis() - startedAt) / 1000,
                    describePidDiscoverySource(deployment)
            );
            if (System.currentTimeMillis() >= deadline) {
                break;
            }
            try {
                Thread.sleep(MONITOR_INTERVAL_MILLIS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.warn("部署 {} 的 PID 检测超时，超时时间={}毫秒。", deploymentId, PID_DISCOVERY_TIMEOUT_MILLIS);
        return null;
    }

    private String buildPidDiscoveryBanner(DeploymentEntity deployment, HostEntity targetHost) {
        StringBuilder builder = new StringBuilder("开始检测服务 PID。");
        builder.append(" 目标主机类型=").append(targetHost != null ? targetHost.getType() : HostType.LOCAL);
        builder.append("，检测来源=").append(describePidDiscoverySource(deployment));
        return builder.toString();
    }

    private String describePidDiscoverySource(DeploymentEntity deployment) {
        DeploymentPluginPlan plan = deploymentPluginBridgeService.resolveEffectiveServicePlan(deployment);
        if (plan == null || !plan.processLocatorEnabled()) {
            return "未声明 PID 检测插件";
        }
        return plan.processLocatorPluginId();
    }

    private Long observeStartupWindow(DeploymentEntity deployment, HostEntity targetHost, Long monitoredPid, Path logFile) {
        long startedAt = System.currentTimeMillis();
        long configuredTimeoutMillis = resolveStartupTimeoutMillis(deployment);
        int attempt = 0;
        StringBuilder startupOutputBuffer = new StringBuilder();
        StartupLogCursor logCursor = initializeStartupLogCursor(deployment);
        StartupJudgeResult startupJudge = deploymentPluginBridgeService.resolveStartupJudge(
                deployment,
                monitoredPid,
                logCursor == null ? null : logCursor.runtimeLogPath()
        );
        String startupKeyword = startupJudge == null ? null : startupJudge.keyword();
        boolean keywordRequired = startupJudge != null && startupJudge.keywordRequired();
        long observeWindowMillis = keywordRequired
                ? configuredTimeoutMillis
                : Math.min(configuredTimeoutMillis, PROCESS_STABLE_OBSERVE_MILLIS);
        long deadline = startedAt + observeWindowMillis;
        log.info(
                "部署 {} 开始启动观察，PID={}，观察窗口={}毫秒，配置超时={}毫秒，启动判定条件='{}'，策略='{}'，运行日志路径='{}'，初始偏移={}。",
                deployment.getId(),
                monitoredPid,
                observeWindowMillis,
                configuredTimeoutMillis,
                startupKeyword,
                startupJudge == null ? "默认观察逻辑" : startupJudge.strategyDescription(),
                logCursor == null ? null : logCursor.runtimeLogPath(),
                logCursor == null ? null : logCursor.nextOffset()
        );
        while (System.currentTimeMillis() <= deadline) {
            if (isStopRequested(deployment.getId())) {
                try {
                    appendSystemLog(logFile, "已收到手动停止请求，结束启动观察。");
                } catch (Exception ignored) {
                }
                return null;
            }
            attempt++;
            StartupLogReadResult logReadResult = readRuntimeLogDelta(logCursor, targetHost);
            logCursor = logReadResult.cursor();
            appendRuntimeLogDelta(logFile, logReadResult.content());
            appendStartupOutput(startupOutputBuffer, logReadResult.content());
            log.info(
                    "部署 {} 启动观察第 {} 次读取日志增量完成：本次增量长度={}，累计缓存长度={}，当前偏移={}。",
                    deployment.getId(),
                    attempt,
                    logReadResult.content() == null ? 0 : logReadResult.content().length(),
                    startupOutputBuffer.length(),
                    logCursor == null ? null : logCursor.nextOffset()
            );
            boolean alive = isProcessAlive(targetHost, monitoredPid);
            if (!alive) {
                try {
                    appendSystemLog(logFile, "启动观察失败：PID " + monitoredPid + " 已退出。");
                } catch (Exception ignored) {
                }
                log.warn("部署 {} 启动观察失败：PID {} 在第 {} 次检测时已退出。", deployment.getId(), monitoredPid, attempt);
                return null;
            }
            if (keywordRequired && matchesStartupJudge(startupOutputBuffer.toString(), startupJudge)) {
                try {
                    appendSystemLog(logFile, "启动判定条件已命中，服务启动成功。");
                } catch (Exception ignored) {
                }
                log.info("部署 {} 启动观察成功，启动判定表达式 '{}' 已命中。", deployment.getId(), startupKeyword);
                return monitoredPid;
            }
            long elapsedMillis = System.currentTimeMillis() - startedAt;
            if (!keywordRequired && elapsedMillis >= PROCESS_STABLE_OBSERVE_MILLIS) {
                try {
                    appendSystemLog(logFile, "服务监测通过，PID " + monitoredPid + " 已稳定运行 " + (elapsedMillis / 1000) + " 秒。");
                } catch (Exception ignored) {
                }
                log.info("部署 {} 进程存活观察通过，PID={}，稳定运行={}毫秒。", deployment.getId(), monitoredPid, elapsedMillis);
                return monitoredPid;
            }
            try {
                long elapsedSeconds = elapsedMillis / 1000;
                if (keywordRequired) {
                    appendSystemLog(logFile, "第 " + attempt + " 次启动观察：PID " + monitoredPid + " 已运行 " + elapsedSeconds + " 秒，仍在等待启动判定条件。");
                } else {
                    appendSystemLog(logFile, "第 " + attempt + " 次启动观察通过，PID " + monitoredPid + " 已稳定运行 " + elapsedSeconds + " 秒。");
                }
            } catch (Exception ignored) {
            }
            log.info("部署 {} 启动观察第 {} 次通过，PID={}。", deployment.getId(), attempt, monitoredPid);
            if (System.currentTimeMillis() >= deadline) {
                break;
            }
            try {
                Thread.sleep(MONITOR_INTERVAL_MILLIS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        StartupLogReadResult finalLogReadResult = readRuntimeLogDelta(logCursor, targetHost);
        appendRuntimeLogDelta(logFile, finalLogReadResult.content());
        appendStartupOutput(startupOutputBuffer, finalLogReadResult.content());
        if (keywordRequired && matchesStartupJudge(startupOutputBuffer.toString(), startupJudge)) {
            try {
                appendSystemLog(logFile, "启动判定条件已命中，服务启动成功。");
            } catch (Exception ignored) {
            }
            log.info("部署 {} 启动观察成功，启动判定表达式 '{}' 在最终日志读取时命中。", deployment.getId(), startupKeyword);
            return monitoredPid;
        }
        if (keywordRequired) {
            try {
                appendSystemLog(logFile, "启动观察失败：在超时时间内未检测到启动判定条件。");
            } catch (Exception ignored) {
            }
            log.warn("部署 {} 启动观察失败：启动判定条件 '{}' 在超时时间内未命中。", deployment.getId(), startupKeyword);
            return null;
        }
        try {
            appendSystemLog(logFile, "服务监测通过，PID " + monitoredPid + " 已完成启动观察窗口。");
        } catch (Exception ignored) {
        }
        log.info("部署 {} 启动观察成功结束，PID={}。", deployment.getId(), monitoredPid);
        return monitoredPid;
    }

    private boolean matchesStartupJudge(String output, StartupJudgeResult startupJudge) {
        if (startupJudge == null || !startupJudge.keywordRequired() || TextKit.isBlank(startupJudge.keyword())) {
            return false;
        }
        if (startupJudge.matchMode() == top.fusb.deploybot.plugin.api.startup.StartupJudgeMatchMode.REGEX) {
            try {
                return Pattern.compile(startupJudge.keyword(), Pattern.MULTILINE).matcher(output == null ? "" : output).find();
            } catch (PatternSyntaxException ex) {
                log.warn("启动判定正则不合法，将按普通关键字匹配。pattern={}", startupJudge.keyword());
            }
        }
        return output != null && output.contains(startupJudge.keyword());
    }

    private record ServiceVerificationResult(boolean success, boolean managedService, Long monitoredPid) {

        private static ServiceVerificationResult failed() {
            return new ServiceVerificationResult(false, true, null);
        }

        private static ServiceVerificationResult managed(Long monitoredPid) {
            return new ServiceVerificationResult(true, true, monitoredPid);
        }

        private static ServiceVerificationResult skipped() {
            return new ServiceVerificationResult(true, false, null);
        }
    }

    private boolean isProcessAlive(HostEntity targetHost, Long pid) {
        if (pid == null) {
            return false;
        }
        if (targetHost == null || targetHost.getType() == HostType.LOCAL) {
            return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
        }
        try {
            String output = hostService.executeRemoteScript(
                    targetHost.getId(),
                    "if kill -0 " + pid + " >/dev/null 2>&1; then echo RUNNING; else echo STOPPED; fi\n",
                    8
            );
            return output.contains("RUNNING");
        } catch (Exception ex) {
            return false;
        }
    }

    private long resolveStartupTimeoutMillis(DeploymentEntity deployment) {
        Integer startupTimeoutSeconds = deployment.getPipeline().getStartupTimeoutSeconds();
        if (startupTimeoutSeconds == null || startupTimeoutSeconds <= 0) {
            return DEFAULT_STARTUP_TIMEOUT_MILLIS;
        }
        return Math.max(5L, startupTimeoutSeconds.longValue()) * 1_000L;
    }

    private StartupLogCursor initializeStartupLogCursor(DeploymentEntity deployment) {
        String runtimeLogPath = resolveRuntimeLogPath(deployment);
        if (runtimeLogPath == null) {
            return null;
        }
        return new StartupLogCursor(runtimeLogPath, 0L);
    }

    private StartupLogReadResult readRuntimeLogDelta(StartupLogCursor cursor, HostEntity targetHost) {
        if (cursor == null || cursor.runtimeLogPath() == null || cursor.runtimeLogPath().isBlank()) {
            return new StartupLogReadResult(cursor, "");
        }
        try {
            if (targetHost != null && targetHost.getType() == HostType.SSH) {
                String output = hostService.executeRemoteScript(
                        targetHost.getId(),
                        buildRuntimeLogDeltaScript(cursor.runtimeLogPath(), cursor.nextOffset()),
                        8
                );
                return parseRemoteRuntimeLogDelta(cursor.runtimeLogPath(), output, cursor.nextOffset());
            }
            Path logPath = Path.of(cursor.runtimeLogPath());
            if (!Files.exists(logPath)) {
                return new StartupLogReadResult(cursor, "");
            }
            long size = Files.size(logPath);
            long offset = cursor.nextOffset();
            if (size < offset) {
                offset = 0L;
            }
            if (size == offset) {
                return new StartupLogReadResult(new StartupLogCursor(cursor.runtimeLogPath(), size), "");
            }
            try (InputStream inputStream = Files.newInputStream(logPath)) {
                inputStream.skip(offset);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                inputStream.transferTo(outputStream);
                return new StartupLogReadResult(
                        new StartupLogCursor(cursor.runtimeLogPath(), size),
                        outputStream.toString(StandardCharsets.UTF_8)
                );
            }
        } catch (Exception ignored) {
            return new StartupLogReadResult(cursor, "");
        }
    }

    private String buildRuntimeLogDeltaScript(String runtimeLogPath, long offset) {
        String escapedPath = runtimeLogPath.replace("\"", "\\\"");
        return """
                FILE="%s"
                OFFSET=%d
                if [ ! -f "$FILE" ]; then
                  echo "__DEPLOYBOT_LOG_SIZE__:0"
                  exit 0
                fi
                SIZE=$(wc -c < "$FILE" | tr -d '[:space:]')
                if [ -z "$SIZE" ]; then SIZE=0; fi
                if [ "$SIZE" -lt "$OFFSET" ]; then OFFSET=0; fi
                echo "__DEPLOYBOT_LOG_SIZE__:$SIZE"
                if [ "$SIZE" -gt "$OFFSET" ]; then
                  tail -c +$((OFFSET + 1)) "$FILE"
                fi
                """.formatted(escapedPath, Math.max(0L, offset));
    }

    private StartupLogReadResult parseRemoteRuntimeLogDelta(String runtimeLogPath, String output, long previousOffset) {
        if (output == null) {
            return new StartupLogReadResult(new StartupLogCursor(runtimeLogPath, previousOffset), "");
        }
        String marker = "__DEPLOYBOT_LOG_SIZE__:";
        String[] lines = output.split("\\R", -1);
        long size = previousOffset;
        int contentStart = 0;
        for (int index = 0; index < lines.length; index++) {
            if (lines[index].startsWith(marker)) {
                size = parseLongSafely(lines[index].substring(marker.length()), previousOffset);
                contentStart = index + 1;
                break;
            }
        }
        String content = contentStart >= lines.length
                ? ""
                : String.join(System.lineSeparator(), java.util.Arrays.copyOfRange(lines, contentStart, lines.length));
        return new StartupLogReadResult(new StartupLogCursor(runtimeLogPath, size), content);
    }

    private long parseLongSafely(String value, long fallback) {
        try {
            return Long.parseLong(value == null ? "" : value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String resolveRuntimeLogPath(DeploymentEntity deployment) {
        Map<String, String> variables = deployment.getVariables() == null ? Map.of() : deployment.getVariables();
        return TextKit.trimToNull(variables.get("runtimeLogPath"));
    }

    private void appendRuntimeLogDelta(Path logFile, String delta) {
        if (delta == null || delta.isBlank()) {
            return;
        }
        try {
            Files.writeString(
                    logFile,
                    delta + (delta.endsWith(System.lineSeparator()) ? "" : System.lineSeparator()),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (Exception ignored) {
        }
    }

    private void appendStartupOutput(StringBuilder buffer, String delta) {
        if (delta == null || delta.isBlank()) {
            return;
        }
        buffer.append(delta);
        if (buffer.length() > STARTUP_LOG_BUFFER_LIMIT) {
            buffer.delete(0, buffer.length() - STARTUP_LOG_BUFFER_LIMIT);
        }
    }

    private int runProcess(ProcessBuilder processBuilder, String stdin, Path logFile, Long deploymentId) throws Exception {
        log.info("部署 {} 开始执行进程，命令={}，目录={}。", deploymentId, processBuilder.command(), processBuilder.directory());
        if (stdin != null) {
            log.info("部署 {} 正在向进程标准输入写入脚本内容，长度={}。", deploymentId, stdin.length());
        }
        try (java.io.BufferedWriter writer = Files.newBufferedWriter(
                logFile,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        )) {
            int exitCode = ProcessKit.runStreaming(
                    processBuilder,
                    stdin,
                    (buffer, offset, length) -> {
                        writer.write(buffer, offset, length);
                        writer.flush();
                    },
                    ex -> {
                        if (isStopInterruption(deploymentId, ex)) {
                            log.info("部署 {} 的日志流在手动停止后关闭，按正常停止处理。", deploymentId);
                            return true;
                        }
                        return false;
                    },
                    process -> runningProcesses.put(deploymentId, process)
            );
            log.info("部署 {} 的进程执行结束，退出码={}。", deploymentId, exitCode);
            return exitCode;
        }
    }

    private boolean isStopInterruption(Long deploymentId, IOException ex) {
        if (ex == null || ex.getMessage() == null) {
            return false;
        }
        if (!"Stream closed".equalsIgnoreCase(ex.getMessage().trim())) {
            return false;
        }
        for (int attempt = 0; attempt < 5; attempt++) {
            if (isStopRequested(deploymentId)) {
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return deploymentRepository.findById(deploymentId)
                .map(item -> item.getStatus() == DeploymentStatus.STOPPED)
                .orElse(false);
    }

    private DeploymentEntity refreshDeploymentState(DeploymentEntity deployment) {
        if (deployment == null || deployment.getId() == null) {
            return deployment;
        }
        return deploymentRepository.findById(deployment.getId()).orElse(deployment);
    }

    private boolean isStopRequested(Long deploymentId) {
        if (deploymentId == null) {
            return false;
        }
        if (stopRequests.containsKey(deploymentId)) {
            return true;
        }
        return deploymentRepository.findById(deploymentId)
                .map(item -> item.getStatus() == DeploymentStatus.STOPPED)
                .orElse(false);
    }

    private void stopRuntimeProcessIfPresent(DeploymentEntity deployment) {
        if (deployment == null) {
            return;
        }
        HostEntity targetHost = deployment.getPipeline() == null ? null : deployment.getPipeline().getTargetHost();
        Long pid = deployment.getMonitoredPid();
        if (pid == null) {
            pid = readMonitoredPid(deployment, targetHost, deployment.getId());
        }
        if (pid == null) {
            return;
        }
        if (targetHost != null && targetHost.getType() == HostType.SSH) {
            try {
                hostService.executeRemoteScript(
                        targetHost.getId(),
                        "kill " + pid + " >/dev/null 2>&1 || true\n" +
                                "sleep 1\n" +
                                "if kill -0 " + pid + " >/dev/null 2>&1; then kill -9 " + pid + " >/dev/null 2>&1 || true; fi\n",
                        10
                );
            } catch (Exception ex) {
                log.warn("远程停止部署 {} 的运行进程 {} 失败：{}", deployment.getId(), pid, ex.getMessage());
            }
            return;
        }
        ProcessHandle.of(pid).ifPresent(process -> {
            process.destroy();
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        });
    }

    private void stopRuntimeProcessQuietly(DeploymentEntity deployment, Path logFile, String message) {
        try {
            if (logFile != null && message != null && !message.isBlank()) {
                appendSystemLog(logFile, message);
            }
            stopRuntimeProcessIfPresent(deployment);
        } catch (Exception ex) {
            log.warn("部署 {} 异常收尾时清理运行进程失败：{}", deployment == null ? null : deployment.getId(), ex.getMessage());
        }
    }

    private void syncArtifactsToRemote(
            HostEntity targetHost,
            Path localArtifactDir,
            Path remoteArtifactDir,
            Path sshDir,
            Path logFile,
            Long deploymentId
    ) throws Exception {
        appendSystemLog(logFile, "开始同步构建产物到远程主机：" + remoteArtifactDir);
        log.info("部署 {} 开始同步构建产物到远端目录 {}。", deploymentId, remoteArtifactDir);
        int copyExitCode = runProcess(
                ProcessKit.mergedBuilder(buildArtifactSyncCommand(targetHost, sshDir, localArtifactDir, remoteArtifactDir.toString())),
                null,
                logFile,
                deploymentId
        );
        log.info("部署 {} 的构建产物同步进程结束，退出码={}。", deploymentId, copyExitCode);
        if (copyExitCode != 0) {
            log.warn("Artifact sync failed for deployment {} with exit code {}.", deploymentId, copyExitCode);
            throw new BusinessException(ErrorSubCode.REMOTE_ARTIFACT_SYNC_FAILED);
        }
    }
}
