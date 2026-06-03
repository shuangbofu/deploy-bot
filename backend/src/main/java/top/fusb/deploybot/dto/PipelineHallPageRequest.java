package top.fusb.deploybot.dto;

import java.util.List;

public record PipelineHallPageRequest(
        int page,
        int pageSize,
        String keyword,
        List<String> tags,
        String filterMode,
        Long selectedPipelineId,
        boolean pinActivePipelines
) {
}
