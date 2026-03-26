package top.fusb.deploybot.dto;

import top.fusb.deploybot.model.RuntimeEnvironmentType;

/**
 * 自动检测到的运行环境信息。
 */
public record RuntimeEnvironmentDetection(
        /** 检测结果名称。 */
        String name,
        /** 组件类型。 */
        RuntimeEnvironmentType type,
        /** 检测到的版本文案。 */
        String version,
        /** 检测到的 HOME 路径。 */
        String homePath,
        /** 检测到的 bin 路径。 */
        String binPath
) {
}
