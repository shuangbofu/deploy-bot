package top.fusb.deploybot.dto;

public record RuntimeEnvironmentInstallAccepted(
        boolean accepted,
        String message
) {
}
