package top.fusb.deploybot.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import top.fusb.deploybot.dto.RuntimeEnvironmentInstallRequest;

/**
 * 运行环境安装异步执行器。
 * 单独拆出 Bean，避免同类内部调用导致 @Async 失效。
 */
@Service
public class RuntimeEnvironmentInstallAsyncService {
    private final RuntimeEnvironmentService runtimeEnvironmentService;

    public RuntimeEnvironmentInstallAsyncService(RuntimeEnvironmentService runtimeEnvironmentService) {
        this.runtimeEnvironmentService = runtimeEnvironmentService;
    }

    @Async
    public void installPresetAsync(String taskId, RuntimeEnvironmentInstallRequest request) {
        runtimeEnvironmentService.runInstallPresetTask(taskId, request);
    }
}
