#!/usr/bin/env bash
# =================================================================
# 学院综合服务平台 - 服务器端部署脚本
# =================================================================
# 在目标服务器（Ubuntu，无外网，已装 Docker + docker-compose）上执行。
# 配合本地的 build-deploy-package.ps1 生成的 deploy-package/ 使用。
#
# 用法 (在 deploy-package/ 目录下):
#   bash deploy.sh                # 自动检测全新部署 / 增量更新
#   bash deploy.sh --fresh        # 强制全新部署 (会 docker compose down -v 清空数据!)
#   bash deploy.sh --restart-only # 不更新文件, 只重启容器
#
# 第一次执行: 加载所有镜像, 拷贝配置, docker compose up
# 后续执行:   只更新变化的镜像和静态文件, restart 受影响的容器
# =================================================================

set -euo pipefail

# ---------- 配置 ----------
DEPLOY_DIR=/opt/college-service
PKG_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ---------- 参数 ----------
MODE=auto
for arg in "$@"; do
    case "$arg" in
        --fresh)        MODE=fresh ;;
        --restart-only) MODE=restart ;;
        -h|--help)
            sed -n '2,18p' "$0"
            exit 0 ;;
        *) echo "Unknown arg: $arg"; exit 1 ;;
    esac
done

# ---------- 工具函数 ----------
step()  { printf '\n\033[36m=== %s ===\033[0m\n' "$*"; }
ok()    { printf '  \033[32m✓\033[0m %s\n' "$*"; }
warn()  { printf '  \033[33m⚠\033[0m %s\n' "$*"; }
skip()  { printf '  \033[90m→ %s (skipped)\033[0m\n' "$*"; }
fail()  { printf '  \033[31m✗ %s\033[0m\n' "$*"; exit 1; }

# 用合适的 docker compose 命令 (v1: docker-compose, v2: docker compose)
if docker compose version >/dev/null 2>&1; then
    DC='docker compose'
elif command -v docker-compose >/dev/null 2>&1; then
    DC='docker-compose'
else
    fail 'docker compose / docker-compose 都没装'
fi

# 权限拆分:
# - /opt/college-service 属于系统目录, 非 root 总是需要 sudo 写入
# - Docker 命令只有在用户不属于 docker 组时才需要 sudo
if [ "$EUID" -ne 0 ]; then
    FS_SUDO=sudo
    if groups | grep -qw docker; then
        DOCKER_SUDO=
    else
        DOCKER_SUDO=sudo
    fi
else
    FS_SUDO=
    DOCKER_SUDO=
fi

# ---------- 0) 前置检查 ----------
step '0/6 环境检查'
for f in docker-compose.yml .env nginx.conf schema.sql images models admin; do
    [ -e "$PKG_DIR/$f" ] || fail "缺少 $f, 部署包不完整"
done
ok "部署包结构完整 ($DC, fs_sudo=${FS_SUDO:-no}, docker_sudo=${DOCKER_SUDO:-no})"

# ---------- 1) 决定模式 ----------
step '1/6 判定部署模式'
FRESH=0
if [ "$MODE" = fresh ]; then
    FRESH=1
    warn '强制 fresh 模式: 将清空 /opt/college-service 数据卷'
    read -p '确定吗? [y/N] ' yn
    [ "$yn" = y ] || [ "$yn" = Y ] || fail '用户取消'
    if [ -d "$DEPLOY_DIR" ]; then
        (cd "$DEPLOY_DIR" && $DOCKER_SUDO $DC down -v 2>/dev/null) || true
    fi
elif [ ! -d "$DEPLOY_DIR" ] || [ -z "$($FS_SUDO ls -A "$DEPLOY_DIR" 2>/dev/null || true)" ]; then
    FRESH=1
    ok '目标目录不存在或为空 → 全新部署'
elif [ "$MODE" = restart ]; then
    ok '仅重启模式'
else
    ok '增量更新模式'
fi

# ---------- 2) 加载镜像 ----------
if [ "$MODE" != restart ]; then
    step '2/6 加载 Docker 镜像'
    for tar in "$PKG_DIR/images"/*.tar; do
        name=$(basename "$tar")
        echo "  $name"
        $DOCKER_SUDO docker load -i "$tar" >/dev/null
        ok "$name"
    done
else
    skip '2/6 仅重启模式跳过镜像加载'
fi

# ---------- 3) 同步配置 / 静态文件 / 模型 ----------
if [ "$MODE" != restart ]; then
    step '3/6 同步配置 + 静态文件 + 模型'
    $FS_SUDO mkdir -p "$DEPLOY_DIR"/{sql,admin,models}
    $FS_SUDO cp "$PKG_DIR/docker-compose.yml" "$DEPLOY_DIR/"
    if [ -f "$DEPLOY_DIR/.env" ]; then
        skip ".env 已存在于服务器, 保留现有配置 (要重置请手动删除 $DEPLOY_DIR/.env)"
    else
        $FS_SUDO cp "$PKG_DIR/.env" "$DEPLOY_DIR/"
        warn ".env 是首次部署 (用默认值), 请编辑 $DEPLOY_DIR/.env 填入实际密钥后重启 backend"
    fi
    $FS_SUDO cp "$PKG_DIR/nginx.conf" "$DEPLOY_DIR/"
    $FS_SUDO cp "$PKG_DIR/schema.sql" "$DEPLOY_DIR/sql/"
    $FS_SUDO rsync -a --delete "$PKG_DIR/admin/" "$DEPLOY_DIR/admin/" 2>/dev/null \
        || $FS_SUDO cp -r "$PKG_DIR/admin/." "$DEPLOY_DIR/admin/"
    $FS_SUDO rsync -a --delete "$PKG_DIR/models/" "$DEPLOY_DIR/models/" 2>/dev/null \
        || $FS_SUDO cp -r "$PKG_DIR/models/." "$DEPLOY_DIR/models/"
    ok 'docker-compose.yml + .env + nginx.conf + schema.sql + admin/ + models/'
else
    skip '3/6 仅重启模式跳过文件同步'
fi

# ---------- 4) 启动 / 重启 ----------
step '4/6 启动容器'
cd "$DEPLOY_DIR"
if [ "$FRESH" = 1 ]; then
    $DOCKER_SUDO $DC up -d
    ok '所有容器已启动 (首次启动 embedding 加载模型约 30 秒)'
elif [ "$MODE" = restart ]; then
    $DOCKER_SUDO $DC restart
    ok '所有容器已重启'
else
    # 增量: 后端镜像变了一定要 down + up 才能用新 image
    $DOCKER_SUDO $DC up -d --remove-orphans
    ok '增量启动 (有镜像变更的容器会被重建)'
fi

# ---------- 5) 健康检查 ----------
step '5/6 健康检查 (最长等 120 秒)'
deadline=$(($(date +%s) + 120))
required_services="postgres redis embedding backend nginx"
while [ "$(date +%s)" -lt "$deadline" ]; do
    all_healthy=1
    for svc in $required_services; do
        cid=$($DOCKER_SUDO $DC ps -q "$svc" 2>/dev/null || true)
        [ -z "$cid" ] && { all_healthy=0; break; }
        # 有 healthcheck 用 health, 没有就看 running
        state=$($DOCKER_SUDO docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cid" 2>/dev/null || echo unknown)
        case "$state" in
            healthy|running) ;;
            *) all_healthy=0; break ;;
        esac
    done
    if [ "$all_healthy" = 1 ]; then
        ok '所有容器健康'
        break
    fi
    sleep 3
    printf '.'
done
echo
$DOCKER_SUDO $DC ps

# ---------- 6) 冒烟测试 ----------
step '6/6 冒烟测试'
embed_resp=$($DOCKER_SUDO $DC exec -T embedding wget -qO- --post-data='{"input":"测试"}' \
    --header='Content-Type: application/json' \
    http://localhost:80/v1/embeddings 2>/dev/null | head -c 50 || true)
if echo "$embed_resp" | grep -q embedding; then
    ok 'embedding 接口返回向量'
else
    warn "embedding 接口异常, 看日志: $DC logs embedding"
fi

login_resp=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/auth/login \
    -H 'Content-Type: application/json' \
    -d '{"studentId":"admin","password":"admin123"}' || echo 000)
if [ "$login_resp" = 200 ]; then
    ok '后端登录接口 200 OK'
else
    warn "后端登录返回 HTTP $login_resp, 看日志: $DC logs backend"
fi

nginx_resp=$(curl -s -o /dev/null -w '%{http_code}' http://localhost/ || echo 000)
if [ "$nginx_resp" = 200 ] || [ "$nginx_resp" = 304 ]; then
    ok "管理端首页 HTTP $nginx_resp"
else
    warn "管理端首页 HTTP $nginx_resp"
fi

echo
echo "======================================================================"
echo "  部署完成: http://$(hostname -I | awk '{print $1}')"
echo "  默认登录: admin / admin123  (生产环境请立即修改)"
echo "  日志:    $DC -f /opt/college-service/docker-compose.yml logs -f"
echo "======================================================================"
