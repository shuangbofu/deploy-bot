package top.fusb.deploybot.dto;

import java.util.Arrays;

public enum PipelineHallFilterMode {
    ALL("all"),
    FAVORITES("favorites"),
    RUNNING("running"),
    FAILED("failed"),
    RECENT("recent");

    private final String value;

    PipelineHallFilterMode(String value) {
        this.value = value;
    }

    public static PipelineHallFilterMode fromValue(String value) {
        return Arrays.stream(values())
                .filter(item -> item.value.equals(value))
                .findFirst()
                .orElse(ALL);
    }
}
