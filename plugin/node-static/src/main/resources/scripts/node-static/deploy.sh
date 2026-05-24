#!/usr/bin/env bash
set -e

# [步骤 1/2] 准备部署目录
mkdir -p "$TARGET_DIR"

# [步骤 2/2] 发布静态资源
rsync -a "$ARTIFACT_DIR/" "$TARGET_DIR/"
