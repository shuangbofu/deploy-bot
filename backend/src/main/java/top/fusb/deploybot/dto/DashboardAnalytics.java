package top.fusb.deploybot.dto;

import java.util.List;

/**
 * 仪表盘图表分析数据。
 *
 * @param metrics 顶部指标
 * @param trend 趋势图数据
 * @param statusDistribution 状态分布
 * @param projectRanking 项目排行
 * @param pipelineRanking 流水线排行
 * @param triggerRanking 触发人排行
 * @param templateTypeDistribution 模板类型分布
 * @param hostDistribution 目标主机分布
 * @param recentDeployments 最近部署散点
 * @param durationDistribution 耗时分布
 */
public record DashboardAnalytics(
        List<DashboardMetricCard> metrics,
        List<DashboardChartPoint> trend,
        List<DashboardChartPoint> statusDistribution,
        List<DashboardChartPoint> projectRanking,
        List<DashboardChartPoint> pipelineRanking,
        List<DashboardChartPoint> triggerRanking,
        List<DashboardChartPoint> templateTypeDistribution,
        List<DashboardChartPoint> hostDistribution,
        List<DashboardRecentPoint> recentDeployments,
        List<DashboardChartPoint> durationDistribution
) {
}
