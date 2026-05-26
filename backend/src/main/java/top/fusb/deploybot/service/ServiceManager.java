package top.fusb.deploybot.service;

import lombok.RequiredArgsConstructor;
import top.fusb.deploybot.dto.PipelineHallRunningServiceSummary;
import top.fusb.deploybot.dto.ServicePidHistorySummary;
import top.fusb.deploybot.dto.ServiceProcessSummary;
import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.model.DeploymentEntity;
import top.fusb.deploybot.model.HostEntity;
import top.fusb.deploybot.model.HostType;
import top.fusb.deploybot.model.ServiceEntity;
import top.fusb.deploybot.model.ServicePidChangeSource;
import top.fusb.deploybot.model.ServicePidHistoryEntity;
import top.fusb.deploybot.model.ServiceStatus;
import top.fusb.deploybot.repo.ServicePidHistoryRepository;
import top.fusb.deploybot.repo.ServiceRepository;
import top.fusb.deploybot.repo.DeploymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ServiceManager {
    private static final Logger log = LoggerFactory.getLogger(ServiceManager.class);
    private static final int HEARTBEAT_MISS_THRESHOLD = 3;
    private static final int PRE_DEPLOY_STOP_MAX_RETRIES = 3;
    private static final int DEPLOYMENT_CONFIRM_MAX_RETRIES = 3;
    private static final int MANUAL_STOP_MAX_RETRIES = 3;
    private static final DateTimeFormatter REMOTE_PROCESS_START_FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("EEE MMM d HH:mm:ss yyyy")
            .toFormatter(Locale.ENGLISH);
    private static final Comparator<ServiceEntity> RUNNING_HALL_COMPARATOR =
            Comparator.comparing((ServiceEntity service) -> service.getStatus() == ServiceStatus.RUNNING ? 0 : 1)
                    .thenComparing(ServiceEntity::getActiveSince, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(ServiceEntity::getLastHeartbeatAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(service -> service.getPipeline() == null ? null : service.getPipeline().getId(), Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(ServiceEntity::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final ServiceRepository serviceRepository;
    private final ServicePidHistoryRepository servicePidHistoryRepository;
    private final DeploymentRepository deploymentRepository;
    private final HostService hostService;

    public List<ServiceEntity> findAll() {
        return serviceRepository.findAllByOrderByIdDesc();
    }

    public List<PipelineHallRunningServiceSummary> findRunningHallSummaries() {
        return serviceRepository.findAllByOrderByUpdatedAtDesc().stream()
                .filter(service -> service.getStatus() == ServiceStatus.RUNNING || service.getStatus() == ServiceStatus.STOPPED)
                .sorted(RUNNING_HALL_COMPARATOR)
                .limit(12)
                .map(service -> new PipelineHallRunningServiceSummary(
                        service.getId(),
                        service.getPipeline() == null ? null : service.getPipeline().getId(),
                        service.getPipeline() == null ? null : service.getPipeline().getName(),
                        service.getPipeline() == null || service.getPipeline().getImportantTags() == null ? List.of() : service.getPipeline().getImportantTags(),
                        service.getServiceName(),
                        service.getPipeline() == null ? null : service.getPipeline().getTemplateTypeSnapshot(),
                        service.getPipeline() != null && service.getPipeline().getTargetHost() != null ? service.getPipeline().getTargetHost().getName() : "本机",
                        service.getCurrentPid(),
                        service.getStatus(),
                        service.getActiveSince(),
                        service.getLastHeartbeatAt()
                ))
                .toList();
    }

    /**
     * 定时刷新服务状态，给“服务管理”和控制台提供最近心跳时间。
     */
    @Scheduled(fixedDelay = 15000L)
    public void heartbeatServices() {
        List<ServiceEntity> services = serviceRepository.findAll();
        if (services.isEmpty()) {
            return;
        }
        log.info("开始执行服务心跳刷新，服务数量={}。", services.size());
        services.forEach(service -> {
            try {
                refreshStatus(service);
            } catch (ObjectOptimisticLockingFailureException ex) {
                log.info("服务 {} 心跳刷新命中乐观锁冲突，说明期间已有更新写入，跳过本次旧快照回写。", service.getId());
            } catch (Exception ex) {
                log.warn("服务 {} 心跳刷新失败：{}", service.getId(), ex.getMessage());
            }
        });
        log.info("服务心跳刷新完成。");
    }

    public ServiceEntity findById(Long id) {
        ServiceEntity service = serviceRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.SERVICE_NOT_FOUND));
        return refreshStatus(service);
    }

    /**
     * 在新的部署启动前，优先停止当前流水线已经被系统接管的旧服务。
     */
    public ServiceEntity stopManagedServiceBeforeDeploy(DeploymentEntity currentDeployment) {
        Long pipelineId = currentDeployment == null || currentDeployment.getPipeline() == null
                ? null
                : currentDeployment.getPipeline().getId();
        if (pipelineId == null) {
            log.warn("部署前停止旧服务时缺少当前部署或流水线信息，跳过处理。");
            return null;
        }
        Long lastObservedPid = null;
        for (int attempt = 1; attempt <= PRE_DEPLOY_STOP_MAX_RETRIES; attempt++) {
            ServiceEntity service = serviceRepository.findByPipelineId(pipelineId).orElse(null);
            if (service == null) {
                log.info("流水线 {} 当前没有可接管的旧服务，无需在部署前停止。", pipelineId);
                return null;
            }
            Long stoppedPid = resolveManagedPid(service);
            lastObservedPid = stoppedPid;
            log.info(
                    "流水线 {} 在部署前检测到已受管服务：serviceId={}，pid={}，状态={}，pid来源={}，currentPid={}，lastDeploymentId={}，lastDeploymentPid={}，heartbeatMissCount={}，attempt={}/{}。",
                    pipelineId,
                    service.getId(),
                    stoppedPid,
                    service.getStatus(),
                    describeManagedPidSource(service),
                    service.getCurrentPid(),
                    service.getLastDeployment() == null ? null : service.getLastDeployment().getId(),
                    service.getLastDeployment() == null ? null : service.getLastDeployment().getMonitoredPid(),
                    service.getHeartbeatMissCount(),
                    attempt,
                    PRE_DEPLOY_STOP_MAX_RETRIES
            );
            if (stoppedPid != null) {
                HostEntity targetHost = service.getPipeline() == null ? null : service.getPipeline().getTargetHost();
                if (isAlive(stoppedPid, targetHost)) {
                    log.info("流水线 {} 准备停止旧服务进程 pid={}，目标主机类型={}。", pipelineId, stoppedPid, targetHost == null ? HostType.LOCAL : targetHost.getType());
                    stopProcess(stoppedPid, targetHost);
                } else {
                    log.info("流水线 {} 的旧服务 pid={} 已不存在，直接清理服务状态。", pipelineId, stoppedPid);
                }
            } else {
                log.warn(
                        "流水线 {} 在部署前发现服务记录存在，但无法解析到可接管 pid。serviceId={}，currentPid={}，lastDeploymentId={}，lastDeploymentPid={}。",
                        pipelineId,
                        service.getId(),
                        service.getCurrentPid(),
                        service.getLastDeployment() == null ? null : service.getLastDeployment().getId(),
                        service.getLastDeployment() == null ? null : service.getLastDeployment().getMonitoredPid()
                );
            }
            try {
                ServiceStatus previousStatus = service.getStatus();
                service.setCurrentPid(null);
                service.setStatus(ServiceStatus.STOPPED);
                service.setActiveSince(null);
                service.setHeartbeatMissCount(0);
                service.setUpdatedAt(LocalDateTime.now());
                ServiceEntity saved = serviceRepository.save(service);
                recordPidHistory(saved, currentDeployment, stoppedPid, null, previousStatus, ServiceStatus.STOPPED, ServicePidChangeSource.PRE_DEPLOY_STOP, "部署前停止旧服务");
                saved.setCurrentPid(stoppedPid);
                log.info("流水线 {} 部署前旧服务停止完成。serviceId={}。", pipelineId, saved.getId());
                return saved;
            } catch (ObjectOptimisticLockingFailureException ex) {
                log.info("流水线 {} 在部署前停止旧服务时命中乐观锁冲突，准备重新读取最新服务状态后重试。attempt={}/{}，lastObservedPid={}。", pipelineId, attempt, PRE_DEPLOY_STOP_MAX_RETRIES, stoppedPid);
            }
        }
        ServiceEntity latestService = serviceRepository.findByPipelineId(pipelineId).orElse(null);
        log.warn("流水线 {} 在部署前停止旧服务时连续 {} 次命中乐观锁冲突，本次按技术冲突降级处理，不中断部署。latestServiceId={}，latestPid={}，latestStatus={}，lastObservedPid={}。",
                pipelineId,
                PRE_DEPLOY_STOP_MAX_RETRIES,
                latestService == null ? null : latestService.getId(),
                latestService == null ? null : latestService.getCurrentPid(),
                latestService == null ? null : latestService.getStatus(),
                lastObservedPid);
        if (latestService != null) {
            latestService.setCurrentPid(lastObservedPid);
        }
        return latestService;
    }

    public void updateFromDeployment(DeploymentEntity deployment, Long pid) {
        Long pipelineId = deployment.getPipeline().getId();
        for (int attempt = 1; attempt <= DEPLOYMENT_CONFIRM_MAX_RETRIES; attempt++) {
            ServiceEntity service = serviceRepository.findByPipelineId(pipelineId)
                    .orElseGet(ServiceEntity::new);
            try {
                updateFromDeploymentOnce(service, deployment, pid);
                return;
            } catch (ObjectOptimisticLockingFailureException ex) {
                if (attempt >= DEPLOYMENT_CONFIRM_MAX_RETRIES) {
                    throw ex;
                }
                log.info("部署 {} 接管服务记录时命中乐观锁冲突，重新读取服务后重试。pipelineId={}，attempt={}/{}。",
                        deployment.getId(), pipelineId, attempt, DEPLOYMENT_CONFIRM_MAX_RETRIES);
            }
        }
    }

    private void updateFromDeploymentOnce(ServiceEntity service, DeploymentEntity deployment, Long pid) {
        Map<String, String> variables = deployment.getVariables() == null ? Map.of() : deployment.getVariables();
        Long previousPid = service.getCurrentPid();
        ServiceStatus previousStatus = service.getStatus();
        service.setPipeline(deployment.getPipeline());
        service.setLastDeployment(deployment);
        service.setServiceName(variables.getOrDefault("serviceName", deployment.getPipeline().getName()));
        service.setCurrentPid(pid);
        boolean running = isAlive(pid, deployment.getPipeline().getTargetHost());
        service.setStatus(running ? ServiceStatus.RUNNING : ServiceStatus.STOPPED);
        if (service.getCreatedAt() == null) {
            service.setCreatedAt(LocalDateTime.now());
        }
        if (running) {
            service.setActiveSince(LocalDateTime.now());
            service.setLastHeartbeatAt(LocalDateTime.now());
            service.setHeartbeatMissCount(0);
        }
        service.setUpdatedAt(LocalDateTime.now());
        ServiceEntity saved = serviceRepository.save(service);
        recordPidHistory(saved, deployment, previousPid, pid, previousStatus, saved.getStatus(), ServicePidChangeSource.DEPLOYMENT_CONFIRMED, "部署完成后接管进程");
    }

    public ServiceEntity stop(Long id) {
        Long lastObservedPid = null;
        for (int attempt = 1; attempt <= MANUAL_STOP_MAX_RETRIES; attempt++) {
            ServiceEntity service = serviceRepository.findById(id)
                    .orElseThrow(() -> new BusinessException(ErrorSubCode.SERVICE_NOT_FOUND));
            HostEntity targetHost = service.getPipeline() == null ? null : service.getPipeline().getTargetHost();
            Long previousPid = service.getCurrentPid();
            ServiceStatus previousStatus = service.getStatus();
            Long pid = resolveManagedPid(service);
            lastObservedPid = pid;
            if (pid != null) {
                stopProcess(pid, targetHost);
            }
            service.setCurrentPid(null);
            service.setStatus(ServiceStatus.STOPPED);
            service.setActiveSince(null);
            service.setHeartbeatMissCount(0);
            service.setUpdatedAt(LocalDateTime.now());
            try {
                ServiceEntity saved = serviceRepository.save(service);
                recordPidHistory(saved, saved.getLastDeployment(), previousPid, null, previousStatus, ServiceStatus.STOPPED, ServicePidChangeSource.MANUAL_STOP, "手动停止服务");
                return saved;
            } catch (ObjectOptimisticLockingFailureException ex) {
                log.info("手动停止服务 {} 时命中乐观锁冲突，准备重读最新服务状态后重试。attempt={}/{}，lastObservedPid={}。",
                        id, attempt, MANUAL_STOP_MAX_RETRIES, pid);
            }
        }
        log.warn("手动停止服务 {} 时连续 {} 次命中乐观锁冲突，lastObservedPid={}。", id, MANUAL_STOP_MAX_RETRIES, lastObservedPid);
        throw new BusinessException(ErrorSubCode.REMOTE_SERVICE_STOP_FAILED, "服务状态正在刷新，请稍后重试。");
    }

    public List<ServiceProcessSummary> listProcessCandidates(Long id) {
        ServiceEntity service = serviceRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.SERVICE_NOT_FOUND));
        HostEntity targetHost = service.getPipeline() == null ? null : service.getPipeline().getTargetHost();
        if (targetHost == null || targetHost.getType() == HostType.LOCAL) {
            return ProcessHandle.allProcesses()
                    .filter(ProcessHandle::isAlive)
                    .map(this::toLocalProcessSummary)
                    .filter(this::isSupportedMonitoredProcess)
                    .sorted(Comparator.comparing(ServiceProcessSummary::pid).reversed())
                    .limit(200)
                    .toList();
        }
        return listRemoteProcessCandidates(targetHost);
    }

    public ServiceEntity bindProcess(Long id, Long pid) {
        if (pid == null || pid <= 0) {
            throw new BusinessException(ErrorSubCode.REMOTE_SERVICE_STOP_FAILED, "请选择要绑定的进程。");
        }
        ServiceEntity service = serviceRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.SERVICE_NOT_FOUND));
        HostEntity targetHost = service.getPipeline() == null ? null : service.getPipeline().getTargetHost();
        if (!isAlive(pid, targetHost)) {
            throw new BusinessException(ErrorSubCode.REMOTE_SERVICE_STOP_FAILED, "进程不存在或已经退出，无法绑定。");
        }
        Long previousPid = service.getCurrentPid();
        ServiceStatus previousStatus = service.getStatus();
        service.setCurrentPid(pid);
        service.setStatus(ServiceStatus.RUNNING);
        service.setActiveSince(resolveProcessStartedAt(pid, targetHost).orElseGet(() -> service.getActiveSince() != null ? service.getActiveSince() : LocalDateTime.now()));
        service.setLastHeartbeatAt(LocalDateTime.now());
        service.setHeartbeatMissCount(0);
        service.setUpdatedAt(LocalDateTime.now());
        log.info("服务 {} 手动绑定进程成功，pid={}，目标主机类型={}。", service.getId(), pid, targetHost == null ? HostType.LOCAL : targetHost.getType());
        ServiceEntity saved = serviceRepository.save(service);
        recordPidHistory(saved, saved.getLastDeployment(), previousPid, pid, previousStatus, ServiceStatus.RUNNING, ServicePidChangeSource.MANUAL_BIND, "手动绑定进程");
        return saved;
    }

    public ServiceEntity refreshStatus(ServiceEntity service) {
        HostEntity targetHost = service.getPipeline() == null ? null : service.getPipeline().getTargetHost();
        Long pid = resolveManagedPid(service);
        Long previousPid = service.getCurrentPid();
        ServiceStatus previousStatus = service.getStatus();
        log.info(
                "服务 {} 开始刷新状态：status={}，currentPid={}，解析后pid={}，pid来源={}，lastDeploymentId={}，lastDeploymentPid={}，heartbeatMissCount={}。",
                service.getId(),
                service.getStatus(),
                service.getCurrentPid(),
                pid,
                describeManagedPidSource(service),
                service.getLastDeployment() == null ? null : service.getLastDeployment().getId(),
                service.getLastDeployment() == null ? null : service.getLastDeployment().getMonitoredPid(),
                service.getHeartbeatMissCount()
        );
        boolean running = isAlive(pid, targetHost);
        if (running) {
            service.setCurrentPid(pid);
            service.setStatus(ServiceStatus.RUNNING);
            if (service.getActiveSince() == null) {
                service.setActiveSince(LocalDateTime.now());
            }
            service.setLastHeartbeatAt(LocalDateTime.now());
            service.setHeartbeatMissCount(0);
        } else {
            int missCount = (service.getHeartbeatMissCount() == null ? 0 : service.getHeartbeatMissCount()) + 1;
            service.setHeartbeatMissCount(missCount);
            if (missCount >= HEARTBEAT_MISS_THRESHOLD) {
                service.setStatus(ServiceStatus.STOPPED);
                service.setActiveSince(null);
                log.info(
                        "服务 {} 连续 {} 次心跳未命中，标记为已停止，保留 pid={} 供后续排查。currentPid={}，lastDeploymentId={}，lastDeploymentPid={}。",
                        service.getId(),
                        missCount,
                        pid,
                        service.getCurrentPid(),
                        service.getLastDeployment() == null ? null : service.getLastDeployment().getId(),
                        service.getLastDeployment() == null ? null : service.getLastDeployment().getMonitoredPid()
                );
            } else {
                log.info(
                        "服务 {} 第 {} 次心跳未命中，暂不清理 pid={}。currentPid={}，lastDeploymentId={}，lastDeploymentPid={}。",
                        service.getId(),
                        missCount,
                        pid,
                        service.getCurrentPid(),
                        service.getLastDeployment() == null ? null : service.getLastDeployment().getId(),
                        service.getLastDeployment() == null ? null : service.getLastDeployment().getMonitoredPid()
                );
            }
        }
        service.setUpdatedAt(LocalDateTime.now());
        ServiceEntity saved = serviceRepository.save(service);
        if (saved.getStatus() == ServiceStatus.STOPPED && previousStatus != ServiceStatus.STOPPED) {
            recordPidHistory(saved, saved.getLastDeployment(), previousPid, saved.getCurrentPid(), previousStatus, ServiceStatus.STOPPED, ServicePidChangeSource.HEARTBEAT_STOPPED, "心跳连续未命中，标记为已停止");
        } else if (saved.getStatus() == ServiceStatus.RUNNING
                && (previousStatus != ServiceStatus.RUNNING || !java.util.Objects.equals(previousPid, saved.getCurrentPid()))) {
            recordPidHistory(saved, saved.getLastDeployment(), previousPid, saved.getCurrentPid(), previousStatus, ServiceStatus.RUNNING, ServicePidChangeSource.HEARTBEAT_RECOVERED, "心跳确认服务仍在运行");
        }
        return saved;
    }

    public List<ServicePidHistorySummary> listPidHistory(Long id) {
        serviceRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.SERVICE_NOT_FOUND));
        return servicePidHistoryRepository.findTop50ByServiceIdOrderByCreatedAtDescIdDesc(id).stream()
                .map(item -> new ServicePidHistorySummary(
                        item.getId(),
                        item.getService().getId(),
                        item.getDeployment() == null ? null : item.getDeployment().getId(),
                        item.getPreviousPid(),
                        item.getCurrentPid(),
                        item.getPreviousStatus(),
                        item.getCurrentStatus(),
                        item.getChangeSource(),
                        item.getNote(),
                        item.getCreatedAt()
                ))
                .toList();
    }

    private Long resolveManagedPid(ServiceEntity service) {
        if (service == null) {
            return null;
        }
        if (service.getCurrentPid() != null) {
            return service.getCurrentPid();
        }
        if (service.getLastDeployment() != null && service.getLastDeployment().getMonitoredPid() != null) {
            return service.getLastDeployment().getMonitoredPid();
        }
        return null;
    }

    private String describeManagedPidSource(ServiceEntity service) {
        if (service == null) {
            return "无服务记录";
        }
        if (service.getCurrentPid() != null) {
            return "service.currentPid";
        }
        if (service.getLastDeployment() != null && service.getLastDeployment().getMonitoredPid() != null) {
            return "lastDeployment.monitoredPid";
        }
        return "无可用PID";
    }

    private void recordPidHistory(
            ServiceEntity service,
            DeploymentEntity deployment,
            Long previousPid,
            Long currentPid,
            ServiceStatus previousStatus,
            ServiceStatus currentStatus,
            ServicePidChangeSource source,
            String note
    ) {
        if (service == null || service.getId() == null) {
            return;
        }
        if (java.util.Objects.equals(previousPid, currentPid) && previousStatus == currentStatus) {
            return;
        }
        ServicePidHistoryEntity history = new ServicePidHistoryEntity();
        history.setService(serviceRepository.getReferenceById(service.getId()));
        if (deployment != null && deployment.getId() != null) {
            history.setDeployment(deploymentRepository.getReferenceById(deployment.getId()));
        }
        history.setPreviousPid(previousPid);
        history.setCurrentPid(currentPid);
        history.setPreviousStatus(previousStatus);
        history.setCurrentStatus(currentStatus);
        history.setChangeSource(source);
        history.setNote(note);
        history.setCreatedAt(LocalDateTime.now());
        try {
            servicePidHistoryRepository.save(history);
        } catch (Exception ex) {
            log.warn(
                    "服务 {} 写入 PID 轨迹失败：source={}，previousPid={}，currentPid={}，reason={}",
                    service.getId(),
                    source,
                    previousPid,
                    currentPid,
                    ex.getMessage()
            );
        }
    }

    private boolean isAlive(Long pid, HostEntity host) {
        if (pid == null) {
            return false;
        }
        if (host == null || host.getType() == HostType.LOCAL) {
            return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
        }
        try {
            String output = hostService.executeRemoteScript(
                    host.getId(),
                    "if kill -0 " + pid + " >/dev/null 2>&1; then echo RUNNING; else echo STOPPED; fi\n",
                    8
            );
            return output.contains("RUNNING");
        } catch (Exception ex) {
            return false;
        }
    }

    private java.util.Optional<LocalDateTime> resolveProcessStartedAt(Long pid, HostEntity host) {
        if (pid == null) {
            return java.util.Optional.empty();
        }
        if (host == null || host.getType() == HostType.LOCAL) {
            return ProcessHandle.of(pid)
                    .flatMap(handle -> handle.info().startInstant())
                    .map(instant -> LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));
        }
        try {
            String output = hostService.executeRemoteScript(
                    host.getId(),
                    "ps -p " + pid + " -o lstart= 2>/dev/null | head -n 1\n",
                    8
            ).trim();
            if (output.isBlank()) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(parseRemoteProcessStartedAt(output));
        } catch (Exception ex) {
            return java.util.Optional.empty();
        }
    }

    private LocalDateTime parseRemoteProcessStartedAt(String output) {
        String normalized = output.trim().replaceAll("\\s+", " ");
        try {
            return LocalDateTime.parse(normalized, REMOTE_PROCESS_START_FORMATTER);
        } catch (DateTimeParseException ex) {
            log.debug("解析远程进程启动时间失败，output={}", output, ex);
            throw ex;
        }
    }

    private ServiceProcessSummary toLocalProcessSummary(ProcessHandle process) {
        ProcessHandle.Info info = process.info();
        String command = info.command().orElse("");
        String commandLine = info.commandLine().orElse(command);
        return new ServiceProcessSummary(process.pid(), command, commandLine);
    }

    private List<ServiceProcessSummary> listRemoteProcessCandidates(HostEntity host) {
        try {
            String output = hostService.executeRemoteScript(
                    host.getId(),
                    """
                            ps -efww | awk '
                            {
                              pid=$2;
                              line=$0;
                              for (i=1; i<=7; i++) sub(/^[[:space:]]*[^[:space:]]+[[:space:]]+/, "", line);
                              split(line, args, /[[:space:]]+/);
                              comm=args[1];
                              if (pid ~ /^[0-9]+$/ && line ~ /(^|[[:space:]\\/])java([[:space:]]|$)/ && line !~ /[a]wk/) {
                                print pid "\\t" comm "\\t" line;
                              }
                            }'
                            """,
                    8
            );
            List<ServiceProcessSummary> result = new ArrayList<>();
            output.lines().forEach(line -> {
                String[] parts = line.split("\\t", 3);
                if (parts.length < 3) {
                    return;
                }
                try {
                    result.add(new ServiceProcessSummary(Long.parseLong(parts[0].trim()), parts[1].trim(), parts[2].trim()));
                } catch (NumberFormatException ignored) {
                }
            });
            return result.stream()
                    .filter(this::isSupportedMonitoredProcess)
                    .limit(200)
                    .toList();
        } catch (Exception ex) {
            throw new BusinessException(ErrorSubCode.REMOTE_EXECUTION_FAILED, "拉取远程进程列表失败：" + ex.getMessage(), ex);
        }
    }

    private boolean isSupportedMonitoredProcess(ServiceProcessSummary process) {
        if (process == null || process.commandLine() == null || process.commandLine().isBlank()) {
            return false;
        }
        // 当前服务监控只接管 Java 进程；后续增加 Node 等类型时，在这里扩展规则即可。
        String command = process.command() == null ? "" : process.command().trim();
        if (command.equals("java") || command.endsWith("/java")) {
            return true;
        }
        return process.commandLine().matches("(^|\\s)([^\\s]*/)?java(\\s|$).*");
    }

    private void stopProcess(Long pid, HostEntity host) {
        if (host == null || host.getType() == HostType.LOCAL) {
            ProcessHandle handle = ProcessHandle.of(pid)
                    .orElseThrow(() -> new BusinessException(ErrorSubCode.REMOTE_SERVICE_STOP_FAILED, "旧服务进程不存在，无法停止。"));
            handle.destroy();
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            if (handle.isAlive()) {
                handle.destroyForcibly();
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            if (handle.isAlive()) {
                throw new BusinessException(ErrorSubCode.REMOTE_SERVICE_STOP_FAILED, "旧服务进程停止失败，请先手工处理后再重新部署。");
            }
            return;
        }
        try {
            String output = hostService.executeRemoteScript(
                    host.getId(),
                    "kill " + pid + " >/dev/null 2>&1 || true\n" +
                            "sleep 1\n" +
                            "if kill -0 " + pid + " >/dev/null 2>&1; then kill -9 " + pid + " >/dev/null 2>&1 || true; fi\n" +
                            "sleep 1\n" +
                            "if kill -0 " + pid + " >/dev/null 2>&1; then echo STILL_RUNNING; else echo STOPPED; fi\n",
                    12
            );
            if (output == null || !output.contains("STOPPED")) {
                throw new BusinessException(ErrorSubCode.REMOTE_SERVICE_STOP_FAILED, "旧服务进程停止失败，请先手工处理后再重新部署。");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorSubCode.REMOTE_SERVICE_STOP_FAILED, ex.getMessage(), ex);
        }
    }
}
