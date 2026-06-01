# Docker 快速启动

这个方式用于快速把 Deploy Bot 平台本身跑起来，适合个人或小团队先完成平台部署，再通过页面配置项目、主机、运行环境和流水线。

## 启动

在项目根目录执行：

```bash
docker compose up -d --build
```

启动后访问：

```text
http://localhost:8080
```

默认会把运行数据挂载到宿主机：

```text
./runtime:/app/runtime
```

H2 数据库、部署日志、构建工作区、上传文件和部署产物都会放在这个目录下。升级或重建容器时，只要保留 `runtime` 目录，平台数据就会继续保留。

## 常用环境变量

可以通过环境变量调整端口、访问地址和 JVM 参数：

```bash
DEPLOY_BOT_PORT=7005 \
DEPLOYBOT_BASE_URL=http://localhost:7005 \
JAVA_OPTS="-Xms256m -Xmx768m" \
docker compose up -d --build
```

变量说明：

- `DEPLOY_BOT_PORT`：宿主机暴露端口，默认 `8080`。
- `DEPLOYBOT_BASE_URL`：平台对外访问地址，用于通知链接等场景。
- `JAVA_OPTS`：追加给 Deploy Bot 后端进程的 JVM 参数。低配机器可以使用 `-Xms256m -Xmx512m`，部署记录和日志较多时可以提高到 `-Xms512m -Xmx1g`。

## 镜像里包含什么

镜像会构建前端静态资源，并打包到 Spring Boot 后端 Jar 中，最终只启动一个后端服务。

运行镜像内置了平台自身常用工具：

- JRE 17
- `git`
- `openssh-client`
- `rsync`
- `tar`
- `gzip`
- `curl`

这些工具用于平台自身执行 Git 拉取、SSH 远程发布、产物同步等基础动作。

## 关于构建环境

Docker 快速启动只负责把 Deploy Bot 平台跑起来，不建议把所有项目构建环境都塞进平台镜像里。

如果后续要用 Deploy Bot 构建 Java、Node、Maven 项目，推荐在页面的“运行环境”里维护对应环境：

- 远程主机构建或发布时，配置目标主机上的 Java / Node / Maven 路径。
- 容器内本机构建时，可以使用预置环境安装到 `/app/runtime` 下，或者基于当前 Dockerfile 扩展自己的镜像。

这样平台镜像不会过大，也更符合不同团队按需配置构建环境的方式。

## 停止和查看日志

停止服务：

```bash
docker compose down
```

查看容器日志：

```bash
docker compose logs -f deploy-bot
```

查看平台运行日志：

```bash
tail -f runtime/logs/deploy-bot-app.log
```
