# Plugin Architecture

`plugin` 目录用于承载 Deploy Bot 的类型插件体系，目标是让新的部署类型通过新增模块、打包 jar、放入 classpath 的方式接入，而不是继续改写 `backend` 主流程。

## Module Layout

- `api`
  - 对外稳定接口与模型。
  - 新插件至少依赖这个模块。
- `common`
  - 插件侧抽象基类。
  - 复用根模块 `common` 里的 `kit`，不重复实现文本、shell、时间工具。
- `runtime`
  - 插件运行时入口。
  - 通过 `ServiceLoader` 只加载 `DeploymentPlugin`。
  - 不直接依赖任何具体类型插件，保持对外部插件 jar 的中立性。
- `starter-builtin`
  - 内置插件集合 starter。
  - 用于一次性引入系统自带的 `springboot`、`node-static`、`fullstack` 类型插件。
- `springboot`
  - Spring Boot 后端服务类型插件。
- `node-static`
  - Node 静态站点类型插件。
- `fullstack`
  - 前后端一体复合类型插件。

## Design Rules

1. 一个部署类型对应一个完整插件模块。
2. 平台流程是固定的，插件只声明自己是否参与某个流程节点，不重新发明流程。
3. 前端与 backend 主流程只感知 `DeploymentPlugin`，不直接面向 `ProcessLocatorPlugin`、`StartupJudgePlugin` 这类能力接口。
4. PID 发现、启动判定等能力由类型插件内部携带并暴露。
5. 复合类型插件通过组合其他类型插件的计划来完成编排，不在主流程里写死分支。

## How To Add A New Plugin

以 `python-backend` 为例：

1. 在 `plugin/` 下新增模块目录与 `pom.xml`。
2. 依赖：
   - `deploy-bot-plugin-api`
   - `deploy-bot-plugin-common`
3. 实现一个完整的 `DeploymentPlugin`：
   - `descriptor()`
   - `runtimeRequirement()`
   - `formSchema()`
   - `variableDefinitions()`
   - `lifecycleDescriptor()`
   - `supports()`
   - `plan()`
4. 如需自定义 PID 发现或启动判定：
   - 在插件模块内实现 `ProcessLocatorPlugin`
   - 在插件模块内实现 `StartupJudgePlugin`
   - 通过 `DeploymentPlugin.processLocatorPlugin()` / `startupJudgePlugin()` 暴露
5. 在插件模块的 `META-INF/services/top.fusb.deploybot.plugin.api.deployment.DeploymentPlugin` 中注册实现类。
6. 将该模块打成 jar 并放入 classpath，`DeploymentPluginRuntime` 会自动通过 `ServiceLoader` 发现它。
7. 如果希望同时保留系统内置类型插件，可额外引入 `deploy-bot-plugin-starter-builtin`。

## What A Plugin Should Own

一个类型插件需要自己负责：

- 类型描述
- 配置表单结构
- 变量定义
- 运行时要求
- 生命周期能力说明
- 类型识别逻辑
- 部署计划输出
- 可选的 PID 发现能力
- 可选的启动判定能力
- 固定流程节点的参与情况

## What The Runtime Owns

`DeploymentPluginRuntime` 负责：

- 发现所有类型插件
- 校验插件标识唯一性
- 根据上下文选择匹配的类型插件
- 输出部署计划
- 从计划中解析该类型插件内部携带的 PID 发现与启动判定能力

## Current Scope

当前插件层已经抽出的重点是：

- 类型识别
- 元信息输出
- 运行时要求
- 变量与表单定义
- 固定流程节点定义
- PID 发现
- 启动判定

构建脚本、发布脚本、启动命令仍然主要由模板承载，后续如果继续解耦，可以在现有类型插件基础上再逐步往“执行策略”方向抽。
