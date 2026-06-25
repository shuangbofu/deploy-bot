package top.fusb.deploybot.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Deploy Bot 命令行入口，面向 Agent/脚本封装常用 HTTP API。
 */
public class DeployBotCli {
    private static final int SUCCESS = 0;
    private static final int FAILURE = 1;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public static void main(String[] args) {
        int exitCode = new DeployBotCli().run(args);
        if (exitCode != SUCCESS) {
            System.exit(exitCode);
        }
    }

    /**
     * 执行 CLI 命令。
     *
     * @param args 命令行参数
     * @return 进程退出码
     */
    int run(String[] args) {
        try {
            if (args.length == 0 || "help".equals(args[0]) || "--help".equals(args[0]) || "-h".equals(args[0])) {
                printHelp();
                return SUCCESS;
            }
            dispatch(args);
            return SUCCESS;
        } catch (CliException ex) {
            System.err.println(ex.getMessage());
            return FAILURE;
        } catch (Exception ex) {
            System.err.println("Deploy Bot CLI 执行失败：" + ex.getMessage());
            return FAILURE;
        }
    }

    /**
     * 分发一级命令。
     *
     * @param args 命令行参数
     * @throws Exception 调用 HTTP API 或读取文件失败时抛出
     */
    private void dispatch(String[] args) throws Exception {
        String command = args[0];
        switch (command) {
            case "request" -> rawRequest(args);
            case "projects" -> projects(args);
            case "hosts" -> hosts(args);
            case "runtimes" -> runtimes(args);
            case "plugins" -> plugins(args);
            case "templates" -> templates(args);
            case "pipelines" -> pipelines(args);
            case "deployments" -> deployments(args);
            default -> throw new CliException("未知命令：" + command + "\n执行 deploy-bot help 查看用法。");
        }
    }

    private void projects(String[] args) throws Exception {
        requireSubCommand(args, "projects");
        switch (args[1]) {
            case "list" -> {
                List<String> query = pageQuery(args);
                addQuery(query, "keyword", optionValue(args, "--keyword"));
                addQuery(query, "gitAuthType", optionValue(args, "--git-auth-type"));
                printApi("GET", "/projects/page" + queryString(query), null);
            }
            case "create" -> printApi("POST", "/projects", readBodyArg(args, 2));
            case "update" -> {
                requireArgCount(args, 4, "projects update <id> <json|@file|->");
                printApi("PUT", "/projects/" + args[2], readBody(args[3]));
            }
            default -> throw new CliException("未知 projects 子命令：" + args[1]);
        }
    }

    private void hosts(String[] args) throws Exception {
        requireSubCommand(args, "hosts");
        if (!"list".equals(args[1])) {
            throw new CliException("未知 hosts 子命令：" + args[1]);
        }
        List<String> query = pageQuery(args);
        addQuery(query, "keyword", optionValue(args, "--keyword"));
        addQuery(query, "type", optionValue(args, "--type"));
        if (!hasFlag(args, "--all")) {
            addQuery(query, "enabled", "true");
        }
        printApi("GET", "/hosts/page" + queryString(query), null);
    }

    private void runtimes(String[] args) throws Exception {
        requireSubCommand(args, "runtimes");
        if (!"list".equals(args[1])) {
            throw new CliException("未知 runtimes 子命令：" + args[1]);
        }
        List<String> query = new ArrayList<>();
        addQuery(query, "hostId", optionValue(args, "--host-id"));
        addQuery(query, "type", optionValue(args, "--type"));
        printApi("GET", "/runtime-environments" + queryString(query), null);
    }

    private void plugins(String[] args) throws Exception {
        requireSubCommand(args, "plugins");
        switch (args[1]) {
            case "list" -> printApi("GET", "/deployment-plugins", null);
            case "shell-variables" -> printApi("GET", "/deployment-plugins/shell-variables", null);
            default -> throw new CliException("未知 plugins 子命令：" + args[1]);
        }
    }

    private void templates(String[] args) throws Exception {
        requireSubCommand(args, "templates");
        switch (args[1]) {
            case "list" -> {
                List<String> query = pageQuery(args);
                addQuery(query, "keyword", optionValue(args, "--keyword"));
                addQuery(query, "templateType", optionValue(args, "--template-type"));
                addQuery(query, "pluginId", optionValue(args, "--plugin-id"));
                addQuery(query, "monitorProcess", optionValue(args, "--monitor-process"));
                printApi("GET", "/templates/page" + queryString(query), null);
            }
            case "create" -> printApi("POST", "/templates", readBodyArg(args, 2));
            case "update" -> {
                requireArgCount(args, 4, "templates update <id> <json|@file|->");
                printApi("PUT", "/templates/" + args[2], readBody(args[3]));
            }
            default -> throw new CliException("未知 templates 子命令：" + args[1]);
        }
    }

    private void pipelines(String[] args) throws Exception {
        requireSubCommand(args, "pipelines");
        switch (args[1]) {
            case "list" -> {
                List<String> query = pageQuery(args);
                addQuery(query, "keyword", optionValue(args, "--keyword"));
                addQuery(query, "projectId", optionValue(args, "--project-id"));
                addQuery(query, "templateId", optionValue(args, "--template-id"));
                addQuery(query, "hostId", optionValue(args, "--host-id"));
                addQuery(query, "tags", optionValue(args, "--tags"));
                printApi("GET", "/pipelines/page" + queryString(query), null);
            }
            case "detail" -> {
                requireArgCount(args, 3, "pipelines detail <id>");
                printApi("GET", "/pipelines/" + args[2], null);
            }
            case "create" -> printApi("POST", "/pipelines", readBodyArg(args, 2));
            case "apply" -> applyPipeline(args);
            case "update" -> {
                requireArgCount(args, 4, "pipelines update <id> <json|@file|->");
                printApi("PUT", "/pipelines/" + args[2], readBody(args[3]));
            }
            case "plugin-plan" -> {
                requireArgCount(args, 3, "pipelines plugin-plan <id>");
                printApi("GET", "/pipelines/" + args[2] + "/plugin-plan", null);
            }
            default -> throw new CliException("未知 pipelines 子命令：" + args[1]);
        }
    }

    private void applyPipeline(String[] args) throws Exception {
        String id = optionValue(args, "--id");
        String bodyArg = lastPositional(args, 2);
        if (bodyArg == null) {
            throw new CliException("参数不足，用法：pipelines apply [--id <id>] <json|@file|->");
        }
        if (id == null || id.isBlank()) {
            printApi("POST", "/pipelines", readBody(bodyArg));
            return;
        }
        printApi("PUT", "/pipelines/" + id, readBody(bodyArg));
    }

    private void deployments(String[] args) throws Exception {
        requireSubCommand(args, "deployments");
        switch (args[1]) {
            case "precheck" -> printApi("POST", "/deployments/precheck", readBodyArg(args, 2));
            case "run" -> printApi("POST", "/deployments", readBodyArg(args, 2));
            case "detail" -> {
                requireArgCount(args, 3, "deployments detail <id>");
                printApi("GET", "/deployments/" + args[2], null);
            }
            case "logs" -> deploymentLogs(args);
            case "stop" -> {
                requireArgCount(args, 3, "deployments stop <id>");
                printApi("POST", "/deployments/" + args[2] + "/stop", "{}");
            }
            case "rollback" -> {
                requireArgCount(args, 3, "deployments rollback <id>");
                printApi("POST", "/deployments/" + args[2] + "/rollback", "{}");
            }
            default -> throw new CliException("未知 deployments 子命令：" + args[1]);
        }
    }

    private void deploymentLogs(String[] args) throws Exception {
        requireArgCount(args, 3, "deployments logs <id> [--follow] [--offset <n>]");
        if (hasFlag(args, "--follow")) {
            long offset = parseLong(optionValue(args, "--offset"), 0L);
            streamLogs(args[2], offset);
            return;
        }
        JsonNode data = callApi("GET", "/deployments/" + args[2] + "/log", null);
        System.out.print(data.path("content").asText(""));
    }

    private void rawRequest(String[] args) throws Exception {
        requireArgCount(args, 3, "request <method> <path> [json|@file|-]");
        String body = args.length >= 4 ? readBody(args[3]) : null;
        printApi(args[1].toUpperCase(Locale.ROOT), args[2], body);
    }

    private void printApi(String method, String path, String body) throws Exception {
        JsonNode data = callApi(method, path, body);
        if (data == null || data.isMissingNode() || data.isNull()) {
            return;
        }
        if (data.isTextual()) {
            System.out.println(data.asText());
            return;
        }
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data));
    }

    /**
     * 调用 Deploy Bot API 并返回统一响应中的 data。
     *
     * @param method HTTP 方法
     * @param path API 路径，可以带查询参数
     * @param body JSON 请求体，GET 请求传 null
     * @return 统一响应中的 data 节点
     * @throws Exception 请求失败或 API 返回失败时抛出
     */
    private JsonNode callApi(String method, String path, String body) throws Exception {
        HttpRequest request = buildRequest(method, path, body);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String responseBody = response.body() == null ? "" : response.body();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new CliException("HTTP " + response.statusCode() + "：" + extractErrorMessage(responseBody));
        }
        if (responseBody.isBlank()) {
            return objectMapper.nullNode();
        }
        JsonNode root = objectMapper.readTree(responseBody);
        if (root.has("success")) {
            if (!root.path("success").asBoolean(false)) {
                throw new CliException(extractApiFailure(root));
            }
            return root.path("data");
        }
        return root;
    }

    private void streamLogs(String deploymentId, long offset) throws Exception {
        HttpRequest request = buildRequest("GET", "/deployments/" + deploymentId + "/log/stream?offset=" + offset, null);
        HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            throw new CliException("HTTP " + response.statusCode() + "：" + extractErrorMessage(body));
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String event = "";
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("event:")) {
                    event = line.substring("event:".length()).trim();
                } else if (line.startsWith("data:") && "log".equals(event)) {
                    printLogEvent(line.substring("data:".length()).trim());
                }
            }
        }
    }

    private void printLogEvent(String data) throws IOException {
        JsonNode node = objectMapper.readTree(data);
        String contentBase64 = node.path("contentBase64").asText("");
        if (!contentBase64.isBlank()) {
            System.out.print(new String(Base64.getDecoder().decode(contentBase64), StandardCharsets.UTF_8));
        }
    }

    private HttpRequest buildRequest(String method, String path, String body) {
        String token = requiredEnv("DEPLOY_BOT_TOKEN");
        HttpRequest.Builder builder = HttpRequest.newBuilder(apiUri(path))
                .timeout(Duration.ofMinutes(5))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json");
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        return builder.build();
    }

    private URI apiUri(String path) {
        String baseUrl = requiredEnv("DEPLOY_BOT_BASE_URL").trim();
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String apiBase = normalizedBase.endsWith("/api") ? normalizedBase : normalizedBase + "/api";
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return URI.create(apiBase + normalizedPath);
    }

    private String readBodyArg(String[] args, int index) throws IOException {
        requireArgCount(args, index + 1, args[0] + " " + args[1] + " <json|@file|->");
        return readBody(args[index]);
    }

    private String readBody(String value) throws IOException {
        if ("-".equals(value)) {
            return new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (value.startsWith("@")) {
            return Files.readString(Path.of(value.substring(1)), StandardCharsets.UTF_8);
        }
        return value;
    }

    private String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new CliException("缺少环境变量：" + name);
        }
        return value;
    }

    private void requireSubCommand(String[] args, String command) {
        if (args.length < 2) {
            throw new CliException("缺少子命令：" + command);
        }
    }

    private void requireArgCount(String[] args, int min, String usage) {
        if (args.length < min) {
            throw new CliException("参数不足，用法：" + usage);
        }
    }

    private boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private String optionValue(String[] args, String option) {
        for (int i = 0; i < args.length - 1; i++) {
            if (option.equals(args[i])) {
                return args[i + 1];
            }
        }
        return null;
    }

    private String lastPositional(String[] args, int fromIndex) {
        String value = null;
        for (int i = fromIndex; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                i++;
                continue;
            }
            value = args[i];
        }
        return value;
    }

    private long parseLong(String value, long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new CliException("数字参数不合法：" + value);
        }
    }

    /**
     * 构建分页查询参数，避免 Agent 场景默认拉取全量资源。
     *
     * @param args 命令行参数
     * @return 包含 page 和 pageSize 的查询参数列表
     */
    private List<String> pageQuery(String[] args) {
        List<String> query = new ArrayList<>();
        String pageSize = optionValue(args, "--page-size");
        if (pageSize == null || pageSize.isBlank()) {
            pageSize = optionValue(args, "--limit");
        }
        addQuery(query, "page", String.valueOf(parseLong(optionValue(args, "--page"), 1L)));
        addQuery(query, "pageSize", String.valueOf(parseLong(pageSize, 20L)));
        return query;
    }

    private void addQuery(List<String> query, String name, String value) {
        if (value != null && !value.isBlank()) {
            query.add(URLEncoder.encode(name, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
    }

    private String queryString(List<String> query) {
        return query.isEmpty() ? "" : "?" + String.join("&", query);
    }

    private String extractErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "请求失败";
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.has("success")) {
                return extractApiFailure(root);
            }
            return root.toString();
        } catch (Exception ex) {
            return body;
        }
    }

    private String extractApiFailure(JsonNode root) {
        String subMessage = root.path("subMessage").asText("");
        if (!subMessage.isBlank()) {
            return subMessage;
        }
        String message = root.path("message").asText("");
        return message.isBlank() ? "请求失败" : message;
    }

    private void printHelp() {
        System.out.println("""
                Deploy Bot CLI

                环境变量：
                  DEPLOY_BOT_BASE_URL  例如 https://deploy.example.com 或 http://localhost:8080
                  DEPLOY_BOT_TOKEN     系统设置中创建的 API Token

                通用请求：
                  deploy-bot request GET /projects
                  deploy-bot request POST /projects @project.json

                常用命令：
                  deploy-bot projects list [--page <n>] [--page-size <n>|--limit <n>] [--keyword <kw>] [--git-auth-type <type>]
                  deploy-bot projects create <json|@file|->
                  deploy-bot projects update <id> <json|@file|->
                  deploy-bot hosts list [--all] [--page <n>] [--page-size <n>|--limit <n>] [--keyword <kw>] [--type LOCAL|SSH]
                  deploy-bot runtimes list [--host-id <id>] [--type JAVA|NODE|MAVEN]
                  deploy-bot plugins list
                  deploy-bot plugins shell-variables
                  deploy-bot templates list [--page <n>] [--page-size <n>|--limit <n>] [--keyword <kw>] [--template-type <type>] [--plugin-id <id>] [--monitor-process <true|false>]
                  deploy-bot templates create <json|@file|->
                  deploy-bot templates update <id> <json|@file|->
                  deploy-bot pipelines list [--page <n>] [--page-size <n>|--limit <n>] [--keyword <kw>] [--project-id <id>] [--template-id <id>] [--host-id <id>] [--tags <tag>]
                  deploy-bot pipelines detail <id>
                  deploy-bot pipelines create <json|@file|->
                  deploy-bot pipelines apply [--id <id>] <json|@file|->
                  deploy-bot pipelines update <id> <json|@file|->
                  deploy-bot pipelines plugin-plan <id>
                  deploy-bot deployments precheck <json|@file|->
                  deploy-bot deployments run <json|@file|->
                  deploy-bot deployments detail <id>
                  deploy-bot deployments logs <id> [--follow] [--offset <n>]
                  deploy-bot deployments stop <id>
                  deploy-bot deployments rollback <id>
                """);
    }

    private static class CliException extends RuntimeException {
        private CliException(String message) {
            super(message);
        }
    }
}
