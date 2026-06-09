package top.fusb.deploybot.dto;

import java.util.List;

/**
 * 部署限制策略配置。
 */
public record DeploymentRestrictionPolicyConfig(
        /** 策略 ID，由前端生成并随配置一起保存。 */
        String id,
        /** 策略名称。 */
        String name,
        /** 是否启用。 */
        Boolean enabled,
        /** 策略生效范围。 */
        DeploymentRestrictionScopeType scopeType,
        /** 范围 ID 列表，项目范围取第一个，流水线范围可多选。 */
        List<Long> scopeIds,
        /** 每周时间窗口，多个窗口之间为或关系。 */
        List<DeploymentRestrictionWeeklyWindow> weeklyWindows,
        /** 命中限制时展示的原因。 */
        String reason
) {
}
