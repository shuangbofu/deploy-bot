#!/usr/bin/env bash
set -e

# [步骤 1/4] 准备本机工作目录
rm -rf "$BUILD_SOURCE_DIR"

# [步骤 2/4] 拉取代码并切换分支
git clone --progress --verbose --depth=1 --branch "$BRANCH" "$GIT_URL" "$BUILD_SOURCE_DIR"
cd "$BUILD_SOURCE_DIR"

# [步骤 3/4] 执行前端构建
{{buildCommand}}

# [步骤 4/4] 收集静态产物
DIST_PATH="$BUILD_SOURCE_DIR/{{distDir}}"
for i in $(seq 1 15); do
  if [ -d "$DIST_PATH" ] && [ -n "$(find "$DIST_PATH" -mindepth 1 -print -quit 2>/dev/null)" ]; then
    break
  fi
  sleep 1
done
if [ ! -d "$DIST_PATH" ]; then
  echo "未找到前端产物目录：$DIST_PATH"
  exit 1
fi
if [ -z "$(find "$DIST_PATH" -mindepth 1 -print -quit 2>/dev/null)" ]; then
  echo "前端产物目录为空：$DIST_PATH"
  exit 1
fi
mkdir -p "$ARTIFACT_DIR"
rsync -a "$DIST_PATH/" "$ARTIFACT_DIR/"
