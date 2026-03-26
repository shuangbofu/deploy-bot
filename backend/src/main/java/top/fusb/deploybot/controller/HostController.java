package top.fusb.deploybot.controller;

import top.fusb.deploybot.dto.HostRequest;
import top.fusb.deploybot.dto.HostConnectionTestResult;
import top.fusb.deploybot.dto.HostResourceSnapshot;
import top.fusb.deploybot.model.HostEntity;
import top.fusb.deploybot.service.HostService;
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

import java.util.List;

@RestController
@RequestMapping("/api/hosts")
public class HostController {

    private final HostService hostService;

    public HostController(HostService hostService) {
        this.hostService = hostService;
    }

    /**
     * 列出主机，可选只返回启用中的主机。
     */
    @GetMapping
    public List<HostEntity> list(@RequestParam(defaultValue = "false") boolean enabledOnly) {
        return enabledOnly ? hostService.findEnabled() : hostService.findAll();
    }

    /**
     * 新建主机。
     */
    @PostMapping
    public HostEntity create(@Valid @RequestBody HostRequest request) {
        return hostService.save(request, null);
    }

    /**
     * 更新主机配置。
     */
    @PutMapping("/{id}")
    public HostEntity update(@PathVariable Long id, @Valid @RequestBody HostRequest request) {
        return hostService.save(request, id);
    }

    /**
     * 删除主机。
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        hostService.delete(id);
    }

    /**
     * 测试远程主机连通性与工作空间可用性。
     */
    @PostMapping("/{id}/test-connection")
    public HostConnectionTestResult testConnection(@PathVariable Long id) throws Exception {
        return hostService.testConnection(id);
    }

    /**
     * 获取主机资源预览。
     */
    @GetMapping("/{id}/resources")
    public HostResourceSnapshot previewResources(@PathVariable Long id) throws Exception {
        return hostService.previewResources(id);
    }
}
