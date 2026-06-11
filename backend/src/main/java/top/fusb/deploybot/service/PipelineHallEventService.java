package top.fusb.deploybot.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 流水线大厅变更通知器。
 * 用于唤醒大厅 SSE，避免长连接里固定短间隔轮询数据库。
 */
@Service
public class PipelineHallEventService {
    private static final long LOG_CHANGE_MIN_INTERVAL_MILLIS = 800L;
    private static final int MAX_CHANGE_HISTORY_SIZE = 200;

    private final Object monitor = new Object();
    private final AtomicLong version = new AtomicLong(System.currentTimeMillis());
    private final Map<Long, Long> lastLogChangeAt = new ConcurrentHashMap<>();
    private final List<ChangeSnapshot> changeHistory = new ArrayList<>();

    public long currentVersion() {
        return version.get();
    }

    public void publishChange() {
        synchronized (monitor) {
            appendChange(new ChangeSnapshot(version.incrementAndGet(), true, Set.of()));
            monitor.notifyAll();
        }
    }

    public void publishPipelineChange(Long pipelineId) {
        if (pipelineId == null) {
            publishChange();
            return;
        }
        long now = System.currentTimeMillis();
        Long previous = lastLogChangeAt.put(pipelineId, now);
        if (previous == null || now - previous >= LOG_CHANGE_MIN_INTERVAL_MILLIS) {
            synchronized (monitor) {
                appendChange(new ChangeSnapshot(version.incrementAndGet(), false, Set.of(pipelineId)));
                monitor.notifyAll();
            }
        }
    }

    public ChangeSnapshot changesSince(long observedVersion) {
        synchronized (monitor) {
            long currentVersion = version.get();
            if (observedVersion >= currentVersion) {
                return new ChangeSnapshot(currentVersion, false, Set.of());
            }
            if (changeHistory.isEmpty() || observedVersion < changeHistory.get(0).version()) {
                return new ChangeSnapshot(currentVersion, true, Set.of());
            }
            Set<Long> pipelineIds = new HashSet<>();
            for (ChangeSnapshot change : changeHistory) {
                if (change.version() <= observedVersion) {
                    continue;
                }
                if (change.full()) {
                    return new ChangeSnapshot(currentVersion, true, Set.of());
                }
                pipelineIds.addAll(change.pipelineIds());
            }
            return new ChangeSnapshot(currentVersion, false, Set.copyOf(pipelineIds));
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

    private void appendChange(ChangeSnapshot snapshot) {
        changeHistory.add(snapshot);
        if (changeHistory.size() > MAX_CHANGE_HISTORY_SIZE) {
            changeHistory.remove(0);
        }
    }

    public record ChangeSnapshot(long version, boolean full, Set<Long> pipelineIds) {
    }
}
