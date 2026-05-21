# 服务器部署操作手册（离线部署版）

> 目标服务器：10.10.0.27
> 操作系统：Ubuntu 24.04 LTS
> 用户名：user
> 特殊约束：**服务器无外网访问能力**，所有依赖必须从本地传输
> 本文档记录从零部署到系统可访问的完整步骤。

---

## 一、部署架构概述

由于服务器无法访问外网，采用**本地构建 + 离线传输**方案：

```
本地开发电脑 (有网络)                    服务器 10.10.0.27 (无网络)
┌─────────────────────────┐    SCP     ┌─────────────────────────┐
│ 1. mvn package → JAR    │ ────────→  │ docker-compose up -d    │
│ 2. npm run build → dist │ ────────→  │                         │
│ 3. docker save → .tar   │ ────────→  │ docker load < .tar      │
│ 4. 配置文件              │ ────────→  │                         │
└─────────────────────────┘            └─────────────────────────┘
```

**需要传输到服务器的文件清单：**

| 文件 | 来源 | 用途 |
|------|------|------|
| `college-backend-1.0.0.tar` | `docker save` | 后端应用镜像（含 JRE + JAR） |
| `postgres-16-alpine.tar` | `docker save` | PostgreSQL 镜像 |
| `redis-7-alpine.tar` | `docker save` | Redis 镜像 |
| `nginx-alpine.tar` | `docker save` | Nginx 镜像 |
| `frontend-admin/dist/` | `npm run build` | 管理端静态文件 |
| `docker-compose.prod.yml` | `deploy/` 目录 | 生产编排配置（使用 image 引用） |
| `deploy/nginx.conf` | 项目目录 | Nginx 配置 |
| `deploy/sql/schema.sql` | 项目目录 | 数据库建表脚本 |
| `.env.prod` | `deploy/` 目录 | 环境变量配置 |

---

## 二、本地构建（在开发电脑上执行）

### 2.1 构建后端 JAR 包

```bash
cd college-service-platform/backend
./mvnw.cmd package -DskipTests
# 产物: target/college-service-platform-1.0.0-SNAPSHOT.jar
```

### 2.2 构建管理端前端

```bash
cd college-service-platform/frontend-admin
npm install
npm run build
# 产物: dist/ 目录
```

### 2.3 构建后端 Docker 镜像并导出

```bash
# 使用精简 Dockerfile 构建后端镜像（基于已编译的 JAR）
cd college-service-platform/backend
docker build -t college-backend:1.0.0 -f Dockerfile.prod .

# 拉取基础设施镜像
docker pull postgres:16-alpine
docker pull redis:7-alpine
docker pull nginx:alpine

# 导出为 tar 文件
docker save college-backend:1.0.0 -o deploy/images/college-backend-1.0.0.tar
docker save postgres:16-alpine -o deploy/images/postgres-16-alpine.tar
docker save redis:7-alpine -o deploy/images/redis-7-alpine.tar
docker save nginx:alpine -o deploy/images/nginx-alpine.tar
```

> 四个镜像 tar 合计约 293MB。`backend/Dockerfile.prod` 直接复制预编译 JAR，无需在服务器上执行 Maven 构建。

---

## 三、服务器环境安装（离线方式）

> 以下所有安装包均需从本地电脑下载后通过 SCP 传输到服务器。

### 3.1 已安装环境（当前状态）

| 组件 | 版本 | 安装方式 |
|------|------|---------|
| Docker | 29.1.3 | 离线 deb 包安装 |
| Docker Compose | v2.29.2 | 二进制文件直接放入 /usr/local/bin |
| Git | 2.43.0 | 离线 deb 包安装 |
| Node.js | v18.20.8 | 二进制 tar 解压到 /usr/local |
| npm | 10.8.2 | 随 Node.js 一起安装 |

### 3.2 离线安装 Docker（如需重装）

**本地下载 deb 包（从 USTC 镜像）：**

- `pigz_2.8-1_amd64.deb`
- `bridge-utils_1.7.1-1ubuntu2_amd64.deb`
- `runc_1.1.12-0ubuntu3_amd64.deb`
- `containerd_1.7.28-0ubuntu1~24.04.2_amd64.deb`
- `docker.io_29.1.3-0ubuntu3~24.04.2_amd64.deb`

**传输并安装：**

```bash
# 本地传输
scp *.deb user@10.10.0.27:~/

# 服务器上安装
ssh user@10.10.0.27
sudo dpkg -i pigz_*.deb bridge-utils_*.deb runc_*.deb containerd_*.deb docker.io_*.deb
sudo systemctl start docker && sudo systemctl enable docker
sudo usermod -aG docker $USER
```

### 3.3 离线安装 Docker Compose

```bash
# 本地下载 (GitHub Releases)
# https://github.com/docker/compose/releases/download/v2.29.2/docker-compose-linux-x86_64

# 传输并安装
scp docker-compose-linux-x86_64 user@10.10.0.27:~/docker-compose
ssh user@10.10.0.27 "sudo cp ~/docker-compose /usr/local/bin/docker-compose && sudo chmod +x /usr/local/bin/docker-compose"
```

### 3.4 离线安装 Git

```bash
# 本地下载 deb 包（从 USTC 镜像 ubuntu/pool/main/）
# liberror-perl_0.17029-2_all.deb
# git-man_2.43.0-1ubuntu7.3_all.deb
# git_2.43.0-1ubuntu7.3_amd64.deb

scp liberror-perl_*.deb git-man_*.deb git_*.deb user@10.10.0.27:~/
ssh user@10.10.0.27 "sudo dpkg -i ~/liberror-perl_*.deb ~/git-man_*.deb ~/git_*.deb"
```

### 3.5 离线安装 Node.js

```bash
# 本地下载 (npmmirror.com/mirrors/node/v18.20.8/node-v18.20.8-linux-x64.tar.xz)

scp node-v18.20.8-linux-x64.tar.xz user@10.10.0.27:~/
ssh user@10.10.0.27 "sudo tar -xf ~/node-v18.20.8-linux-x64.tar.xz -C /usr/local --strip-components=1"
# 验证: node --version → v18.20.8
```

---

## 四、部署项目（离线方式）

### 4.1 本地准备部署包

在开发电脑上，创建部署目录并收集所有文件：

```bash
mkdir -p ~/deploy-package && cd ~/deploy-package

# 复制镜像 tar 文件
cp college-service-platform/deploy/images/*.tar ./

# 复制前端构建产物
cp -r college-service-platform/frontend-admin/dist/ ./admin/

# 复制配置文件
cp college-service-platform/deploy/docker-compose.prod.yml ./docker-compose.yml
cp college-service-platform/deploy/.env.prod ./.env
cp college-service-platform/deploy/nginx.conf ./
cp college-service-platform/deploy/sql/schema.sql ./
```

### 4.2 传输到服务器

```bash
# 一次性传输整个部署包
scp -r ~/deploy-package/ user@10.10.0.27:~/deploy-package/
```

> 总大小约 500-600MB，视网络速度需要 5-15 分钟。

### 4.3 服务器上加载 Docker 镜像

```bash
ssh user@10.10.0.27
cd ~/deploy-package

# 加载镜像
docker load -i college-backend-1.0.0.tar
docker load -i postgres-16-alpine.tar
docker load -i redis-7-alpine.tar
docker load -i nginx-alpine.tar

# 验证
docker images
```

### 4.4 组织项目目录并启动

```bash
sudo mkdir -p /opt/college-service/{sql,admin}

# 放置文件
sudo cp ~/deploy-package/docker-compose.yml /opt/college-service/
sudo cp ~/deploy-package/.env /opt/college-service/
sudo cp ~/deploy-package/nginx.conf /opt/college-service/
sudo cp ~/deploy-package/schema.sql /opt/college-service/sql/
sudo cp -r ~/deploy-package/admin/* /opt/college-service/admin/
```

### 4.5 启动所有服务

```bash
cd /opt/college-service

# 启动（无需构建，所有镜像已预加载）
sudo docker-compose up -d

# 查看启动状态
sudo docker-compose ps

# 查看后端日志（确认启动成功）
sudo docker-compose logs -f backend
# 看到 "Started CollegeApplication" 表示成功
# Ctrl+C 退出日志
```

> `docker-compose.prod.yml` 中所有服务均使用 `image:` 引用预构建镜像，无需在服务器上执行任何构建操作。

### 4.6 验证部署

```bash
# 测试后端 API
curl http://localhost:8080/api/auth/login \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"studentId":"admin","password":"admin123"}'
# 应返回 {"code":200, ...}

# 测试 Nginx（管理端页面）
curl -I http://localhost
# 应返回 HTTP/1.1 200 OK
```

从外部浏览器访问：

- 管理端：`http://10.10.0.27`
- API：`http://10.10.0.27/api/doc.html`（如果开启了 Swagger）

---

## 五、小程序端配置

小程序需要将 API 地址指向服务器：

### 5.1 修改小程序 API 地址

编辑 `frontend-mp/src/api/index.js`：

```javascript
// 生产环境（改为服务器地址）
const BASE_URL = "http://10.10.0.27/api";
```

### 5.2 重新编译小程序

```bash
cd frontend-mp
npm run build:mp-weixin
```

### 5.3 注意事项

- 内网演示（10.10.0.27 是内网 IP）：微信开发者工具中勾选"不校验合法域名"
- 正式发布到微信：必须有已备案域名 + HTTPS 证书

---

## 六、常用运维命令

### 6.1 查看服务状态

```bash
cd /opt/college-service
sudo docker-compose ps
```

### 6.2 查看日志

```bash
docker-compose logs -f backend     # 后端日志
docker-compose logs -f postgres    # 数据库日志
docker-compose logs -f nginx       # Nginx 日志
docker-compose logs -f             # 所有服务
```

### 6.3 重启服务

```bash
docker-compose restart             # 重启所有
docker-compose restart backend     # 只重启后端
docker-compose restart nginx       # 只重启 Nginx
```

### 6.4 停止 / 销毁

```bash
docker-compose down                # 停止所有服务（保留数据）
docker-compose down -v             # 停止并删除数据（慎用！清空数据库）
```

### 6.5 进入容器调试

```bash
docker-compose exec backend sh
docker-compose exec postgres psql -U postgres -d college_service
docker-compose exec redis redis-cli
```

### 6.6 备份数据库

```bash
# 导出
docker-compose exec postgres pg_dump -U postgres college_service > backup_$(date +%Y%m%d).sql

# 恢复
docker-compose exec -T postgres psql -U postgres college_service < backup_20260520.sql
```

---

## 七、更新部署（离线方式）

代码更新后重新部署的流程：

```bash
# === 本地操作 ===

# 1. 重新构建后端
cd backend && ./mvnw.cmd package -DskipTests && cd ..

# 2. 重新构建前端（如有改动）
cd frontend-admin && npm run build && cd ..

# 3. 重新构建后端镜像
docker build -t college-backend:1.0.0 -f backend/Dockerfile.prod backend/
docker save college-backend:1.0.0 -o deploy/images/college-backend-1.0.0.tar

# 4. 传输到服务器
scp deploy/images/college-backend-1.0.0.tar user@10.10.0.27:/tmp/
scp -r frontend-admin/dist/* user@10.10.0.27:/opt/college-service/admin/

# === 服务器操作 ===
ssh user@10.10.0.27

# 5. 加载新镜像并重启
docker load -i /tmp/college-backend-1.0.0.tar
cd /opt/college-service
sudo docker-compose restart backend

# 6. 如果修改了 nginx.conf
sudo docker-compose restart nginx
```

---

## 八、切换到 Kingbase 数据库

当前默认使用 PostgreSQL 16。如需切换到 Kingbase：

### 方式一：使用学校提供的 Kingbase 服务器

修改 `.env`：

```env
DB_HOST=10.10.0.50
DB_PORT=54321
DB_NAME=college_service
DB_USER=system
DB_PASSWORD=学校提供的密码
```

修改 `application-prod.yml` 中的驱动：

```yaml
spring:
  datasource:
    url: jdbc:kingbase8://${DB_HOST}:${DB_PORT}/${DB_NAME}
    driver-class-name: com.kingbase8.Driver
```

在 `pom.xml` 中启用 Kingbase 驱动，重新打包 JAR 后传输到服务器。

### 方式二：继续使用 PostgreSQL（当前方案）

无需修改。PostgreSQL 和 Kingbase SQL 语法完全兼容，适用于开发和答辩演示。

---

## 九、故障排查

### 后端启动失败

```bash
docker-compose logs backend | tail -50
```

常见原因：
- 数据库未就绪 → 已通过 healthcheck + depends_on 解决
- 端口被占用 → `sudo lsof -i :8080`
- 内存不足 → `free -h`

### Nginx 502 Bad Gateway

```bash
docker-compose ps backend          # 检查后端是否在运行
docker-compose logs backend | tail -20
```

### 镜像加载失败

```bash
docker images                      # 确认镜像已加载
docker load < xxx.tar              # 重新加载
```

### 磁盘空间不足

```bash
df -h
docker system prune -a             # 清理无用镜像
```

---

## 十、环境检查命令（一键验证）

```bash
docker --version && docker-compose --version && git --version && node --version && npm --version && sudo systemctl status docker --no-pager | head -5
```

---

## 十一、快速部署命令汇总

假设所有文件已传输到服务器 `~/deploy-package/`：

```bash
# 1. 加载 Docker 镜像
cd ~/deploy-package
docker load -i college-backend-1.0.0.tar
docker load -i postgres-16-alpine.tar
docker load -i redis-7-alpine.tar
docker load -i nginx-alpine.tar

# 2. 部署到 /opt/college-service
sudo mkdir -p /opt/college-service/{sql,admin}
sudo cp docker-compose.yml /opt/college-service/
sudo cp .env /opt/college-service/
sudo cp nginx.conf /opt/college-service/
sudo cp schema.sql /opt/college-service/sql/
sudo cp -r admin/* /opt/college-service/admin/

# 3. 启动
cd /opt/college-service
sudo docker-compose up -d

# 4. 验证
sudo docker-compose ps
curl -s http://localhost:8080/api/auth/login -X POST -H "Content-Type: application/json" -d '{"studentId":"admin","password":"admin123"}' | head -c 100
```

部署完成后访问：`http://10.10.0.27`
