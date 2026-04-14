package top.fusb.deploybot.service;

import top.fusb.deploybot.model.DeploymentEntity;
import top.fusb.deploybot.model.DeploymentStatus;
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
public class DeploymentCleanupService {
    private static final Logger log = LoggerFactory.getLogger(DeploymentCleanupService.class);
    private static final String RUNS_DIR = "runs";

    private final DeploymentRepository deploymentRepository;
    private final SystemSettingsService systemSettingsService;

    public DeploymentCleanupService(DeploymentRepository deploymentRepository, SystemSettingsService systemSettingsService) {
        this.deploymentRepository = deploymentRepository;
        this.systemSettingsService = systemSettingsService;
    }

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
}
