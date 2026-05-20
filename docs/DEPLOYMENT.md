# 服务器部署操作手册

> 目标服务器：10.10.0.27
> 操作系统：Linux
> 用户名：user
> 本文档记录从零部署到系统可访问的完整步骤。

---

## 一、部署前准备

### 1.1 本地构建前端

在你的开发电脑上执行（不是服务器上）：

```bash
cd college-service-platform/frontend-admin
npm install
npm run build
```

构建完成后会生成 `frontend-admin/dist/` 目录，这就是管理端的静态文件。

### 1.2 确认代码可编译

```bash
cd college-service-platform/backend
./mvnw.cmd compile
# 确保 BUILD SUCCESS
```

---

## 二、连接服务器

```bash
ssh user@10.10.0.27
# 密码: Yb9GU5N%NZ!}7J1@Fx4u
```

> 如果提示 `Are you sure you want to continue connecting`，输入 `yes`。

---

## 三、服务器环境安装

### 3.1 安装 Docker

```bash
# 检查是否已安装
docker --version

# 如果没有，执行以下安装（Ubuntu/Debian）
sudo apt update
sudo apt install -y docker.io docker-compose-plugin

# 启动 Docker 并设置开机自启
sudo systemctl start docker
sudo systemctl enable docker

# 将当前用户加入 docker 组（免 sudo）
sudo usermod -aG docker $USER

# 重新登录使组权限生效
exit
ssh user@10.10.0.27
```

### 3.2 安装 Docker Compose（如果 docker compose 命令不可用）

```bash
# 检查
docker compose version

# 如果没有，安装独立版
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
docker-compose --version
```

### 3.3 安装 Git（用于拉取代码）

```bash
sudo apt install -y git
```

---

## 四、部署项目

### 4.1 拉取代码

```bash
cd ~
git clone https://github.com/liufuhuaxian6/college-service-platform.git
cd college-service-platform
git checkout main   # 或 dev，取决于你要部署哪个版本
```

### 4.2 构建前端（在服务器上）

如果服务器有 Node.js：

```bash
# 安装 Node.js（如果没有）
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt install -y nodejs

# 构建管理端前端
cd frontend-admin
npm install
npm run build
cd ..
```

如果服务器没有 Node.js，可以在本地构建后把 `dist/` 目录上传：

```bash
# 在本地电脑执行
scp -r frontend-admin/dist/ user@10.10.0.27:~/college-service-platform/frontend-admin/dist/
```

### 4.3 创建环境配置文件

```bash
cd ~/college-service-platform

# 复制示例配置
cp .env.example .env

# 编辑配置（修改密码等敏感信息）
nano .env
```

`.env` 文件内容（根据实际情况修改）：

```env
# 数据库配置
DB_HOST=postgres
DB_PORT=5432
DB_NAME=college_service
DB_USER=postgres
DB_PASSWORD=College@2026!Secure

# Redis 配置
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=

# Spring Boot
SPRING_PROFILES_ACTIVE=prod

# JWT 密钥（务必修改为随机字符串）
JWT_SECRET=YourProductionSecretKeyMustBeAtLeast32BytesLong!!

# 文件上传
FILE_UPLOAD_PATH=./uploads
FILE_MAX_SIZE=31457280
```

### 4.4 创建 SSL 目录（可选，有域名时配置）

```bash
mkdir -p deploy/ssl
# 如果有 SSL 证书，放入：
# deploy/ssl/fullchain.pem
# deploy/ssl/privkey.pem
# 然后取消 deploy/nginx.conf 中 HTTPS 部分的注释
```

### 4.5 启动所有服务

```bash
cd ~/college-service-platform

# 首次启动（会自动构建后端 Docker 镜像，约 5-10 分钟）
docker compose up -d

# 查看启动状态
docker compose ps

# 查看后端日志（确认启动成功）
docker compose logs -f backend
# 看到 "Started CollegeApplication" 表示成功
# Ctrl+C 退出日志
```

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
// 开发环境
// const BASE_URL = 'http://localhost:8080/api'

// 生产环境（改为服务器地址）
const BASE_URL = 'http://10.10.0.27/api'

// 如果有域名和 HTTPS
// const BASE_URL = 'https://your-domain.com/api'
```

### 5.2 重新编译小程序

```bash
cd frontend-mp
npm run build:mp-weixin
```

### 5.3 注意事项

- 如果只是内网演示（10.10.0.27 是内网 IP），微信开发者工具中勾选"不校验合法域名"即可
- 如果要正式发布到微信，必须有已备案域名 + HTTPS 证书

---

## 六、常用运维命令

### 6.1 查看服务状态

```bash
cd ~/college-service-platform
docker compose ps
```

输出示例：
```
NAME                IMAGE               STATUS          PORTS
backend             ...                 Up (healthy)    0.0.0.0:8080->8080/tcp
postgres            postgres:16-alpine  Up (healthy)    0.0.0.0:5432->5432/tcp
redis               redis:7-alpine      Up (healthy)    0.0.0.0:6379->6379/tcp
nginx               nginx:alpine        Up              0.0.0.0:80->80/tcp
```

### 6.2 查看日志

```bash
# 后端日志
docker compose logs -f backend

# 数据库日志
docker compose logs -f postgres

# Nginx 日志
docker compose logs -f nginx

# 所有服务日志
docker compose logs -f
```

### 6.3 重启服务

```bash
# 重启所有
docker compose restart

# 只重启后端
docker compose restart backend

# 只重启 Nginx（修改了 nginx.conf 后）
docker compose restart nginx
```

### 6.4 停止所有服务

```bash
docker compose down
```

### 6.5 停止并删除数据（慎用！会清空数据库）

```bash
docker compose down -v
```

### 6.6 更新代码后重新部署

```bash
cd ~/college-service-platform

# 拉取最新代码
git pull origin main

# 重新构建前端
cd frontend-admin && npm install && npm run build && cd ..

# 重新构建后端镜像并启动
docker compose up -d --build backend

# 如果修改了 nginx.conf
docker compose restart nginx
```

### 6.7 进入容器调试

```bash
# 进入后端容器
docker compose exec backend sh

# 进入数据库容器
docker compose exec postgres psql -U postgres -d college_service

# 进入 Redis 容器
docker compose exec redis redis-cli
```

### 6.8 备份数据库

```bash
# 导出
docker compose exec postgres pg_dump -U postgres college_service > backup_$(date +%Y%m%d).sql

# 恢复
docker compose exec -T postgres psql -U postgres college_service < backup_20260520.sql
```

---

## 七、切换到 Kingbase 数据库

当前默认使用 PostgreSQL 16。如果学校提供了 Kingbase 环境（独立服务器或 Docker 镜像），按以下步骤切换：

### 方式一：使用学校提供的 Kingbase 服务器（推荐）

如果学校已经有一台装好 Kingbase 的服务器（比如 `10.10.0.50:54321`）：

**1. 修改 `.env`**：

```env
DB_HOST=10.10.0.50
DB_PORT=54321
DB_NAME=college_service
DB_USER=system
DB_PASSWORD=学校提供的密码
```

**2. 修改 `application-prod.yml`**（切换驱动）：

```yaml
spring:
  datasource:
    url: jdbc:kingbase8://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    driver-class-name: com.kingbase8.Driver
```

**3. 在 `pom.xml` 中启用 Kingbase 驱动**：

取消 Kingbase 依赖的注释（或手动添加 jar 到项目）：

```xml
<dependency>
    <groupId>com.kingbase8</groupId>
    <artifactId>kingbase8</artifactId>
    <version>8.6.0</version>
</dependency>
```

> 注意：Kingbase JDBC 驱动不在 Maven 中央仓库，需要从金仓官网下载 jar 后手动安装到本地仓库：
> ```bash
> mvn install:install-file -Dfile=kingbase8-8.6.0.jar -DgroupId=com.kingbase8 -DartifactId=kingbase8 -Dversion=8.6.0 -Dpackaging=jar
> ```

**4. 在 Kingbase 上建表**：

```bash
# 连接 Kingbase（ksql 是 Kingbase 的命令行工具，语法同 psql）
ksql -U system -d college_service -h 10.10.0.50 -p 54321 -f deploy/sql/schema.sql
```

**5. 从 docker-compose.yml 中移除 postgres 容器**（因为不再需要）：

删除或注释掉 `services.postgres` 整个块，同时删除 `backend.depends_on` 中的 `postgres`。

**6. 重新构建并启动**：

```bash
docker compose up -d --build backend
```

### 方式二：使用 Kingbase Docker 镜像

如果金仓公司提供了 Docker 镜像（如 `kingbase/kingbase:v8`）：

**1. 修改 `docker-compose.yml`**，将 postgres 服务替换为：

```yaml
services:
  kingbase:
    image: kingbase/kingbase:v8    # 替换为实际镜像名
    ports:
      - "54321:54321"
    environment:
      DB_USER: system
      DB_PASSWORD: ${DB_PASSWORD}
      DB_NAME: ${DB_NAME}
    volumes:
      - db-data:/var/lib/kingbase/data
      - ./deploy/sql/schema.sql:/docker-entrypoint-initdb.d/01-schema.sql
    restart: unless-stopped
```

**2. 修改 `.env`**：

```env
DB_HOST=kingbase
DB_PORT=54321
DB_USER=system
```

**3. 修改 `application-prod.yml`** 和 `pom.xml`（同方式一的步骤 2、3）。

**4. 启动**：

```bash
docker compose up -d
```

### 方式三：继续使用 PostgreSQL（当前方案）

不需要任何修改。PostgreSQL 和 Kingbase 的 SQL 语法完全兼容，`schema.sql` 在两者上都能正常执行。当前方案适用于：
- 开发阶段
- 答辩演示
- 学校未提供 Kingbase 环境时

### 切换对照表

| 配置项 | PostgreSQL（当前） | Kingbase |
|--------|-------------------|----------|
| `.env` DB_HOST | postgres（容器名） | kingbase 或学校 IP |
| `.env` DB_PORT | 5432 | 54321 |
| `.env` DB_USER | postgres | system |
| driver-class-name | org.postgresql.Driver | com.kingbase8.Driver |
| JDBC URL 前缀 | jdbc:postgresql:// | jdbc:kingbase8:// |
| pom.xml 依赖 | postgresql（已有） | kingbase8（需添加） |
| SQL 语法 | 相同 | 相同 |

---

## 八、防火墙配置

如果服务器有防火墙，需要开放以下端口：

```bash
# Ubuntu UFW
sudo ufw allow 80/tcp    # HTTP
sudo ufw allow 443/tcp   # HTTPS（如果配置了）
sudo ufw allow 22/tcp    # SSH（确保不要把自己锁在外面）

# 不要开放 5432 和 6379 到外网（数据库和 Redis 只在 Docker 内部通信）
```

---

## 九、部署架构图

```
外部访问
  │
  ▼
┌─────────────────────────────────────────────────┐
│           服务器 10.10.0.27                       │
│                                                  │
│  ┌────────────────────────────────────────────┐ │
│  │         Docker Compose                      │ │
│  │                                             │ │
│  │  ┌─────────────┐                           │ │
│  │  │ Nginx :80   │ ← 浏览器/小程序访问入口    │ │
│  │  │  / → 静态文件│                           │ │
│  │  │  /api → 后端 │                           │ │
│  │  └──────┬──────┘                           │ │
│  │         │                                   │ │
│  │  ┌──────▼──────┐                           │ │
│  │  │ Backend     │                           │ │
│  │  │ :8080       │                           │ │
│  │  │ Spring Boot │                           │ │
│  │  └───┬─────┬───┘                           │ │
│  │      │     │                                │ │
│  │  ┌───▼───┐ ┌▼──────┐                       │ │
│  │  │Postgres│ │ Redis │                       │ │
│  │  │ :5432  │ │ :6379 │                       │ │
│  │  └───────┘ └───────┘                       │ │
│  └────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────┘
```

---

## 十、故障排查

### 后端启动失败

```bash
docker compose logs backend | tail -50
```

常见原因：
- 数据库还没启动完就连接 → 已通过 healthcheck + depends_on 解决
- 端口被占用 → `sudo lsof -i :8080`
- 内存不足 → `free -h` 检查

### 数据库连接失败

```bash
# 检查数据库容器是否在运行
docker compose ps postgres

# 手动测试连接
docker compose exec postgres psql -U postgres -c "SELECT 1;"
```

### Nginx 502 Bad Gateway

说明 Nginx 无法连接到后端：
```bash
# 检查后端是否在运行
docker compose ps backend

# 检查后端日志
docker compose logs backend | tail -20
```

### 磁盘空间不足

```bash
df -h
# 清理 Docker 无用镜像
docker system prune -a
```

---

## 十一、快速部署命令汇总（一键复制）

首次部署完整流程：

```bash
# 1. SSH 连接服务器
ssh user@10.10.0.27

# 2. 安装 Docker（如果没有）
sudo apt update && sudo apt install -y docker.io docker-compose-plugin git
sudo systemctl start docker && sudo systemctl enable docker
sudo usermod -aG docker $USER && exit

# 3. 重新连接
ssh user@10.10.0.27

# 4. 拉取代码
cd ~ && git clone https://github.com/liufuhuaxian6/college-service-platform.git
cd college-service-platform && git checkout main

# 5. 安装 Node.js 并构建前端
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt install -y nodejs
cd frontend-admin && npm install && npm run build && cd ..

# 6. 配置环境变量
cp .env.example .env
nano .env  # 修改密码

# 7. 启动
docker compose up -d

# 8. 验证
docker compose ps
curl http://localhost/api/auth/login -X POST -H "Content-Type: application/json" -d '{"studentId":"admin","password":"admin123"}'
```

部署完成后访问：`http://10.10.0.27`
