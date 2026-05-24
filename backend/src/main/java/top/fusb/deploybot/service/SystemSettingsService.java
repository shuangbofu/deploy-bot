package top.fusb.deploybot.service;

import lombok.RequiredArgsConstructor;
import top.fusb.deploybot.dto.SystemSettingsRequest;
import top.fusb.deploybot.kit.NumberKit;
import top.fusb.deploybot.kit.SecretKit;
import top.fusb.deploybot.kit.TextKit;
import top.fusb.deploybot.model.GitAuthType;
import top.fusb.deploybot.model.SystemSettingsEntity;
import top.fusb.deploybot.repo.SystemSettingsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SystemSettingsService {

    private final SystemSettingsRepository repository;
    @Value("${deploybot.workspace-root:./runtime}")
    private String defaultWorkspaceRoot;
    @Value("${deploybot.cleanup.enabled:true}")
    private boolean defaultCleanupEnabled;
    @Value("${deploybot.cleanup.artifact-retain-success-count:2}")
    private int defaultArtifactRetainSuccessCount;
    @Value("${deploybot.cleanup.clean-runs-on-success:true}")
    private boolean defaultCleanRunsOnSuccess;
    @Value("${deploybot.cleanup.failed-run-retain-days:0}")
    private int defaultFailedRunRetainDays;

    public SystemSettingsEntity get() {
        return repository.findById(1L).orElseGet(() -> {
            SystemSettingsEntity entity = new SystemSettingsEntity();
            entity.setId(1L);
            entity.setWorkspaceRoot(defaultWorkspaceRoot);
            entity.setGitExecutable("git");
            entity.setGitAuthType(GitAuthType.NONE);
            entity.setCleanupEnabled(defaultCleanupEnabled);
            entity.setArtifactRetainSuccessCount(defaultArtifactRetainSuccessCount);
            entity.setCleanRunsOnSuccess(defaultCleanRunsOnSuccess);
            entity.setFailedRunRetainDays(defaultFailedRunRetainDays);
            return repository.save(entity);
        });
    }

    public SystemSettingsEntity save(SystemSettingsRequest request) {
        SystemSettingsEntity entity = get();
        entity.setWorkspaceRoot(TextKit.isBlank(request.workspaceRoot()) ? defaultWorkspaceRoot : request.workspaceRoot().trim());
        entity.setGitExecutable(TextKit.isBlank(request.gitExecutable()) ? "git" : request.gitExecutable().trim());
        entity.setGitAuthType(request.gitAuthType() == null ? GitAuthType.NONE : request.gitAuthType());
        entity.setGitUsername(TextKit.trimToNull(request.gitUsername()));
        entity.setGitPassword(TextKit.trimToNull(request.gitPassword()));
        entity.setGitSshPrivateKey(SecretKit.mergeOptionalSecret(entity.getGitSshPrivateKey(), request.gitSshPrivateKey()));
        entity.setGitSshPublicKey(SecretKit.mergeOptionalSecret(entity.getGitSshPublicKey(), request.gitSshPublicKey()));
        entity.setGitSshKnownHosts(SecretKit.mergeOptionalSecret(entity.getGitSshKnownHosts(), request.gitSshKnownHosts()));
        entity.setHostSshPrivateKey(SecretKit.mergeOptionalSecret(entity.getHostSshPrivateKey(), request.hostSshPrivateKey()));
        entity.setHostSshPublicKey(SecretKit.mergeOptionalSecret(entity.getHostSshPublicKey(), request.hostSshPublicKey()));
        entity.setCleanupEnabled(request.cleanupEnabled() == null ? defaultCleanupEnabled : request.cleanupEnabled());
        entity.setArtifactRetainSuccessCount(NumberKit.nonNegativeOrDefault(request.artifactRetainSuccessCount(), defaultArtifactRetainSuccessCount));
        entity.setCleanRunsOnSuccess(request.cleanRunsOnSuccess() == null ? defaultCleanRunsOnSuccess : request.cleanRunsOnSuccess());
        entity.setFailedRunRetainDays(NumberKit.nonNegativeOrDefault(request.failedRunRetainDays(), defaultFailedRunRetainDays));
        return saveEntity(entity);
    }

    public boolean isCleanupEnabled(SystemSettingsEntity settings) {
        return settings.getCleanupEnabled() == null ? defaultCleanupEnabled : settings.getCleanupEnabled();
    }

    public int artifactRetainSuccessCount(SystemSettingsEntity settings) {
        return NumberKit.nonNegativeOrDefault(settings.getArtifactRetainSuccessCount(), defaultArtifactRetainSuccessCount);
    }

    public boolean cleanRunsOnSuccess(SystemSettingsEntity settings) {
        return settings.getCleanRunsOnSuccess() == null ? defaultCleanRunsOnSuccess : settings.getCleanRunsOnSuccess();
    }

    public int failedRunRetainDays(SystemSettingsEntity settings) {
        return NumberKit.nonNegativeOrDefault(settings.getFailedRunRetainDays(), defaultFailedRunRetainDays);
    }

    public SystemSettingsEntity saveEntity(SystemSettingsEntity entity) {
        return repository.save(entity);
    }

}
