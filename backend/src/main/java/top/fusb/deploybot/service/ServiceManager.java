package top.fusb.deploybot.service;

import top.fusb.deploybot.dto.PipelineHallRunningServiceSummary;
import top.fusb.deploybot.dto.ServiceProcessSummary;
import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.model.DeploymentEntity;
import top.fusb.deploybot.model.HostEntity;
import top.fusb.deploybot.model.HostType;
import top.fusb.deploybot.model.ServiceEntity;
import top.fusb.deploybot.model.ServiceStatus;
import top.fusb.deploybot.repo.ServiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class ServiceManager {
    private static final Logger log = LoggerFactory.getLogger(ServiceManager.class);
    private static final int HEARTBEAT_MISS_THRESHOLD = 3;
    private static final DateTimeFormatter REMOTE_PROCESS_START_FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("EEE MMM d HH:mm:ss yyyy")
            .toFormatter(Locale.ENGLISH);
    private static final Comparator<ServiceEntity> SERVICE_HEARTBEAT_COMPARATOR =
            Comparator.comparing(ServiceEntity::getLastHeartbeatAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(ServiceEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(ServiceEntity::getId, Comparator.nullsLast(Comparator.reverseOrder()));
    private static final Comparator<ServiceEntity> RUNNING_HALL_COMPARATOR =
            Comparator.comparing((ServiceEntity service) -> service.getStatus() == ServiceStatus.RUNNING ? 0 : 1)
                    .thenComparing(ServiceEntity::getActiveSince, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(ServiceEntity::getLastHeartbeatAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(service -> service.getPipeline() == null ? null : service.getPipeline().getId(), Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(ServiceEntity::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final ServiceRepository serviceRepository;
    private final JsonMapper jsonMapper;
    private final HostService hostService;

    public ServiceManager(ServiceRepository serviceRepository, JsonMapper jsonMapper, HostService hostService) {
        this.serviceRepository = serviceRepository;
        this.jsonMapper = jsonMapper;
        this.hostService = hostService;
    }

    public List<ServiceEntity> findAll() {
        List<ServiceEntity> services = serviceRepository.findAllByOrderByUpdatedAtDesc();
        services.sort(SERVICE_HEARTBEAT_COMPARATOR);
        return services;
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
                        service.getServiceName(),
                        service.getPipeline() != null && service.getPipeline().getTemplate() != null ? service.getPipeline().getTemplate().getTemplateType() : null,
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
    public ServiceEntity stopManagedServiceBeforeDeploy(Long pipelineId) {
        ServiceEntity service = serviceRepository.findFirstByPipelineId(pipelineId).orElse(null);
        if (service == null) {
            log.info("流水线 {} 当前没有可接管的旧服务，无需在部署前停止。", pipelineId);
            return null;
        }
        Long stoppedPid = resolveManagedPid(service);
        log.info(
                "流水线 {} 在部署前检测到已受管服务：serviceId={}，pid={}，状态={}，pid来源={}，currentPid={}，lastDeploymentId={}，lastDeploymentPid={}，heartbeatMissCount={}。",
                pipelineId,
                service.getId(),
                stoppedPid,
                service.getStatus(),
                describeManagedPidSource(service),
                service.getCurrentPid(),
                service.getLastDeployment() == null ? null : service.getLastDeployment().getId(),
                service.getLastDeployment() == null ? null : service.getLastDeployment().getMonitoredPid(),
                service.getHeartbeatMissCount()
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
        service.setCurrentPid(null);
        service.setStatus(ServiceStatus.STOPPED);
        service.setActiveSince(null);
        service.setHeartbeatMissCount(0);
        service.setUpdatedAt(LocalDateTime.now());
        ServiceEntity saved = serviceRepository.save(service);
        saved.setCurrentPid(stoppedPid);
        log.info("流水线 {} 部署前旧服务停止完成。serviceId={}。", pipelineId, saved.getId());
        return saved;
    }

    public void updateFromDeployment(DeploymentEntity deployment, Long pid) {
        ServiceEntity service = serviceRepository.findFirstByPipelineId(deployment.getPipeline().getId())
                .orElseGet(ServiceEntity::new);

        Map<String, String> variables = jsonMapper.toStringMap(deployment.getVariablesJson());
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
        serviceRepository.save(service);
    }

    public ServiceEntity stop(Long id) {
        ServiceEntity service = serviceRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.SERVICE_NOT_FOUND));
        HostEntity targetHost = service.getPipeline() == null ? null : service.getPipeline().getTargetHost();
        if (service.getCurrentPid() != null) {
            stopProcess(service.getCurrentPid(), targetHost);
        }
        service.setCurrentPid(null);
        service.setStatus(ServiceStatus.STOPPED);
        service.setActiveSince(null);
        service.setHeartbeatMissCount(0);
        service.setUpdatedAt(LocalDateTime.now());
        return serviceRepository.save(service);
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
        service.setCurrentPid(pid);
        service.setStatus(ServiceStatus.RUNNING);
        service.setActiveSince(resolveProcessStartedAt(pid, targetHost).orElseGet(() -> service.getActiveSince() != null ? service.getActiveSince() : LocalDateTime.now()));
        service.setLastHeartbeatAt(LocalDateTime.now());
        service.setHeartbeatMissCount(0);
        service.setUpdatedAt(LocalDateTime.now());
        log.info("服务 {} 手动绑定进程成功，pid={}，目标主机类型={}。", service.getId(), pid, targetHost == null ? HostType.LOCAL : targetHost.getType());
        return serviceRepository.save(service);
    }

    public ServiceEntity refreshStatus(ServiceEntity service) {
        HostEntity targetHost = service.getPipeline() == null ? null : service.getPipeline().getTargetHost();
        Long pid = resolveManagedPid(service);
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
        return serviceRepository.save(service);
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
