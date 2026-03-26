package top.fusb.deploybot.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 发起部署时的请求体。
 */
public record DeploymentRequest(
        /** 目标流水线 ID。 */
        @NotNull Long pipelineId,
        /** 本次部署使用的分支；为空时回退到流水线默认分支。 */
        String branchName,
        /** 触发人。 */
        String triggeredBy,
        /** 本次部署临时覆盖的变量。 */
        Map<String, String> variableOverrides,
        /** 若当前流水线已有运行中的部署，是否允许替换。 */
        Boolean replaceRunning
) {
}
