package top.fusb.deploybot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.fusb.deploybot.dto.ShellVariableSummary;
import top.fusb.deploybot.kit.TextKit;

import java.util.List;
import java.util.Set;

/**
 * 统一维护脚本可直接使用的 Shell 上下文变量，避免前端、模板渲染和部署流程各维护一份清单。
 */
@Service
@RequiredArgsConstructor
public class ShellVariableService {

    private static final Set<String> CONTEXT_KEYS = Set.of(
            "branch",
            "gitUrl",
            "gitRepositoryUrl",
            "projectId",
            "projectName",
            "pipelineId",
            "pipelineName",
            "pipelineTags",
            "serviceName",
            "targetDir",
            "buildWorkspaceRoot",
            "buildSourceDir",
            "deployWorkspaceRoot",
            "deploymentId",
            "artifactDir",
            "sourceArtifactPath"
    );

    private static final List<ShellVariableSummary> VARIABLES = List.of(
            context("BRANCH", "本次部署选择的 Git 分支。", "ALL", "branch"),
            context("GIT_URL", "平台处理认证后的 Git 拉取地址。", "BUILD", "gitUrl"),
            context("GIT_REPOSITORY_URL", "项目原始仓库地址。", "BUILD", "gitRepositoryUrl"),
            context("PROJECT_ID", "项目编号，用于进程识别和诊断。", "ALL", "projectId"),
            context("PROJECT_NAME", "项目名称。", "ALL", "projectName"),
            context("PIPELINE_ID", "流水线编号，用于进程识别和诊断。", "ALL", "pipelineId"),
            context("PIPELINE_NAME", "流水线名称。", "ALL", "pipelineName"),
            context("PIPELINE_TAGS", "流水线标签，多个标签使用英文逗号分隔。", "ALL", "pipelineTags"),
            context("SERVICE_NAME", "平台根据流水线名称生成的服务名，用于产物命名和日志。", "ALL", "serviceName"),
            context("TARGET_DIR", "流水线目标主机步骤填写的部署目录。", "DEPLOY", "targetDir"),
            context("DEPLOYMENT_ID", "本次部署编号。", "ALL", "deploymentId"),
            context("BUILD_WORKSPACE_ROOT", "部署平台本机构建工作根目录，用于缓存、源码检出和构建产物暂存。", "BUILD", "buildWorkspaceRoot"),
            context("BUILD_SOURCE_DIR", "部署平台本机检出的 Git 源码目录，仅构建脚本可直接读取。", "BUILD", "buildSourceDir"),
            context("DEPLOY_WORKSPACE_ROOT", "发布脚本所在机器的工作根目录。SSH 发布时是目标主机工作根目录，本机发布时是本机工作根目录。", "DEPLOY", "deployWorkspaceRoot"),
            context("ARTIFACT_DIR", "当前阶段产物目录：构建阶段写入本地产物目录，发布阶段读取目标主机上的同步产物目录。", "ALL", "artifactDir"),
            context("SOURCE_ARTIFACT_PATH", "回滚时的历史产物目录；普通部署为空。", "DEPLOY", "sourceArtifactPath"),
            plugin("RUNTIME_CONFIG_YAML_BASE64", "插件根据运行配置生成的 application.yml Base64 内容。", "DEPLOY"),
            plugin("RUNTIME_CONFIG_FILE_PATH", "平台为本次部署生成的运行配置文件路径。", "DEPLOY"),
            plugin("START_COMMAND", "插件生成的最终启动命令，平台会写入托管启动脚本。", "DEPLOY"),
            plugin("MANAGED_START_SCRIPT", "平台根据启动命令生成的托管启动脚本路径，模板发布脚本可直接执行。", "DEPLOY"),
            runtime("<TYPE>_HOME", "流水线选择的组件 Home 路径，例如 JAVA_HOME、NODE_HOME。", "ALL"),
            runtime("<TYPE>_BIN_PATH", "流水线选择的组件 Bin 路径，例如 JAVA_BIN_PATH、NODE_BIN_PATH。", "ALL")
    );

    /**
     * 查询所有脚本可用 Shell 变量。
     *
     * @return Shell 变量说明列表
     */
    public List<ShellVariableSummary> listVariables() {
        return VARIABLES;
    }

    /**
     * 查询所有平台上下文 key，用于模板渲染时区分 shell 变量和 {{模板变量}}。
     *
     * @return 平台上下文 key 集合
     */
    public Set<String> contextKeys() {
        return CONTEXT_KEYS;
    }

    /**
     * 将上下文 key 转成 shell 表达式。
     *
     * @param contextKey 平台上下文 key
     * @return shell 表达式，例如 ${PIPELINE_NAME}
     */
    public String placeholderForContextKey(String contextKey) {
        return "${" + TextKit.toUpperSnakeCase(contextKey) + "}";
    }

    private static ShellVariableSummary context(String key, String description, String stage, String contextKey) {
        return new ShellVariableSummary(key, "$" + key, description, stage, "PLATFORM", contextKey);
    }

    private static ShellVariableSummary platform(String key, String description, String stage, String contextKey) {
        return new ShellVariableSummary(key, "$" + key, description, stage, "PLATFORM", contextKey);
    }

    private static ShellVariableSummary runtime(String key, String description, String stage) {
        return new ShellVariableSummary(key, "$" + key, description, stage, "RUNTIME", null);
    }

    private static ShellVariableSummary plugin(String key, String description, String stage) {
        return new ShellVariableSummary(key, "$" + key, description, stage, "PLUGIN", null);
    }

}
