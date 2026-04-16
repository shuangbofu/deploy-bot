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
    private static final DateTimeFormatter REMOTE_PROCESS_START_FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("EEE MMM d HH:mm:ss yyyy")
            .toFormatter(Locale.ENGLISH);
    private static final Comparator<ServiceEntity> SERVICE_HEARTBEAT_COMPARATOR =
            Comparator.comparing(ServiceEntity::getLastHeartbeatAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(ServiceEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(ServiceEntity::getId, Comparator.nullsLast(Comparator.reverseOrder()));
    private static final Comparator<ServiceEntity> RUNNING_HALL_COMPARATOR =
            Comparator.comparing(ServiceEntity::getActiveSince, Comparator.nullsLast(Comparator.reverseOrder()))
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
        return serviceRepository.findAllByStatusOrderByLastHeartbeatAtDescUpdatedAtDescIdDesc(ServiceStatus.RUNNING).stream()
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
        log.info("流水线 {} 在部署前检测到已受管服务：serviceId={}，pid={}，状态={}。", pipelineId, service.getId(), service.getCurrentPid(), service.getStatus());
        if (service.getCurrentPid() != null) {
            HostEntity targetHost = service.getPipeline() == null ? null : service.getPipeline().getTargetHost();
            stopProcess(service.getCurrentPid(), targetHost);
        }
        service.setCurrentPid(null);
        service.setStatus(ServiceStatus.STOPPED);
        service.setActiveSince(null);
        service.setUpdatedAt(LocalDateTime.now());
        ServiceEntity saved = serviceRepository.save(service);
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
        service.setUpdatedAt(LocalDateTime.now());
        return serviceRepository.save(service);
    }

    public ServiceEntity refreshStatus(ServiceEntity service) {
        HostEntity targetHost = service.getPipeline() == null ? null : service.getPipeline().getTargetHost();
        boolean running = isAlive(service.getCurrentPid(), targetHost);
        service.setStatus(running ? ServiceStatus.RUNNING : ServiceStatus.STOPPED);
        if (running) {
            if (service.getActiveSince() == null) {
                service.setActiveSince(LocalDateTime.now());
            }
            service.setLastHeartbeatAt(LocalDateTime.now());
        } else {
            service.setCurrentPid(null);
            service.setActiveSince(null);
        }
        service.setUpdatedAt(LocalDateTime.now());
        return serviceRepository.save(service);
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
            ProcessHandle.of(pid).ifPresent(handle -> {
                handle.destroy();
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                if (handle.isAlive()) {
                    handle.destroyForcibly();
                }
            });
            return;
        }
        try {
            hostService.executeRemoteScript(
                    host.getId(),
                    "kill " + pid + " >/dev/null 2>&1 || true\n" +
                            "sleep 1\n" +
                            "if kill -0 " + pid + " >/dev/null 2>&1; then kill -9 " + pid + " >/dev/null 2>&1 || true; fi\n",
                    10
            );
        } catch (Exception ex) {
            throw new BusinessException(ErrorSubCode.REMOTE_SERVICE_STOP_FAILED, ex.getMessage(), ex);
        }
    }
}
