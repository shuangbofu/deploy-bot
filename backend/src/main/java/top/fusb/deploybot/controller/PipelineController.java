package top.fusb.deploybot.controller;

import top.fusb.deploybot.dto.PipelineRequest;
import top.fusb.deploybot.model.PipelineEntity;
import top.fusb.deploybot.security.AdminOnly;
import top.fusb.deploybot.service.GitBranchService;
import top.fusb.deploybot.service.PipelineService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/pipelines")
public class PipelineController {

    private final PipelineService service;
    private final GitBranchService gitBranchService;

    public PipelineController(PipelineService service, GitBranchService gitBranchService) {
        this.service = service;
        this.gitBranchService = gitBranchService;
    }

    /**
     * 返回所有流水线，供管理端列表与用户端流水线大厅复用。
     */
    @GetMapping
    public List<PipelineEntity> list() {
        return service.findAll();
    }

    /**
     * 分支下拉直接读取项目对应仓库的远端分支列表。
     */
    @GetMapping("/{id}/branches")
    public List<String> branches(@PathVariable Long id) {
        return gitBranchService.listBranches(id);
    }

    /**
     * 新建流水线时只保存配置，不会立即触发部署。
     */
    @AdminOnly
    @PostMapping
    public PipelineEntity create(@Valid @RequestBody PipelineRequest request) {
        return service.save(request, null);
    }

    /**
     * 更新流水线配置后，后续部署会自动使用最新配置。
     */
    @AdminOnly
    @PutMapping("/{id}")
    public PipelineEntity update(@PathVariable Long id, @Valid @RequestBody PipelineRequest request) {
        return service.save(request, id);
    }

    /**
     * 删除前应保证没有业务仍依赖这条流水线。
     */
    @AdminOnly
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
