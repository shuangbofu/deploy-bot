#!/usr/bin/env bash
set -e

# [步骤 1/5] 准备本机工作目录：$WORKSPACE
rm -rf "$WORKSPACE"
mkdir -p "$WORKSPACE"

# [步骤 2/5] 拉取代码：分支=$BRANCH 仓库=$GIT_URL
git clone --depth=1 --branch "$BRANCH" "$GIT_URL" "$WORKSPACE"

# [步骤 3/5] 在构建目录执行构建：{{buildWorkDir}}
cd "$WORKSPACE/{{buildWorkDir}}"
{{buildCommand}}

# [步骤 4/5] 准备构建产物目录：$ARTIFACT_DIR
mkdir -p "$ARTIFACT_DIR"

# [步骤 5/5] 复制 Jar 到构建产物目录
cp "$WORKSPACE/{{jarPath}}" "$ARTIFACT_DIR/$SERVICE_NAME.jar"

# [完成] 本机构建完成
