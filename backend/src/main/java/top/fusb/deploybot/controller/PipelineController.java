package top.fusb.deploybot.controller;

import top.fusb.deploybot.dto.PageResult;
import top.fusb.deploybot.dto.DeploymentPluginPlanSummary;
import top.fusb.deploybot.dto.PipelineBranchOption;
import top.fusb.deploybot.dto.PipelineHallRunningServiceSummary;
import top.fusb.deploybot.dto.PipelineHallPageRequest;
import top.fusb.deploybot.dto.PipelineHallSummary;
import top.fusb.deploybot.dto.PipelineHallStreamEvent;
import top.fusb.deploybot.dto.PipelineRequest;
import top.fusb.deploybot.model.PipelineEntity;
import top.fusb.deploybot.security.AuthContextHolder;
import top.fusb.deploybot.security.AuthenticatedUser;
import top.fusb.deploybot.security.AdminOnly;
import top.fusb.deploybot.service.GitBranchService;
import top.fusb.deploybot.service.DeploymentPluginBridgeService;
import top.fusb.deploybot.service.PipelineHallEventService;
import top.fusb.deploybot.service.PipelineService;
import top.fusb.deploybot.service.ServiceManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/pipelines")
@RequiredArgsConstructor
public class PipelineController {
    private static final long HALL_IDLE_WAIT_MILLIS = 15_000L;

    private final PipelineService service;
    private final GitBranchService gitBranchService;
    private final ServiceManager serviceManager;
    private final PipelineHallEventService pipelineHallEventService;
    private final DeploymentPluginBridgeService deploymentPluginBridgeService;

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

    @GetMapping("/hall/page")
    public PageResult<PipelineHallSummary> hallPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(defaultValue = "all") String filterMode,
            @RequestParam(required = false) Long selectedPipelineId,
            @RequestParam(defaultValue = "false") boolean pinActivePipelines
    ) {
        return service.findHallPage(new PipelineHallPageRequest(
                page,
                pageSize,
                keyword,
                tags,
                filterMode,
                selectedPipelineId,
                pinActivePipelines
        ));
    }

    @GetMapping("/hall/by-ids")
    public List<PipelineHallSummary> hallByIds(@RequestParam List<Long> ids) {
        return service.findHallSummariesByIds(ids);
    }

    @GetMapping("/hall/running-services")
    public List<PipelineHallRunningServiceSummary> runningServices() {
        return serviceManager.findRunningHallSummaries();
    }

    @GetMapping(value = "/hall/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter hallStream(@RequestParam(required = false) Long version) {
        AuthenticatedUser currentUser = AuthContextHolder.get();
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean closed = new AtomicBoolean(false);
        Thread thread = new Thread(() -> {
            long observedVersion = pipelineHallEventService.currentVersion();
            try {
                AuthContextHolder.set(currentUser);
                if (version == null || version < observedVersion) {
                    emitter.send(SseEmitter.event().name("hall").data(new PipelineHallStreamEvent(observedVersion)));
                }
                while (!Thread.currentThread().isInterrupted() && !closed.get()) {
                    long nextVersion = pipelineHallEventService.awaitChange(observedVersion, HALL_IDLE_WAIT_MILLIS);
                    if (nextVersion != observedVersion) {
                        observedVersion = nextVersion;
                        emitter.send(SseEmitter.event().name("hall").data(new PipelineHallStreamEvent(observedVersion)));
                    } else {
                        emitter.send(SseEmitter.event().comment("heartbeat"));
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                closed.set(true);
            } finally {
                AuthContextHolder.clear();
            }
        }, "pipeline-hall-stream");
        thread.setDaemon(true);
        Runnable closeStream = () -> {
            closed.set(true);
            thread.interrupt();
        };
        emitter.onCompletion(closeStream);
        emitter.onTimeout(closeStream);
        emitter.onError(ignored -> closeStream.run());
        thread.start();
        return emitter;
    }

    @GetMapping(value = "/hall/running-services/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter runningServicesStream() {
        return SseStreamSupport.stream("pipeline-running-services-stream", "running-services", serviceManager::findRunningHallSummaries);
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

    @GetMapping("/{id}")
    public PipelineEntity detail(@PathVariable Long id) {
        return service.findById(id);
    }

    /**
     * 分支下拉直接读取项目对应仓库的远端分支列表。
     */
    @GetMapping("/{id}/branches")
    public List<String> branches(@PathVariable Long id) {
        return gitBranchService.listBranches(id);
    }

    @GetMapping("/{id}/branch-options")
    public List<PipelineBranchOption> branchOptions(@PathVariable Long id) {
        return service.buildBranchOptions(id, gitBranchService.listBranches(id));
    }

    /**
     * 预览当前流水线会命中的插件计划。
     */
    @GetMapping("/{id}/plugin-plan")
    public DeploymentPluginPlanSummary pluginPlan(@PathVariable Long id) {
        return deploymentPluginBridgeService.summarizePlan(service.findById(id));
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
