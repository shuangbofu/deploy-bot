package top.fusb.deploybot.service;

import top.fusb.deploybot.dto.DashboardDeploymentSummary;
import top.fusb.deploybot.dto.DashboardServiceSummary;
import top.fusb.deploybot.dto.DashboardStatsSummary;
import top.fusb.deploybot.dto.DashboardSummary;
import top.fusb.deploybot.dto.DashboardTrendItem;
import top.fusb.deploybot.model.DeploymentEntity;
import top.fusb.deploybot.model.DeploymentStatus;
import top.fusb.deploybot.model.ServiceEntity;
import top.fusb.deploybot.model.ServiceStatus;
import top.fusb.deploybot.model.UserEntity;
import top.fusb.deploybot.repo.DeploymentRepository;
import top.fusb.deploybot.repo.HostRepository;
import top.fusb.deploybot.repo.PipelineRepository;
import top.fusb.deploybot.repo.ProjectRepository;
import top.fusb.deploybot.repo.ServiceRepository;
import top.fusb.deploybot.repo.TemplateRepository;
import top.fusb.deploybot.repo.UserRepository;
import top.fusb.deploybot.security.AuthContextHolder;
import top.fusb.deploybot.security.AuthenticatedUser;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {
    private static final List<DeploymentStatus> ACTIVE_STATUSES = List.of(DeploymentStatus.PENDING, DeploymentStatus.RUNNING);
    private static final List<DeploymentStatus> FINISHED_STATUSES = List.of(DeploymentStatus.SUCCESS, DeploymentStatus.FAILED, DeploymentStatus.STOPPED);
    private static final List<DeploymentStatus> ATTENTION_STATUSES = List.of(DeploymentStatus.PENDING, DeploymentStatus.RUNNING, DeploymentStatus.FAILED);

    private final ProjectRepository projectRepository;
    private final TemplateRepository templateRepository;
    private final PipelineRepository pipelineRepository;
    private final DeploymentRepository deploymentRepository;
    private final HostService hostService;
    private final HostRepository hostRepository;
    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;

    public DashboardService(
            ProjectRepository projectRepository,
            TemplateRepository templateRepository,
            PipelineRepository pipelineRepository,
            DeploymentRepository deploymentRepository,
            HostService hostService,
            HostRepository hostRepository,
            ServiceRepository serviceRepository,
            UserRepository userRepository
    ) {
        this.projectRepository = projectRepository;
        this.templateRepository = templateRepository;
        this.pipelineRepository = pipelineRepository;
        this.deploymentRepository = deploymentRepository;
        this.hostService = hostService;
        this.hostRepository = hostRepository;
        this.serviceRepository = serviceRepository;
        this.userRepository = userRepository;
    }

    public DashboardSummary buildSummary() {
        AuthenticatedUser currentUser = requireCurrentUser();
        boolean admin = currentUser.isAdmin();
        DashboardStatsSummary stats = buildStats(currentUser, admin);
        List<DashboardTrendItem> trend = buildTrend(currentUser, admin);
        List<DashboardDeploymentSummary> latestDeployments = buildLatestDeployments(currentUser, admin);
        List<DashboardDeploymentSummary> attentionDeployments = buildAttentionDeployments(currentUser, admin);
        List<DashboardServiceSummary> services = admin
                ? serviceRepository.findTop6ByOrderByUpdatedAtDesc().stream().map(this::toDashboardServiceSummary).toList()
                : List.of();
        return new DashboardSummary(stats, trend, latestDeployments, attentionDeployments, services);
    }

    private DashboardStatsSummary buildStats(AuthenticatedUser currentUser, boolean admin) {
        long deploymentCount = admin
                ? deploymentRepository.count()
                : deploymentRepository.countByTriggeredBy(currentUser.username());
        long runningDeployments = admin
                ? deploymentRepository.countByStatusIn(ACTIVE_STATUSES)
                : deploymentRepository.countByTriggeredByAndStatusIn(currentUser.username(), ACTIVE_STATUSES);
        long failedDeployments = admin
                ? deploymentRepository.countByStatus(DeploymentStatus.FAILED)
                : deploymentRepository.countByTriggeredByAndStatus(currentUser.username(), DeploymentStatus.FAILED);
        long successDeployments = admin
                ? deploymentRepository.countByStatus(DeploymentStatus.SUCCESS)
                : deploymentRepository.countByTriggeredByAndStatus(currentUser.username(), DeploymentStatus.SUCCESS);
        long finishedDeployments = admin
                ? deploymentRepository.countByStatusIn(FINISHED_STATUSES)
                : deploymentRepository.countByTriggeredByAndStatusIn(currentUser.username(), FINISHED_STATUSES);
        int successRate = finishedDeployments > 0 ? (int) Math.round(successDeployments * 100.0 / finishedDeployments) : 0;

        long hostCount = 0;
        if (admin) {
            hostService.ensureLocalHost();
            hostCount = hostRepository.count();
        }
        long serviceCount = admin ? serviceRepository.count() : 0;
        long runningServices = admin ? serviceRepository.countByStatus(ServiceStatus.RUNNING) : 0;
        long userCount = admin ? userRepository.count() : 0;

        return new DashboardStatsSummary(
                admin ? projectRepository.count() : 0,
                admin ? templateRepository.count() : 0,
                pipelineRepository.count(),
                deploymentCount,
                hostCount,
                serviceCount,
                userCount,
                runningServices,
                successRate,
                runningDeployments,
                failedDeployments
        );
    }

    private List<DashboardTrendItem> buildTrend(AuthenticatedUser currentUser, boolean admin) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(6);
        LocalDateTime startTime = startDate.atStartOfDay();
        List<DeploymentEntity> deployments = admin
                ? deploymentRepository.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(startTime)
                : deploymentRepository.findByTriggeredByAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(currentUser.username(), startTime);
        Map<LocalDate, long[]> buckets = new LinkedHashMap<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = startDate.plusDays(i);
            buckets.put(date, new long[]{0, 0});
        }
        for (DeploymentEntity deployment : deployments) {
            if (deployment.getCreatedAt() == null) {
                continue;
            }
            LocalDate date = deployment.getCreatedAt().toLocalDate();
            long[] bucket = buckets.get(date);
            if (bucket == null) {
                continue;
            }
            bucket[0] += 1;
            if (deployment.getStatus() == DeploymentStatus.SUCCESS) {
                bucket[1] += 1;
            }
        }
        List<DashboardTrendItem> result = new ArrayList<>();
        for (Map.Entry<LocalDate, long[]> entry : buckets.entrySet()) {
            LocalDate date = entry.getKey();
            long[] bucket = entry.getValue();
            result.add(new DashboardTrendItem(
                    date.toString(),
                    date.getMonthValue() + "/" + date.getDayOfMonth(),
                    bucket[0],
                    bucket[1]
            ));
        }
        return result;
    }

    private List<DashboardDeploymentSummary> buildLatestDeployments(AuthenticatedUser currentUser, boolean admin) {
        List<DeploymentEntity> deployments = admin
                ? deploymentRepository.findTop5ByOrderByCreatedAtDesc()
                : deploymentRepository.findTop5ByTriggeredByOrderByCreatedAtDesc(currentUser.username());
        return deployments.stream().map(this::toDashboardDeploymentSummary).toList();
    }

    private List<DashboardDeploymentSummary> buildAttentionDeployments(AuthenticatedUser currentUser, boolean admin) {
        List<DeploymentEntity> deployments = admin
                ? deploymentRepository.findTop5ByStatusInOrderByCreatedAtDesc(ATTENTION_STATUSES)
                : deploymentRepository.findTop5ByTriggeredByAndStatusInOrderByCreatedAtDesc(currentUser.username(), ATTENTION_STATUSES);
        return deployments.stream()
                .sorted(Comparator.comparing(this::attentionWeight).thenComparing(DeploymentEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toDashboardDeploymentSummary)
                .toList();
    }

    private int attentionWeight(DeploymentEntity entity) {
        if (entity.getStatus() == DeploymentStatus.FAILED) {
            return 0;
        }
        if (entity.getStatus() == DeploymentStatus.RUNNING) {
            return 1;
        }
        return 2;
    }

    private DashboardDeploymentSummary toDashboardDeploymentSummary(DeploymentEntity entity) {
        return new DashboardDeploymentSummary(
                entity.getId(),
                entity.getPipeline() == null ? null : entity.getPipeline().getName(),
                entity.getPipeline() != null && entity.getPipeline().getProject() != null ? entity.getPipeline().getProject().getName() : null,
                entity.getBranchName(),
                entity.getTriggeredBy(),
                resolveDisplayName(entity.getTriggeredBy()),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getProgressPercent(),
                entity.getProgressText()
        );
    }

    private DashboardServiceSummary toDashboardServiceSummary(ServiceEntity entity) {
        return new DashboardServiceSummary(
                entity.getId(),
                entity.getServiceName(),
                entity.getStatus() == null ? null : entity.getStatus().name(),
                entity.getPipeline() == null ? null : entity.getPipeline().getName(),
                entity.getPipeline() != null && entity.getPipeline().getTargetHost() != null ? entity.getPipeline().getTargetHost().getName() : "本机",
                entity.getUpdatedAt()
        );
    }

    private String resolveDisplayName(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return userRepository.findByUsername(username)
                .map(UserEntity::getDisplayName)
                .filter(value -> value != null && !value.isBlank())
                .orElse(username);
    }

    private AuthenticatedUser requireCurrentUser() {
        AuthenticatedUser currentUser = AuthContextHolder.get();
        if (currentUser == null) {
            throw new IllegalStateException("当前请求缺少登录用户上下文。");
        }
        return currentUser;
    }
}
