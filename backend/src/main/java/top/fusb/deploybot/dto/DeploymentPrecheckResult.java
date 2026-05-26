package top.fusb.deploybot.dto;

import java.util.List;

/**
 * 部署前检查结果。
 */
public record DeploymentPrecheckResult(
        boolean passed,
        List<DeploymentPrecheckMissingItem> missingItems
) {
}
