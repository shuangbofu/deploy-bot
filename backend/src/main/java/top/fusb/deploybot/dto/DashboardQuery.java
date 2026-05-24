package top.fusb.deploybot.dto;

/**
 * 仪表盘查询条件。
 *
 * @param range 时间范围，支持 today、7d、30d、90d
 * @param granularity 时间粒度，支持 hour、day、week、month
 * @param projectName 项目名称筛选
 * @param pipelineName 流水线名称筛选
 * @param triggeredBy 触发人筛选
 * @param status 部署状态筛选
 */
public record DashboardQuery(
        String range,
        String granularity,
        String projectName,
        String pipelineName,
        String triggeredBy,
        String status
) {
}
