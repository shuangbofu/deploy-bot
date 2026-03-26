package top.fusb.deploybot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 安装预置运行环境请求体。
 */
public record RuntimeEnvironmentInstallRequest(
        /** 预置 ID。 */
        @NotBlank String presetId,
        /** 目标主机 ID。 */
        @NotNull Long hostId
) {
}
