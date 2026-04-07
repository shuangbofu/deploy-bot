package top.fusb.deploybot.dto;

public record DashboardTrendItem(
        String key,
        String label,
        long total,
        long success
) {
}
