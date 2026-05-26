package top.fusb.deploybot.dto;

/**
 * 仪表盘通用图表点。
 *
 * @param key 数据键
 * @param label 数据显示名，仅承载业务实体原始名称；固定文案由前端根据 key/category 渲染。
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
