package top.fusb.deploybot.dto;

/**
 * 模板新增或编辑请求体。
 */
public record TemplateRequest(
        /** 模板名称。 */
        String name,
        /** 模板说明。 */
        String description,
        /** 模板类型。 */
        String templateType,
        /** 构建脚本。 */
        String buildScriptContent,
        /** 发布脚本。 */
        String deployScriptContent,
        /** 模板变量定义 JSON。 */
        String variablesSchema,
        /** 发布后是否监控进程。 */
        Boolean monitorProcess
) {
}
