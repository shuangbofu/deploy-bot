package top.fusb.deploybot.controller;

import top.fusb.deploybot.dto.RuntimeEnvironmentDetection;
import top.fusb.deploybot.dto.RuntimeEnvironmentInstallRequest;
import top.fusb.deploybot.dto.RuntimeEnvironmentPreset;
import top.fusb.deploybot.dto.RuntimeEnvironmentRequest;
import top.fusb.deploybot.model.RuntimeEnvironmentEntity;
import top.fusb.deploybot.model.RuntimeEnvironmentType;
import top.fusb.deploybot.security.AdminOnly;
import top.fusb.deploybot.service.RuntimeEnvironmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@AdminOnly
@RestController
@RequestMapping("/api/runtime-environments")
public class RuntimeEnvironmentController {

    private final RuntimeEnvironmentService service;

    public RuntimeEnvironmentController(RuntimeEnvironmentService service) {
        this.service = service;
    }

    /**
     * 列出运行环境。可按主机或类型过滤。
     */
    @GetMapping
    public List<RuntimeEnvironmentEntity> list(
            @RequestParam(required = false) RuntimeEnvironmentType type,
            @RequestParam(required = false) Long hostId
    ) {
        return type == null ? service.findAll(hostId) : service.findEnabledByType(type, hostId);
    }

    @GetMapping("/detections")
    public List<RuntimeEnvironmentDetection> detections(@RequestParam(required = false) Long hostId) {
        return service.detectAvailable(hostId);
    }

    /**
     * 返回当前主机可下载安装的预置环境。
     */
    @GetMapping("/presets")
    public List<RuntimeEnvironmentPreset> presets(@RequestParam(required = false) Long hostId) {
        return service.listPresets(hostId);
    }

    /**
     * 下载并安装某个预置运行环境。
     */
    @PostMapping("/install")
    public RuntimeEnvironmentEntity install(@Valid @RequestBody RuntimeEnvironmentInstallRequest request) throws IOException, InterruptedException {
        return service.installPreset(request);
    }

    /**
     * 手动创建运行环境。
     */
    @PostMapping
    public RuntimeEnvironmentEntity create(@Valid @RequestBody RuntimeEnvironmentRequest request) {
        return service.save(request, null);
    }

    /**
     * 更新运行环境配置。
     */
    @PutMapping("/{id}")
    public RuntimeEnvironmentEntity update(@PathVariable Long id, @Valid @RequestBody RuntimeEnvironmentRequest request) {
        return service.save(request, id);
    }

    /**
     * 删除指定运行环境。
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
