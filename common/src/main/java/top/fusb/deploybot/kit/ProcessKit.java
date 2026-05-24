package top.fusb.deploybot.kit;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 本地进程执行工具，统一管理 {@link ProcessBuilder} 创建、标准输入写入、输出流消费和退出码等待。
 */
public final class ProcessKit {

    private static final int BUFFER_SIZE = 2048;

    private ProcessKit() {
    }

    /**
     * 创建已合并标准错误流的进程构造器。
     *
     * @param command 命令及参数
     * @return 已设置 {@code redirectErrorStream(true)} 的进程构造器
     */
    public static ProcessBuilder mergedBuilder(String... command) {
        return mergedBuilder(List.of(command));
    }

    /**
     * 创建已合并标准错误流的进程构造器。
     *
     * @param command 命令及参数
     * @return 已设置 {@code redirectErrorStream(true)} 的进程构造器
     */
    public static ProcessBuilder mergedBuilder(List<String> command) {
        return new ProcessBuilder(command).redirectErrorStream(true);
    }

    /**
     * 创建通过 bash 执行脚本文本的进程构造器。
     *
     * @param script 脚本文本
     * @return 已设置 {@code redirectErrorStream(true)} 的 bash 进程构造器
     */
    public static ProcessBuilder bash(String script) {
        return mergedBuilder("bash", "-lc", script == null ? "" : script);
    }

    /**
     * 执行命令并完整捕获输出。
     *
     * @param processBuilder 进程构造器
     * @return 进程执行结果
     * @throws IOException 进程启动或读取输出失败时抛出
     * @throws InterruptedException 等待进程结束时线程被中断则抛出
     */
    public static ProcessResult runAndCapture(ProcessBuilder processBuilder) throws IOException, InterruptedException {
        return runAndCapture(processBuilder, null);
    }

    /**
     * 执行命令并完整捕获输出，可选超时控制。
     *
     * @param processBuilder 进程构造器
     * @param timeout 最大等待时长；为空则一直等待
     * @return 进程执行结果
     * @throws IOException 进程启动或读取输出失败时抛出
     * @throws InterruptedException 等待进程结束时线程被中断则抛出
     */
    public static ProcessResult runAndCapture(ProcessBuilder processBuilder, Duration timeout) throws IOException, InterruptedException {
        return runAndCaptureWithStdin(processBuilder, null, timeout);
    }

    /**
     * 执行命令、写入标准输入并完整捕获输出。
     *
     * @param processBuilder 进程构造器
     * @param stdin 写入进程标准输入的内容；为空则关闭输入流
     * @param timeout 最大等待时长；为空则一直等待
     * @return 进程执行结果
     * @throws IOException 进程启动、写入输入或读取输出失败时抛出
     * @throws InterruptedException 等待进程结束时线程被中断则抛出
     */
    public static ProcessResult runAndCaptureWithStdin(ProcessBuilder processBuilder, String stdin, Duration timeout)
            throws IOException, InterruptedException {
        if (timeout != null) {
            return runAndCaptureWithTimeout(processBuilder, stdin, timeout);
        }
        StringBuilder output = new StringBuilder();
        int exitCode = runStreaming(
                processBuilder,
                stdin,
                (buffer, offset, length) -> output.append(buffer, offset, length),
                exception -> false,
                process -> {
                },
                timeout
        );
        return new ProcessResult(exitCode, output.toString(), exitCode == -1);
    }

    private static ProcessResult runAndCaptureWithTimeout(ProcessBuilder processBuilder, String stdin, Duration timeout)
            throws IOException, InterruptedException {
        Process process = processBuilder.start();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> outputFuture = executor.submit(captureOutputTask(process));
        writeStdin(process, stdin);
        boolean finished = process.waitFor(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            String output = readFutureQuietly(outputFuture);
            executor.shutdownNow();
            return new ProcessResult(-1, output, true);
        }
        String output = readFutureQuietly(outputFuture);
        executor.shutdownNow();
        return new ProcessResult(process.exitValue(), output, false);
    }

    private static Callable<String> captureOutputTask(Process process) {
        return () -> {
            StringBuilder output = new StringBuilder();
            consumeOutput(
                    process,
                    (buffer, offset, length) -> output.append(buffer, offset, length),
                    exception -> false
            );
            return output.toString();
        };
    }

    private static String readFutureQuietly(Future<String> future) {
        try {
            return future.get(1, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            future.cancel(true);
            return "";
        }
    }

    /**
     * 执行命令并把输出按字符块交给调用方消费。
     *
     * @param processBuilder 进程构造器
     * @param stdin 写入进程标准输入的内容；为空则不写入
     * @param outputConsumer 输出消费器
     * @param interruptionHandler 输出流读取异常处理器；返回 true 表示吞掉该异常并结束读取
     * @param processConsumer 进程启动后的回调，常用于记录当前运行进程
     * @return 进程退出码
     * @throws IOException 进程启动、写入输入或读取输出失败时抛出
     * @throws InterruptedException 等待进程结束时线程被中断则抛出
     */
    public static int runStreaming(
            ProcessBuilder processBuilder,
            String stdin,
            OutputConsumer outputConsumer,
            ReadExceptionHandler interruptionHandler,
            StartedProcessConsumer processConsumer
    ) throws IOException, InterruptedException {
        return runStreaming(processBuilder, stdin, outputConsumer, interruptionHandler, processConsumer, null);
    }

    /**
     * 执行命令并把输出按字符块交给调用方消费，可选超时控制。
     *
     * @param processBuilder 进程构造器
     * @param stdin 写入进程标准输入的内容；为空则不写入
     * @param outputConsumer 输出消费器
     * @param interruptionHandler 输出流读取异常处理器；返回 true 表示吞掉该异常并结束读取
     * @param processConsumer 进程启动后的回调，常用于记录当前运行进程
     * @param timeout 最大等待时长；为空则一直等待
     * @return 进程退出码
     * @throws IOException 进程启动、写入输入或读取输出失败时抛出
     * @throws InterruptedException 等待进程结束时线程被中断则抛出
     */
    public static int runStreaming(
            ProcessBuilder processBuilder,
            String stdin,
            OutputConsumer outputConsumer,
            ReadExceptionHandler interruptionHandler,
            StartedProcessConsumer processConsumer,
            Duration timeout
    ) throws IOException, InterruptedException {
        Process process = processBuilder.start();
        processConsumer.started(process);
        writeStdin(process, stdin);
        consumeOutput(process, outputConsumer, interruptionHandler);
        return waitFor(process, timeout);
    }

    /**
     * 向进程标准输入写入文本。
     *
     * @param process 目标进程
     * @param stdin 标准输入内容；为空则不写入并关闭输入流
     * @throws IOException 写入失败时抛出
     */
    public static void writeStdin(Process process, String stdin) throws IOException {
        if (stdin == null) {
            process.getOutputStream().close();
            return;
        }
        try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
            writer.write(stdin);
            writer.flush();
        }
    }

    /**
     * 按 UTF-8 消费进程合并后的输出流。
     *
     * @param process 目标进程
     * @param outputConsumer 输出消费器
     * @param interruptionHandler 输出流读取异常处理器；返回 true 表示吞掉该异常并结束读取
     * @throws IOException 读取输出失败且异常未被处理器吞掉时抛出
     */
    public static void consumeOutput(
            Process process,
            OutputConsumer outputConsumer,
            ReadExceptionHandler interruptionHandler
    ) throws IOException {
        try (InputStream stream = process.getInputStream();
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            char[] buffer = new char[BUFFER_SIZE];
            int length;
            while (true) {
                try {
                    length = reader.read(buffer);
                } catch (IOException ex) {
                    if (interruptionHandler != null && interruptionHandler.handled(ex)) {
                        break;
                    }
                    throw ex;
                }
                if (length == -1) {
                    break;
                }
                outputConsumer.accept(buffer, 0, length);
            }
        }
    }

    /**
     * 等待进程结束，并在超时时强制销毁进程。
     *
     * @param process 目标进程
     * @param timeout 最大等待时长；为空则一直等待
     * @return 进程退出码；超时时返回 {@code -1}
     * @throws InterruptedException 等待进程结束时线程被中断则抛出
     */
    public static int waitFor(Process process, Duration timeout) throws InterruptedException {
        if (timeout == null) {
            return process.waitFor();
        }
        boolean finished = process.waitFor(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
        if (finished) {
            return process.exitValue();
        }
        process.destroyForcibly();
        return -1;
    }

    /**
     * 进程执行结果。
     *
     * @param exitCode 退出码；超时被强杀时为 {@code -1}
     * @param output 合并后的输出文本
     * @param timedOut 是否超时
     */
    public record ProcessResult(int exitCode, String output, boolean timedOut) {
    }

    /**
     * 进程输出消费回调。
     */
    @FunctionalInterface
    public interface OutputConsumer {

        /**
         * 消费一段输出字符。
         *
         * @param buffer 输出缓冲区
         * @param offset 本次输出起始位置
         * @param length 本次输出长度
         * @throws IOException 消费失败时抛出
         */
        void accept(char[] buffer, int offset, int length) throws IOException;
    }

    /**
     * 输出流读取异常处理回调。
     */
    @FunctionalInterface
    public interface ReadExceptionHandler {

        /**
         * 判断读取异常是否已被业务处理。
         *
         * @param exception 读取输出流时抛出的异常
         * @return 返回 {@code true} 表示吞掉异常并结束读取，返回 {@code false} 表示继续抛出
         */
        boolean handled(IOException exception);
    }

    /**
     * 进程启动后的回调。
     */
    @FunctionalInterface
    public interface StartedProcessConsumer {

        /**
         * 接收已启动的进程。
         *
         * @param process 已启动的进程
         */
        void started(Process process);
    }
}
