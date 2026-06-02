package top.fusb.deploybot.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 流水线大厅变更通知器。
 * 用于唤醒大厅 SSE，避免长连接里固定短间隔轮询数据库。
 */
@Service
public class PipelineHallEventService {
    private static final long LOG_CHANGE_MIN_INTERVAL_MILLIS = 800L;

    private final Object monitor = new Object();
    private final AtomicLong version = new AtomicLong(System.currentTimeMillis());
    private final Map<Long, Long> lastLogChangeAt = new ConcurrentHashMap<>();

    public long currentVersion() {
        return version.get();
    }

    public void publishChange() {
        version.incrementAndGet();
        synchronized (monitor) {
            monitor.notifyAll();
        }
    }

    public void publishDeploymentLogChange(Long deploymentId) {
        if (deploymentId == null) {
            publishChange();
            return;
        }
        long now = System.currentTimeMillis();
        Long previous = lastLogChangeAt.put(deploymentId, now);
        if (previous == null || now - previous >= LOG_CHANGE_MIN_INTERVAL_MILLIS) {
            publishChange();
        }
    }

    public long awaitChange(long observedVersion, long timeoutMillis) throws InterruptedException {
        long current = version.get();
        if (current != observedVersion) {
            return current;
        }
        synchronized (monitor) {
            current = version.get();
            if (current == observedVersion) {
                monitor.wait(Math.max(1L, timeoutMillis));
            }
            return version.get();
        }
    }
}
