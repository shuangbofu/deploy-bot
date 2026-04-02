package top.fusb.deploybot.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.fusb.deploybot.dto.MavenSettingsRequest;
import top.fusb.deploybot.model.MavenSettingsEntity;
import top.fusb.deploybot.security.AdminOnly;
import top.fusb.deploybot.service.MavenSettingsService;

import java.util.List;

@AdminOnly
@RestController
@RequestMapping("/api/runtime-environments/{runtimeEnvironmentId}/maven-settings")
public class MavenSettingsController {

    private final MavenSettingsService service;

    public MavenSettingsController(MavenSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public List<MavenSettingsEntity> list(@PathVariable Long runtimeEnvironmentId) {
        return service.findAll(runtimeEnvironmentId);
    }

    @PostMapping
    public MavenSettingsEntity create(@PathVariable Long runtimeEnvironmentId, @Valid @RequestBody MavenSettingsRequest request) {
        return service.save(runtimeEnvironmentId, request, null);
    }

    @PutMapping("/{id}")
    public MavenSettingsEntity update(@PathVariable Long runtimeEnvironmentId, @PathVariable Long id, @Valid @RequestBody MavenSettingsRequest request) {
        return service.save(runtimeEnvironmentId, request, id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long runtimeEnvironmentId, @PathVariable Long id) {
        service.delete(runtimeEnvironmentId, id);
    }
}
