package top.fusb.deploybot.model;

import jakarta.persistence.Column;
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
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Data
@Entity
@Table(name = "deployments")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"pipeline", "renderedBuildScript", "renderedDeployScript"})
public class DeploymentEntity {
    /**
     * 用户脚本里统一约定使用“[步骤 x/y]”或“[回滚 x/y]”来暴露进度。
     */
    private static final Pattern STEP_PATTERN = Pattern.compile("\\[(?:步骤|回滚)\\s+(\\d+)/(\\d+)]");

    /**
     * 系统阶段标记由执行器写入日志，实体读取日志时据此判断当前处于构建还是发布。
     */
    private static final String BUILD_STAGE_MARKER = "[系统] 开始本机构建阶段。";
    private static final String DEPLOY_STAGE_MARKER = "[系统] 本机构建完成，开始发布阶段。";
    private static final String ROLLBACK_STAGE_MARKER = "[回滚 ";
    private static final String BUILD_STAGE = "BUILD";
    private static final String DEPLOY_STAGE = "DEPLOY";
    private static final String ROLLBACK_STAGE = "ROLLBACK";
    private static final int BUILD_PROGRESS_WEIGHT = 50;
    private static final int MAX_RUNNING_PROGRESS = 99;

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 本次部署关联的流水线。 */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "pipeline_id")
    private PipelineEntity pipeline;

    /** 本次部署使用的分支。 */
    @Column(nullable = false)
    private String branchName;

    /** 本次部署实际使用的变量 JSON。 */
    @Column(length = 4000)
    private String variablesJson;

    /** 部署状态。 */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private DeploymentStatus status;

    /** 触发人。 */
    @Column(length = 1000)
    private String triggeredBy;

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

    /** 部署前备份目录。 */
    @Column(length = 2000)
    private String backupPath;

    /** 若这是回滚任务，则记录回滚来源部署 ID。 */
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
        if (snapshot == null || snapshot.step() == null || snapshot.step().total <= 0) {
            return status == DeploymentStatus.RUNNING ? 10 : 0;
        }
        ProgressStep step = snapshot.step();
        int percent;
        if (DEPLOY_STAGE.equals(snapshot.stage())) {
            percent = BUILD_PROGRESS_WEIGHT + Math.round((step.current * BUILD_PROGRESS_WEIGHT * 1.0f) / step.total);
        } else {
            percent = Math.round((step.current * BUILD_PROGRESS_WEIGHT * 1.0f) / step.total);
        }
        if (ROLLBACK_STAGE.equals(snapshot.stage())) {
            percent = Math.round((step.current * 100.0f) / step.total);
        }
        return Math.max(0, Math.min(percent, MAX_RUNNING_PROGRESS));
    }

    @Transient
    public String getProgressText() {
        ProgressSnapshot snapshot = readProgressSnapshot();
        if (snapshot == null || snapshot.step() == null) {
            return null;
        }
        String stageLabel = switch (snapshot.stage()) {
            case DEPLOY_STAGE -> "发布";
            case ROLLBACK_STAGE -> "回滚";
            default -> "构建";
        };
        ProgressStep step = snapshot.step();
        if (status == DeploymentStatus.SUCCESS) {
            return stageLabel + " " + step.current + "/" + step.total;
        }
        return stageLabel + " " + step.current + "/" + step.total;
    }

    @Transient
    public String getProgressStage() {
        ProgressSnapshot snapshot = readProgressSnapshot();
        return snapshot == null ? null : snapshot.stage();
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
            ProgressStep latest = null;
            while (matcher.find()) {
                latest = new ProgressStep(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
            }
            if (latest == null) {
                return null;
            }
            String stage = BUILD_STAGE;
            if (content.contains(ROLLBACK_STAGE_MARKER)) {
                stage = ROLLBACK_STAGE;
            } else if (content.contains(DEPLOY_STAGE_MARKER)) {
                stage = DEPLOY_STAGE;
            } else if (content.contains(BUILD_STAGE_MARKER)) {
                stage = BUILD_STAGE;
            }
            return new ProgressSnapshot(stage, latest);
        } catch (IOException ignored) {
            return null;
        }
    }

    private record ProgressSnapshot(String stage, ProgressStep step) {
    }

    private record ProgressStep(int current, int total) {
    }
}
