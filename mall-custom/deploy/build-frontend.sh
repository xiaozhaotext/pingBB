#!/usr/bin/env bash
# 原生部署：构建 H5 前端静态包
# 用法： bash build-frontend.sh   （在 mall-custom 目录下执行）
set -e
cd "$(dirname "$0")/.."
cd mall-app-web
echo "==> npm install"
npm install
echo "==> 构建 H5（取 .env.production 的 VITE_API_BASE_URL=/portal）"
npm run build:h5
echo "==> 构建完成，静态文件在： $(pwd)/dist/build/h5"
echo "    用 nginx 托管该目录，并把 /portal 代理到后端 :8085 即可（见 deploy/nginx.conf）"
