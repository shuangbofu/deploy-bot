package top.fusb.deploybot.dto;

/**
 * 仪表盘顶部指标卡。
 *
 * @param key 指标键
 * @param label 指标名称
 * @param value 指标值
 * @param suffix 指标单位
 * @param trendLabel 辅助说明
 */
public record DashboardMetricCard(
        String key,
        String label,
        String value,
        String suffix,
        String trendLabel
) {
}
