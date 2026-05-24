package top.fusb.deploybot.controller;

import top.fusb.deploybot.dto.ServiceProcessBindRequest;
import top.fusb.deploybot.dto.ServicePidHistorySummary;
import top.fusb.deploybot.dto.ServiceProcessSummary;
import top.fusb.deploybot.model.DeploymentEntity;
import top.fusb.deploybot.model.ServiceEntity;
import top.fusb.deploybot.security.AdminOnly;
import top.fusb.deploybot.service.DeploymentService;
import top.fusb.deploybot.service.ServiceManager;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@AdminOnly
@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
public class ServiceController {

    private final ServiceManager serviceManager;
    private final DeploymentService deploymentService;

    /**
     * 列出当前受管服务。
     */
    @GetMapping
    public List<ServiceEntity> list() {
        return serviceManager.findAll();
    }

    /**
     * 停止指定服务。
     */
    @PostMapping("/{id}/stop")
    public ServiceEntity stop(@PathVariable Long id) {
        return serviceManager.stop(id);
    }

    /**
     * 拉取服务目标主机上的进程列表，用于手动绑定外部平台启动的进程。
     */
    @GetMapping("/{id}/processes")
    public List<ServiceProcessSummary> processes(@PathVariable Long id) {
        return serviceManager.listProcessCandidates(id);
    }

    @GetMapping("/{id}/pid-history")
    public List<ServicePidHistorySummary> pidHistory(@PathVariable Long id) {
        return serviceManager.listPidHistory(id);
    }

    /**
     * 手动绑定一个已有进程到服务。
     */
    @PostMapping("/{id}/bind-process")
    public ServiceEntity bindProcess(@PathVariable Long id, @RequestBody ServiceProcessBindRequest request) {
        return serviceManager.bindProcess(id, request.pid());
    }

    /**
     * 启动指定服务，底层会创建一次新的部署任务。
     */
    @PostMapping("/{id}/start")
    public DeploymentEntity start(@PathVariable Long id) {
        return deploymentService.startService(id);
    }

    /**
     * 重启指定服务。
     */
    @PostMapping("/{id}/restart")
    public DeploymentEntity restart(@PathVariable Long id) {
        serviceManager.stop(id);
        return deploymentService.startService(id);
    }
}
