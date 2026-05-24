package top.fusb.deploybot.service;

import lombok.RequiredArgsConstructor;
import top.fusb.deploybot.model.DeploymentEntity;
import top.fusb.deploybot.model.DeploymentStatus;
import top.fusb.deploybot.model.HostEntity;
import top.fusb.deploybot.model.HostType;
import top.fusb.deploybot.model.SystemSettingsEntity;
import top.fusb.deploybot.repo.DeploymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 清理部署产生的临时工作区和过期构建产物。
 */
@Service
@RequiredArgsConstructor
public class DeploymentCleanupService {
    private static final Logger log = LoggerFactory.getLogger(DeploymentCleanupService.class);
    private static final String RUNS_DIR = "runs";

    private final DeploymentRepository deploymentRepository;
    private final SystemSettingsService systemSettingsService;
    private final HostService hostService;

    @Transactional
    public void cleanupAfterDeployment(DeploymentEntity deployment, Path buildWorkspaceRoot) {
        if (deployment == null || deployment.getId() == null || buildWorkspaceRoot == null) {
            return;
        }
        SystemSettingsEntity settings = systemSettingsService.get();
        if (!systemSettingsService.isCleanupEnabled(settings)) {
            return;
        }
        cleanupRunWorkspace(deployment, buildWorkspaceRoot, settings);
        cleanupOldRunWorkspaces(buildWorkspaceRoot, settings);
        cleanupPipelineArtifacts(deployment, settings);
        cleanupRemoteArtifacts(deployment, buildWorkspaceRoot, settings);
    }

    private void cleanupRunWorkspace(DeploymentEntity deployment, Path buildWorkspaceRoot, SystemSettingsEntity settings) {
        if (deployment.getStatus() == DeploymentStatus.SUCCESS && systemSettingsService.cleanRunsOnSuccess(settings)) {
            deleteRunWorkspace(buildWorkspaceRoot, deployment.getId());
            return;
        }
        if ((deployment.getStatus() == DeploymentStatus.FAILED || deployment.getStatus() == DeploymentStatus.STOPPED)
                && systemSettingsService.failedRunRetainDays(settings) <= 0) {
            deleteRunWorkspace(buildWorkspaceRoot, deployment.getId());
        }
    }

    private void cleanupOldRunWorkspaces(Path buildWorkspaceRoot, SystemSettingsEntity settings) {
        int retainDays = systemSettingsService.failedRunRetainDays(settings);
        if (retainDays <= 0) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retainDays);
        List<DeploymentEntity> expired = deploymentRepository.findByStatusInAndCreatedAtBefore(
                List.of(DeploymentStatus.FAILED, DeploymentStatus.STOPPED),
                cutoff
        );
        expired.forEach(item -> deleteRunWorkspace(buildWorkspaceRoot, item.getId()));
    }

    private void cleanupPipelineArtifacts(DeploymentEntity deployment, SystemSettingsEntity settings) {
        if (deployment.getStatus() == DeploymentStatus.FAILED || deployment.getStatus() == DeploymentStatus.STOPPED) {
            deleteDirectory(deployment.getArtifactPath());
            deployment.setArtifactPath(null);
            deploymentRepository.save(deployment);
        }
        if (deployment.getPipeline() == null || deployment.getPipeline().getId() == null) {
            return;
        }
        int retainCount = systemSettingsService.artifactRetainSuccessCount(settings);
        List<DeploymentEntity> successfulDeployments = deploymentRepository
                .findByPipelineIdAndStatusAndArtifactPathIsNotNullOrderByCreatedAtDesc(
                        deployment.getPipeline().getId(),
                        DeploymentStatus.SUCCESS
                );
        for (int index = retainCount; index < successfulDeployments.size(); index++) {
            DeploymentEntity expired = successfulDeployments.get(index);
            deleteDirectory(expired.getArtifactPath());
            expired.setArtifactPath(null);
            deploymentRepository.save(expired);
        }
    }

    private void cleanupRemoteArtifacts(DeploymentEntity deployment, Path buildWorkspaceRoot, SystemSettingsEntity settings) {
        HostEntity targetHost = deployment.getPipeline() == null ? null : deployment.getPipeline().getTargetHost();
        if (targetHost == null || targetHost.getType() != HostType.SSH) {
            return;
        }
        Path remoteWorkspaceRoot = resolveTargetWorkspaceRoot(targetHost, buildWorkspaceRoot);
        if (deployment.getStatus() == DeploymentStatus.FAILED || deployment.getStatus() == DeploymentStatus.STOPPED) {
            deleteRemoteDirectory(targetHost, remoteWorkspaceRoot.resolve("artifacts").resolve("deploy-" + deployment.getId()));
        }
        if (deployment.getPipeline() == null || deployment.getPipeline().getId() == null) {
            return;
        }
        int retainCount = systemSettingsService.artifactRetainSuccessCount(settings);
        List<DeploymentEntity> successfulDeployments = deploymentRepository
                .findByPipelineIdAndStatusAndArtifactPathIsNotNullOrderByCreatedAtDesc(
                        deployment.getPipeline().getId(),
                        DeploymentStatus.SUCCESS
                );
        for (int index = retainCount; index < successfulDeployments.size(); index++) {
            DeploymentEntity expired = successfulDeployments.get(index);
            deleteRemoteDirectory(targetHost, remoteWorkspaceRoot.resolve("artifacts").resolve("deploy-" + expired.getId()));
        }
    }

    private void deleteRunWorkspace(Path buildWorkspaceRoot, Long deploymentId) {
        if (deploymentId == null) {
            return;
        }
        deleteDirectory(buildWorkspaceRoot.resolve(RUNS_DIR).resolve(String.valueOf(deploymentId)));
    }

    private void deleteDirectory(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        deleteDirectory(Path.of(path));
    }

    private void deleteDirectory(Path path) {
        try {
            if (path == null || !Files.exists(path)) {
                return;
            }
            try (var stream = Files.walk(path)) {
                stream.sorted(Comparator.reverseOrder()).forEach(item -> {
                    try {
                        Files.deleteIfExists(item);
                    } catch (IOException ex) {
                        log.warn("清理部署目录失败：{} -> {}", item, ex.getMessage());
                    }
                });
            }
            log.info("已清理部署目录：{}", path.toAbsolutePath().normalize());
        } catch (Exception ex) {
            log.warn("清理部署目录失败：{} -> {}", path, ex.getMessage());
        }
    }

    private Path resolveTargetWorkspaceRoot(HostEntity targetHost, Path localWorkspaceRoot) {
        if (targetHost != null && targetHost.getWorkspaceRoot() != null && !targetHost.getWorkspaceRoot().isBlank()) {
            return Path.of(targetHost.getWorkspaceRoot().trim());
        }
        return localWorkspaceRoot;
    }

    private void deleteRemoteDirectory(HostEntity targetHost, Path path) {
        try {
            hostService.executeRemoteScript(
                    targetHost.getId(),
                    "rm -rf \"" + path.toString().replace("\"", "\\\"") + "\"\n",
                    30
            );
            log.info("已清理远程部署目录：{} -> {}", targetHost.getName(), path.toAbsolutePath().normalize());
        } catch (Exception ex) {
            log.warn("清理远程部署目录失败：{} -> {} -> {}", targetHost.getName(), path, ex.getMessage());
        }
    }
}
