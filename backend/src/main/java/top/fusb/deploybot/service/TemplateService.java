package top.fusb.deploybot.service;

import top.fusb.deploybot.dto.TemplateRequest;
import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.model.TemplateEntity;
import top.fusb.deploybot.repo.TemplateRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TemplateService {

    private final TemplateRepository repository;

    public TemplateService(TemplateRepository repository) {
        this.repository = repository;
    }

    public List<TemplateEntity> findAll() {
        return repository.findAll();
    }

    public TemplateEntity save(TemplateRequest request, Long id) {
        TemplateEntity entity = id == null ? new TemplateEntity() : repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.TEMPLATE_NOT_FOUND));
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isBlank()) {
            throw new BusinessException(ErrorSubCode.TEMPLATE_NAME_REQUIRED);
        }
        String variablesSchema = request.variablesSchema() == null ? "" : request.variablesSchema().trim();
        if (variablesSchema.isBlank()) {
            throw new BusinessException(ErrorSubCode.TEMPLATE_VARIABLES_REQUIRED);
        }
        String buildScript = normalizeScript(request.buildScriptContent());
        String deployScript = normalizeScript(request.deployScriptContent());
        if (buildScript == null || buildScript.isBlank()) {
            throw new BusinessException(ErrorSubCode.TEMPLATE_BUILD_SCRIPT_REQUIRED);
        }

        entity.setName(name);
        entity.setDescription(request.description());
        entity.setTemplateType(request.templateType());
        entity.setBuildScriptContent(buildScript);
        entity.setDeployScriptContent(deployScript);
        entity.setVariablesSchema(variablesSchema);
        entity.setMonitorProcess(Boolean.TRUE.equals(request.monitorProcess()));
        return repository.save(entity);
    }

    public void delete(Long id) {
        repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.TEMPLATE_NOT_FOUND));
        repository.deleteById(id);
    }

    /**
     * 统一收口脚本内容，保证保存到数据库中的脚本以换行结尾，后续落文件更稳定。
     */
    private String normalizeScript(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.trim();
        return trimmed.isBlank() ? null : trimmed + "\n";
    }
    private String normalizeText(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
