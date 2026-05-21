#!/bin/bash
# 学院综合服务平台 - 离线部署脚本
# 在目标服务器 10.10.0.27 上执行

set -e

DEPLOY_DIR="/opt/college-service"
echo "=== 创建部署目录 ==="
mkdir -p $DEPLOY_DIR/{sql,admin}

echo "=== 加载 Docker 镜像 ==="
docker load -i /tmp/college-images/postgres-16-alpine.tar
docker load -i /tmp/college-images/redis-7-alpine.tar
docker load -i /tmp/college-images/nginx-alpine.tar
docker load -i /tmp/college-images/college-backend-1.0.0.tar

echo "=== 复制配置文件 ==="
cp /tmp/college-deploy/docker-compose.prod.yml $DEPLOY_DIR/docker-compose.yml
cp /tmp/college-deploy/.env.prod $DEPLOY_DIR/.env
cp /tmp/college-deploy/nginx.conf $DEPLOY_DIR/nginx.conf
cp /tmp/college-deploy/schema.sql $DEPLOY_DIR/sql/schema.sql
cp -r /tmp/college-deploy/admin/* $DEPLOY_DIR/admin/

echo "=== 启动服务 ==="
cd $DEPLOY_DIR
docker-compose up -d

echo "=== 等待服务启动 ==="
sleep 10
docker-compose ps

echo "=== 部署完成 ==="
echo "访问地址: http://10.10.0.27"
echo "API地址:  http://10.10.0.27/api/"
