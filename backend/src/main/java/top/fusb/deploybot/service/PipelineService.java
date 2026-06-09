package top.fusb.deploybot.service;

import lombok.RequiredArgsConstructor;
import top.fusb.deploybot.dto.PageResult;
import top.fusb.deploybot.dto.PipelineBranchOption;
import top.fusb.deploybot.dto.PipelineHallFilterMode;
import top.fusb.deploybot.dto.PipelineHallPageRequest;
import top.fusb.deploybot.dto.PipelineHallSummary;
import top.fusb.deploybot.dto.PipelineLatestDeploymentSummary;
import top.fusb.deploybot.dto.PipelineRequest;
import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.kit.CollectionKit;
import top.fusb.deploybot.kit.TextKit;
import top.fusb.deploybot.model.HostEntity;
import top.fusb.deploybot.model.MavenSettingsEntity;
import top.fusb.deploybot.model.TemplateEntity;
import top.fusb.deploybot.model.RuntimeEnvironmentEntity;
import top.fusb.deploybot.model.PipelineEntity;
import top.fusb.deploybot.model.UserFavoritePipelineEntity;
import top.fusb.deploybot.model.DeploymentEntity;
import top.fusb.deploybot.model.DeploymentStatus;
import top.fusb.deploybot.notification.dto.NotificationBinding;
import top.fusb.deploybot.notification.repo.NotificationChannelRepository;
import top.fusb.deploybot.repo.HostRepository;
import top.fusb.deploybot.repo.MavenSettingsRepository;
import top.fusb.deploybot.repo.RuntimeEnvironmentRepository;
import top.fusb.deploybot.repo.PipelineRepository;
import top.fusb.deploybot.repo.DeploymentRepository;
import top.fusb.deploybot.repo.ProjectRepository;
import top.fusb.deploybot.repo.ServiceRepository;
import top.fusb.deploybot.repo.ServicePidHistoryRepository;
import top.fusb.deploybot.repo.TemplateRepository;
import top.fusb.deploybot.repo.UserRepository;
import top.fusb.deploybot.repo.UserFavoritePipelineRepository;
import top.fusb.deploybot.security.AuthContextHolder;
import top.fusb.deploybot.security.AuthenticatedUser;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.function.Function;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class PipelineService {

    private final PipelineRepository pipelineRepository;
    private final ProjectRepository projectRepository;
    private final TemplateRepository templateRepository;
    private final RuntimeEnvironmentRepository runtimeEnvironmentRepository;
    private final DeploymentRepository deploymentRepository;
    private final ServiceRepository serviceRepository;
    private final HostRepository hostRepository;
    private final MavenSettingsRepository mavenSettingsRepository;
    private final HostService hostService;
    private final NotificationChannelRepository notificationChannelRepository;
    private final UserRepository userRepository;
    private final UserFavoritePipelineRepository userFavoritePipelineRepository;
    private final ServicePidHistoryRepository servicePidHistoryRepository;
    private final PipelineTemplateResolverService pipelineTemplateResolverService;
    private final PipelineHallEventService pipelineHallEventService;

    @Transactional
    public List<PipelineEntity> findAll() {
        return pipelineRepository.findAll();
    }

    @Transactional
    public PipelineEntity findById(Long id) {
        return pipelineRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.PIPELINE_NOT_FOUND));
    }

    @Transactional
    public List<PipelineHallSummary> findHallSummaries() {
        return buildHallSummaries(pipelineRepository.findAll(Sort.by(Sort.Order.desc("id"))));
    }

    @Transactional
    public PageResult<PipelineHallSummary> findHallPage(PipelineHallPageRequest request) {
        List<PipelineHallSummary> filtered = filterHallSummaries(findHallSummaries(), request);
        return PageResult.of(filtered, request.page(), request.pageSize());
    }

    public List<PipelineHallSummary> findHallSummariesByIds(List<Long> pipelineIds) {
        if (pipelineIds == null || pipelineIds.isEmpty()) {
            return List.of();
        }
        Set<Long> requestedIds = pipelineIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        if (requestedIds.isEmpty()) {
            return List.of();
        }
        return buildHallSummaries(
                pipelineRepository.findAllById(requestedIds).stream()
                        .sorted(Comparator.comparing(PipelineEntity::getId).reversed())
                        .toList()
        );
    }

    private List<PipelineHallSummary> buildHallSummaries(List<PipelineEntity> pipelines) {
        Set<Long> favoritePipelineIds = findFavoritePipelineIdSet(
                pipelines.stream().map(PipelineEntity::getId).toList()
        );
        Map<Long, PipelineLatestDeploymentSummary> latestDeploymentMap = deploymentRepository
                .findLatestSummariesByPipelineIds(pipelines.stream().map(PipelineEntity::getId).toList())
                .stream()
                .collect(Collectors.toMap(
                        PipelineLatestDeploymentSummary::pipelineId,
                        Function.identity(),
                        (left, right) -> left.createdAt().isAfter(right.createdAt()) ? left : right
                ));
        return pipelines.stream()
                .map(pipeline -> {
                    var latestDeployment = latestDeploymentMap.get(pipeline.getId());
                    var progress = resolveHallProgress(latestDeployment);
                    Long latestDeploymentOrder = latestDeployment == null ? null : deploymentRepository.countByPipelineId(pipeline.getId());
                    return new PipelineHallSummary(
                            pipeline.getId(),
                            pipeline.getName(),
                            pipeline.getDescription(),
                            pipeline.getDefaultBranch(),
                            pipeline.getProject() == null ? null : pipeline.getProject().getName(),
                            pipeline.getTemplateTypeSnapshot(),
                            pipeline.getTags() == null ? List.of() : pipeline.getTags(),
                            pipeline.getImportantTags() == null ? List.of() : pipeline.getImportantTags(),
                            pipeline.getLocked(),
                            pipeline.getLockReason(),
                            pipeline.getLockStartAt(),
                            pipeline.getLockEndAt(),
                            latestDeployment == null ? null : latestDeployment.id(),
                            latestDeploymentOrder,
                            latestDeployment == null || latestDeployment.status() == null ? null : latestDeployment.status().name(),
                            latestDeployment == null ? null : latestDeployment.branchName(),
                            latestDeployment == null ? null : latestDeployment.triggeredBy(),
                            latestDeployment == null ? null : resolveDisplayName(latestDeployment.triggeredBy()),
                            latestDeployment == null ? null : resolveAvatar(latestDeployment.triggeredBy()),
                            latestDeployment == null ? null : latestDeployment.createdAt(),
                            latestDeployment == null ? null : latestDeployment.startedAt(),
                            latestDeployment == null ? null : latestDeployment.finishedAt(),
                            progress.percent(),
                            progress.stage(),
                            progress.current(),
                            progress.total(),
                            latestDeployment == null ? pipeline.getId() : latestDeployment.id(),
                            favoritePipelineIds.contains(pipeline.getId())
                    );
                })
                .toList();
    }

    private List<PipelineHallSummary> filterHallSummaries(List<PipelineHallSummary> summaries, PipelineHallPageRequest request) {
        PipelineHallFilterMode mode = PipelineHallFilterMode.fromValue(request.filterMode());
        Set<Long> recentPipelineIds = mode == PipelineHallFilterMode.RECENT
                ? deploymentRepository.findTop30ByTriggeredByOrderByCreatedAtDesc(requireCurrentUser().username()).stream()
                .map(DeploymentEntity::getPipeline)
                .filter(java.util.Objects::nonNull)
                .map(PipelineEntity::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                : Set.of();
        String normalizedKeyword = TextKit.isBlank(request.keyword()) ? null : request.keyword().trim().toLowerCase();
        List<String> requiredTags = request.tags() == null
                ? List.of()
                : request.tags().stream().filter(TextKit::isNotBlank).map(String::trim).toList();
        List<PipelineHallSummary> filtered = summaries.stream()
                .filter(item -> request.selectedPipelineId() == null || request.selectedPipelineId().equals(item.pipelineId()))
                .filter(item -> matchesHallMode(item, mode, recentPipelineIds))
                .filter(item -> matchesHallKeyword(item, normalizedKeyword))
                .filter(item -> requiredTags.isEmpty() || item.tags().containsAll(requiredTags))
                .toList();
        if (!request.pinActivePipelines()) {
            return filtered;
        }
        return filtered.stream()
                .sorted(Comparator.comparing((PipelineHallSummary item) -> !isActiveHallItem(item)))
                .toList();
    }

    private boolean matchesHallMode(PipelineHallSummary item, PipelineHallFilterMode mode, Set<Long> recentPipelineIds) {
        return switch (mode) {
            case FAVORITES -> Boolean.TRUE.equals(item.favorited());
            case RUNNING -> isActiveHallItem(item);
            case FAILED -> DeploymentStatus.FAILED.name().equals(item.latestStatus());
            case RECENT -> recentPipelineIds.contains(item.pipelineId());
            case ALL -> true;
        };
    }

    private boolean matchesHallKeyword(PipelineHallSummary item, String normalizedKeyword) {
        if (normalizedKeyword == null) {
            return true;
        }
        return List.of(item.pipelineName(), item.pipelineDescription(), item.projectName(), item.defaultBranch()).stream()
                .filter(TextKit::isNotBlank)
                .anyMatch(value -> value.toLowerCase().contains(normalizedKeyword))
                || item.tags().stream().anyMatch(tag -> tag.toLowerCase().contains(normalizedKeyword));
    }

    private boolean isActiveHallItem(PipelineHallSummary item) {
        if (item.latestStatus() == null) {
            return false;
        }
        return DeploymentStatus.PENDING.name().equals(item.latestStatus())
                || DeploymentStatus.RUNNING.name().equals(item.latestStatus());
    }

    private HallProgress resolveHallProgress(PipelineLatestDeploymentSummary deployment) {
        if (deployment == null || deployment.status() == null || deployment.status() == DeploymentStatus.PENDING) {
            return new HallProgress(0, null, null, null);
        }
        if (deployment.status() == DeploymentStatus.SUCCESS) {
            return new HallProgress(100, null, null, null);
        }
        DeploymentEntity probe = new DeploymentEntity();
        probe.setStatus(deployment.status());
        probe.setLogPath(deployment.logPath());
        probe.setBuildStepTotal(deployment.buildStepTotal());
        probe.setDeployStepTotal(deployment.deployStepTotal());
        boolean startupObservationRequired = Boolean.TRUE.equals(deployment.monitorProcess());
        DeploymentEntity.ProgressSnapshot snapshot = probe.readProgressSnapshot(
                DeploymentEntity.resolveStartupObservationAttemptTotal(deployment.startupTimeoutSeconds())
        );
        if (snapshot == null) {
            return new HallProgress(0, null, null, null);
        }
        return new HallProgress(
                probe.progressPercent(snapshot, startupObservationRequired),
                probe.progressStage(snapshot),
                probe.progressCurrent(snapshot),
                probe.progressTotal(snapshot)
        );
    }

    private record HallProgress(
            Integer percent,
            String stage,
            Integer current,
            Integer total
    ) {
    }

    public List<PipelineBranchOption> buildBranchOptions(Long pipelineId, List<String> branches) {
        PipelineEntity pipeline = findById(pipelineId);
        Set<String> recentBranches = deploymentRepository.findTop10ByPipelineIdOrderByCreatedAtDesc(pipelineId).stream()
                .map(top.fusb.deploybot.model.DeploymentEntity::getBranchName)
                .filter(TextKit::isNotBlank)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        Set<String> mergedBranches = new java.util.LinkedHashSet<>();
        if (branches != null) {
            branches.stream().filter(TextKit::isNotBlank).map(String::trim).forEach(mergedBranches::add);
        }
        if (TextKit.isNotBlank(pipeline.getDefaultBranch())) {
            mergedBranches.add(pipeline.getDefaultBranch().trim());
        }
        mergedBranches.addAll(recentBranches);
        return mergedBranches.stream()
                .map(branch -> new PipelineBranchOption(
                        branch,
                        branch.equals(pipeline.getDefaultBranch()),
                        recentBranches.contains(branch)
                ))
                .toList();
    }

    public List<Long> findFavoritePipelineIds() {
        AuthenticatedUser currentUser = requireCurrentUser();
        return userFavoritePipelineRepository.findByUserId(currentUser.id()).stream()
                .map(item -> item.getPipeline().getId())
                .toList();
    }

    @Transactional
    public void favorite(Long pipelineId) {
        AuthenticatedUser currentUser = requireCurrentUser();
        PipelineEntity pipeline = pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.PIPELINE_NOT_FOUND));
        if (userFavoritePipelineRepository.findByUserIdAndPipelineId(currentUser.id(), pipelineId).isPresent()) {
            return;
        }
        UserFavoritePipelineEntity entity = new UserFavoritePipelineEntity();
        entity.setUser(userRepository.findById(currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorSubCode.USER_NOT_FOUND)));
        entity.setPipeline(pipeline);
        entity.setCreatedAt(LocalDateTime.now());
        userFavoritePipelineRepository.save(entity);
        pipelineHallEventService.publishChange();
    }

    @Transactional
    public void unfavorite(Long pipelineId) {
        AuthenticatedUser currentUser = requireCurrentUser();
        userFavoritePipelineRepository.findByUserIdAndPipelineId(currentUser.id(), pipelineId)
                .ifPresent(entity -> {
                    userFavoritePipelineRepository.delete(entity);
                    pipelineHallEventService.publishChange();
                });
    }

    private top.fusb.deploybot.model.DeploymentEntity enrichTriggeredByDisplayName(top.fusb.deploybot.model.DeploymentEntity entity) {
        if (entity == null) {
            return null;
        }
        entity.setTriggeredByDisplayName(resolveDisplayName(entity.getTriggeredBy()));
        entity.setStoppedByDisplayName(resolveDisplayName(entity.getStoppedBy()));
        return entity;
    }

    private String resolveDisplayName(String username) {
        if (TextKit.isBlank(username)) {
            return "-";
        }
        return userRepository.findByUsername(username)
                .map(item -> TextKit.isBlank(item.getDisplayName()) ? item.getUsername() : item.getDisplayName())
                .orElse(username);
    }

    private String resolveAvatar(String username) {
        if (TextKit.isBlank(username)) {
            return null;
        }
        return userRepository.findByUsername(username)
                .map(top.fusb.deploybot.model.UserEntity::getAvatar)
                .orElse(null);
    }

    private Set<Long> findFavoritePipelineIdSet(List<Long> pipelineIds) {
        AuthenticatedUser currentUser = requireCurrentUser();
        if (pipelineIds == null || pipelineIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(userFavoritePipelineRepository.findByUserIdAndPipelineIdIn(currentUser.id(), pipelineIds).stream()
                .map(item -> item.getPipeline().getId())
                .toList());
    }

    public PageResult<PipelineEntity> findPage(
            int page,
            int pageSize,
            String keyword,
            Long projectId,
            Long templateId,
            Long hostId,
            List<String> tags
    ) {
        var result = pipelineRepository.findAll((root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (TextKit.isNotBlank(keyword)) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern),
                        cb.like(cb.lower(root.get("defaultBranch")), pattern)
                ));
            }
            if (projectId != null) {
                predicates.add(cb.equal(root.get("project").get("id"), projectId));
            }
            if (templateId != null) {
                predicates.add(cb.equal(root.get("template").get("id"), templateId));
            }
            if (hostId != null) {
                predicates.add(cb.equal(root.get("targetHost").get("id"), hostId));
            }
            if (tags != null && !tags.isEmpty()) {
                for (String tag : tags) {
                    if (TextKit.isNotBlank(tag)) {
                        predicates.add(cb.like(root.get("tags"), "%" + "\"" + tag + "\"" + "%"));
                    }
                }
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, PageRequest.of(Math.max(0, page - 1), Math.max(1, Math.min(100, pageSize)), Sort.by(Sort.Order.desc("id"))));
        return new PageResult<>(
                result.getContent(),
                result.getTotalElements(),
                result.getNumber() + 1,
                result.getSize()
        );
    }

    public List<String> findAllTags() {
        return CollectionKit.sortedDistinctNonBlankStrings(
                pipelineRepository.findAll().stream()
                        .flatMap(item -> (item.getTags() == null ? List.<String>of() : item.getTags()).stream())
                        .toList()
        );
    }

    /**
     * 流水线是“项目 + 模板 + 目标主机 + 默认变量”的聚合对象。
     */
    public PipelineEntity save(PipelineRequest request, Long id) {
        PipelineEntity entity = id == null ? new PipelineEntity() : pipelineRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.PIPELINE_NOT_FOUND));
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setProject(projectRepository.findById(request.projectId())
                .orElseThrow(() -> new BusinessException(ErrorSubCode.PROJECT_NOT_FOUND)));
        bindTemplate(entity, request);
        HostEntity targetHost = hostRepository.findById(request.targetHostId())
                .orElseThrow(() -> new BusinessException(ErrorSubCode.HOST_NOT_FOUND));
        entity.setTargetHost(targetHost);
        entity.setTargetDir(TextKit.trimToNull(request.targetDir()));
        entity.setDefaultBranch(request.defaultBranch());
        Map<String, String> variables = new LinkedHashMap<>(request.variables() == null ? Map.of() : request.variables());
        Map<String, String> pluginConfig = normalizePluginConfig(request.pluginConfig());
        entity.setPluginConfig(pluginConfig);
        entity.setVariables(variables);
        entity.setTags(normalizeTags(request.tags()));
        entity.setImportantTags(normalizeTags(request.importantTags()).stream().limit(2).toList());
        entity.setJavaEnvironment(resolveEnvironment(request.javaEnvironmentId()));
        entity.setNodeEnvironment(resolveEnvironment(request.nodeEnvironmentId()));
        RuntimeEnvironmentEntity mavenEnvironment = resolveEnvironment(request.mavenEnvironmentId());
        entity.setMavenEnvironment(mavenEnvironment);
        MavenSettingsEntity mavenSettings = resolveMavenSettings(request.mavenSettingsId());
        if (mavenSettings != null && (mavenEnvironment == null || mavenSettings.getRuntimeEnvironment() == null
                || !mavenSettings.getRuntimeEnvironment().getId().equals(mavenEnvironment.getId()))) {
            throw new BusinessException(ErrorSubCode.MAVEN_SETTINGS_NOT_FOUND, "请选择当前 Maven 环境下的 settings.xml 配置。");
        }
        entity.setMavenSettings(mavenSettings);
        entity.setRuntimeJavaEnvironment(resolveRuntimeJavaEnvironment(request.runtimeJavaEnvironmentId(), targetHost));
        entity.setStartupKeyword(TextKit.trimToNull(request.startupKeyword()));
        entity.setStartupTimeoutSeconds(normalizeStartupTimeout(request.startupTimeoutSeconds()));
        entity.setNotificationBindings(normalizeNotificationBindings(request.notificationBindings()));
        PipelineEntity saved = pipelineRepository.save(entity);
        pipelineHallEventService.publishChange();
        return saved;
    }

    private Map<String, String> normalizePluginConfig(Map<String, String> pluginConfig) {
        Map<String, String> normalized = new LinkedHashMap<>();
        (pluginConfig == null ? Map.<String, String>of() : pluginConfig).forEach((key, value) -> {
            if (TextKit.isNotBlank(key) && TextKit.isNotBlank(value)) {
                normalized.put(key, value);
            }
        });
        return normalized;
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        tags.forEach(tag -> {
            String trimmed = TextKit.trimToNull(tag);
            if (trimmed != null) {
                normalized.add(trimmed);
            }
        });
        return List.copyOf(normalized);
    }

    private void bindTemplate(PipelineEntity entity, PipelineRequest request) {
        if (request.templateId() != null) {
            TemplateEntity template = templateRepository.findById(request.templateId())
                    .orElseThrow(() -> new BusinessException(ErrorSubCode.TEMPLATE_NOT_FOUND));
            entity.setTemplate(template);
            entity.setTemplatePluginId(pipelineTemplateResolverService.resolvePluginId(entity));
            entity.setBuiltinTemplateKey(null);
            entity.setTemplateNameSnapshot(template.getName());
            entity.setTemplateTypeSnapshot(template.getTemplateType());
            entity.setTemplateMonitorProcess(Boolean.TRUE.equals(template.getMonitorProcess()));
            return;
        }
        String pluginId = TextKit.trimToNull(request.templatePluginId());
        String builtinTemplateKey = TextKit.trimToNull(request.builtinTemplateKey());
        if (pluginId == null || builtinTemplateKey == null) {
            throw new BusinessException(ErrorSubCode.TEMPLATE_NOT_FOUND);
        }
        PipelineTemplateResolverService.ResolvedPipelineTemplate builtinTemplate =
                pipelineTemplateResolverService.resolveBuiltinTemplate(pluginId, builtinTemplateKey);
        if (builtinTemplate == null) {
            throw new BusinessException(ErrorSubCode.TEMPLATE_NOT_FOUND);
        }
        entity.setTemplate(null);
        entity.setTemplatePluginId(pluginId);
        entity.setBuiltinTemplateKey(builtinTemplateKey);
        entity.setTemplateNameSnapshot(builtinTemplate.name());
        entity.setTemplateTypeSnapshot(builtinTemplate.templateType());
        entity.setTemplateMonitorProcess(builtinTemplate.monitorProcess());
    }

    private MavenSettingsEntity resolveMavenSettings(Long mavenSettingsId) {
        if (mavenSettingsId == null) {
            return null;
        }
        MavenSettingsEntity settings = mavenSettingsRepository.findByIdAndDeletedFalse(mavenSettingsId)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.MAVEN_SETTINGS_NOT_FOUND));
        RuntimeEnvironmentEntity mavenEnvironment = settings.getRuntimeEnvironment();
        if (mavenEnvironment == null || mavenEnvironment.getType() != top.fusb.deploybot.model.RuntimeEnvironmentType.MAVEN) {
            throw new BusinessException(ErrorSubCode.MAVEN_SETTINGS_NOT_FOUND);
        }
        return settings;
    }

    @Transactional
    public void delete(Long id) {
        PipelineEntity pipeline = pipelineRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.PIPELINE_NOT_FOUND));
        deploymentRepository.backfillPipelineSnapshot(
                id,
                pipeline.getName(),
                pipeline.getProject() == null ? null : pipeline.getProject().getName()
        );
        userFavoritePipelineRepository.deleteByPipelineId(id);
        servicePidHistoryRepository.deleteByPipelineId(id);
        serviceRepository.deleteByPipelineId(id);
        deploymentRepository.detachPipeline(id);
        pipelineRepository.deleteById(id);
        pipelineHallEventService.publishChange();
    }

    /**
     * 当前执行模型约定所有构建都在本机完成，因此构建环境必须来自本机。
     */
    private RuntimeEnvironmentEntity resolveEnvironment(Long environmentId) {
        if (environmentId == null) {
            return null;
        }
        RuntimeEnvironmentEntity environment = runtimeEnvironmentRepository.findById(environmentId)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.RUNTIME_ENVIRONMENT_NOT_FOUND));
        HostEntity localHost = hostService.ensureLocalHost();
        if (environment.getHost() == null || !environment.getHost().getId().equals(localHost.getId())) {
            throw new BusinessException(ErrorSubCode.PIPELINE_ENV_MUST_BE_LOCAL);
        }
        return environment;
    }

    /**
     * 发布阶段的 Java 环境必须来自目标主机，避免把本机构建环境误用于远端运行。
     */
    private RuntimeEnvironmentEntity resolveRuntimeJavaEnvironment(Long environmentId, HostEntity targetHost) {
        if (environmentId == null) {
            return null;
        }
        RuntimeEnvironmentEntity environment = runtimeEnvironmentRepository.findById(environmentId)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.RUNTIME_ENVIRONMENT_NOT_FOUND));
        if (environment.getType() != top.fusb.deploybot.model.RuntimeEnvironmentType.JAVA) {
            throw new BusinessException(ErrorSubCode.PIPELINE_RUNTIME_ENV_MUST_BE_JAVA);
        }
        if (targetHost == null || environment.getHost() == null || !environment.getHost().getId().equals(targetHost.getId())) {
            throw new BusinessException(ErrorSubCode.PIPELINE_RUNTIME_ENV_MUST_MATCH_TARGET_HOST);
        }
        return environment;
    }

    private AuthenticatedUser requireCurrentUser() {
        AuthenticatedUser currentUser = AuthContextHolder.get();
        if (currentUser == null) {
            throw new BusinessException(ErrorSubCode.AUTH_REQUIRED);
        }
        return currentUser;
    }

    private Integer normalizeStartupTimeout(Integer startupTimeoutSeconds) {
        if (startupTimeoutSeconds == null) {
            return null;
        }
        return Math.max(5, startupTimeoutSeconds);
    }

    private List<NotificationBinding> normalizeNotificationBindings(List<NotificationBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return List.of();
        }
        List<NotificationBinding> normalizedBindings = bindings.stream()
                .filter(item -> item != null && item.notificationId() != null && item.eventType() != null)
                .toList();
        Set<String> uniquePairs = normalizedBindings.stream()
                .map(item -> item.notificationId() + ":" + item.eventType().name())
                .collect(Collectors.toSet());
        if (uniquePairs.size() != normalizedBindings.size()) {
            throw new BusinessException(ErrorSubCode.PIPELINE_NOTIFICATION_BINDING_INVALID);
        }
        Set<Long> ids = normalizedBindings.stream()
                .map(NotificationBinding::notificationId)
                .collect(Collectors.toSet());
        if (!ids.isEmpty() && notificationChannelRepository.findAllById(ids).size() != ids.size()) {
            throw new BusinessException(ErrorSubCode.PIPELINE_NOTIFICATION_BINDING_INVALID);
        }
        return normalizedBindings;
    }

    private boolean matchesKeyword(PipelineEntity item, String keyword) {
        if (TextKit.isBlank(keyword)) {
            return true;
        }
        return TextKit.containsIgnoreCase(item.getName(), keyword)
                || TextKit.containsIgnoreCase(item.getDescription(), keyword)
                || TextKit.containsIgnoreCase(item.getDefaultBranch(), keyword);
    }

    private boolean containsAllTags(PipelineEntity item, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return true;
        }
        List<String> currentTags = item.getTags() == null ? List.of() : item.getTags();
        return tags.stream().allMatch(currentTags::contains);
    }
}
