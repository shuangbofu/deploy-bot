package top.fusb.deploybot.dto;

import top.fusb.deploybot.model.RuntimeEnvironmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 运行环境新增或编辑请求体。
 */
public record RuntimeEnvironmentRequest(
        /** 运行环境名称。 */
        @NotBlank String name,
        /** 运行环境类型。 */
        @NotNull RuntimeEnvironmentType type,
        /** 所属主机 ID。 */
        @NotNull Long hostId,
        /** 版本。 */
        String version,
        /** 安装根目录。 */
        String homePath,
        /** bin 目录。 */
        String binPath,
        /** 激活脚本。 */
        String activationScript,
        /** 附加环境变量 JSON。 */
        String environmentJson,
        /** 是否启用。 */
        Boolean enabled
) {
}
