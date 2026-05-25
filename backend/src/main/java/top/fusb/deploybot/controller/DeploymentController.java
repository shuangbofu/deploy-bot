package top.fusb.deploybot.controller;

import top.fusb.deploybot.dto.DeploymentRequest;
import top.fusb.deploybot.dto.DeploymentFilterOptions;
import top.fusb.deploybot.dto.DeploymentListSummary;
import top.fusb.deploybot.dto.DeploymentPrecheckResult;
import top.fusb.deploybot.dto.DeploymentPluginPlanSummary;
import top.fusb.deploybot.dto.PageResult;
import top.fusb.deploybot.model.DeploymentEntity;
import top.fusb.deploybot.model.DeploymentStatus;
import top.fusb.deploybot.dto.UserRecentPipelineSummary;
import top.fusb.deploybot.service.DeploymentService;
import top.fusb.deploybot.service.DeploymentPluginBridgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/deployments")
@RequiredArgsConstructor
public class DeploymentController {

    private final DeploymentService service;
    private final DeploymentPluginBridgeService deploymentPluginBridgeService;

    /**
     * 列出全部部署记录。
     */
    @GetMapping
    public List<DeploymentEntity> list() {
        return service.findAll();
    }

    @GetMapping("/page")
    public PageResult<DeploymentListSummary> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String pipelineName,
            @RequestParam(required = false) String triggeredBy,
            @RequestParam(required = false) DeploymentStatus status,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime
    ) {
        return service.findPage(page, pageSize, projectName, pipelineName, triggeredBy, status, startTime, endTime);
    }

    @GetMapping("/mine/filter-options")
    public DeploymentFilterOptions mineFilterOptions() {
        return service.findMineFilterOptions();
    }

    @GetMapping("/mine/recent-pipelines")
    public List<UserRecentPipelineSummary> myRecentPipelines() {
        return service.findMyRecentPipelines();
    }

    @GetMapping("/mine/page")
    public PageResult<DeploymentListSummary> minePage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String pipelineName,
            @RequestParam(required = false) String triggeredBy,
            @RequestParam(required = false) String branchName,
            @RequestParam(required = false) DeploymentStatus status,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(required = false) Long pipelineId
    ) {
        return service.findMinePage(page, pageSize, projectName, pipelineName, triggeredBy, branchName, status, startTime, endTime, pipelineId);
    }

    /**
     * 查询单条部署详情。
     */
    @GetMapping("/{id}")
    public DeploymentEntity detail(@PathVariable Long id) {
        return service.findById(id);
    }

    /**
     * 查询当前部署命中的插件计划，方便验证插件解耦结果。
     */
    @GetMapping("/{id}/plugin-plan")
    public DeploymentPluginPlanSummary pluginPlan(@PathVariable Long id) {
        return deploymentPluginBridgeService.summarizePlan(service.findById(id));
    }

    /**
     * 读取部署日志文本。
     */
    @GetMapping(value = "/{id}/log", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> log(@PathVariable Long id) throws IOException {
        return Map.of("content", service.readLog(id));
    }

    /**
     * 流式读取部署日志增量。
     */
    @GetMapping(value = "/{id}/log/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter logStream(@PathVariable Long id, @RequestParam(defaultValue = "0") long offset) {
        service.findById(id);
        SseEmitter emitter = new SseEmitter(0L);
        Thread thread = new Thread(() -> {
            long currentOffset = Math.max(0L, offset);
            try {
                boolean finished = false;
                int idleAfterFinished = 0;
                while (!finished || idleAfterFinished < 2) {
                    DeploymentService.LogChunk chunk = service.readAuthorizedLogChunk(id, currentOffset);
                    currentOffset = chunk.offset();
                    if (chunk.content() != null && !chunk.content().isEmpty()) {
                        emitter.send(SseEmitter.event()
                                .name("log")
                                .data(Map.of(
                                        "contentBase64", Base64.getEncoder().encodeToString(chunk.content().getBytes(StandardCharsets.UTF_8)),
                                        "offset", chunk.offset(),
                                        "finished", chunk.finished()
                                )));
                    }
                    finished = chunk.finished();
                    if (finished) {
                        idleAfterFinished++;
                    }
                    Thread.sleep(200L);
                }
                emitter.send(SseEmitter.event().name("done").data(Map.of("offset", currentOffset)));
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        }, "deployment-log-stream-" + id);
        thread.setDaemon(true);
        thread.start();
        return emitter;
    }

    /**
     * 发起一次新的部署。
     */
    @PostMapping
    public DeploymentEntity create(@Valid @RequestBody DeploymentRequest request) {
        return service.create(request);
    }

    @PostMapping("/precheck")
    public DeploymentPrecheckResult precheck(@Valid @RequestBody DeploymentRequest request) {
        return service.precheck(request);
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
