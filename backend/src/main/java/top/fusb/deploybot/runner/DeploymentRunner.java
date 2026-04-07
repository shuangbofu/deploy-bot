package top.fusb.deploybot.runner;

import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.model.DeploymentEntity;
import top.fusb.deploybot.model.DeploymentStatus;
import top.fusb.deploybot.model.HostEntity;
import top.fusb.deploybot.model.HostSshAuthType;
import top.fusb.deploybot.model.HostType;
import top.fusb.deploybot.model.ServiceEntity;
import top.fusb.deploybot.notification.model.NotificationEventType;
import top.fusb.deploybot.repo.DeploymentRepository;
import top.fusb.deploybot.service.DeploymentBackupService;
import top.fusb.deploybot.notification.service.DeploymentNotificationAsyncService;
import top.fusb.deploybot.service.GitCredentialService;
import top.fusb.deploybot.service.HostService;
import top.fusb.deploybot.service.JsonMapper;
import top.fusb.deploybot.service.ServiceManager;
import top.fusb.deploybot.service.SystemSettingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DeploymentRunner {
    private static final Logger log = LoggerFactory.getLogger(DeploymentRunner.class);
    private static final String LOG_DIR = "logs";
    private static final String SCRIPT_DIR = "scripts";
    private static final String SSH_DIR = "ssh";
    private static final String ARTIFACT_DIR = "artifacts";
    private static final String PID_DIR = "pids";
    private static final long PID_DISCOVERY_TIMEOUT_MILLIS = 30_000L;
    private static final long DEFAULT_STARTUP_TIMEOUT_MILLIS = 30_000L;
    private static final long MONITOR_INTERVAL_MILLIS = 2_000L;
    private static final int STARTUP_LOG_BUFFER_LIMIT = 256 * 1024;

    private final DeploymentRepository deploymentRepository;
    private final ServiceManager serviceManager;
    private final SystemSettingsService systemSettingsService;
    private final GitCredentialService gitCredentialService;
    private final DeploymentBackupService deploymentBackupService;
    private final JsonMapper jsonMapper;
    private final HostService hostService;
    private final DeploymentNotificationAsyncService deploymentNotificationAsyncService;
    private final Path defaultWorkspaceRoot;
    private final Map<Long, Process> runningProcesses = new ConcurrentHashMap<>();

    private record StartupLogCursor(String runtimeLogPath, long nextOffset) {
    }

    private record StartupLogReadResult(StartupLogCursor cursor, String content) {
    }

    public DeploymentRunner(
            DeploymentRepository deploymentRepository,
            ServiceManager serviceManager,
            SystemSettingsService systemSettingsService,
            GitCredentialService gitCredentialService,
            DeploymentBackupService deploymentBackupService,
            JsonMapper jsonMapper,
            HostService hostService,
            DeploymentNotificationAsyncService deploymentNotificationAsyncService,
            @Value("${deploybot.workspace-root:./runtime}") String workspaceRoot
    ) {
        this.deploymentRepository = deploymentRepository;
        this.serviceManager = serviceManager;
        this.systemSettingsService = systemSettingsService;
        this.gitCredentialService = gitCredentialService;
        this.deploymentBackupService = deploymentBackupService;
        this.jsonMapper = jsonMapper;
        this.hostService = hostService;
        this.deploymentNotificationAsyncService = deploymentNotificationAsyncService;
        this.defaultWorkspaceRoot = Path.of(workspaceRoot);
    }

    @Async
    public void runAsync(Long deploymentId) {
        Path logFile = null;
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
                    deployment.getPipeline().getTemplate() == null ? "-" : deployment.getPipeline().getTemplate().getName(),
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
            if (deployment.getStatus() == DeploymentStatus.STOPPED) {
                appendSystemLog(logFile, "部署已停止。");
                deploymentRepository.save(deployment);
                return;
            }
            if (exitCode == 0 && deployment.getRenderedDeployScript() != null && !deployment.getRenderedDeployScript().isBlank()) {
                if (Boolean.TRUE.equals(deployment.getPipeline().getTemplate().getMonitorProcess())) {
                    ServiceEntity stoppedService = serviceManager.stopManagedServiceBeforeDeploy(deployment.getPipeline().getId());
                    if (stoppedService != null) {
                        appendSystemLog(logFile, "本次发布前已停止旧服务，serviceId=" + stoppedService.getId() + "。");
                        log.info(
                                "流水线 '{}' 在构建成功后、发布开始前已由系统停止旧服务：serviceId={}。",
                                deployment.getPipeline().getName(),
                                stoppedService.getId()
                        );
                    }
                }
                appendSystemLog(logFile, "本机构建完成，开始发布阶段。");
                log.info("部署 {} 发布阶段开始。", deploymentId);
                if (targetHost != null && targetHost.getType() == HostType.SSH) {
                    Path remoteArtifactDir = deployWorkspaceRoot.resolve(ARTIFACT_DIR).resolve("deploy-" + deploymentId).toAbsolutePath().normalize();
                    prepareRemoteExecutionDirectories(targetHost, deployment, remoteArtifactDir, sshDir, logFile, deploymentId);
                    syncArtifactsToRemote(targetHost, artifactsDir, remoteArtifactDir, sshDir, logFile, deploymentId);
                    log.info("部署 {} 构建产物同步完成。目标主机='{}'，远端产物目录='{}'。", deploymentId, targetHost.getHostname(), remoteArtifactDir);
                    exitCode = runProcess(
                            buildRemoteDeployProcessBuilder(targetHost, sshDir),
                            deployment.getRenderedDeployScript(),
                            logFile,
                            deploymentId
                    );
                } else {
                    Path pidsDir = deployWorkspaceRoot.resolve(PID_DIR);
                    Files.createDirectories(pidsDir);
                    exitCode = runProcess(
                        buildLocalDeployProcessBuilder(buildWorkspaceRoot, deployScriptFile),
                        null,
                        logFile,
                        deploymentId
                    );
                }
                log.info("部署 {} 发布阶段结束，退出码={}.", deploymentId, exitCode);
                deployment = refreshDeploymentState(deployment);
                if (deployment.getStatus() == DeploymentStatus.STOPPED) {
                    appendSystemLog(logFile, "部署已停止。");
                    deploymentRepository.save(deployment);
                    return;
                }
            }
            runningProcesses.remove(deploymentId);
            LocalDateTime finishedAt = LocalDateTime.now();
            deployment = refreshDeploymentState(deployment);
            deployment.setFinishedAt(finishedAt);
            if (deployment.getStatus() == DeploymentStatus.STOPPED) {
                appendSystemLog(logFile, "部署已停止。");
                deploymentRepository.save(deployment);
                return;
            }
            if (exitCode == 0) {
                if (Boolean.TRUE.equals(deployment.getPipeline().getTemplate().getMonitorProcess())) {
                    Path pidsDir = deployWorkspaceRoot.resolve(PID_DIR);
                    log.info(
                            "Deployment {} service monitoring started. startupKeyword='{}', startupTimeoutSeconds={}.",
                            deploymentId,
                            deployment.getPipeline().getStartupKeyword(),
                            deployment.getPipeline().getStartupTimeoutSeconds()
                    );
                    Long monitoredPid = verifyMonitoredProcess(deployment, targetHost, pidsDir, deploymentId, logFile);
                    deployment = refreshDeploymentState(deployment);
                    deployment.setFinishedAt(finishedAt);
                    if (deployment.getStatus() == DeploymentStatus.STOPPED) {
                        appendSystemLog(logFile, "部署已停止。");
                        deploymentRepository.save(deployment);
                        return;
                    }
                    if (monitoredPid == null) {
                        deployment.setStatus(DeploymentStatus.FAILED);
                        deployment.setErrorMessage(ErrorSubCode.DEPLOYMENT_MONITORED_PROCESS_NOT_RUNNING.getMessage());
                        appendSystemLog(logFile, "部署失败：服务启动后未检测到可用进程。");
                        log.warn("部署 {} 在服务监测阶段失败，未确认到稳定可接管的进程。", deploymentId);
                    } else {
                        deployment.setMonitoredPid(monitoredPid);
                        serviceManager.updateFromDeployment(deployment, monitoredPid);
                        deployment.setStatus(DeploymentStatus.SUCCESS);
                        appendSystemLog(logFile, "发布阶段执行完成。");
                        appendSystemLog(logFile, "部署完成。");
                        log.info("部署 {} 成功完成，受管进程 PID={}。", deploymentId, monitoredPid);
                    }
                } else {
                    deployment.setStatus(DeploymentStatus.SUCCESS);
                    appendSystemLog(logFile, "发布阶段执行完成。");
                    appendSystemLog(logFile, "部署完成。");
                    log.info("部署 {} 成功完成，本次未启用服务监测。", deploymentId);
                }
            } else {
                deployment.setStatus(DeploymentStatus.FAILED);
                deployment.setErrorMessage("Script exited with code " + exitCode);
                appendSystemLog(logFile, "部署失败，脚本退出码：" + exitCode);
                log.warn("部署 {} 失败，脚本进程退出码={}。", deploymentId, exitCode);
            }
            deploymentRepository.save(deployment);
            deploymentNotificationAsyncService.notifyAsync(deployment.getId(), NotificationEventType.DEPLOYMENT_FINISHED);
        } catch (Exception ex) {
            runningProcesses.remove(deploymentId);
            log.error("部署 {} 在完成前发生未预期异常。", deploymentId, ex);
            final Path capturedLogFile = logFile;
            deploymentRepository.findById(deploymentId).ifPresent(deployment -> {
                if (deployment.getStatus() == DeploymentStatus.STOPPED) {
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
                deploymentRepository.save(deployment);
                deploymentNotificationAsyncService.notifyAsync(deployment.getId(), NotificationEventType.DEPLOYMENT_FINISHED);
            });
        }
    }

    /**
     * 远程主机只负责接收产物与执行发布脚本，因此先准备远端产物目录和 PID 目录。
     */
    private void prepareRemoteExecutionDirectories(
            HostEntity targetHost,
            DeploymentEntity deployment,
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

        String pidFilePath = jsonMapper.toStringMap(deployment.getVariablesJson()).get("pidFilePath");
        if (pidFilePath != null && !pidFilePath.isBlank()) {
            Path pidDir = Path.of(pidFilePath).getParent();
            if (pidDir != null) {
                script.append("mkdir -p \"")
                        .append(pidDir.toString().replace("\"", "\\\""))
                        .append("\"\n");
            }
        }

        appendSystemLog(logFile, "准备远程目录：" + remoteArtifactDir);
        int prepareExitCode = runProcess(
                new ProcessBuilder(buildSshCommand(targetHost, sshDir)).redirectErrorStream(true),
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
            try {
                stopRuntimeProcessIfPresent(deployment);
            } catch (Exception ex) {
                log.warn("部署 {} 在手动停止时清理运行中服务失败：{}", deploymentId, ex.getMessage());
            }
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
        ProcessBuilder processBuilder = new ProcessBuilder("bash", scriptFile.toAbsolutePath().toString())
                .directory(workspaceRoot.toFile())
                .redirectErrorStream(true);
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
        ProcessBuilder processBuilder = new ProcessBuilder("bash", scriptFile.toAbsolutePath().toString())
                .directory(workspaceRoot.toFile())
                .redirectErrorStream(true);
        processBuilder.environment().put("GIT_TERMINAL_PROMPT", "0");
        processBuilder.environment().put("GIT_ASKPASS", "echo");
        processBuilder.environment().put("DEPLOYBOT_GIT_EXECUTABLE", gitCredentialService.getGitExecutable());
        log.info("已准备本地发布进程，工作目录={}，脚本={}。", workspaceRoot.toAbsolutePath().normalize(), scriptFile.toAbsolutePath().normalize());
        return processBuilder;
    }

    private ProcessBuilder buildRemoteDeployProcessBuilder(HostEntity targetHost, Path sshDir) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(buildSshCommand(targetHost, sshDir))
                .redirectErrorStream(true);
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

    private java.util.List<String> buildScpCommand(HostEntity targetHost, Path sshDir, Path localSource, String remoteTarget) throws Exception {
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
        command.add("scp");
        command.add("-r");
        if (Files.exists(privateKey)) {
            command.add("-i");
            command.add(privateKey.toAbsolutePath().toString());
            command.add("-o");
            command.add("IdentitiesOnly=yes");
        }
        if (targetHost.getPort() != null) {
            command.add("-P");
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
        command.add(localSource.toAbsolutePath().toString() + "/.");
        String userAtHost = (targetHost.getUsername() == null || targetHost.getUsername().isBlank())
                ? targetHost.getHostname()
                : targetHost.getUsername().trim() + "@" + targetHost.getHostname().trim();
        command.add(userAtHost + ":" + remoteTarget);
        log.info("Built SCP command to sync {} to {}:{}", localSource.toAbsolutePath().normalize(), userAtHost, remoteTarget);
        return command;
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
                "[系统] " + message + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
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

    private Long readMonitoredPid(DeploymentEntity deployment, HostEntity targetHost, Path pidsDir, Long deploymentId) {
        try {
            if (targetHost != null && targetHost.getType() == HostType.SSH) {
                String pidFilePath = jsonMapper.toStringMap(deployment.getVariablesJson()).get("pidFilePath");
                if (pidFilePath != null && !pidFilePath.isBlank()) {
                    String output = hostService.executeRemoteScript(
                            targetHost.getId(),
                            "if [ -f \"" + pidFilePath.replace("\"", "\\\"") + "\" ]; then cat \"" + pidFilePath.replace("\"", "\\\"") + "\"; fi\n",
                            8
                    ).trim();
                    if (!output.isBlank()) {
                        log.info("Deployment {} read monitored pid {} from remote pid file {}.", deploymentId, output.lines().findFirst().orElse("").trim(), pidFilePath);
                        return Long.parseLong(output.lines().findFirst().orElse("").trim());
                    }
                }
            } else {
                Path pidFile = pidsDir.resolve("service-" + deploymentId + ".pid");
                if (Files.exists(pidFile)) {
                    String pidText = Files.readString(pidFile, StandardCharsets.UTF_8).trim();
                    if (!pidText.isBlank()) {
                        log.info("Deployment {} read monitored pid {} from local pid file {}.", deploymentId, pidText, pidFile.toAbsolutePath().normalize());
                        return Long.parseLong(pidText);
                    }
                }
            }
            String discoveryKeyword = resolvePidDiscoveryKeyword(deployment);
            if (discoveryKeyword != null && !discoveryKeyword.isBlank()) {
                log.info("部署 {} 正在使用推导关键字 '{}' 尝试发现 PID。", deploymentId, discoveryKeyword);
                return readMonitoredPidByKeyword(targetHost, discoveryKeyword);
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long readMonitoredPidByKeyword(HostEntity targetHost, String keyword) {
        String escapedKeyword = keyword.replace("\"", "\\\"");
        try {
            if (targetHost != null && targetHost.getType() == HostType.SSH) {
                String output = hostService.executeRemoteScript(
                        targetHost.getId(),
                        buildKeywordLookupScript(escapedKeyword, keyword),
                        8
                ).trim();
                return output.isBlank() ? null : Long.parseLong(output.lines().findFirst().orElse("").trim());
            }
            Process process = new ProcessBuilder("bash", "-lc", buildKeywordLookupScript(escapedKeyword, keyword)).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            process.waitFor();
            return output.isBlank() ? null : Long.parseLong(output.lines().findFirst().orElse("").trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long verifyMonitoredProcess(DeploymentEntity deployment, HostEntity targetHost, Path pidsDir, Long deploymentId, Path logFile) {
        Long monitoredPid = waitForMonitoredPid(deployment, targetHost, pidsDir, deploymentId, logFile);
        if (monitoredPid == null) {
            try {
                appendSystemLog(logFile, "服务检测超时，未能获取到可接管的进程 PID。");
            } catch (Exception ignored) {
            }
            return null;
        }
        return observeStartupWindow(deployment, targetHost, monitoredPid, logFile);
    }

    private Long waitForMonitoredPid(DeploymentEntity deployment, HostEntity targetHost, Path pidsDir, Long deploymentId, Path logFile) {
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
            Long monitoredPid = readMonitoredPid(deployment, targetHost, pidsDir, deploymentId);
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
                    "部署 {} 第 {} 次 PID 检测未命中，已等待 {} 秒。来源={}，诊断={}",
                    deploymentId,
                    attempt,
                    (System.currentTimeMillis() - startedAt) / 1000,
                    describePidDiscoverySource(deployment),
                    loadRemotePidDiagnostics(deployment, targetHost)
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
        String startupKeyword = deployment.getPipeline().getStartupKeyword();
        if (startupKeyword != null && !startupKeyword.isBlank()) {
            builder.append("，启动关键字=").append(startupKeyword);
        }
        return builder.toString();
    }

    private String describePidDiscoverySource(DeploymentEntity deployment) {
        String pidFilePath = jsonMapper.toStringMap(deployment.getVariablesJson()).get("pidFilePath");
        if (pidFilePath != null && !pidFilePath.isBlank()) {
            return "PID 文件优先，其次命令推导";
        }
        return "命令推导";
    }

    private String loadRemotePidDiagnostics(DeploymentEntity deployment, HostEntity targetHost) {
        try {
            String keyword = resolvePidDiscoveryKeyword(deployment);
            String pidFilePath = jsonMapper.toStringMap(deployment.getVariablesJson()).get("pidFilePath");
            List<String> lines = new java.util.ArrayList<>();
            if (pidFilePath != null && !pidFilePath.isBlank()) {
                lines.add("if [ -f \"" + pidFilePath.replace("\"", "\\\"") + "\" ]; then echo \"[系统] 远程 PID 文件：$(cat \\\"" + pidFilePath.replace("\"", "\\\"") + "\\\")\"; else echo \"[系统] 远程 PID 文件不存在\"; fi");
            }
            if (keyword != null && !keyword.isBlank()) {
                String escapedKeyword = keyword.replace("\"", "\\\"");
                lines.add("echo \"[系统] 远程 PID 推导关键字：" + escapedKeyword + "\"");
                lines.add("pgrep -af \"" + escapedKeyword + "\" || true");
                lines.add("if command -v jps >/dev/null 2>&1; then echo \"[系统] 远程 jps 结果：\"; jps -lv | grep \"" + escapedKeyword + "\" || true; fi");
            }
            if (lines.isEmpty()) {
                return "[系统] 当前没有可用于 PID 推导的远程诊断信息。";
            }
            String script = String.join("\n", lines) + "\n";
            if (targetHost != null && targetHost.getType() == HostType.SSH) {
                String output = hostService.executeRemoteScript(targetHost.getId(), script, 8).trim();
                return output.isBlank() ? "[系统] 远程 PID 诊断无输出。" : output;
            }
            Process process = new ProcessBuilder("bash", "-lc", script).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            process.waitFor();
            return output.isBlank() ? "[系统] 本地 PID 诊断无输出。" : output;
        } catch (Exception ex) {
            return "[系统] PID 诊断执行失败：" + ex.getMessage();
        }
    }

    private Long observeStartupWindow(DeploymentEntity deployment, HostEntity targetHost, Long monitoredPid, Path logFile) {
        long startedAt = System.currentTimeMillis();
        long deadline = startedAt + resolveStartupTimeoutMillis(deployment);
        int attempt = 0;
        String startupKeyword = deployment.getPipeline().getStartupKeyword();
        boolean keywordRequired = startupKeyword != null && !startupKeyword.isBlank();
        StringBuilder startupOutputBuffer = new StringBuilder();
        StartupLogCursor logCursor = initializeStartupLogCursor(deployment, targetHost);
        log.info(
                "部署 {} 开始启动观察，PID={}，超时时间={}毫秒，启动关键字='{}'。",
                deployment.getId(),
                monitoredPid,
                resolveStartupTimeoutMillis(deployment),
                startupKeyword
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
            boolean alive = isProcessAlive(targetHost, monitoredPid);
            if (!alive) {
                try {
                    appendSystemLog(logFile, "启动观察失败：PID " + monitoredPid + " 已退出。");
                } catch (Exception ignored) {
                }
                log.warn("部署 {} 启动观察失败：PID {} 在第 {} 次检测时已退出。", deployment.getId(), monitoredPid, attempt);
                return null;
            }
            if (keywordRequired && startupOutputBuffer.toString().contains(startupKeyword)) {
                try {
                    appendSystemLog(logFile, "启动关键字已命中，服务启动成功。");
                } catch (Exception ignored) {
                }
                log.info("部署 {} 启动观察成功，关键字 '{}' 已命中。", deployment.getId(), startupKeyword);
                return monitoredPid;
            }
            try {
                long elapsedSeconds = (System.currentTimeMillis() - startedAt) / 1000;
                if (keywordRequired) {
                    appendSystemLog(logFile, "第 " + attempt + " 次启动观察：PID " + monitoredPid + " 已运行 " + elapsedSeconds + " 秒，仍在等待启动关键字。");
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
        if (keywordRequired) {
            try {
                appendSystemLog(logFile, "启动观察失败：在超时时间内未检测到启动关键字。");
            } catch (Exception ignored) {
            }
            log.warn("部署 {} 启动观察失败：关键字 '{}' 在超时时间内未命中。", deployment.getId(), startupKeyword);
            return null;
        }
        try {
            appendSystemLog(logFile, "服务监测通过，PID " + monitoredPid + " 已完成启动观察窗口。");
        } catch (Exception ignored) {
        }
        log.info("部署 {} 启动观察成功结束，PID={}。", deployment.getId(), monitoredPid);
        return monitoredPid;
    }

    private String buildKeywordLookupScript(String escapedKeyword, String rawKeyword) {
        StringBuilder script = new StringBuilder();
        script.append("PID=$(pgrep -f \"").append(escapedKeyword).append("\" | head -n 1 || true)\n");
        script.append("if [ -z \"$PID\" ]");
        if (rawKeyword.endsWith(".jar")) {
            script.append(" && command -v jps >/dev/null 2>&1");
        }
        script.append("; then\n");
        if (rawKeyword.endsWith(".jar")) {
            script.append("  PID=$(jps -lv 2>/dev/null | grep \"").append(escapedKeyword).append("\" | awk '{print $1}' | head -n 1 || true)\n");
        }
        script.append("fi\n");
        script.append("if [ -n \"$PID\" ]; then echo \"$PID\"; fi\n");
        return script.toString();
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

    private String resolvePidDiscoveryKeyword(DeploymentEntity deployment) {
        Map<String, String> variables = jsonMapper.toStringMap(deployment.getVariablesJson());
        String startCommand = variables.get("startCommand");
        if (startCommand != null) {
            Matcher javaMatcher = Pattern.compile("java\\s+-jar\\s+([^\\s]+)").matcher(startCommand);
            if (javaMatcher.find()) {
                return Path.of(javaMatcher.group(1)).getFileName().toString();
            }
            Matcher nodeMatcher = Pattern.compile("node\\s+([^\\s]+)").matcher(startCommand);
            if (nodeMatcher.find()) {
                return Path.of(nodeMatcher.group(1)).getFileName().toString();
            }
        }
        String jarPath = variables.get("jarPath");
        if (jarPath != null && !jarPath.isBlank()) {
            return Path.of(jarPath).getFileName().toString();
        }
        return null;
    }

    private long resolveStartupTimeoutMillis(DeploymentEntity deployment) {
        Integer startupTimeoutSeconds = deployment.getPipeline().getStartupTimeoutSeconds();
        if (startupTimeoutSeconds == null || startupTimeoutSeconds <= 0) {
            return DEFAULT_STARTUP_TIMEOUT_MILLIS;
        }
        return Math.max(5L, startupTimeoutSeconds.longValue()) * 1_000L;
    }

    private StartupLogCursor initializeStartupLogCursor(DeploymentEntity deployment, HostEntity targetHost) {
        String runtimeLogPath = resolveRuntimeLogPath(deployment);
        if (runtimeLogPath == null) {
            return null;
        }
        return new StartupLogCursor(runtimeLogPath, resolveRuntimeLogSize(targetHost, runtimeLogPath));
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

    private long resolveRuntimeLogSize(HostEntity targetHost, String runtimeLogPath) {
        try {
            if (targetHost != null && targetHost.getType() == HostType.SSH) {
                String output = hostService.executeRemoteScript(
                        targetHost.getId(),
                        "if [ -f \"" + runtimeLogPath.replace("\"", "\\\"") + "\" ]; then wc -c < \"" + runtimeLogPath.replace("\"", "\\\"") + "\" | tr -d '[:space:]'; else echo 0; fi\n",
                        8
                );
                return parseLongSafely(output, 0L);
            }
            Path logPath = Path.of(runtimeLogPath);
            return Files.exists(logPath) ? Files.size(logPath) : 0L;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private long parseLongSafely(String value, long fallback) {
        try {
            return Long.parseLong(value == null ? "" : value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String resolveRuntimeLogPath(DeploymentEntity deployment) {
        Map<String, String> variables = jsonMapper.toStringMap(deployment.getVariablesJson());
        String targetDir = variables.get("targetDir");
        if (targetDir == null || targetDir.isBlank()) {
            return null;
        }
        String applicationName = variables.get("applicationName");
        if (applicationName == null || applicationName.isBlank()) {
            applicationName = "application";
        }
        return targetDir + "/" + applicationName + ".log";
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
        Process process = processBuilder.start();
        runningProcesses.put(deploymentId, process);
        if (stdin != null) {
            log.info("部署 {} 正在向进程标准输入写入脚本内容，长度={}。", deploymentId, stdin.length());
            try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(stdin);
                writer.flush();
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
             InputStream stream = process.getInputStream();
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            char[] buffer = new char[2048];
            int len;
            while (true) {
                try {
                    len = reader.read(buffer);
                } catch (IOException ex) {
                    if (isStopInterruption(deploymentId, ex)) {
                        log.info("部署 {} 的日志流在手动停止后关闭，按正常停止处理。", deploymentId);
                        break;
                    }
                    throw ex;
                }
                if (len == -1) {
                    break;
                }
                writer.write(buffer, 0, len);
                writer.flush();
            }
        }
        int exitCode = process.waitFor();
        log.info("部署 {} 的进程执行结束，退出码={}。", deploymentId, exitCode);
        return exitCode;
    }

    private boolean isStopInterruption(Long deploymentId, IOException ex) {
        if (ex == null || ex.getMessage() == null) {
            return false;
        }
        if (!"Stream closed".equalsIgnoreCase(ex.getMessage().trim())) {
            return false;
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
        return deploymentRepository.findById(deploymentId)
                .map(item -> item.getStatus() == DeploymentStatus.STOPPED)
                .orElse(false);
    }

    private void stopRuntimeProcessIfPresent(DeploymentEntity deployment) {
        if (deployment == null) {
            return;
        }
        HostEntity targetHost = deployment.getPipeline() == null ? null : deployment.getPipeline().getTargetHost();
        Path buildWorkspaceRoot = resolveLocalWorkspaceRoot();
        Path deployWorkspaceRoot = resolveTargetWorkspaceRoot(targetHost, buildWorkspaceRoot);
        Long pid = deployment.getMonitoredPid();
        if (pid == null) {
            pid = readMonitoredPid(
                    deployment,
                    targetHost,
                    deployWorkspaceRoot.resolve(PID_DIR),
                    deployment.getId()
            );
        }
        if (pid == null) {
            return;
        }
        if (targetHost != null && targetHost.getType() == HostType.SSH) {
            try {
                hostService.executeRemoteScript(
                        targetHost.getId(),
                        "kill " + pid + " >/dev/null 2>&1 || true\n",
                        8
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
                new ProcessBuilder(buildScpCommand(targetHost, sshDir, localArtifactDir, remoteArtifactDir.toString())).redirectErrorStream(true),
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
