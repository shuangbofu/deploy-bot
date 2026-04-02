package top.fusb.deploybot.dto;

import jakarta.validation.constraints.NotBlank;

public record MavenSettingsRequest(
        @NotBlank String name,
        String description,
        @NotBlank String contentXml,
        Boolean enabled,
        Boolean isDefault
) {
}
