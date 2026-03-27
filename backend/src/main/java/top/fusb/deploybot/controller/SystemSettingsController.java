package top.fusb.deploybot.controller;

import top.fusb.deploybot.dto.SystemSettingsRequest;
import top.fusb.deploybot.model.SystemSettingsEntity;
import top.fusb.deploybot.security.AdminOnly;
import top.fusb.deploybot.service.SystemSshKeyService;
import top.fusb.deploybot.service.SystemSettingsService;
import java.io.IOException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AdminOnly
@RestController
@RequestMapping("/api/system-settings")
public class SystemSettingsController {

    private final SystemSettingsService service;
    private final SystemSshKeyService systemSshKeyService;

    public SystemSettingsController(SystemSettingsService service, SystemSshKeyService systemSshKeyService) {
        this.service = service;
        this.systemSshKeyService = systemSshKeyService;
    }

    /**
     * 读取系统设置。
     */
    @GetMapping
    public SystemSettingsEntity get() {
        return service.get();
    }

    /**
     * 更新系统设置。
     */
    @PutMapping
    public SystemSettingsEntity update(@RequestBody SystemSettingsRequest request) {
        return service.save(request);
    }

    /**
     * 生成系统级 Git SSH 密钥对。
     */
    @PostMapping("/generate-ssh-keypair")
    public SystemSettingsEntity generateSshKeypair() throws IOException, InterruptedException {
        return systemSshKeyService.generateKeyPair();
    }

    /**
     * 生成系统级主机 SSH 密钥对。
     */
    @PostMapping("/generate-host-ssh-keypair")
    public SystemSettingsEntity generateHostSshKeypair() throws IOException, InterruptedException {
        return systemSshKeyService.generateHostKeyPair();
    }
}
