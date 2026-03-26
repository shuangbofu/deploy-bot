package top.fusb.deploybot.service;

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
import java.util.List;
import java.util.Map;

@Service
public class ServiceManager {
    private static final Logger log = LoggerFactory.getLogger(ServiceManager.class);

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
        services.forEach(this::refreshStatus);
        return services;
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
