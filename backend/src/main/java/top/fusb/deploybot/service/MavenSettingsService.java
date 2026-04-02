package top.fusb.deploybot.service;

import org.springframework.stereotype.Service;
import top.fusb.deploybot.dto.MavenSettingsRequest;
import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.model.MavenSettingsEntity;
import top.fusb.deploybot.model.RuntimeEnvironmentEntity;
import top.fusb.deploybot.model.RuntimeEnvironmentType;
import top.fusb.deploybot.repo.MavenSettingsRepository;
import top.fusb.deploybot.repo.RuntimeEnvironmentRepository;

import java.util.List;

@Service
public class MavenSettingsService {

    private final MavenSettingsRepository repository;
    private final RuntimeEnvironmentRepository runtimeEnvironmentRepository;

    public MavenSettingsService(MavenSettingsRepository repository, RuntimeEnvironmentRepository runtimeEnvironmentRepository) {
        this.repository = repository;
        this.runtimeEnvironmentRepository = runtimeEnvironmentRepository;
    }

    public List<MavenSettingsEntity> findAll(Long runtimeEnvironmentId) {
        if (runtimeEnvironmentId == null) {
            return List.of();
        }
        return repository.findByRuntimeEnvironmentIdOrderByIsDefaultDescNameAsc(runtimeEnvironmentId);
    }

    public MavenSettingsEntity save(Long runtimeEnvironmentId, MavenSettingsRequest request, Long id) {
        RuntimeEnvironmentEntity runtimeEnvironment = runtimeEnvironmentRepository.findById(runtimeEnvironmentId)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.RUNTIME_ENVIRONMENT_NOT_FOUND));
        if (runtimeEnvironment.getType() != RuntimeEnvironmentType.MAVEN) {
            throw new BusinessException(ErrorSubCode.MAVEN_SETTINGS_NOT_FOUND, "只能给 Maven 环境配置 settings.xml。");
        }

        MavenSettingsEntity entity = id == null ? new MavenSettingsEntity() : repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.MAVEN_SETTINGS_NOT_FOUND));
        if (id != null && (entity.getRuntimeEnvironment() == null || !entity.getRuntimeEnvironment().getId().equals(runtimeEnvironmentId))) {
            throw new BusinessException(ErrorSubCode.MAVEN_SETTINGS_NOT_FOUND);
        }

        entity.setName(request.name().trim());
        entity.setRuntimeEnvironment(runtimeEnvironment);
        entity.setDescription(trimToNull(request.description()));
        entity.setContentXml(request.contentXml().replace("\r\n", "\n").trim());
        entity.setEnabled(request.enabled() == null ? Boolean.TRUE : request.enabled());
        entity.setIsDefault(request.isDefault() == null ? Boolean.FALSE : request.isDefault());
        MavenSettingsEntity saved = repository.save(entity);

        if (Boolean.TRUE.equals(saved.getIsDefault())) {
            clearOtherDefaults(saved.getRuntimeEnvironment().getId(), saved.getId());
        }
        return saved;
    }

    public void delete(Long runtimeEnvironmentId, Long id) {
        if (!repository.existsByIdAndRuntimeEnvironmentId(id, runtimeEnvironmentId)) {
            throw new BusinessException(ErrorSubCode.MAVEN_SETTINGS_NOT_FOUND);
        }
        repository.deleteById(id);
    }

    private void clearOtherDefaults(Long runtimeEnvironmentId, Long currentId) {
        List<MavenSettingsEntity> settings = repository.findByRuntimeEnvironmentIdOrderByIsDefaultDescNameAsc(runtimeEnvironmentId);
        settings.stream()
                .filter(item -> !item.getId().equals(currentId) && Boolean.TRUE.equals(item.getIsDefault()))
                .forEach(item -> {
                    item.setIsDefault(Boolean.FALSE);
                    repository.save(item);
                });
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
