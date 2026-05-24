package top.fusb.deploybot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.fusb.deploybot.kit.TextKit;
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
@RequiredArgsConstructor
public class MavenSettingsService {

    private final MavenSettingsRepository repository;
    private final RuntimeEnvironmentRepository runtimeEnvironmentRepository;

    public List<MavenSettingsEntity> findAll(Long runtimeEnvironmentId) {
        if (runtimeEnvironmentId == null) {
            return List.of();
        }
        return repository.findByRuntimeEnvironmentIdAndDeletedFalseOrderByIsDefaultDescNameAsc(runtimeEnvironmentId);
    }

    public MavenSettingsEntity save(Long runtimeEnvironmentId, MavenSettingsRequest request, Long id) {
        RuntimeEnvironmentEntity runtimeEnvironment = runtimeEnvironmentRepository.findById(runtimeEnvironmentId)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.RUNTIME_ENVIRONMENT_NOT_FOUND));
        if (runtimeEnvironment.getType() != RuntimeEnvironmentType.MAVEN) {
            throw new BusinessException(ErrorSubCode.MAVEN_SETTINGS_NOT_FOUND, "只能给 Maven 环境配置 settings.xml。");
        }

        MavenSettingsEntity entity = id == null ? new MavenSettingsEntity() : repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.MAVEN_SETTINGS_NOT_FOUND));
        if (id != null && (entity.getRuntimeEnvironment() == null || !entity.getRuntimeEnvironment().getId().equals(runtimeEnvironmentId))) {
            throw new BusinessException(ErrorSubCode.MAVEN_SETTINGS_NOT_FOUND);
        }

        entity.setName(request.name().trim());
        entity.setRuntimeEnvironment(runtimeEnvironment);
        entity.setDescription(TextKit.trimToNull(request.description()));
        entity.setContentXml(request.contentXml().replace("\r\n", "\n").trim());
        entity.setEnabled(request.enabled() == null ? Boolean.TRUE : request.enabled());
        entity.setIsDefault(request.isDefault() == null ? Boolean.FALSE : request.isDefault());
        entity.setDeleted(Boolean.FALSE);
        MavenSettingsEntity saved = repository.save(entity);

        if (Boolean.TRUE.equals(saved.getIsDefault())) {
            clearOtherDefaults(saved.getRuntimeEnvironment().getId(), saved.getId());
        }
        return saved;
    }

    public void delete(Long runtimeEnvironmentId, Long id) {
        MavenSettingsEntity entity = repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.MAVEN_SETTINGS_NOT_FOUND));
        if (entity.getRuntimeEnvironment() == null || !entity.getRuntimeEnvironment().getId().equals(runtimeEnvironmentId)) {
            throw new BusinessException(ErrorSubCode.MAVEN_SETTINGS_NOT_FOUND);
        }
        entity.setDeleted(Boolean.TRUE);
        entity.setEnabled(Boolean.FALSE);
        entity.setIsDefault(Boolean.FALSE);
        repository.save(entity);
    }

    private void clearOtherDefaults(Long runtimeEnvironmentId, Long currentId) {
        List<MavenSettingsEntity> settings = repository.findByRuntimeEnvironmentIdAndDeletedFalseOrderByIsDefaultDescNameAsc(runtimeEnvironmentId);
        settings.stream()
                .filter(item -> !item.getId().equals(currentId) && Boolean.TRUE.equals(item.getIsDefault()))
                .forEach(item -> {
                    item.setIsDefault(Boolean.FALSE);
                    repository.save(item);
                });
    }
}
