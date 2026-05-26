package top.fusb.deploybot.dto;

import top.fusb.deploybot.model.DeploymentStatus;

/**
 * 最近部署散点图数据。
 *
 * @param id 部署 ID
 * @param time 时间键
 * @param axisKey 纵轴键
 * @param pipelineName 流水线名称
 * @param projectName 项目名称
 * @param status 部署状态
 * @param durationSeconds 执行耗时，单位秒
 */
public record DashboardRecentPoint(
        Long id,
        String time,
        String axisKey,
        String pipelineName,
        String projectName,
        DeploymentStatus status,
        long durationSeconds
) {
}
