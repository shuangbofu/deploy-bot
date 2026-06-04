#!/usr/bin/env bash
set -e

# [步骤 1/7] 准备本机源码目录：$BUILD_SOURCE_DIR
rm -rf "$BUILD_SOURCE_DIR"
mkdir -p "$BUILD_SOURCE_DIR"

# [步骤 2/7] 拉取代码：分支=$BRANCH 仓库=$GIT_URL
git clone --progress --verbose --depth=1 --branch "$BRANCH" "$GIT_URL" "$BUILD_SOURCE_DIR"

# [步骤 3/7] 构建前端工程：{{frontendDir}}
cd "$BUILD_SOURCE_DIR/{{frontendDir}}"
{{frontendBuildCommand}}

# [步骤 4/7] 同步前端产物到后端 static：{{backendStaticDir}}
rm -rf "$BUILD_SOURCE_DIR/{{backendStaticDir}}"
mkdir -p "$BUILD_SOURCE_DIR/{{backendStaticDir}}"
cp -R "$BUILD_SOURCE_DIR/{{frontendDir}}/{{distDir}}/." "$BUILD_SOURCE_DIR/{{backendStaticDir}}/"

# [步骤 5/7] 在后端构建目录执行构建：{{backendBuildWorkDir}}
cd "$BUILD_SOURCE_DIR/{{backendBuildWorkDir}}"
{{backendBuildCommand}}

# [步骤 6/7] 准备构建产物目录：$ARTIFACT_DIR
mkdir -p "$ARTIFACT_DIR"

# [步骤 7/7] 复制 Jar 到构建产物目录
cp "$BUILD_SOURCE_DIR/{{jarPath}}" "$ARTIFACT_DIR/$SERVICE_NAME.jar"

# [完成] 前后端一体项目构建完成
