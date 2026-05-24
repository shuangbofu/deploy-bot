package top.fusb.deploybot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.fusb.deploybot.dto.TemplateVariableSchemaItem;
import top.fusb.deploybot.kit.TemplatePluginKit;
import top.fusb.deploybot.kit.TemplateVariableSchemaKit;
import top.fusb.deploybot.kit.TextKit;
import top.fusb.deploybot.model.TemplateEntity;
import top.fusb.deploybot.plugin.api.deployment.DeploymentPlugin;
import top.fusb.deploybot.plugin.api.deployment.variable.PluginVariableDefinition;
import top.fusb.deploybot.plugin.runtime.DeploymentPluginRuntime;
import top.fusb.deploybot.repo.TemplateRepository;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 清理模板变量定义中的平台保留项。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateVariableSchemaCleanupService {

    private static final Set<String> RESERVED_VARIABLES = Set.of(
            "branch",
            "gitUrl",
            "gitRepositoryUrl",
            "projectId",
            "projectName",
            "pipelineId",
            "pipelineName",
            "serviceName",
            "targetDir",
            "workspaceRoot",
            "buildWorkspaceRoot",
            "deployWorkspaceRoot",
            "deploymentId",
            "artifactDir",
            "sourceArtifactPath"
    );

    private final DeploymentPluginRuntime deploymentPluginRuntime;
    private final TemplateRepository templateRepository;

    public String sanitizeSchema(String pluginId, String templateType, String variablesSchema) {
        if (TextKit.isBlank(variablesSchema)) {
            return variablesSchema;
        }
        Set<String> excluded = excludedVariableNames();
        Map<String, PluginVariableDefinition> pluginVariableMap = pluginVariableMap(pluginId, templateType);
        List<TemplateVariableSchemaItem> sanitized = TemplateVariableSchemaKit.read(variablesSchema).stream()
                .filter(item -> item != null && TextKit.isNotBlank(item.name()))
                .filter(item -> !excluded.contains(item.name()))
                .map(item -> new TemplateVariableSchemaItem(
                        item.name(),
                        item.label(),
                        item.placeholder(),
                        item.required(),
                        item.pipelineInput() != null
                                ? item.pipelineInput()
                                : (pluginVariableMap.containsKey(item.name()) ? pluginVariableMap.get(item.name()).pipelineInput() : Boolean.TRUE),
                        item.phase(),
                        item.mutationRuleIds(),
                        item.binding()
                ))
                .toList();
        return TemplateVariableSchemaKit.write(sanitized);
    }

    public void cleanupExistingTemplates() {
        List<TemplateEntity> templates = templateRepository.findAll();
        int changed = 0;
        for (TemplateEntity template : templates) {
            String sanitized = sanitizeSchema(template.getPluginId(), template.getTemplateType(), template.getVariablesSchema());
            if (!String.valueOf(template.getVariablesSchema()).equals(String.valueOf(sanitized))) {
                template.setVariablesSchema(sanitized);
                templateRepository.save(template);
                changed++;
            }
        }
        if (changed > 0) {
            log.info("已清理 {} 条模板变量定义，移除了平台保留变量。", changed);
        }
    }

    private Set<String> excludedVariableNames() {
        return new LinkedHashSet<>(RESERVED_VARIABLES);
    }

    private Map<String, PluginVariableDefinition> pluginVariableMap(String pluginId, String templateType) {
        String resolvedPluginId = TextKit.trimToNull(pluginId);
        if (resolvedPluginId == null) {
            resolvedPluginId = TemplatePluginKit.resolvePluginIdByTemplateType(templateType);
        }
        DeploymentPlugin plugin = deploymentPluginRuntime.resolveDeploymentPlugin(resolvedPluginId);
        if (plugin == null) {
            return Map.of();
        }
        Map<String, PluginVariableDefinition> result = new LinkedHashMap<>();
        for (PluginVariableDefinition item : plugin.variableDefinitions()) {
            if (item != null && TextKit.isNotBlank(item.key())) {
                result.put(item.key(), item);
            }
        }
        return result;
    }
}
