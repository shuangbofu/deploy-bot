package top.fusb.deploybot.service;

import top.fusb.deploybot.dto.SystemSettingsRequest;
import top.fusb.deploybot.model.GitAuthType;
import top.fusb.deploybot.model.SystemSettingsEntity;
import top.fusb.deploybot.repo.SystemSettingsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SystemSettingsService {

    private final SystemSettingsRepository repository;
    private final String defaultWorkspaceRoot;

    public SystemSettingsService(
            SystemSettingsRepository repository,
            @Value("${deploybot.workspace-root:./runtime}") String defaultWorkspaceRoot
    ) {
        this.repository = repository;
        this.defaultWorkspaceRoot = defaultWorkspaceRoot;
    }

    public SystemSettingsEntity get() {
        return repository.findById(1L).orElseGet(() -> {
            SystemSettingsEntity entity = new SystemSettingsEntity();
            entity.setId(1L);
            entity.setWorkspaceRoot(defaultWorkspaceRoot);
            entity.setGitExecutable("git");
            entity.setGitAuthType(GitAuthType.NONE);
            return repository.save(entity);
        });
    }

    public SystemSettingsEntity save(SystemSettingsRequest request) {
        SystemSettingsEntity entity = get();
        entity.setWorkspaceRoot(isBlank(request.workspaceRoot()) ? defaultWorkspaceRoot : request.workspaceRoot().trim());
        entity.setGitExecutable(isBlank(request.gitExecutable()) ? "git" : request.gitExecutable().trim());
        entity.setGitAuthType(request.gitAuthType() == null ? GitAuthType.NONE : request.gitAuthType());
        entity.setGitUsername(trimToNull(request.gitUsername()));
        entity.setGitPassword(trimToNull(request.gitPassword()));
        entity.setGitSshPrivateKey(trimToNull(request.gitSshPrivateKey()));
        entity.setGitSshPublicKey(trimToNull(request.gitSshPublicKey()));
        entity.setGitSshKnownHosts(trimToNull(request.gitSshKnownHosts()));
        entity.setHostSshPrivateKey(trimToNull(request.hostSshPrivateKey()));
        entity.setHostSshPublicKey(trimToNull(request.hostSshPublicKey()));
        return saveEntity(entity);
    }

    public SystemSettingsEntity saveEntity(SystemSettingsEntity entity) {
        return repository.save(entity);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
