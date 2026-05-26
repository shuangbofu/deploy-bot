#!/usr/bin/env bash
set -e

# [步骤 1/4] 准备部署目录：$TARGET_DIR
mkdir -p "$TARGET_DIR"

# [步骤 2/4] 发布 Jar：$ARTIFACT_DIR/$SERVICE_NAME.jar -> $TARGET_DIR/$SERVICE_NAME.jar
cp "$ARTIFACT_DIR/$SERVICE_NAME.jar" "$TARGET_DIR/$SERVICE_NAME.jar"

# [步骤 3/4] 准备运行配置
if [ -n "$RUNTIME_CONFIG_YAML_BASE64" ]; then
  mkdir -p "$(dirname "$RUNTIME_CONFIG_FILE_PATH")"
  printf '%s' "$RUNTIME_CONFIG_YAML_BASE64" | base64 --decode > "$RUNTIME_CONFIG_FILE_PATH"
else
  rm -f "$RUNTIME_CONFIG_FILE_PATH"
fi

# [步骤 4/4] 启动新进程
if [ -z "$MANAGED_START_SCRIPT" ]; then
  echo "托管进程启动脚本未生成，请检查插件运行配置。" >&2
  exit 1
fi
bash "$MANAGED_START_SCRIPT"

# [完成] Spring Boot 前后端一体部署完成
