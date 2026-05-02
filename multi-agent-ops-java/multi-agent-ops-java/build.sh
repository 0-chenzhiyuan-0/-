#!/bin/bash
# 编译脚本（无需Maven，纯javac）
set -e
echo "🔨 编译中..."
mkdir -p target/classes
javac -d target/classes -source 17 -target 17 \
  $(find src/main/java -name "*.java")
echo "📦 打包..."
cd target/classes
jar cfe ../multi-agent-ops.jar ops.App *
cd ../..
echo "✅ 完成！运行: java -jar target/multi-agent-ops.jar"
