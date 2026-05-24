package top.fusb.deploybot.plugin.api.deployment.context;

/**
 * 描述一次部署所属的项目、流水线与模板基础信息。
 *
 * @param templateType 模板类型
 * @param templateName 模板名称
 * @param projectName 项目名称
 * @param pipelineName 流水线名称
 * @param branchName 分支名称
 */
public record PluginProjectContext(
        String templateType,
        String templateName,
        String projectName,
        String pipelineName,
        String branchName
) {
}
