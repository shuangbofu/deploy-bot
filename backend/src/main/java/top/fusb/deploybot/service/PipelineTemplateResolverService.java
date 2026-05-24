package top.fusb.deploybot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.kit.TemplatePluginKit;
import top.fusb.deploybot.kit.TextKit;
import top.fusb.deploybot.model.PipelineEntity;
import top.fusb.deploybot.model.TemplateEntity;
import top.fusb.deploybot.plugin.api.deployment.definition.DeploymentPluginDefinition;
import top.fusb.deploybot.plugin.api.deployment.template.PluginBuiltinTemplate;
import top.fusb.deploybot.plugin.runtime.DeploymentPluginRuntime;

/**
 * 统一解析流水线当前绑定的模板来源。
 */
@Service
@RequiredArgsConstructor
public class PipelineTemplateResolverService {

    private final DeploymentPluginRuntime deploymentPluginRuntime;

    public ResolvedPipelineTemplate resolveRequired(PipelineEntity pipeline) {
        ResolvedPipelineTemplate resolved = resolve(pipeline);
        if (resolved == null) {
            throw new BusinessException(ErrorSubCode.TEMPLATE_NOT_FOUND);
        }
        return resolved;
    }

    public ResolvedPipelineTemplate resolve(PipelineEntity pipeline) {
        if (pipeline == null) {
            return null;
        }
        if (pipeline.getTemplate() != null) {
            return fromEntity(pipeline.getTemplate());
        }
        String pluginId = TextKit.trimToNull(pipeline.getTemplatePluginId());
        String builtinTemplateKey = TextKit.trimToNull(pipeline.getBuiltinTemplateKey());
        if (pluginId == null || builtinTemplateKey == null) {
            return null;
        }
        return resolveBuiltinTemplate(pluginId, builtinTemplateKey);
    }

    public ResolvedPipelineTemplate resolveBuiltinTemplate(String pluginId, String builtinTemplateKey) {
        if (TextKit.isBlank(pluginId) || TextKit.isBlank(builtinTemplateKey)) {
            return null;
        }
        DeploymentPluginDefinition definition = deploymentPluginRuntime.listDeploymentPluginDefinitions().stream()
                .filter(item -> item != null && item.descriptor() != null && pluginId.equals(item.descriptor().pluginId()))
                .findFirst()
                .orElse(null);
        if (definition == null || definition.builtinTemplates() == null) {
            return null;
        }
        PluginBuiltinTemplate builtinTemplate = definition.builtinTemplates().stream()
                .filter(item -> item != null && builtinTemplateKey.equals(item.templateKey()))
                .findFirst()
                .orElse(null);
        if (builtinTemplate == null) {
            return null;
        }
        return new ResolvedPipelineTemplate(
                builtinTemplate.name(),
                builtinTemplate.templateType(),
                pluginId,
                builtinTemplate.templateKey(),
                builtinTemplate.buildScriptContent(),
                builtinTemplate.deployScriptContent(),
                builtinTemplate.variablesSchema(),
                builtinTemplate.monitorProcess()
        );
    }

    public String resolvePluginId(PipelineEntity pipeline) {
        if (pipeline == null) {
            return null;
        }
        if (TextKit.isNotBlank(pipeline.getTemplatePluginId())) {
            return pipeline.getTemplatePluginId();
        }
        if (pipeline.getTemplate() == null) {
            return null;
        }
        if (TextKit.isNotBlank(pipeline.getTemplate().getPluginId())) {
            return pipeline.getTemplate().getPluginId();
        }
        return TemplatePluginKit.resolvePluginIdByTemplateType(pipeline.getTemplate().getTemplateType());
    }

    private ResolvedPipelineTemplate fromEntity(TemplateEntity template) {
        String pluginId = TextKit.trimToNull(template.getPluginId());
        if (pluginId == null) {
            pluginId = TemplatePluginKit.resolvePluginIdByTemplateType(template.getTemplateType());
        }
        return new ResolvedPipelineTemplate(
                template.getName(),
                template.getTemplateType(),
                pluginId,
                null,
                template.getBuildScriptContent(),
                template.getDeployScriptContent(),
                template.getVariablesSchema(),
                Boolean.TRUE.equals(template.getMonitorProcess())
        );
    }

    public record ResolvedPipelineTemplate(
            String name,
            String templateType,
            String pluginId,
            String builtinTemplateKey,
            String buildScriptContent,
            String deployScriptContent,
            String variablesSchema,
            boolean monitorProcess
    ) {
    }
}
