package top.fusb.deploybot.dto;

/**
 * 仪表盘通用图表点。
 *
 * @param key 数据键
 * @param label 展示名称
 * @param category 分类名称
 * @param value 数值
 * @param extra 辅助文本
 */
public record DashboardChartPoint(
        String key,
        String label,
        String category,
        long value,
        String extra
) {
}
