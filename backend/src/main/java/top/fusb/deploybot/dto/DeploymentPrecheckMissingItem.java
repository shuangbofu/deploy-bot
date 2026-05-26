package top.fusb.deploybot.dto;

/**
 * 部署前检查缺失项。
 *
 * @param code 缺失项类型码
 * @param name 业务字段名
 * @param label 业务配置自身的展示名，例如模板变量名称
 */
public record DeploymentPrecheckMissingItem(
        String code,
        String name,
        String label
) {
}
