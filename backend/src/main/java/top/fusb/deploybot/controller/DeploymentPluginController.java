package top.fusb.deploybot.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.fusb.deploybot.dto.ShellVariableSummary;
import top.fusb.deploybot.plugin.api.deployment.definition.DeploymentPluginDefinition;
import top.fusb.deploybot.plugin.runtime.DeploymentPluginRuntime;
import lombok.RequiredArgsConstructor;
import top.fusb.deploybot.service.ShellVariableService;

import java.util.List;

/**
 * 提供部署类型插件查询接口。
 */
@RestController
@RequestMapping("/api/deployment-plugins")
@RequiredArgsConstructor
public class DeploymentPluginController {

    private final DeploymentPluginRuntime deploymentPluginRuntime;
    private final ShellVariableService shellVariableService;

    /**
     * 列出当前已注册的完整部署类型插件定义。
     *
     * @return 插件完整定义列表
     */
    @GetMapping
    public List<DeploymentPluginDefinition> list() {
        return deploymentPluginRuntime.listDeploymentPluginDefinitions();
    }

    /**
     * 查询模板脚本可直接使用的 Shell 变量。
     *
     * @return Shell 变量说明列表
     */
    @GetMapping("/shell-variables")
    public List<ShellVariableSummary> shellVariables() {
        return shellVariableService.listVariables();
    }
}
