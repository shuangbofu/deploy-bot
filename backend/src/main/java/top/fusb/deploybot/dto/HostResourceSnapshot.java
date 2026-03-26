package top.fusb.deploybot.dto;

import java.time.LocalDateTime;

/**
 * 主机资源快照。
 */
public record HostResourceSnapshot(
        /** 主机主键。 */
        Long hostId,
        /** 主机名称。 */
        String hostName,
        /** 当前采集时间。 */
        LocalDateTime collectedAt,
        /** 操作系统类型。 */
        String osType,
        /** 工作空间目录。 */
        String workspaceRoot,
        /** CPU 核数。 */
        Integer cpuCores,
        /** CPU 使用率。 */
        Integer cpuUsagePercent,
        /** 1 分钟平均负载。 */
        Double loadAverage,
        /** 内存总量（MB）。 */
        Long memoryTotalMb,
        /** 已用内存（MB）。 */
        Long memoryUsedMb,
        /** 内存使用率。 */
        Integer memoryUsagePercent,
        /** 磁盘总量（GB）。 */
        Long diskTotalGb,
        /** 已用磁盘（GB）。 */
        Long diskUsedGb,
        /** 磁盘使用率。 */
        Integer diskUsagePercent,
        /** 面向用户的预览文本。 */
        String preview
) {
}
