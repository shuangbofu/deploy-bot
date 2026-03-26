package top.fusb.deploybot.dto;

import top.fusb.deploybot.model.RuntimeEnvironmentType;

/**
 * 返回给前端的运行环境预置项。
 * 这里已经是按主机平台渲染完成后的最终结果。
 */
public record RuntimeEnvironmentPreset(
        /** 预置唯一标识。 */
        String id,
        /** 预置名称。 */
        String name,
        /** 组件类型。 */
        RuntimeEnvironmentType type,
        /** 预置版本。 */
        String version,
        /** 预置说明。 */
        String description,
        /** 下载地址。 */
        String downloadUrl,
        /** 安装后的 home 目录。 */
        String homePath,
        /** 安装后的 bin 目录。 */
        String binPath
) {
}
