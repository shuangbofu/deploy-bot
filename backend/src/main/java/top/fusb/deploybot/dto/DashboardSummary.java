package top.fusb.deploybot.dto;

import java.util.List;

public record DashboardSummary(
        DashboardStatsSummary stats,
        List<DashboardTrendItem> trend,
        List<DashboardDeploymentSummary> latestDeployments,
        List<DashboardDeploymentSummary> attentionDeployments,
        List<DashboardServiceSummary> services
) {
}
