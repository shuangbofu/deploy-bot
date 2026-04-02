package top.fusb.deploybot.service;

import top.fusb.deploybot.dto.HostRequest;
import top.fusb.deploybot.dto.HostConnectionTestResult;
import top.fusb.deploybot.dto.HostResourceSnapshot;
import top.fusb.deploybot.dto.PageResult;
import top.fusb.deploybot.exception.BusinessException;
import top.fusb.deploybot.exception.ErrorSubCode;
import top.fusb.deploybot.model.HostEntity;
import top.fusb.deploybot.model.HostSshAuthType;
import top.fusb.deploybot.model.HostType;
import top.fusb.deploybot.repo.HostRepository;
import top.fusb.deploybot.repo.PipelineRepository;
import top.fusb.deploybot.repo.RuntimeEnvironmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class HostService {
    private static final Logger log = LoggerFactory.getLogger(HostService.class);

    private final HostRepository hostRepository;
    private final PipelineRepository pipelineRepository;
    private final RuntimeEnvironmentRepository runtimeEnvironmentRepository;
    private final SystemSettingsService systemSettingsService;
    private final String defaultWorkspaceRoot;

    public HostService(
            HostRepository hostRepository,
            PipelineRepository pipelineRepository,
            RuntimeEnvironmentRepository runtimeEnvironmentRepository,
            SystemSettingsService systemSettingsService,
            @Value("${deploybot.workspace-root:./runtime}") String defaultWorkspaceRoot
    ) {
        this.hostRepository = hostRepository;
        this.pipelineRepository = pipelineRepository;
        this.runtimeEnvironmentRepository = runtimeEnvironmentRepository;
        this.systemSettingsService = systemSettingsService;
        this.defaultWorkspaceRoot = defaultWorkspaceRoot;
    }

    public List<HostEntity> findAll() {
        ensureLocalHost();
        return hostRepository.findAllByOrderByBuiltInDescTypeAscNameAsc();
    }

    public List<HostEntity> findEnabled() {
        ensureLocalHost();
        return hostRepository.findByEnabledTrueOrderByBuiltInDescTypeAscNameAsc();
    }

    public PageResult<HostEntity> findPage(int page, int pageSize, String keyword, HostType type, Boolean enabled) {
        ensureLocalHost();
        return PageResult.of(hostRepository.findAll((root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern),
                        cb.like(cb.lower(root.get("hostname")), pattern),
                        cb.like(cb.lower(root.get("workspaceRoot")), pattern)
                ));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, PageRequest.of(
                Math.max(0, page - 1),
                Math.max(1, Math.min(100, pageSize)),
                Sort.by(Sort.Order.desc("builtIn"), Sort.Order.asc("type"), Sort.Order.asc("name"))
        )));
    }

    public HostEntity findById(Long id) {
        ensureLocalHost();
        return hostRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.HOST_NOT_FOUND));
    }

    public HostEntity ensureLocalHost() {
        return hostRepository.findFirstByTypeAndBuiltInTrue(HostType.LOCAL)
                .orElseGet(() -> {
                    HostEntity host = new HostEntity();
                    host.setName("本机");
                    host.setType(HostType.LOCAL);
                    host.setDescription("部署平台所在机器，直接在本机执行部署脚本。");
                    host.setWorkspaceRoot(defaultWorkspaceRoot);
                    host.setEnabled(true);
                    host.setBuiltIn(true);
                    return hostRepository.save(host);
                });
    }

    public HostEntity save(HostRequest request, Long id) {
        HostEntity entity = id == null ? new HostEntity() : hostRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.HOST_NOT_FOUND));
        if (id == null && request.type() == HostType.LOCAL) {
            throw new BusinessException(ErrorSubCode.BUILT_IN_LOCAL_HOST_TYPE_CHANGE_FORBIDDEN, "本机由系统自动创建，不支持手动新增。");
        }
        if (Boolean.TRUE.equals(entity.getBuiltIn()) && request.type() != HostType.LOCAL) {
            throw new BusinessException(ErrorSubCode.BUILT_IN_LOCAL_HOST_TYPE_CHANGE_FORBIDDEN);
        }
        entity.setName(request.name().trim());
        entity.setType(request.type());
        entity.setDescription(request.description());
        entity.setHostname(request.hostname());
        entity.setPort(request.port());
        entity.setUsername(request.username());
        entity.setSshAuthType(request.type() == HostType.SSH ? (request.sshAuthType() == null ? HostSshAuthType.SYSTEM_KEY_PAIR : request.sshAuthType()) : null);
        entity.setSshPassword(request.type() == HostType.SSH ? request.sshPassword() : null);
        entity.setSshPrivateKey(request.type() == HostType.SSH ? request.sshPrivateKey() : null);
        entity.setSshPassphrase(request.type() == HostType.SSH ? request.sshPassphrase() : null);
        entity.setWorkspaceRoot(request.workspaceRoot());
        entity.setSshKnownHosts(request.sshKnownHosts());
        entity.setEnabled(request.enabled() == null || request.enabled());
        if (entity.getBuiltIn() == null) {
            entity.setBuiltIn(false);
        }
        return hostRepository.save(entity);
    }

    public void delete(Long id) {
        HostEntity entity = hostRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorSubCode.HOST_NOT_FOUND));
        if (Boolean.TRUE.equals(entity.getBuiltIn())) {
            throw new BusinessException(ErrorSubCode.BUILT_IN_LOCAL_HOST_DELETE_FORBIDDEN);
        }
        if (pipelineRepository.existsByTargetHostId(id)) {
            throw new BusinessException(ErrorSubCode.HOST_HAS_PIPELINES);
        }
        if (runtimeEnvironmentRepository.existsByHostId(id)) {
            throw new BusinessException(ErrorSubCode.HOST_HAS_ENVIRONMENTS);
        }
        hostRepository.delete(entity);
    }

    public HostConnectionTestResult testConnection(Long id) throws Exception {
        HostEntity host = findById(id);
        log.info("Testing connection for host {} (type={}).", host.getName(), host.getType());
        if (host.getType() == HostType.LOCAL) {
            String workspace = host.getWorkspaceRoot() == null || host.getWorkspaceRoot().isBlank() ? defaultWorkspaceRoot : host.getWorkspaceRoot().trim();
            Files.createDirectories(Path.of(workspace));
            log.info("Local host {} workspace {} is available.", host.getName(), workspace);
            return new HostConnectionTestResult(
                    true,
                    "本机工作空间可用",
                    System.getProperty("user.name"),
                    "localhost",
                    workspace
            );
        }

        Path tempDir = Files.createTempDirectory("deploybot-host-test-");
        List<String> command = buildSshCommand(host, tempDir, 8);
        log.info("Executing host connectivity check for {} with command {}.", host.getName(), command);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String workspace = host.getWorkspaceRoot() == null || host.getWorkspaceRoot().isBlank() ? "/tmp/deploy-bot/workspace" : host.getWorkspaceRoot().trim();
        try (var writer = process.outputWriter(StandardCharsets.UTF_8)) {
            writer.write("set -e\n");
            writer.write("mkdir -p \"" + workspace.replace("\"", "\\\"") + "\"\n");
            writer.write("test -w \"" + workspace.replace("\"", "\\\"") + "\"\n");
            writer.write("printf '__DEPLOYBOT_USER__%s\\n' \"$(whoami)\"\n");
            writer.write("printf '__DEPLOYBOT_HOST__%s\\n' \"$(hostname)\"\n");
            writer.flush();
        }

        String output;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().reduce("", (left, right) -> left + right + "\n");
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.warn("Host connectivity check failed for {} with exit code {}.", host.getName(), exitCode);
            return new HostConnectionTestResult(false, output.isBlank() ? "连接失败" : output.trim(), null, null, workspace);
        }

        String remoteUser = extractValue(output, "__DEPLOYBOT_USER__");
        String remoteHost = extractValue(output, "__DEPLOYBOT_HOST__");
        log.info("Host connectivity check succeeded for {}. remoteUser={}, remoteHost={}", host.getName(), remoteUser, remoteHost);
        return new HostConnectionTestResult(true, "连接成功", remoteUser, remoteHost, workspace);
    }

    public String executeRemoteScript(Long id, String script, int timeoutSeconds) throws Exception {
        HostEntity host = findById(id);
        if (host.getType() != HostType.SSH) {
            throw new BusinessException(ErrorSubCode.SSH_ONLY_REMOTE_SCRIPT);
        }

        Path tempDir = Files.createTempDirectory("deploybot-host-exec-");
        List<String> command = buildSshCommand(host, tempDir, timeoutSeconds);
        log.info("Executing remote script on host {} with timeout {}s.", host.getName(), timeoutSeconds);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try (var writer = process.outputWriter(StandardCharsets.UTF_8)) {
            writer.write(script);
            writer.flush();
        }
        String output;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().reduce("", (left, right) -> left + right + "\n");
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.warn("主机 {} 的远程脚本执行失败，退出码={}。", host.getName(), exitCode);
            throw new BusinessException(ErrorSubCode.REMOTE_EXECUTION_FAILED, output.isBlank() ? null : output.trim());
        }
        log.info(
                "主机 {} 的远程脚本执行成功，输出预览={}",
                host.getName(),
                previewOutput(output)
        );
        return output;
    }

    /**
     * 采集主机当前资源使用情况，既用于主机管理页预览，也用于控制台概览。
     */
    public HostResourceSnapshot previewResources(Long id) throws Exception {
        HostEntity host = findById(id);
        String workspace = host.getWorkspaceRoot() == null || host.getWorkspaceRoot().isBlank()
                ? defaultWorkspaceRoot
                : host.getWorkspaceRoot().trim();
        String script = buildResourcePreviewScript(workspace);
        String output;
        if (host.getType() == HostType.LOCAL) {
            Path tempDir = Files.createTempDirectory("deploybot-host-resource-");
            Process process = new ProcessBuilder("bash", "-lc", script)
                    .directory(tempDir.toFile())
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new BusinessException(ErrorSubCode.LOCAL_RESOURCE_PREVIEW_FAILED, output.isBlank() ? null : output);
            }
        } else {
            output = executeRemoteScript(id, script, 10);
        }
        return mapResourceSnapshot(host, workspace, output);
    }

    private List<String> buildSshCommand(HostEntity host, Path sshDir, int timeoutSeconds) throws Exception {
        var settings = systemSettingsService.get();
        Files.createDirectories(sshDir);
        Path privateKey = sshDir.resolve("id_host");
        Path knownHosts = sshDir.resolve("known_hosts");
        HostSshAuthType authType = host.getSshAuthType() == null ? HostSshAuthType.SYSTEM_KEY_PAIR : host.getSshAuthType();

        String keyContent = null;
        if (authType == HostSshAuthType.PRIVATE_KEY) {
            keyContent = host.getSshPrivateKey();
        } else if (authType == HostSshAuthType.SYSTEM_KEY_PAIR) {
            keyContent = settings.getHostSshPrivateKey();
        }
        if (keyContent != null && !keyContent.isBlank()) {
            Files.writeString(privateKey, keyContent.replace("\r\n", "\n").trim() + "\n", StandardCharsets.UTF_8);
            try {
                Files.setPosixFilePermissions(privateKey, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            } catch (Exception ignored) {
            }
        }
        if (host.getSshKnownHosts() != null && !host.getSshKnownHosts().isBlank()) {
            Files.writeString(knownHosts, host.getSshKnownHosts().trim() + "\n", StandardCharsets.UTF_8);
        }

        java.util.List<String> command = new java.util.ArrayList<>();
        if (authType == HostSshAuthType.PASSWORD) {
            command.add("sshpass");
            command.add("-p");
            command.add(host.getSshPassword() == null ? "" : host.getSshPassword());
        }
        command.add("ssh");
        command.add("-o");
        command.add(authType == HostSshAuthType.PASSWORD ? "BatchMode=no" : "BatchMode=yes");
        command.add("-o");
        command.add("ConnectTimeout=" + timeoutSeconds);
        if (Files.exists(privateKey)) {
            command.add("-i");
            command.add(privateKey.toAbsolutePath().toString());
            command.add("-o");
            command.add("IdentitiesOnly=yes");
        }
        if (host.getPort() != null) {
            command.add("-p");
            command.add(String.valueOf(host.getPort()));
        }
        if (Files.exists(knownHosts)) {
            command.add("-o");
            command.add("StrictHostKeyChecking=yes");
            command.add("-o");
            command.add("UserKnownHostsFile=" + knownHosts.toAbsolutePath());
        } else {
            command.add("-o");
            command.add("StrictHostKeyChecking=no");
        }
        command.add((host.getUsername() == null || host.getUsername().isBlank()) ? host.getHostname() : host.getUsername().trim() + "@" + host.getHostname().trim());
        command.add("bash -s");
        log.info("已构建主机 {} 的 SSH 命令，认证方式={}，超时时间={}秒。", host.getName(), authType, timeoutSeconds);
        return command;
    }

    private boolean matchesKeyword(HostEntity item, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase(Locale.ROOT);
        return contains(item.getName(), normalized)
                || contains(item.getDescription(), normalized)
                || contains(item.getHostname(), normalized)
                || contains(item.getWorkspaceRoot(), normalized);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String extractValue(String output, String prefix) {
        return output.lines()
                .filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()))
                .findFirst()
                .orElse(null);
    }

    private String buildResourcePreviewScript(String workspaceRoot) {
        String escapedWorkspace = workspaceRoot.replace("\"", "\\\"");
        return """
                set -e
                WORKSPACE="__DEPLOYBOT_WORKSPACE__"
                mkdir -p "$WORKSPACE"
                OS_TYPE="$(uname -s 2>/dev/null || echo UNKNOWN)"
                CPU_CORES="$(getconf _NPROCESSORS_ONLN 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 0)"
                CPU_USAGE=""
                LOAD_AVG=""
                if [ "$OS_TYPE" = "Linux" ]; then
                  CPU_USAGE="$(top -bn1 2>/dev/null | awk -F'[, ]+' '/Cpu\\(s\\)/ {for (i=1; i<=NF; i++) if ($i ~ /id/) {printf \"%d\", 100 - $(i-1); exit}}')"
                  LOAD_AVG="$(awk '{print $1}' /proc/loadavg 2>/dev/null || echo '')"
                  MEM_TOTAL_KB="$(awk '/MemTotal/ {print $2}' /proc/meminfo)"
                  MEM_AVAIL_KB="$(awk '/MemAvailable/ {print $2}' /proc/meminfo)"
                  MEM_USED_MB="$(( (MEM_TOTAL_KB - MEM_AVAIL_KB) / 1024 ))"
                  MEM_TOTAL_MB="$(( MEM_TOTAL_KB / 1024 ))"
                elif [ "$OS_TYPE" = "Darwin" ]; then
                  CPU_USAGE="$(top -l 1 -n 0 2>/dev/null | awk -F'[:,%]' '/CPU usage/ {gsub(/ /, \"\", $3); print int($3); exit}')"
                  LOAD_AVG="$(sysctl -n vm.loadavg 2>/dev/null | awk '{gsub(/[{}]/, \"\", $0); print $1}' || echo '')"
                  MEM_TOTAL_BYTES="$(sysctl -n hw.memsize 2>/dev/null || echo 0)"
                  PAGE_SIZE="$(sysctl -n hw.pagesize 2>/dev/null || echo 4096)"
                  FREE_PAGES="$(vm_stat 2>/dev/null | awk '/Pages free/ {gsub("\\\\.", "", $3); print $3}' || echo 0)"
                  SPEC_PAGES="$(vm_stat 2>/dev/null | awk '/Pages speculative/ {gsub("\\\\.", "", $3); print $3}' || echo 0)"
                  MEM_TOTAL_MB="$(( MEM_TOTAL_BYTES / 1024 / 1024 ))"
                  MEM_FREE_BYTES="$(( (FREE_PAGES + SPEC_PAGES) * PAGE_SIZE ))"
                  MEM_USED_MB="$(( MEM_TOTAL_MB - (MEM_FREE_BYTES / 1024 / 1024) ))"
                else
                  MEM_TOTAL_MB=0
                  MEM_USED_MB=0
                fi
                DISK_LINE="$(df -Pk "$WORKSPACE" | tail -n 1)"
                DISK_TOTAL_KB="$(echo "$DISK_LINE" | awk '{print $2}')"
                DISK_USED_KB="$(echo "$DISK_LINE" | awk '{print $3}')"
                DISK_USE_PERCENT="$(echo "$DISK_LINE" | awk '{print $5}' | tr -d '%')"
                printf '__DEPLOYBOT_OS__%s\\n' "$OS_TYPE"
                printf '__DEPLOYBOT_CPU__%s\\n' "$CPU_CORES"
                printf '__DEPLOYBOT_CPU_USAGE__%s\\n' "$CPU_USAGE"
                printf '__DEPLOYBOT_LOAD__%s\\n' "$LOAD_AVG"
                printf '__DEPLOYBOT_MEM_TOTAL_MB__%s\\n' "$MEM_TOTAL_MB"
                printf '__DEPLOYBOT_MEM_USED_MB__%s\\n' "$MEM_USED_MB"
                printf '__DEPLOYBOT_DISK_TOTAL_GB__%s\\n' "$(( DISK_TOTAL_KB / 1024 / 1024 ))"
                printf '__DEPLOYBOT_DISK_USED_GB__%s\\n' "$(( DISK_USED_KB / 1024 / 1024 ))"
                printf '__DEPLOYBOT_DISK_PERCENT__%s\\n' "$DISK_USE_PERCENT"
                echo "__DEPLOYBOT_PREVIEW__"
                echo "系统类型: $OS_TYPE"
                echo "工作空间: $WORKSPACE"
                echo "CPU 核数: $CPU_CORES"
                echo "CPU 使用率: ${CPU_USAGE:-未知}%"
                echo "1 分钟负载: ${LOAD_AVG:-未知}"
                echo "内存: ${MEM_USED_MB}MB / ${MEM_TOTAL_MB}MB"
                echo "磁盘: $(( DISK_USED_KB / 1024 / 1024 ))GB / $(( DISK_TOTAL_KB / 1024 / 1024 ))GB (${DISK_USE_PERCENT}%)"
                """.replace("__DEPLOYBOT_WORKSPACE__", escapedWorkspace);
    }

    private HostResourceSnapshot mapResourceSnapshot(HostEntity host, String workspaceRoot, String output) {
        String preview = output.contains("__DEPLOYBOT_PREVIEW__")
                ? output.substring(output.indexOf("__DEPLOYBOT_PREVIEW__") + "__DEPLOYBOT_PREVIEW__".length()).trim()
                : output.trim();

        Long memoryTotalMb = parseLong(extractValue(output, "__DEPLOYBOT_MEM_TOTAL_MB__"));
        Long memoryUsedMb = parseLong(extractValue(output, "__DEPLOYBOT_MEM_USED_MB__"));
        Long diskTotalGb = parseLong(extractValue(output, "__DEPLOYBOT_DISK_TOTAL_GB__"));
        Long diskUsedGb = parseLong(extractValue(output, "__DEPLOYBOT_DISK_USED_GB__"));

        return new HostResourceSnapshot(
                host.getId(),
                host.getName(),
                LocalDateTime.now(),
                extractValue(output, "__DEPLOYBOT_OS__"),
                workspaceRoot,
                parseInteger(extractValue(output, "__DEPLOYBOT_CPU__")),
                parseInteger(extractValue(output, "__DEPLOYBOT_CPU_USAGE__")),
                parseDouble(extractValue(output, "__DEPLOYBOT_LOAD__")),
                memoryTotalMb,
                memoryUsedMb,
                percent(memoryUsedMb, memoryTotalMb),
                diskTotalGb,
                diskUsedGb,
                parseInteger(extractValue(output, "__DEPLOYBOT_DISK_PERCENT__")),
                preview
        );
    }

    private Integer percent(Long used, Long total) {
        if (used == null || total == null || total <= 0) {
            return null;
        }
        return (int) Math.round(used * 100.0 / total);
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Integer.parseInt(value.trim());
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.parseLong(value.trim());
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim()
                .replace(",", " ")
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
        String firstToken = normalized.split(" ")[0];
        return Double.parseDouble(firstToken);
    }

    private String previewOutput(String output) {
        if (output == null || output.isBlank()) {
            return "<empty>";
        }
        String normalized = output.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 240) {
            return normalized;
        }
        return normalized.substring(0, 240) + "...";
    }
}
