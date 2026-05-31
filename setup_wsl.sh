#!/bin/bash
# WSL 环境初始化脚本
# 在 WSL 终端里运行：bash setup_wsl.sh

set -e

echo "=== 1. 安装系统依赖 ==="
sudo apt-get update
sudo apt-get install -y nodejs npm g++ cmake libtorrent-rasterbar-dev pkg-config

echo "=== 2. 安装 Node.js 依赖 ==="
cd backend
npm install
cd ..

echo "=== 3. 编译 C++ 引擎 ==="
cd backend
g++ -std=c++17 -O2 torrent_server.cpp -o torrent_server -ltorrent-rasterbar -lpthread
cd ..

echo "=== 4. 创建必要目录 ==="
mkdir -p backend/downloads backend/torrent_cache

echo ""
echo "✅ 初始化完成！启动服务："
echo "   cd backend && node server.js"
echo "   服务地址: http://localhost:18000"
echo ""
echo "⚠️  如果端口 18000 被占用，修改 server.js 中的 PORT"
