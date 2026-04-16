package top.fusb.deploybot.controller;

import top.fusb.deploybot.dto.PageResult;
import top.fusb.deploybot.dto.PipelineHallRunningServiceSummary;
import top.fusb.deploybot.dto.PipelineHallSummary;
import top.fusb.deploybot.dto.PipelineRequest;
import top.fusb.deploybot.model.PipelineEntity;
import top.fusb.deploybot.security.AdminOnly;
import top.fusb.deploybot.service.GitBranchService;
import top.fusb.deploybot.service.PipelineService;
import top.fusb.deploybot.service.ServiceManager;
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
@RequestMapping("/api/pipelines")
public class PipelineController {

    private final PipelineService service;
    private final GitBranchService gitBranchService;
    private final ServiceManager serviceManager;

    public PipelineController(PipelineService service, GitBranchService gitBranchService, ServiceManager serviceManager) {
        this.service = service;
        this.gitBranchService = gitBranchService;
        this.serviceManager = serviceManager;
    }

    /**
     * 返回所有流水线，供管理端列表与用户端流水线大厅复用。
     */
    @GetMapping
    public List<PipelineEntity> list() {
        return service.findAll();
    }

    @GetMapping("/hall")
    public List<PipelineHallSummary> hall() {
        return service.findHallSummaries();
    }

    @GetMapping("/hall/by-ids")
    public List<PipelineHallSummary> hallByIds(@RequestParam List<Long> ids) {
        return service.findHallSummariesByIds(ids);
    }

    @GetMapping("/hall/running-services")
    public List<PipelineHallRunningServiceSummary> runningServices() {
        return serviceManager.findRunningHallSummaries();
    }

    @GetMapping("/page")
    public PageResult<PipelineEntity> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long templateId,
            @RequestParam(required = false) Long hostId,
            @RequestParam(required = false) List<String> tags
    ) {
        return service.findPage(page, pageSize, keyword, projectId, templateId, hostId, tags);
    }

    @GetMapping("/tags")
    public List<String> tags() {
        return service.findAllTags();
    }

    @GetMapping("/favorites")
    public List<Long> favorites() {
        return service.findFavoritePipelineIds();
    }

    /**
     * 分支下拉直接读取项目对应仓库的远端分支列表。
     */
    @GetMapping("/{id}/branches")
    public List<String> branches(@PathVariable Long id) {
        return gitBranchService.listBranches(id);
    }

    @PostMapping("/{id}/favorite")
    public void favorite(@PathVariable Long id) {
        service.favorite(id);
    }

    @DeleteMapping("/{id}/favorite")
    public void unfavorite(@PathVariable Long id) {
        service.unfavorite(id);
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
