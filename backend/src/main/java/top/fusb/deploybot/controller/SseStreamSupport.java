package top.fusb.deploybot.controller;

import top.fusb.deploybot.security.AuthContextHolder;
import top.fusb.deploybot.security.AuthenticatedUser;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

final class SseStreamSupport {
    private static final long DEFAULT_INTERVAL_MILLIS = 1200L;

    private SseStreamSupport() {
    }

    static <T> SseEmitter stream(String threadName, String eventName, Supplier<T> supplier) {
        AuthenticatedUser currentUser = AuthContextHolder.get();
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean closed = new AtomicBoolean(false);
        Thread thread = new Thread(() -> {
            Object lastPayload = null;
            try {
                AuthContextHolder.set(currentUser);
                while (!Thread.currentThread().isInterrupted() && !closed.get()) {
                    T payload = supplier.get();
                    if (!Objects.equals(payload, lastPayload)) {
                        emitter.send(SseEmitter.event().name(eventName).data(payload));
                        lastPayload = payload;
                    } else {
                        emitter.send(SseEmitter.event().comment("heartbeat"));
                    }
                    Thread.sleep(DEFAULT_INTERVAL_MILLIS);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                closed.set(true);
            } finally {
                AuthContextHolder.clear();
            }
        }, threadName);
        thread.setDaemon(true);
        Runnable closeStream = () -> {
            closed.set(true);
            thread.interrupt();
        };
        emitter.onCompletion(closeStream);
        emitter.onTimeout(closeStream);
        emitter.onError(ignored -> closeStream.run());
        thread.start();
        return emitter;
    }
}
