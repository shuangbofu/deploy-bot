package top.fusb.deploybot.dto;

public record DashboardStatsSummary(
        long projects,
        long templates,
        long pipelines,
        long deployments,
        long hosts,
        long services,
        long users,
        long runningServices,
        int successRate,
        long runningDeployments,
        long failedDeployments
) {
}
