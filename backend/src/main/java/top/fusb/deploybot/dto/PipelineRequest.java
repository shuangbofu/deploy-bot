package top.fusb.deploybot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import top.fusb.deploybot.notification.dto.NotificationBinding;

import java.util.List;
import java.util.Map;

/**
 * 流水线新增或编辑请求体。
 */
public record PipelineRequest(
        /** 流水线名称。 */
        @NotBlank String name,
        /** 流水线说明。 */
        String description,
        /** 关联项目 ID。 */
        @NotNull Long projectId,
        /** 关联数据库模板 ID。 */
        Long templateId,
        /** 关联插件默认模板时的插件标识。 */
        String templatePluginId,
        /** 关联插件默认模板时的模板键。 */
        String builtinTemplateKey,
        /** 目标主机 ID。 */
        @NotNull Long targetHostId,
        /** 目标主机上的部署目录。 */
        @NotBlank String targetDir,
        /** 默认分支。 */
        @NotBlank String defaultBranch,
        /** 默认变量。 */
        Map<String, String> variables,
        /** 自定义标签。 */
        List<String> tags,
        /** 重要标签，最多两个，用于标题前突出展示。 */
        List<String> importantTags,
        /** 本机构建 Java 环境 ID。 */
        Long javaEnvironmentId,
        /** 本机构建 Node 环境 ID。 */
        Long nodeEnvironmentId,
        /** 本机构建 Maven 环境 ID。 */
        Long mavenEnvironmentId,
        /** 本机构建 Maven Settings ID。 */
        Long mavenSettingsId,
        /** 目标主机运行 Java 环境 ID。 */
        Long runtimeJavaEnvironmentId,
        /** 插件运行配置，前端只按插件字段 key 提交，不感知具体插件内部字段。 */
        Map<String, String> pluginConfig,
        /** 启用服务监测时的启动关键字。 */
        String startupKeyword,
        /** 启用服务监测时的启动观察窗口，单位秒。 */
        Integer startupTimeoutSeconds,
        /** 流水线绑定的通知配置。 */
        List<NotificationBinding> notificationBindings
) {
}
