package top.fusb.deploybot.runner;

import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.model.DeploymentEntity;
import top.fusb.deploybot.model.DeploymentStatus;
import top.fusb.deploybot.model.HostEntity;
import top.fusb.deploybot.model.HostSshAuthType;
import top.fusb.deploybot.model.HostType;
import top.fusb.deploybot.repo.DeploymentRepository;
import top.fusb.deploybot.service.DeploymentBackupService;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DeploymentRunner {
    private static final Logger log = LoggerFactory.getLogger(DeploymentRunner.class);
    private static final String LOG_DIR = "logs";
    private static final String SCRIPT_DIR = "scripts";
    private static final String BACKUP_DIR = "backups";
    private static final String SSH_DIR = "ssh";
    private static final String ARTIFACT_DIR = "artifacts";
    private static final String PID_DIR = "pids";

    private final DeploymentRepository deploymentRepository;
    private final ServiceManager serviceManager;
    private final SystemSettingsService systemSettingsService;
    private final GitCredentialService gitCredentialService;
    private final DeploymentBackupService deploymentBackupService;
    private final JsonMapper jsonMapper;
    private final HostService hostService;
    private final Path defaultWorkspaceRoot;
    private final Map<Long, Process> runningProcesses = new ConcurrentHashMap<>();

    public DeploymentRunner(
            DeploymentRepository deploymentRepository,
            ServiceManager serviceManager,
            SystemSettingsService systemSettingsService,
            GitCredentialService gitCredentialService,
            DeploymentBackupService deploymentBackupService,
            JsonMapper jsonMapper,
            HostService hostService,
            @Value("${deploybot.workspace-root:./runtime}") String workspaceRoot
    ) {
        this.deploymentRepository = deploymentRepository;
        this.serviceManager = serviceManager;
        this.systemSettingsService = systemSettingsService;
        this.gitCredentialService = gitCredentialService;
        this.deploymentBackupService = deploymentBackupService;
        this.jsonMapper = jsonMapper;
        this.hostService = hostService;
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
                log.info("Skip deployment {} because it is already stopped.", deploymentId);
                return;
            }
            HostEntity targetHost = deployment.getPipeline().getTargetHost();
            log.info(
                    "Starting deployment {} for pipeline '{}' on host '{}'.",
                    deploymentId,
                    deployment.getPipeline().getName(),
                    targetHost == null ? "本机" : targetHost.getName()
            );
            Path buildWorkspaceRoot = resolveLocalWorkspaceRoot();
            Path deployWorkspaceRoot = resolveTargetWorkspaceRoot(targetHost, buildWorkspaceRoot);
            Files.createDirectories(buildWorkspaceRoot);
            Path logsDir = buildWorkspaceRoot.resolve(LOG_DIR);
            Path scriptsDir = buildWorkspaceRoot.resolve(SCRIPT_DIR);
            Path backupsDir = buildWorkspaceRoot.resolve(BACKUP_DIR);
            Path sshDir = buildWorkspaceRoot.resolve(SSH_DIR).resolve("deploy-" + deploymentId);
            Path artifactsDir = buildWorkspaceRoot.resolve(ARTIFACT_DIR).resolve("deploy-" + deploymentId);
            Files.createDirectories(logsDir);
            Files.createDirectories(scriptsDir);
            Files.createDirectories(backupsDir);
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
            appendSystemLog(logFile, "部署任务已开始，日志文件：" + logFile.toAbsolutePath().normalize());
            log.info("Deployment {} log file initialized at {}.", deploymentId, logFile.toAbsolutePath().normalize());

            // 2. 本地部署会先备份现有目录；远程部署则由发布脚本自己控制目标目录。
            createBackupIfNeeded(deployment, backupsDir, logFile, targetHost);

            appendSystemLog(logFile, "开始本机构建阶段。");
            log.info("Deployment {} entering local build stage.", deploymentId);
            int exitCode = runProcess(
                    buildLocalBuildProcessBuilder(deployment, buildWorkspaceRoot, buildScriptFile, sshDir),
                    null,
                    logFile,
                    deploymentId
            );
            if (exitCode == 0 && deployment.getRenderedDeployScript() != null && !deployment.getRenderedDeployScript().isBlank()) {
                appendSystemLog(logFile, "本机构建完成，开始发布阶段。");
                log.info("Deployment {} local build finished, entering deploy stage.", deploymentId);
                if (targetHost != null && targetHost.getType() == HostType.SSH) {
                    Path remoteArtifactDir = deployWorkspaceRoot.resolve(ARTIFACT_DIR).resolve("deploy-" + deploymentId).toAbsolutePath().normalize();
                    prepareRemoteExecutionDirectories(targetHost, deployment, remoteArtifactDir, sshDir, logFile, deploymentId);
                    syncArtifactsToRemote(targetHost, artifactsDir, remoteArtifactDir, sshDir, logFile, deploymentId);
                    log.info("Deployment {} artifacts synced to remote host {}.", deploymentId, targetHost.getHostname());
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
            }
            runningProcesses.remove(deploymentId);
            deployment.setFinishedAt(LocalDateTime.now());
            if (deployment.getStatus() == DeploymentStatus.STOPPED) {
                appendSystemLog(logFile, "部署已停止。");
                deploymentRepository.save(deployment);
                return;
            }
            if (exitCode == 0) {
                deployment.setStatus(DeploymentStatus.SUCCESS);
                appendSystemLog(logFile, "发布阶段执行完成。");
                appendSystemLog(logFile, "部署完成。");
                log.info("Deployment {} finished successfully.", deploymentId);
                if (Boolean.TRUE.equals(deployment.getPipeline().getTemplate().getMonitorProcess())) {
                    Path pidsDir = deployWorkspaceRoot.resolve(PID_DIR);
                    Long monitoredPid = readMonitoredPid(deployment, targetHost, pidsDir, deploymentId);
                    if (monitoredPid != null) {
                        deployment.setMonitoredPid(monitoredPid);
                        serviceManager.updateFromDeployment(deployment, monitoredPid);
                    }
                }
            } else {
                deployment.setStatus(DeploymentStatus.FAILED);
                deployment.setErrorMessage("Script exited with code " + exitCode);
                appendSystemLog(logFile, "部署失败，脚本退出码：" + exitCode);
                log.warn("Deployment {} finished with non-zero exit code {}.", deploymentId, exitCode);
            }
            deploymentRepository.save(deployment);
        } catch (Exception ex) {
            runningProcesses.remove(deploymentId);
            log.error("Deployment {} failed before completion.", deploymentId, ex);
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
        if (prepareExitCode != 0) {
            throw new BusinessException(ErrorSubCode.REMOTE_DIRECTORY_PREPARE_FAILED);
        }
    }

    public void stop(Long deploymentId) {
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
            deployment.setStatus(DeploymentStatus.STOPPED);
            deployment.setFinishedAt(LocalDateTime.now());
            deployment.setErrorMessage("任务已手动停止");
            Path logFile = resolveOrCreateLogFile(null, deployment.getLogPath(), deploymentId);
            if (logFile != null) {
                try {
                    appendSystemLog(logFile, "任务已手动停止。");
                    deployment.setLogPath(logFile.toAbsolutePath().toString());
                } catch (Exception ignored) {
                }
            }
            deploymentRepository.save(deployment);
        });
        runningProcesses.remove(deploymentId);
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

    private void createBackupIfNeeded(DeploymentEntity deployment, Path backupsDir, Path logFile, HostEntity targetHost) throws Exception {
        if (targetHost != null && targetHost.getType() == HostType.SSH) {
            appendSystemLog(logFile, "当前部署目标为远程主机，本地预备份已跳过，将由远端脚本自行处理发布目录。");
            return;
        }
        Map<String, String> variables = jsonMapper.toStringMap(deployment.getVariablesJson());
        String targetDir = variables.get("targetDir");
        if (targetDir == null || targetDir.isBlank()) {
            appendSystemLog(logFile, "未检测到 targetDir，本次部署跳过发布目录备份。");
            return;
        }

        Path targetPath = Path.of(targetDir.trim()).toAbsolutePath().normalize();
        if (!Files.exists(targetPath)) {
            appendSystemLog(logFile, "发布目录不存在，跳过备份：" + targetPath);
            return;
        }

        Path backupPath = backupsDir
                .resolve("pipeline-" + deployment.getPipeline().getId())
                .resolve("deploy-" + deployment.getId());
        appendSystemLog(logFile, "开始备份发布目录：" + targetPath);
        deploymentBackupService.snapshotDirectory(targetPath, backupPath);
        deployment.setBackupPath(backupPath.toAbsolutePath().toString());
        deploymentRepository.save(deployment);
        appendSystemLog(logFile, "备份完成，备份目录：" + backupPath.toAbsolutePath().normalize());
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
        log.info("Prepared local build process for deployment {} in workspace {}.", deployment.getId(), workspaceRoot.toAbsolutePath().normalize());
        return processBuilder;
    }

    private ProcessBuilder buildLocalDeployProcessBuilder(Path workspaceRoot, Path scriptFile) {
        ProcessBuilder processBuilder = new ProcessBuilder("bash", scriptFile.toAbsolutePath().toString())
                .directory(workspaceRoot.toFile())
                .redirectErrorStream(true);
        processBuilder.environment().put("GIT_TERMINAL_PROMPT", "0");
        processBuilder.environment().put("GIT_ASKPASS", "echo");
        processBuilder.environment().put("DEPLOYBOT_GIT_EXECUTABLE", gitCredentialService.getGitExecutable());
        log.info("Prepared local deploy process in workspace {} using script {}.", workspaceRoot.toAbsolutePath().normalize(), scriptFile.toAbsolutePath().normalize());
        return processBuilder;
    }

    private ProcessBuilder buildRemoteDeployProcessBuilder(HostEntity targetHost, Path sshDir) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(buildSshCommand(targetHost, sshDir))
                .redirectErrorStream(true);
        processBuilder.environment().put("GIT_TERMINAL_PROMPT", "0");
        processBuilder.environment().put("GIT_ASKPASS", "echo");
        processBuilder.environment().put("DEPLOYBOT_GIT_EXECUTABLE", gitCredentialService.getGitExecutable());
        log.info("Prepared remote deploy process for host {}.", targetHost.getHostname());
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
        log.info("Built SSH command for host {} with auth type {}.", targetHost.getHostname(), authType);
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
                if (pidFilePath == null || pidFilePath.isBlank()) {
                    return null;
                }
                String output = hostService.executeRemoteScript(
                        targetHost.getId(),
                        "if [ -f \"" + pidFilePath.replace("\"", "\\\"") + "\" ]; then cat \"" + pidFilePath.replace("\"", "\\\"") + "\"; fi\n",
                        8
                ).trim();
                return output.isBlank() ? null : Long.parseLong(output.lines().findFirst().orElse("").trim());
            }
            Path pidFile = pidsDir.resolve("service-" + deploymentId + ".pid");
            if (!Files.exists(pidFile)) {
                return null;
            }
            String pidText = Files.readString(pidFile, StandardCharsets.UTF_8).trim();
            return pidText.isBlank() ? null : Long.parseLong(pidText);
        } catch (Exception ignored) {
            return null;
        }
    }

    private int runProcess(ProcessBuilder processBuilder, String stdin, Path logFile, Long deploymentId) throws Exception {
        log.info("Deployment {} starting process: command={} dir={}", deploymentId, processBuilder.command(), processBuilder.directory());
        Process process = processBuilder.start();
        runningProcesses.put(deploymentId, process);
        if (stdin != null) {
            log.info("Deployment {} writing inline script to process stdin ({} chars).", deploymentId, stdin.length());
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
            while ((len = reader.read(buffer)) != -1) {
                writer.write(buffer, 0, len);
                writer.flush();
            }
        }
        int exitCode = process.waitFor();
        log.info("Deployment {} process finished with exit code {}.", deploymentId, exitCode);
        return exitCode;
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
        log.info("Syncing artifacts for deployment {} to remote directory {}.", deploymentId, remoteArtifactDir);
        int copyExitCode = runProcess(
                new ProcessBuilder(buildScpCommand(targetHost, sshDir, localArtifactDir, remoteArtifactDir.toString())).redirectErrorStream(true),
                null,
                logFile,
                deploymentId
        );
        if (copyExitCode != 0) {
            log.warn("Artifact sync failed for deployment {} with exit code {}.", deploymentId, copyExitCode);
            throw new BusinessException(ErrorSubCode.REMOTE_ARTIFACT_SYNC_FAILED);
        }
    }
}
