<p align="center">
  <img src="./docs/deploy-bot-logo.svg" alt="Deploy Bot Logo" width="140" />
</p>
<h1 align="center">Deploy Bot</h1>

<p align="center">
  <a href="./LICENSE"><img src="https://img.shields.io/badge/license-MIT-0f766e.svg" alt="MIT License" /></a>
  <a href="https://fusb.top/deploy-bot"><img src="https://img.shields.io/badge/官网-fusb.top%2Fdeploy--bot-285fbd.svg" alt="介绍网站" /></a>
  <img src="https://img.shields.io/badge/java-17-f59e0b.svg" alt="Java 17" />
  <img src="https://img.shields.io/badge/backend-Spring%20Boot%203.3.5-111827.svg" alt="Spring Boot 3.3.5" />
  <img src="https://img.shields.io/badge/frontend-React%2018.3-2563eb.svg" alt="React 18.3" />
  <img src="https://img.shields.io/badge/bundler-Vite%205-059669.svg" alt="Vite 5" />
</p>

<p align="center">
  一个面向个人开发者和小团队的轻量部署系统。
</p>

<p align="center">
  把拉代码、构建、复制产物、查看日志、回滚版本这些重复操作整理成项目、模板、主机与流水线。
</p>

<p align="center">
  介绍与文档：<a href="https://fusb.top/deploy-bot">https://fusb.top/deploy-bot</a>
</p>

## 项目定位

AI 让个人开发和小团队协作的节奏变快了。现在一个人用 AI 在一两天里迭代出一个可用项目，比如 [Deploy Bot](https://github.com/shuangbofu/deploy-bot) 自己，以及 [Data Space](https://github.com/shuangbofu/data-space) 这类站点或服务，已经越来越常见。

但把项目上线时，很多个人开发者和小团队面对的仍然是重复部署动作和平台接入成本之间的取舍：

- 继续手工执行拉代码、构建、复制产物、启动服务、回滚版本这些重复动作
- 接入 Jenkins 这类成熟 CI/CD 工具，但要接受较多配置项、插件链路和偏工程平台的交互方式
- 接入商业化 DevOps 平台，但对个人或小团队来说，价格、组织模型和功能复杂度往往偏重

Deploy Bot 的目标是补上中间这一层：比手工脚本更可追踪、可回滚、可协作；比重型平台更轻，也更贴近中文用户日常操作习惯。

Deploy Bot 想解决的就是这类问题：有点子时可以尽快做出项目，也可以尽快把项目发出去。项目会优先覆盖个人和小团队最常用的部署流程，而不是一开始就做完整 DevOps 平台。

后续也可以接入 AI 辅助，主要围绕项目接入和流水线配置。例如：

- 对外提供 MCP 服务，让 AI 能直接读取平台里的项目、模板、主机、环境和流水线信息
- 提供配套 skill，让 AI 按平台约定快速接入新项目、创建流水线、补齐运行配置
- 在现有模板不满足时，由 AI 辅助创建派生模板，抽取变量并生成默认配置
- 当部署类型明显不同，比如 Docker、Python、Go 时，辅助生成新的部署插件
- 根据 Git 地址和目标环境，生成一条可检查、可修改的流水线草稿

当前开发版已经覆盖：

- 项目管理：维护仓库地址、认证方式与基础描述
- 主机管理：统一管理本机与 SSH 远程主机
- 运行环境管理：按主机维护 Java、Node、Maven 等环境
- 预置环境安装：支持一键下载安装常用 Java / Node / Maven，并在界面中跟踪后台安装任务
- 预置环境版本：内置常见 Java / Node / Maven 版本，覆盖 Java 8 / 11 / 17 与 Node 22 / 24 等常见组合
- 插件体系：通过 Spring Boot、Node 静态站点、前后端一体等类型插件声明运行时要求、默认模板、运行配置、PID 发现和启动判定
- 模板管理：支持插件默认模板和自定义派生模板，按构建 / 发布阶段展示变量定义与可用 Shell 上下文变量
- 流水线管理：绑定项目、模板、目标主机、运行环境、插件运行配置、默认变量、自定义标签与重要标签
- 流水线锁定：管理员可以按时间段锁定一条或多条流水线，普通用户部署前会看到锁定原因和时间窗口
- 部署执行：支持本机构建、远程发布、停止任务、SSE 实时日志、日志目录、暂停自动滚动与回到底部
- 部署快照：部署记录会固化变量、运行配置、运行环境和插件上下文，方便回看历史失败原因
- 部署差异：异步生成本次部署与上一次成功部署之间的 Git 提交和文件变更摘要
- 服务管理：对可监控进程进行启停、重启、心跳刷新、PID 轨迹和活跃时间展示
- 通知：支持 Webhook 配置、通知模板、通知配置绑定与通知记录查询，通知记录已并入部署记录视角
- 版本重发：保留历史构建产物，支持按部署记录重新发布某个历史版本
- 用户体系：提供真实登录态、管理员 / 普通用户角色和默认管理员账号
- 用户资料：支持头像上传、显示名称展示、修改密码与管理员重置密码
- 深色模式与布局设置：支持跟随系统、夜间自动深色、左侧 / 顶部菜单和图标风格配置
- 图表化仪表盘：按时间范围和粒度查看趋势、分布、排行、热度、资源等数据
- 工作台：管理员直接看到全部配置与数据，普通用户只看到可用的部署入口和个人 UI 设置

## 技术栈

### 后端

- Java 17
- Spring Boot 3
- Spring Data JPA
- H2 Database

### 前端

- React
- TypeScript
- Vite
- Tailwind CSS
- Ant Design

## 目录结构

```text
backend/    Spring Boot 后端服务
common/     后端通用工具模块
frontend/   React + TypeScript 前端应用
plugin/     部署类型插件 API、运行时与内置插件
runtime/    本地运行时数据库、脚本、日志、备份目录
scripts/    一次性维护脚本或迁移脚本
```

## 核心模型

- `Project`：一个可部署项目，包含 Git 地址与认证方式
- `Host`：部署目标主机，包括本机和 SSH 远程主机
- `RuntimeEnvironment`：归属于某台主机的 Java / Node / Maven 等环境
- `DeploymentPlugin`：部署类型插件，声明运行时要求、默认模板、运行配置、变量与判活方式
- `Template`：部署模板，来源可以是插件默认模板，也可以是管理员派生出的自定义模板
- `Pipeline`：可直接触发的部署流水线
- `Deployment`：一次具体的部署执行记录
- `Service`：由部署产生并可被系统管理的服务进程
- `User`：登录系统的真实账号，负责区分管理员与普通用户

## 执行模型

当前推荐使用插件计划和两阶段执行模型：

1. 流水线选择插件默认模板或自定义模板
2. 插件根据流水线、模板、运行配置和上下文生成部署计划
3. 在本机完成代码拉取与构建
4. 将构建产物同步到目标主机
5. 在目标主机执行发布脚本

这样目标主机通常只需要：

- 能接收文件
- 能执行发布命令
- 能运行最终产物

而 `git / node / maven` 等构建依赖可以集中维护在部署平台所在机器。

当插件 / 模板开启进程监测时，系统会在发布后接管服务进程：

1. 自动检测服务 PID
2. 进入启动观察窗口，通过关键字或插件默认规则判断服务是否稳定启动
3. 将进程登记到服务管理中，供后续查看状态、停止、重启

## 页面结构

### 完整菜单

- `仪表盘`
- `流水线大厅`
- `部署记录`
- `项目管理`
- `主机管理`
- `运行环境`
- `模板管理`
- `流水线管理`
- `插件管理`
- `服务管理`
- `用户管理`
- `系统设置`

### 普通用户可见菜单

- `仪表盘`
- `流水线大厅`
- `部署记录`
- `部署详情`
- `系统设置`（仅 UI 设置）

系统按登录账号的权限展示菜单、数据范围和可用操作。管理员能看到完整菜单，普通用户只看到部署和个人设置相关入口。流水线页面同时提供大厅视角和管理视角，其他页面按权限展示。

## 界面预览与使用文档

### 登录页

![Deploy Bot 登录页](https://fusb.top/deploy-bot/docs/screenshots/login.png)

### 仪表盘

![仪表盘](https://fusb.top/deploy-bot/docs/screenshots/admin-dashboard.png)

### 管理员流水线管理

![管理员流水线管理](https://fusb.top/deploy-bot/docs/screenshots/admin-pipelines.png)

### 普通用户流水线大厅

![普通用户流水线大厅](https://fusb.top/deploy-bot/docs/screenshots/user-pipelines.png)

使用文章和 Docker 快速启动说明已经整理到独立站点。

- [Deploy Bot 官网与文档](https://fusb.top/deploy-bot)
- [Deploy Bot 介绍 01：为什么做 Deploy Bot](https://fusb.top/deploy-bot/#/docs/articles/01-why-deploy-bot.md)
- [Deploy Bot 使用介绍 02：登录、仪表盘与工作台入口](https://fusb.top/deploy-bot/#/docs/articles/02-login-dashboard-and-dual-views.md)
- [Deploy Bot 使用介绍 03：项目怎么配置](https://fusb.top/deploy-bot/#/docs/articles/03-project-setup.md)
- [Deploy Bot 使用介绍 04：主机怎么配置](https://fusb.top/deploy-bot/#/docs/articles/04-host-setup.md)
- [Deploy Bot 使用介绍 05：运行环境怎么配置](https://fusb.top/deploy-bot/#/docs/articles/05-runtime-environments.md)
- [Deploy Bot 使用介绍 06：插件和模板怎么设计](https://fusb.top/deploy-bot/#/docs/articles/06-template-design.md)
- [Deploy Bot 使用介绍 07：流水线怎么拼出完整部署流程](https://fusb.top/deploy-bot/#/docs/articles/07-pipeline-setup.md)
- [Deploy Bot 使用介绍 08：怎么发起部署、查看记录与详情](https://fusb.top/deploy-bot/#/docs/articles/08-deployments-and-history.md)
- [Deploy Bot 使用介绍 09：服务、通知与系统设置怎么用](https://fusb.top/deploy-bot/#/docs/articles/09-ops-and-settings.md)
- [Docker 快速启动](https://fusb.top/deploy-bot/#/docs/docker-quick-start.md)

## 本地快速部署到云端

如果你已经在本地把 Deploy Bot 跑起来了，可以先用它把自己部署到云端服务器上。平台上线后，再部署其他 Java 服务、前端站点或简单全栈项目，就可以直接在云端这套 Deploy Bot 上完成。

准备工作：

- 把部署平台所在机器的 SSH 公钥加入目标云服务器，保证平台可以免密登录目标主机
- 把 Git 拉代码使用的公钥加入对应的 Git 平台
- 在本地这套 Deploy Bot 里先配置好项目、目标主机、运行环境、模板和流水线

Deploy Bot 自己这类前后端一体项目，可以按下面这套方式配置流水线。

这类配置大致是：

- 选择插件内置的前后端一体默认模板
- 前端目录放在 `frontend`，前端构建产物目录使用 `dist`
- 构建完成后把前端产物复制到 `backend/src/main/resources/static`
- 后端构建目录使用 `backend`，后端构建命令使用 `mvn -DskipTests package`
- 插件根据产物、服务名、运行配置、JVM 参数和应用参数生成最终启动脚本
- 如果需要配置 `application.yml`，在流水线运行配置里填写，发布时会写入目标主机并注入启动参数

按这套方式配好流水线之后，直接点击部署，就可以先把 Deploy Bot 本身发到云端。后面再部署其他项目时，就不需要继续依赖本地环境了。

## 快速开始

如果只想先把 Deploy Bot 平台跑起来，可以直接使用 Docker：

```bash
docker compose up -d --build
```

默认访问地址：

```text
http://localhost:8080
```

运行数据会挂载到项目根目录的 `runtime`，升级或重建容器时保留这个目录即可。更多端口、访问地址和 JVM 参数配置见 [Docker 快速启动](https://fusb.top/deploy-bot/#/docs/docker-quick-start.md)。

### 1. 启动后端

```bash
cd backend
mvn spring-boot:run
```

默认端口：`8080`

H2 控制台：

```text
http://<backend-host>:<backend-port>/h2-console
```

JDBC URL：

```text
jdbc:h2:file:./runtime/deploy-bot-db
```

### 2. 启动前端

```bash
cd frontend
npm install
npm run dev
```

默认端口：`5173`

### 3. 访问系统

- 工作台入口：`http://<frontend-host>:<frontend-port>/`
- 管理员完整菜单：`http://<frontend-host>:<frontend-port>/admin`
- 普通用户精简菜单：`http://<frontend-host>:<frontend-port>/user`
- 登录页：`http://<frontend-host>:<frontend-port>/login`

### 4. 默认管理员

空库首次启动后，会自动创建默认管理员账号：

- 用户名：`admin`
- 密码：`Admin@123456`

用户管理中的新建用户和重置密码会使用系统默认密码。默认配置为：

```yaml
deploybot:
  user:
    default-password: 12345
```

## 初始化数据

项目启动后会自动加载内置插件和插件默认模板：

- Spring Boot 插件：Spring Boot Jar 部署，内置 PID 发现和关键字 / `Started ... in ... seconds` 启动判定
- Node 静态站点插件：React / Vue 等静态站点部署
- 前后端一体插件：复用 Node 静态构建和 Spring Boot 发布能力

项目、主机、运行环境、流水线等业务数据仍然保持空库启动。  
默认模板来自各个插件 jar 包内的 resources，由插件运行时加载；不依赖 `runtime/` 目录中的任何运行时数据。
默认模板已支持保留产物、重复发布和插件运行配置。

### 创建项目

```bash
curl -X POST http://<backend-host>:<backend-port>/api/projects \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "<project-name>",
    "description": "<project-description>",
    "gitUrl": "<git-url>"
  }'
```

### 创建模板

推荐从插件默认模板派生自定义模板。确实需要自定义脚本时，可以继续保存模板实体：

```bash
curl -X POST http://<backend-host>:<backend-port>/api/templates \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "<template-name>",
    "description": "<template-description>",
    "pluginId": "node-static-deployment",
    "buildScriptContent": "#!/usr/bin/env bash\nset -e\nset -x\n<build-script>",
    "deployScriptContent": "#!/usr/bin/env bash\nset -e\nset -x\n<deploy-script>",
    "variablesSchema": "[]",
    "monitorProcess": false
  }'
```

### 创建流水线

```bash
curl -X POST http://<backend-host>:<backend-port>/api/pipelines \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "<pipeline-name>",
    "description": "<pipeline-description>",
    "projectId": <project-id>,
    "templatePluginId": "springboot-deployment",
    "builtinTemplateKey": "springboot-jar-default",
    "targetHostId": <host-id>,
    "targetDir": "/opt/apps/demo",
    "defaultBranch": "<branch>",
    "variables": {},
    "pluginConfig": {
      "javaOpts": "-Xms256m -Xmx512m",
      "javaSystemProperties": "-Dfile.encoding=UTF-8",
      "applicationArgs": "--server.port=8080",
      "runtimeConfigYaml": ""
    },
    "tags": ["test", "backend"],
    "importantTags": ["test"]
  }'
```

### 测试项目仓库连通性

```bash
curl -X POST http://<backend-host>:<backend-port>/api/projects/test-connection \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "<project-name>",
    "description": "<project-description>",
    "gitUrl": "<git-url>",
    "gitAuthType": "SSH"
  }'
```

## 插件、模板与变量说明

模板脚本支持 `{{variableName}}` 占位符。系统会自动注入一部分平台上下文变量，例如：

- `deploymentId`
- `branch`
- `gitUrl`
- `gitRepositoryUrl`
- `projectName`
- `pipelineName`
- `pipelineTags`
- `buildWorkspaceRoot`
- `deployWorkspaceRoot`
- `artifactDir`
- `targetDir`
- `serviceName`

插件可以暴露自己的变量和运行配置字段。前端按后端插件 API 返回的表单结构渲染。

你可以通过模板变量定义和流水线默认变量补充业务字段，比如：

- `distDir`
- `buildCommand`
- `frontendDir`
- `backendBuildCommand`

对于 Spring Boot 场景，运行配置更推荐放在插件配置里：

- `javaOpts`：JVM 参数
- `javaSystemProperties`：`-D` 系统属性
- `applicationArgs`：应用参数
- `springProfile`：Spring Profile
- `runtimeConfigYaml`：发布时写入 `application.yml` 并通过启动参数注入

后端会把平台上下文、模板变量和插件运行配置组装成最终脚本上下文，部署详情中也会固化这些数据，方便排查历史问题。

通知模板支持这些常用内置变量：

- `pipelineName`：流水线名称
- `projectName`：项目名称
- `branch`：部署分支
- `eventLabel`：通知事件名称，例如开始通知、结束通知
- `statusLabel`：部署状态名称，例如成功、失败、已停止
- `triggeredByDisplayName`：触发人显示名称
- `stoppedByDisplayName`：停止人显示名称
- `hostName`：目标主机名称
- `startedAt`：开始时间
- `finishedAt`：结束时间
- `duration`：耗时
- `errorMessage`：错误信息
- `deploymentId`：部署编号
- `detailUrl`：部署详情链接

## 用户与权限

- 所有 `/api/**` 接口都需要真实登录态
- 管理员可以访问全部配置页面与管理接口
- 普通用户可以进入流水线大厅、触发部署、查看部署记录与执行日志
- 普通用户也可以请求停止部署，平台会保留停止人、停止原因等信息
- 流水线锁定、配置维护、用户管理等操作仅管理员可用

另外，普通用户也可以进入系统设置维护自己的 UI 偏好。界面支持浅色、深色、跟随系统和夜间自动深色，菜单支持左侧 / 顶部布局、折叠侧栏和图标风格切换。

后端会自动拦截未登录请求，并返回统一的 `code / subCode / message / subMessage` 错误结构。

## Git 认证说明

- 项目支持 `NONE / BASIC / SSH` 三种 Git 认证方式
- 内置测试连通性，可以直接校验仓库是否可访问
- 连通性测试会返回 Git / SSH 诊断结果，便于快速定位是分支、密钥还是网络问题
- 针对老版本 Git（例如 CentOS 7 自带的 `git 1.8.x`），系统支持 `GIT_SSH` wrapper 方案，不依赖 `GIT_SSH_COMMAND`
- 系统设置中的 Git SSH 密钥会持久化保存
- 主机 SSH 与 Git SSH 密钥分离管理，便于分别授权远程主机和代码仓库

## 通知说明

- 系统设置中集中维护 `Webhook 配置` 和 `通知模板`
- 系统设置中的通知设置负责维护 `通知配置`
- 通知记录已并入部署记录页面，通过 tab 查看
- 当前已支持飞书自定义机器人通知
- 通知配置会绑定：
  - `通知渠道类型`
  - `通知类型`
  - `Webhook 配置`
  - `通知模板`
- 流水线只绑定通知配置，不重复填写 Webhook 地址和密钥
- 通知记录会保存每次发送的成功/失败状态，以及失败原因或响应信息
- 默认开始/结束通知模板已内置部署详情链接变量，可直接跳转回系统查看部署详情

## 部署排查说明

- 部署日志通过 SSE 增量刷新，并在终态后完成尾部日志收尾
- 日志查看器支持暂停 / 恢复自动滚动、回到顶部 / 底部、复制日志和日志目录
- 日志目录会聚合部署步骤和关键错误，方便在长日志中快速定位
- 部署详情中部署快照和部署差异分 tab 展示，快照用于回看上下文，差异用于看本次相对上次成功部署改了哪些 commit 和文件
- 部署差异异步生成，不阻塞主部署流程

## 开发建议

- 优先让插件定义部署类型，模板只保存该类型下可复用的脚本结构
- 类型差异明显时优先新增插件，不要靠模板硬塞所有差异
- 对危险目录做额外保护，尤其是带 `--delete` 的同步命令
- 生产场景建议补齐认证、权限、并发控制和执行隔离
- 推荐使用 SSH 密钥而不是在仓库或主机配置里长期保存明文密码

## 仓库清理

公开仓库前请确认这些内容不会进入版本库：

- `runtime/`
  这里应只保存本地运行时数据库、日志、渲染脚本和备份，不应该保存需要随源码发布的初始化数据
- `.idea/`
- 本地数据库文件
- 真实部署日志
- 带内网地址的运行脚本

## 路线图

- 更多内置插件类型，例如 Docker、Python、Go 等轻量部署场景
- 部署队列、并发限制与停止申请等协作功能
- 更完整的审计日志、操作追踪与通知中心
- 面向 AI / MCP 的项目、主机、流水线上下文读取与辅助生成

## License

MIT
