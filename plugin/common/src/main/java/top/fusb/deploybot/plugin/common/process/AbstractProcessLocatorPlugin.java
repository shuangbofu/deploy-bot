package top.fusb.deploybot.plugin.common.process;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import top.fusb.deploybot.kit.TextKit;
import top.fusb.deploybot.plugin.common.deployment.AbstractManagedServiceDeploymentPlugin;
import top.fusb.deploybot.plugin.api.process.ProcessLocatorContext;
import top.fusb.deploybot.plugin.api.process.ProcessLocatorPlugin;
import top.fusb.deploybot.plugin.api.process.ProcessLocatorResult;

/**
 * 提供 PID 文件读取、命令归一化和 shell 输出解析等通用能力。
 */
public abstract class AbstractProcessLocatorPlugin implements ProcessLocatorPlugin {
    private static final Pattern REDIRECTION_PATTERN = Pattern.compile("\\s(?:\\d?>>?|>>?)");

    @Override
    public ProcessLocatorResult locate(ProcessLocatorContext context) {
        String keyword = resolvePidDiscoveryKeyword(context);
        if (TextKit.isBlank(keyword)) {
            return new ProcessLocatorResult(null, describeSource(false), "[系统] 当前没有可用于 PID 推导的命令特征。", null);
        }
        String output = context.executionBridge().runScript(context.remoteHost(), buildLookupScript(keyword));
        Long pid = extractPidFromOutput(output);
        return new ProcessLocatorResult(pid, describeSource(true), normalizeDiagnostics(output), keyword);
    }

    protected abstract String buildLookupScript(String keyword);

    protected abstract String describeSource(boolean keywordAvailable);

    protected String normalizeDiagnostics(String output) {
        if (TextKit.isBlank(output)) {
            return "[系统] 当前没有可用于 PID 推导的诊断信息。";
        }
        return output.trim();
    }

    protected String resolvePidDiscoveryKeyword(ProcessLocatorContext context) {
        String deploymentIdentityKeyword = resolveDeploymentIdentityKeyword(context);
        if (TextKit.isNotBlank(deploymentIdentityKeyword)) {
            return deploymentIdentityKeyword;
        }
        String startCommand = context.deploymentVariable("startCommand");
        if (TextKit.isNotBlank(startCommand)) {
            String processCommand = normalizeStartCommandForPidDiscovery(startCommand);
            if (TextKit.isNotBlank(processCommand)) {
                return processCommand;
            }
        }
        String jarPath = context.deploymentVariable("jarPath");
        if (TextKit.isNotBlank(jarPath)) {
            return Path.of(jarPath).getFileName().toString();
        }
        return null;
    }

    protected String resolveDeploymentIdentityKeyword(ProcessLocatorContext context) {
        String deploymentId = context.deploymentVariable("deploymentId");
        if (TextKit.isBlank(deploymentId)) {
            return null;
        }
        return "--" + AbstractManagedServiceDeploymentPlugin.DEPLOYMENT_ID_ARGUMENT_NAME + "=" + deploymentId.trim();
    }

    protected String normalizeStartCommandForPidDiscovery(String startCommand) {
        if (TextKit.isBlank(startCommand)) {
            return null;
        }
        String command = startCommand.trim();
        if (command.endsWith("&")) {
            command = command.substring(0, command.length() - 1).trim();
        }
        Matcher redirectionMatcher = REDIRECTION_PATTERN.matcher(command);
        if (redirectionMatcher.find()) {
            command = command.substring(0, redirectionMatcher.start()).trim();
        }
        while (command.startsWith("nohup ")) {
            command = command.substring("nohup ".length()).trim();
        }
        command = command.replaceAll("\\s+", " ");
        return command.isBlank() ? null : command;
    }

    protected Long extractPidFromOutput(String output) {
        if (TextKit.isBlank(output)) {
            return null;
        }
        for (String line : output.lines().toList()) {
            String normalized = line.trim();
            if (normalized.startsWith("__DEPLOYBOT_PID__")) {
                return parseNullableLong(normalized.substring("__DEPLOYBOT_PID__".length()));
            }
        }
        for (String line : output.lines().toList()) {
            String normalized = line.trim();
            if (normalized.matches("\\d+")) {
                return parseNullableLong(normalized);
            }
        }
        return null;
    }

    protected Long parseNullableLong(String value) {
        try {
            return Long.parseLong(value == null ? "" : value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    protected String resolveJarName(String keyword) {
        if (TextKit.isBlank(keyword)) {
            return null;
        }
        Matcher matcher = Pattern.compile("-jar\\s+([^\\s]+\\.jar)").matcher(keyword);
        if (matcher.find()) {
            return Path.of(matcher.group(1)).getFileName().toString();
        }
        if (keyword.endsWith(".jar")) {
            return Path.of(keyword).getFileName().toString();
        }
        return null;
    }
}
