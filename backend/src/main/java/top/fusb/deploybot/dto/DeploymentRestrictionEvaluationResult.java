package top.fusb.deploybot.dto;

/**
 * 部署限制策略评估结果。
 */
public record DeploymentRestrictionEvaluationResult(
        /** 是否允许部署。 */
        boolean allowed,
        /** 命中的策略名称。 */
        String policyName,
        /** 限制原因。 */
        String reason
) {
    public static DeploymentRestrictionEvaluationResult pass() {
        return new DeploymentRestrictionEvaluationResult(true, null, null);
    }

    public static DeploymentRestrictionEvaluationResult denied(String policyName, String reason) {
        return new DeploymentRestrictionEvaluationResult(false, policyName, reason);
    }
}
