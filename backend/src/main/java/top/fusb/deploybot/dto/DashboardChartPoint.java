package top.fusb.deploybot.dto;

/**
 * 仪表盘通用图表点。
 *
 * @param key 数据键
 * @param label 数据显示名，来自业务实体名称或时间格式化，不承载前端固定翻译。
 * @param category 分类键，由前端翻译为展示文案。
 * @param value 数值
 */
public record DashboardChartPoint(
        String key,
        String label,
        String category,
        long value
) {
}
