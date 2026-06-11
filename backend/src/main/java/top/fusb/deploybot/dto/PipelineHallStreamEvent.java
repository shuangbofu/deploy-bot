package top.fusb.deploybot.dto;

import java.util.List;

public record PipelineHallStreamEvent(
        long version,
        boolean full,
        List<PipelineHallSummary> items
) {
}
