package top.fusb.deploybot.dto;

/**
 * 部署限制策略生效范围。
 */
public enum DeploymentRestrictionScopeType {
    /** 对所有流水线生效。 */
    GLOBAL,
    /** 对指定项目下的流水线生效。 */
    PROJECT,
    /** 对指定流水线生效。 */
    PIPELINE
}
