package top.fusb.deploybot.dto;

import java.util.List;

public record DeploymentFilterOptions(
        List<String> projectNames,
        List<String> pipelineNames
) {
}
