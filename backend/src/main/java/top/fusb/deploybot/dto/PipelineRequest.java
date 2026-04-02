package top.fusb.deploybot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
        /** 关联模板 ID。 */
        @NotNull Long templateId,
        /** 目标主机 ID。 */
        @NotNull Long targetHostId,
        /** 默认分支。 */
        @NotBlank String defaultBranch,
        /** 默认变量 JSON。 */
        String variablesJson,
        /** 自定义标签 JSON。 */
        String tagsJson,
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
        /** 开启发布监控时用于生成唯一产物名的应用名。 */
        String applicationName,
        /** Spring Boot 运行时激活的 profile。 */
        String springProfile,
        /** Spring Boot 运行时附加 YAML 配置。 */
        String runtimeConfigYaml,
        /** 启用服务监测时的启动关键字。 */
        String startupKeyword,
        /** 启用服务监测时的启动观察窗口，单位秒。 */
        Integer startupTimeoutSeconds,
        /** 流水线绑定的通知配置 JSON。 */
        String notificationBindingsJson
) {
}
