package top.fusb.deploybot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import top.fusb.deploybot.model.converter.ObjectMapJsonConverter;
import top.fusb.deploybot.model.converter.StringMapJsonConverter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Data
@Entity
@Table(name = "deployments")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"pipeline", "renderedBuildScript", "renderedDeployScript"})
public class DeploymentEntity {
    /**
     * 用户脚本里统一约定使用“[步骤 x/y]”或“[回滚 x/y]”来暴露构建/发布进度。
     */
    private static final Pattern STEP_PATTERN = Pattern.compile("\\[(?:步骤|回滚)\\s+(\\d+)/(\\d+)]");

    /**
     * 启动观察阶段由系统日志驱动，这里读取“第 n 次启动观察通过”来推进进度。
     */
    private static final Pattern STARTUP_OBSERVE_PATTERN = Pattern.compile("第\\s+(\\d+)\\s+次启动观察通过");

    /**
     * 系统阶段标记由执行器写入日志，实体读取日志时据此判断当前处于构建还是发布。
     */
    private static final String BUILD_STAGE_MARKER = "开始本机构建阶段。";
    private static final String DEPLOY_STAGE_MARKER = "本机构建完成，开始发布阶段。";
    private static final String STARTUP_STAGE_MARKER = "检测到候选进程 PID";
    private static final String ROLLBACK_STAGE_MARKER = "[回滚 ";
    private static final String BUILD_STAGE = "BUILD";
    private static final String DEPLOY_STAGE = "DEPLOY";
    private static final String STARTUP_STAGE = "STARTUP";
    private static final String ROLLBACK_STAGE = "ROLLBACK";
    private static final int MAX_RUNNING_PROGRESS = 99;

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 本次部署关联的流水线。 */
    @ManyToOne(fetch = FetchType.EAGER, optional = true)
    @JoinColumn(name = "pipeline_id", nullable = true)
    private PipelineEntity pipeline;

    /** 本次部署使用的分支。 */
    @Column(nullable = false)
    private String branchName;

    /** 创建部署时固化的流水线名称，避免删除流水线后历史记录丢失名称。 */
    @Column(length = 255)
    private String pipelineName;

    /** 创建部署时固化的项目名称，避免删除项目/流水线关联变更后历史记录丢失名称。 */
    @Column(length = 255)
    private String projectName;

    /** 本次部署实际使用的变量。 */
    @Convert(converter = StringMapJsonConverter.class)
    @Lob
    @Column(name = "variables_json")
    private Map<String, String> variables;

    /** 部署状态。 */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private DeploymentStatus status;

    /** 触发人。 */
    @Column(length = 1000)
    private String triggeredBy;

    /** 触发人展示名称。 */
    @Transient
    private String triggeredByDisplayName;

    /** 停止人。 */
    @Column(length = 1000)
    private String stoppedBy;

    /** 停止人展示名称。 */
    @Transient
    private String stoppedByDisplayName;

    /** 创建时间。 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 开始执行时间。 */
    private LocalDateTime startedAt;

    /** 结束时间。 */
    private LocalDateTime finishedAt;

    /** 日志文件路径。 */
    @Column(length = 2000)
    private String logPath;

    /** 渲染后的构建脚本。 */
    @Lob
    private String renderedBuildScript;

    /** 渲染后的发布脚本。 */
    @Lob
    private String renderedDeployScript;

    /** 错误信息。 */
    @Column(length = 2000)
    private String errorMessage;

    /** 被监控的进程 PID。 */
    private Long monitoredPid;

    /** 本次部署保留下来的构建产物目录，用于后续重新发布同一版本。 */
    @Column(length = 2000)
    private String artifactPath;

    /** 本次部署实际构建出的 Git 提交。 */
    @Column(length = 64)
    private String commitSha;

    /** 与上一成功部署相比的 Git 差异快照。 */
    @Convert(converter = ObjectMapJsonConverter.class)
    @Lob
    @Column(name = "git_diff_snapshot_json")
    private Map<String, Object> gitDiffSnapshot;

    /** 本次部署创建时固化的执行快照，便于后续排查历史版本差异。 */
    @Convert(converter = ObjectMapJsonConverter.class)
    @Lob
    @Column(name = "execution_snapshot_json")
    private Map<String, Object> executionSnapshot;

    /** 若这是重新发布历史版本的任务，则记录来源部署 ID。 */
    private Long rollbackFromDeploymentId;

    @Transient
    public Integer getProgressPercent() {
        if (status == null) {
            return 0;
        }
        if (status == DeploymentStatus.SUCCESS) {
            return 100;
        }
        if (status == DeploymentStatus.PENDING) {
            return 0;
        }

        ProgressSnapshot snapshot = readProgressSnapshot();
        if (snapshot == null) {
            return 0;
        }
        return calculateSnapshotProgressPercent(snapshot);
    }

    private int calculateSnapshotProgressPercent(ProgressSnapshot snapshot) {
        Integer rollbackPercent = snapshot.rollbackProgressPercent(MAX_RUNNING_PROGRESS);
        if (rollbackPercent != null) {
            return rollbackPercent;
        }

        int buildTotal = snapshot.getBuildTotal();
        int deployTotal = snapshot.getDeployTotal();
        int startupStageWeight = requiresStartupObservation() ? 1 : 0;
        int grandTotal = buildTotal + deployTotal + startupStageWeight;
        if (grandTotal <= 0) {
            return 0;
        }

        float completedUnits = snapshot.completedUnits();
        int percent = Math.round((completedUnits * 100.0f) / grandTotal);
        return Math.max(0, Math.min(percent, MAX_RUNNING_PROGRESS));
    }

    @Transient
    public String getProgressStage() {
        if (status == DeploymentStatus.SUCCESS || status == DeploymentStatus.PENDING) {
            return null;
        }
        ProgressSnapshot snapshot = readProgressSnapshot();
        return snapshot == null ? null : snapshot.getStage();
    }

    @Transient
    public Integer getProgressCurrent() {
        if (status == DeploymentStatus.SUCCESS || status == DeploymentStatus.PENDING) {
            return null;
        }
        ProgressSnapshot snapshot = readProgressSnapshot();
        ProgressStep step = resolveProgressStep(snapshot);
        return step == null ? null : step.getCurrent();
    }

    @Transient
    public Integer getProgressTotal() {
        if (status == DeploymentStatus.SUCCESS || status == DeploymentStatus.PENDING) {
            return null;
        }
        ProgressSnapshot snapshot = readProgressSnapshot();
        ProgressStep step = resolveProgressStep(snapshot);
        return step == null ? null : step.getTotal();
    }

    private ProgressStep resolveProgressStep(ProgressSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        if (ROLLBACK_STAGE.equals(snapshot.getStage())) {
            return snapshot.getRollbackStep();
        }
        if (STARTUP_STAGE.equals(snapshot.getStage())) {
            return new ProgressStep(snapshot.getStartupAttemptCurrent(), snapshot.getStartupAttemptTotal());
        }
        if (DEPLOY_STAGE.equals(snapshot.getStage())) {
            return snapshot.getDeployStep();
        }
        return snapshot.getBuildStep();
    }

    private ProgressSnapshot readProgressSnapshot() {
        if (logPath == null || logPath.isBlank()) {
            return null;
        }
        Path path = Path.of(logPath);
        if (!Files.exists(path)) {
            return null;
        }
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            Matcher matcher = STEP_PATTERN.matcher(content);
            ProgressStep latestBuild = null;
            ProgressStep latestDeploy = null;
            ProgressStep latestRollback = null;
            int buildTotal = extractDeclaredStepTotal(renderedBuildScript);
            int deployTotal = extractDeclaredStepTotal(renderedDeployScript);
            int deployMarkerIndex = content.indexOf(DEPLOY_STAGE_MARKER);
            int rollbackMarkerIndex = content.indexOf(ROLLBACK_STAGE_MARKER);
            while (matcher.find()) {
                ProgressStep step = new ProgressStep(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
                if (rollbackMarkerIndex >= 0 && matcher.start() >= rollbackMarkerIndex) {
                    latestRollback = step;
                } else if (deployMarkerIndex >= 0 && matcher.start() >= deployMarkerIndex) {
                    latestDeploy = step;
                    deployTotal = Math.max(deployTotal, step.getTotal());
                } else {
                    latestBuild = step;
                    buildTotal = Math.max(buildTotal, step.getTotal());
                }
            }
            Matcher startupMatcher = STARTUP_OBSERVE_PATTERN.matcher(content);
            Integer startupAttempt = null;
            while (startupMatcher.find()) {
                startupAttempt = Integer.parseInt(startupMatcher.group(1));
            }

            if (latestBuild == null && latestDeploy == null && latestRollback == null && startupAttempt == null) {
                return null;
            }
            String stage = BUILD_STAGE;
            if (content.contains(ROLLBACK_STAGE_MARKER)) {
                stage = ROLLBACK_STAGE;
            } else if (content.contains(STARTUP_STAGE_MARKER)) {
                stage = STARTUP_STAGE;
            } else if (content.contains(DEPLOY_STAGE_MARKER)) {
                stage = DEPLOY_STAGE;
            } else if (content.contains(BUILD_STAGE_MARKER)) {
                stage = BUILD_STAGE;
            }

            int startupAttemptTotal = resolveStartupObservationAttemptTotal();
            int startupAttemptCurrent = startupAttempt == null ? (STARTUP_STAGE.equals(stage) ? 1 : 0) : Math.min(startupAttempt, startupAttemptTotal);
            return new ProgressSnapshot(stage, latestBuild, latestDeploy, latestRollback, buildTotal, deployTotal, startupAttemptCurrent, startupAttemptTotal);
        } catch (IOException ignored) {
            return null;
        }
    }

    private int extractDeclaredStepTotal(String script) {
        if (script == null || script.isBlank()) {
            return 0;
        }
        Matcher matcher = STEP_PATTERN.matcher(script);
        int total = 0;
        while (matcher.find()) {
            total = Math.max(total, Integer.parseInt(matcher.group(2)));
        }
        return total;
    }

    private boolean requiresStartupObservation() {
        return pipeline != null
                && Boolean.TRUE.equals(pipeline.getTemplateMonitorProcess());
    }

    private int resolveStartupObservationAttemptTotal() {
        Integer startupTimeoutSeconds = pipeline == null ? null : pipeline.getStartupTimeoutSeconds();
        if (startupTimeoutSeconds == null || startupTimeoutSeconds <= 0) {
            startupTimeoutSeconds = 30;
        }
        int total = Math.max(1, (int) Math.ceil(startupTimeoutSeconds / 2.0));
        return total;
    }

    @Data
    @AllArgsConstructor
    private static class ProgressSnapshot {
        private String stage;
        private ProgressStep buildStep;
        private ProgressStep deployStep;
        private ProgressStep rollbackStep;
        private int buildTotal;
        private int deployTotal;
        private int startupAttemptCurrent;
        private int startupAttemptTotal;

        private Integer rollbackProgressPercent(int maxProgress) {
            if (!ROLLBACK_STAGE.equals(stage) || rollbackStep == null || rollbackStep.getTotal() <= 0) {
                return null;
            }
            int percent = Math.round((rollbackStep.getCurrent() * 100.0f) / rollbackStep.getTotal());
            return Math.max(0, Math.min(percent, maxProgress));
        }

        private float completedUnits() {
            if (STARTUP_STAGE.equals(stage)) {
                return buildTotal + deployTotal + startupProgressRatio();
            }
            if (DEPLOY_STAGE.equals(stage)) {
                return buildTotal + currentStepValue(deployStep);
            }
            return currentStepValue(buildStep);
        }

        private int currentStepValue(ProgressStep step) {
            return step == null ? 0 : step.getCurrent();
        }

        private float startupProgressRatio() {
            if (startupAttemptTotal <= 0) {
                return 0.5f;
            }
            return Math.max(0.05f, Math.min(startupAttemptCurrent * 1.0f / startupAttemptTotal, 0.99f));
        }
    }

    @Data
    @AllArgsConstructor
    private static class ProgressStep {
        private int current;
        private int total;
    }
}
