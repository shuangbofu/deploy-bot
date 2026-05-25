package top.fusb.deploybot.dto;

/**
 * 仪表盘顶部指标卡。
 *
 * @param key 指标键
 * @param value 指标值
 */
public record DashboardMetricCard(
        String key,
        String value
) {
}
