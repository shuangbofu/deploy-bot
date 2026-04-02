<p align="center">
  <img src="./docs/deploy-bot-logo.svg" alt="Deploy Bot Logo" width="140" />
</p>
<h1 align="center">Deploy Bot</h1>

<p align="center">
  <a href="./LICENSE"><img src="https://img.shields.io/badge/license-MIT-0f766e.svg" alt="MIT License" /></a>
  <img src="https://img.shields.io/badge/java-17-f59e0b.svg" alt="Java 17" />
  <img src="https://img.shields.io/badge/backend-Spring%20Boot%203.3.5-111827.svg" alt="Spring Boot 3.3.5" />
  <img src="https://img.shields.io/badge/frontend-React%2018.3-2563eb.svg" alt="React 18.3" />
  <img src="https://img.shields.io/badge/bundler-Vite%205-059669.svg" alt="Vite 5" />
</p>

<p align="center">
  一个面向小团队和内部平台场景的轻量部署系统。
</p>

<p align="center">
  把拉代码、构建、复制产物、查看日志、回滚版本这些重复操作沉淀成可配置的项目、模板、主机与流水线。
</p>

## 项目定位

这个项目更偏向“可继续演进的部署平台骨架”，而不是只做一个脚本执行器。

当前已经覆盖这些核心能力：

- 项目管理：维护仓库地址、认证方式与基础描述
- 主机管理：统一管理本机与 SSH 远程主机
- 运行环境管理：按主机维护 Java、Node、Maven 等环境
- 预置环境安装：支持一键下载安装常用 Java / Node / Maven，并在界面中跟踪后台安装任务
- 预置环境版本：内置常见 Java / Node / Maven 版本，覆盖 Java 8 / 11 / 17 与 Node 22 / 24 等常见组合
- 模板管理：支持构建脚本、发布脚本、变量提取与模板类型
- 流水线管理：绑定项目、模板、目标主机、默认变量与自定义标签
- 部署执行：支持本机构建、远程发布、停止任务、查看实时日志
- 部署快照：部署记录会固化关键变量、环境与运行配置，方便回看历史失败原因
- 服务管理：对可监控进程进行启停、心跳刷新和活跃时间展示
- 通知能力：支持 Webhook 配置、通知模板、通知配置绑定与通知记录查询
- 版本重发：保留历史构建产物，支持按部署记录重新发布某个历史版本
- 用户体系：提供真实登录态、管理员 / 普通用户角色和默认管理员账号
- 用户资料：支持头像上传、显示名称展示、修改密码与管理员重置密码
- 管理端 / 用户端分离：管理端负责配置，用户端负责部署和排查，但部署记录全员可见

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
frontend/   React + TypeScript 前端应用
runtime/    本地运行时数据库、脚本、日志、备份目录
scripts/    一次性维护脚本或迁移脚本
```

## 核心模型

- `Project`：一个可部署项目，包含 Git 地址与认证方式
- `Host`：部署目标主机，本机和 SSH 远程主机统一抽象成主机
- `RuntimeEnvironment`：归属于主机的 Java / Node / Maven 等环境
- `Template`：部署模板，定义构建脚本、发布脚本和变量
- `Pipeline`：可直接触发的部署流水线
- `Deployment`：一次具体的部署执行记录
- `Service`：由部署产生并可被系统管理的服务进程
- `User`：登录系统的真实账号，负责区分管理员与普通用户

## 执行模型

当前推荐使用“两阶段执行”模型：

1. 在本机完成代码拉取与构建
2. 将构建产物同步到目标主机
3. 在目标主机执行发布脚本

这样目标主机通常只需要：

- 能接收文件
- 能执行发布命令
- 能运行最终产物

而 `git / node / maven` 等构建依赖可以集中维护在部署平台所在机器。

当模板开启进程监测时，系统会在发布后接管服务进程：

1. 自动检测服务 PID
2. 进入启动观察窗口，判断服务是否真正稳定启动
3. 将进程登记到服务管理中，供后续查看状态、停止、重启

## 页面结构

### 管理端

- `控制台`
- `项目管理`
- `主机管理`
- `模板管理`
- `流水线管理`
- `用户管理`
- `部署记录`
- `服务管理`
- `通知`
- `系统设置`

### 用户端

- `控制台`
- `流水线大厅`
- `部署记录`
- `通知记录`
- `部署详情`

## 快速开始

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

- 管理端：`http://<frontend-host>:<frontend-port>/admin`
- 用户端：`http://<frontend-host>:<frontend-port>/user`
- 登录页：`http://<frontend-host>:<frontend-port>/login`

### 4. 默认管理员

空库首次启动后，会自动创建默认管理员账号：

- 用户名：`admin`
- 密码：`Admin@123456`

用户管理中的“新建用户”和“重置密码”会使用系统默认密码。默认配置为：

```yaml
deploybot:
  user:
    default-password: 12345
```

## 初始化数据

项目启动后会自动初始化一组常用模板：

- Spring Boot Jar 部署
- React 静态站点部署
- Vue 静态站点部署
- Spring Boot 前后端一体部署

项目、主机、运行环境、流水线等业务数据仍然保持空库启动。  
默认模板来自后端资源文件 [default-templates.json](./backend/src/main/resources/default-templates.json)，不依赖 `runtime/` 目录中的任何运行时数据。
其中默认的 Spring Boot / React / Vue 模板已经按“保留产物、可重复发布”的思路整理，不再依赖 `rsync --delete` 这类会破坏历史版本目录的发布方式。

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

```bash
curl -X POST http://<backend-host>:<backend-port>/api/templates \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "<template-name>",
    "description": "<template-description>",
    "templateType": "vue",
    "variablesSchema": "[]",
    "buildScriptContent": "#!/usr/bin/env bash\nset -e\nset -x\n<build-script>",
    "deployScriptContent": "#!/usr/bin/env bash\nset -e\nset -x\n<deploy-script>"
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
    "templateId": <template-id>,
    "targetHostId": <host-id>,
    "defaultBranch": "<branch>",
    "variablesJson": "{}",
    "tagsJson": "[\"test\",\"frontend\"]"
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

## 模板变量说明

模板脚本支持 `{{variableName}}` 占位符。  
系统会自动注入一部分内置变量，例如：

- `deploymentId`
- `branch`
- `gitUrl`
- `projectName`
- `pipelineName`
- `buildWorkspaceRoot`
- `deployWorkspaceRoot`
- `artifactDir`

你也可以继续通过模板变量定义和流水线默认变量补充业务字段，比如：

- `targetDir`
- `distDir`
- `buildCommand`
- `startCommand`

对于 Spring Boot 场景，流水线还支持这些运行时配置：

- `applicationName`：生成唯一的 jar / log 名称，避免所有项目都落成 `app.jar`
- `springProfile`：自动追加 `--spring.profiles.active=...`
- `runtimeConfigYaml`：发布时写入额外 YAML，并通过 `--spring.config.additional-location=...` 注入

通知模板支持这些常用内置变量：

- `pipelineName`：流水线名称
- `projectName`：项目名称
- `branch`：部署分支
- `eventLabel`：通知事件名称，例如“开始通知”“结束通知”
- `statusLabel`：部署状态名称，例如“成功”“失败”“已停止”
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

- 所有 `/api/**` 接口现在都需要真实登录态
- 管理员可以访问管理端全部页面与配置接口
- 普通用户可以进入用户端、触发部署、查看全量部署记录与执行日志
- 停止部署、重新发布历史版本等高风险动作仍然保留权限边界

另外，用户端控制台和流水线大厅中的“最近部署”摘要会按当前登录用户统计，不会混入其他人的最近操作。

后端会自动拦截未登录请求，并返回统一的 `code / subCode / message / subMessage` 错误结构。

## Git 认证说明

- 项目支持 `NONE / BASIC / SSH` 三种 Git 认证方式
- 内置了“测试连通性”能力，可以直接校验仓库是否可访问
- 连通性测试会返回更明确的 Git / SSH 诊断结果，便于快速定位是分支、密钥还是网络问题
- 针对老版本 Git（例如 CentOS 7 自带的 `git 1.8.x`），系统已兼容 `GIT_SSH` wrapper 方案，不依赖 `GIT_SSH_COMMAND`
- 系统设置中的 Git SSH 密钥会持久化保存，不会在保存其他配置时被误清空
- 主机 SSH 与 Git SSH 密钥分离管理，便于分别授权远程主机和代码仓库

## 通知说明

- 系统设置中集中维护 `Webhook 配置` 和 `通知模板`
- 通知页负责维护 `通知配置` 与 `通知记录`
- 当前已支持飞书自定义机器人通知
- 通知配置会绑定：
  - `通知渠道类型`
  - `通知类型`
  - `Webhook 配置`
  - `通知模板`
- 流水线只绑定通知配置，不重复填写 Webhook 地址和密钥
- 通知记录会保存每次发送的成功/失败状态，以及失败原因或响应信息
- 默认开始/结束通知模板已内置部署详情链接变量，可直接跳转回系统查看部署详情

## 开发建议

- 优先让模板保持“职责单一”，不要把完全不同的部署模型揉进同一套脚本
- 对危险目录做额外保护，尤其是带 `--delete` 的同步命令
- 生产场景建议补齐认证、权限、并发控制和执行隔离
- 推荐使用 SSH 密钥而不是在仓库或主机配置里长期保存明文密码

## 仓库清理

公开仓库前请确认这些内容不会进入版本库：

- `runtime/`
  这里应只保存本地运行时数据库、日志、渲染脚本和备份，不应该承载需要随源码发布的初始化数据
- `.idea/`
- 本地数据库文件
- 真实部署日志
- 带内网地址的运行脚本

## 路线图

- WebSocket / SSE 实时日志推送
- 远程主机环境自动安装与探测增强
- 更细粒度的模板测试与变量校验
- 部署队列、并发限制与审批流
- 更完整的审计日志与操作追踪

## License

MIT
