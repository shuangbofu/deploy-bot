package top.fusb.deploybot.dto;

import top.fusb.deploybot.model.DeploymentStatus;

/**
 * 最近部署散点图数据。
 *
 * @param id 部署 ID
 * @param time 时间文本
 * @param axisName 纵轴名称
 * @param pipelineName 流水线名称
 * @param projectName 项目名称
 * @param status 部署状态
 * @param durationSeconds 执行耗时，单位秒
 */
public record DashboardRecentPoint(
        Long id,
        String time,
        String axisName,
        String pipelineName,
        String projectName,
        DeploymentStatus status,
        long durationSeconds
) {
}
