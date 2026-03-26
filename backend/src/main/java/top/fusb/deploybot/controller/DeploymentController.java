package top.fusb.deploybot.controller;

import top.fusb.deploybot.dto.DeploymentRequest;
import top.fusb.deploybot.model.DeploymentEntity;
import top.fusb.deploybot.service.DeploymentService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/deployments")
public class DeploymentController {

    private final DeploymentService service;

    public DeploymentController(DeploymentService service) {
        this.service = service;
    }

    /**
     * 列出全部部署记录。
     */
    @GetMapping
    public List<DeploymentEntity> list() {
        return service.findAll();
    }

    /**
     * 查询单条部署详情。
     */
    @GetMapping("/{id}")
    public DeploymentEntity detail(@PathVariable Long id) {
        return service.findById(id);
    }

    /**
     * 读取部署日志文本。
     */
    @GetMapping(value = "/{id}/log", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> log(@PathVariable Long id) throws IOException {
        return Map.of("content", service.readLog(id));
    }

    /**
     * 发起一次新的部署。
     */
    @PostMapping
    public DeploymentEntity create(@Valid @RequestBody DeploymentRequest request) {
        return service.create(request);
    }

    /**
     * 手动停止部署。
     */
    @PostMapping("/{id}/stop")
    public DeploymentEntity stop(@PathVariable Long id) {
        return service.stop(id);
    }

    /**
     * 基于指定部署创建一次回滚任务。
     */
    @PostMapping("/{id}/rollback")
    public DeploymentEntity rollback(@PathVariable Long id) {
        return service.rollback(id);
    }
}
