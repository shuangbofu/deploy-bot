package top.fusb.deploybot.service;

import top.fusb.deploybot.kit.TextKit;
import top.fusb.deploybot.kit.TemplatePluginKit;
import top.fusb.deploybot.dto.PageResult;
import top.fusb.deploybot.dto.TemplateRequest;
import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.model.TemplateEntity;
import top.fusb.deploybot.repo.TemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private final TemplateRepository repository;
    private final TemplateVariableSchemaCleanupService templateVariableSchemaCleanupService;

    public List<TemplateEntity> findAll() {
        return repository.findAll().stream()
                .map(this::normalizePluginId)
                .toList();
    }

    public TemplateEntity findById(Long id) {
        return normalizePluginId(repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.TEMPLATE_NOT_FOUND)));
    }

    public PageResult<TemplateEntity> findPage(int page, int pageSize, String keyword, String templateType, String pluginId, Boolean monitorProcess) {
        org.springframework.data.domain.Page<TemplateEntity> result = repository.findAll((root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern)
                ));
            }
            if (templateType != null && !templateType.isBlank()) {
                predicates.add(cb.equal(root.get("templateType"), templateType));
            }
            if (pluginId != null && !pluginId.isBlank()) {
                List<String> fallbackTemplateTypes = TemplatePluginKit.resolveTemplateTypesByPluginId(pluginId);
                List<jakarta.persistence.criteria.Predicate> pluginPredicates = new java.util.ArrayList<>();
                pluginPredicates.add(cb.equal(root.get("pluginId"), pluginId));
                if (!fallbackTemplateTypes.isEmpty()) {
                    pluginPredicates.add(cb.and(
                            cb.or(cb.isNull(root.get("pluginId")), cb.equal(root.get("pluginId"), "")),
                            root.get("templateType").in(fallbackTemplateTypes)
                    ));
                }
                predicates.add(cb.or(pluginPredicates.toArray(jakarta.persistence.criteria.Predicate[]::new)));
            }
            if (monitorProcess != null) {
                predicates.add(cb.equal(root.get("monitorProcess"), monitorProcess));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, PageRequest.of(Math.max(0, page - 1), Math.max(1, Math.min(100, pageSize)), Sort.by(Sort.Order.desc("id"))));
        result.getContent().forEach(this::normalizePluginId);
        return PageResult.of(result);
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
        String buildScript = TextKit.normalizeScript(request.buildScriptContent());
        String deployScript = TextKit.normalizeScript(request.deployScriptContent());
        if (buildScript == null || buildScript.isBlank()) {
            throw new BusinessException(ErrorSubCode.TEMPLATE_BUILD_SCRIPT_REQUIRED);
        }

        String pluginId = resolvePluginId(request);
        variablesSchema = templateVariableSchemaCleanupService.sanitizeSchema(pluginId, request.templateType(), variablesSchema);

        entity.setName(name);
        entity.setDescription(request.description());
        entity.setTemplateType(request.templateType());
        entity.setPluginId(pluginId);
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

    private String resolvePluginId(TemplateRequest request) {
        String pluginId = TextKit.trimToNull(request.pluginId());
        if (pluginId != null) {
            return pluginId;
        }
        return TemplatePluginKit.resolvePluginIdByTemplateType(request.templateType());
    }

    private TemplateEntity normalizePluginId(TemplateEntity entity) {
        if (entity == null || TextKit.isNotBlank(entity.getPluginId())) {
            return entity;
        }
        entity.setPluginId(TemplatePluginKit.resolvePluginIdByTemplateType(entity.getTemplateType()));
        return entity;
    }

}
