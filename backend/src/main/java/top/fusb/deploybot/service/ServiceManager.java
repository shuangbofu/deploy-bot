package top.fusb.deploybot.service;

import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.model.DeploymentEntity;
import top.fusb.deploybot.model.HostEntity;
import top.fusb.deploybot.model.HostType;
import top.fusb.deploybot.model.ServiceEntity;
import top.fusb.deploybot.model.ServiceStatus;
import top.fusb.deploybot.repo.ServiceRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ServiceManager {

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

    public ServiceEntity findById(Long id) {
        ServiceEntity service = serviceRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.SERVICE_NOT_FOUND));
        return refreshStatus(service);
    }

    public void updateFromDeployment(DeploymentEntity deployment, Long pid) {
        ServiceEntity service = serviceRepository.findFirstByPipelineId(deployment.getPipeline().getId())
                .orElseGet(ServiceEntity::new);

        Map<String, String> variables = jsonMapper.toStringMap(deployment.getVariablesJson());
        service.setPipeline(deployment.getPipeline());
        service.setLastDeployment(deployment);
        service.setServiceName(variables.getOrDefault("serviceName", deployment.getPipeline().getName()));
        service.setCurrentPid(pid);
        service.setStatus(isAlive(pid, deployment.getPipeline().getTargetHost()) ? ServiceStatus.RUNNING : ServiceStatus.STOPPED);
        if (service.getCreatedAt() == null) {
            service.setCreatedAt(LocalDateTime.now());
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
        service.setUpdatedAt(LocalDateTime.now());
        return serviceRepository.save(service);
    }

    public ServiceEntity refreshStatus(ServiceEntity service) {
        HostEntity targetHost = service.getPipeline() == null ? null : service.getPipeline().getTargetHost();
        boolean running = isAlive(service.getCurrentPid(), targetHost);
        service.setStatus(running ? ServiceStatus.RUNNING : ServiceStatus.STOPPED);
        if (!running) {
          service.setCurrentPid(null);
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
