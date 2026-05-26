# 服务器部署操作手册（离线部署版）

> 目标服务器：10.10.0.27
> 操作系统：Ubuntu 24.04 LTS
> 用户名：user
> 特殊约束：**服务器无外网访问能力**，所有依赖必须从本地传输
> 本文档记录从零部署到系统可访问的完整步骤。

---

## 零、推荐方式：脚本化一键部署（首次 + 后续更新都用它）

仓库里 `scripts/` 目录下有两个脚本封装了完整的离线部署流程。**第一次部署和后续每次更新的步骤完全一样**，只要重新跑这两条命令即可：

```
┌──────── 本地开发电脑 (有外网) ─────────┐    ┌──── 服务器 10.10.0.27 (内网) ────┐
│                                       │    │                                  │
│  pwsh scripts/build-deploy-package.ps1│    │  cd ~/deploy-package             │
│        │                              │    │  bash deploy.sh                  │
│        ▼ 产出 deploy-package/          │    │        │                         │
│        │  ├─ images/*.tar (5 个)      │    │        ▼ 自动:                   │
│        │  ├─ models/bge-small-zh.../  │SCP │        │  - docker load 5 镜像    │
│        │  ├─ admin/ (前端 dist)       │───►│        │  - rsync 配置/模型/前端  │
│        │  ├─ docker-compose.yml       │    │        │  - docker compose up -d  │
│        │  ├─ .env / nginx.conf / sql  │    │        │  - 120s 健康检查         │
│        │  └─ deploy.sh                │    │        │  - 冒烟测试 3 个端点      │
│                                       │    │                                  │
└───────────────────────────────────────┘    └──────────────────────────────────┘
```

### 0.1 本地：构建并打包

```powershell
# 仓库根目录执行（Windows + Docker Desktop + curl.exe）
pwsh scripts/build-deploy-package.ps1
```

脚本会**幂等地**完成 7 步：

| 步骤 | 做什么 | 增量逻辑 |
|---|---|---|
| 0/7 | 检查 docker / curl.exe 可用 | — |
| 1/7 | `mvnw package -DskipTests` 编译后端 JAR | 总是执行（Maven 自己增量） |
| 2/7 | docker build + save 后端镜像 | 如果 tar 已存在则跳过 |
| 3/7 | docker pull + save 4 个基础镜像 | 同上，每个独立判断 |
| 4/7 | 从 hf-mirror 下载 BGE 模型 10 个文件 | 文件已存在 + 体积 > 50 字节就跳过 |
| 5/7 | `npm run build` 管理端前端 | — |
| 6/7 | 装配 `deploy-package/` 目录 | 每次清空重组（除 images 缓存） |
| 7/7 | 打印体积清单和下一步命令 | — |

**常用参数：**

```powershell
pwsh scripts/build-deploy-package.ps1                  # 默认增量
pwsh scripts/build-deploy-package.ps1 -SkipFrontend    # 只改了后端时
pwsh scripts/build-deploy-package.ps1 -SkipBackend     # 只改了前端/模型时
pwsh scripts/build-deploy-package.ps1 -Force           # 强制重建所有镜像 tar 和重下模型
```

产出位置：`deploy-package/`（已在 `.gitignore`，不会入库），大约 1.5GB。

### 0.2 传输 + 服务器部署

```bash
# 整包传一次（之后只需要传变化的子目录，见下方"增量更新"）
scp -r deploy-package user@10.10.0.27:~/

# SSH 上去执行 deploy.sh
ssh user@10.10.0.27 'cd ~/deploy-package && bash deploy.sh'
```

`deploy.sh` 会**自动判定**部署模式：

| 模式 | 触发条件 | 行为 |
|---|---|---|
| **fresh** | `/opt/college-service/` 不存在或为空 | docker load + 全量 cp + `docker compose up -d` |
| **增量** | 目标目录已有部署 | docker load（已加载的镜像 noop）+ rsync 同步变化 + `docker compose up -d --remove-orphans`（变化的容器会被重建） |
| **--fresh** | 命令行强制 | 先 `docker compose down -v` 清空数据卷（**会丢数据库数据**，需确认）再走 fresh 流程 |
| **--restart-only** | 命令行强制 | 不动镜像和文件，只 `docker compose restart` |

随后做 6 步操作：

```
0/6 校验部署包结构完整
1/6 判定 fresh / 增量 / restart 模式
2/6 docker load 所有镜像 tar
3/6 同步配置 + admin/ + models/ 到 /opt/college-service
4/6 docker compose up -d (5 个容器)
5/6 健康检查 (最长等 120 秒, 所有容器需 healthy)
6/6 冒烟测试 embedding / 后端登录 / 管理端首页
```

成功输出例：

```
==================================================================
  部署完成: http://10.10.0.27
  默认登录: admin / admin123  (生产环境请立即修改)
  日志:    docker compose -f /opt/college-service/docker-compose.yml logs -f
==================================================================
```

### 0.3 后续更新（这才是日常用的）

代码改完后，本地：

```powershell
pwsh scripts/build-deploy-package.ps1   # 增量构建, 没改的部分秒跳过
```

只传变化的子目录省带宽：

```bash
# 只改了后端
scp deploy-package/images/college-backend-1.0.0.tar user@10.10.0.27:~/deploy-package/images/

# 只改了前端
scp -r deploy-package/admin user@10.10.0.27:~/deploy-package/

# 然后服务器上重跑
ssh user@10.10.0.27 'cd ~/deploy-package && bash deploy.sh'
```

`deploy.sh` 在增量模式下会做 `docker compose up -d --remove-orphans`：

- 镜像 tag 没变但 sha 变了的容器（如重建后的 `college-backend:1.0.0`）会被重建并启动
- 没变化的容器（postgres / redis / embedding / nginx）保持运行不动
- 数据卷（db-data, redis-data, upload-data, tei-data）始终保留

### 0.4 清空旧部署, 从零再部一次

由于 postgres 的 `docker-entrypoint-initdb.d/schema.sql` 仅在**数据卷为空**时执行，
旧库残留旧 schema 时新加的字段（如 `sys_user.email`、`sys_notification.tags`、
`qa_document.doc_type`、入党流程 29 步）不会自动出现。最稳的办法是清空整个部署
重来一次（**会丢所有数据库数据**，仅在演示前/不重要数据时使用）：

```bash
ssh user@10.10.0.27
cd /opt/college-service 2>/dev/null && sudo docker compose down -v   # 删容器 + 4 个 volume
sudo rm -rf /opt/college-service
rm -rf ~/deploy-package
docker image prune -af            # (可选) 释放磁盘
exit
```

然后在本地重新跑打包 + scp + `bash deploy.sh`，会自动进入 `fresh` 模式：
schema.sql 跑一次 → 16 张表 + 默认 admin/admin123 + 入党 29 步 + 入团 5 步 +
4 种审批类型 全到位。

### 0.5 如果出错怎么办

`deploy.sh` 任何一步失败都会以非零退出码停下并打印彩色 `[FAIL]` 行。最常见排查：

```bash
# 看哪个容器没起来
cd /opt/college-service
docker compose ps

# 看具体容器日志
docker compose logs --tail 50 embedding   # TEI 起不来最常见
docker compose logs --tail 50 backend     # 后端起不来次之
docker compose logs --tail 50 postgres    # 数据库异常少见

# 完整故障排查清单见 §九（基础设施）和 §12.6（embedding 专项）
```

---

> 下面的 §一 ~ §十二 是**手动分步流程**，相当于上面脚本背后做的事的展开说明。
> 不需要照着敲，**对脚本行为有疑问时回来查具体细节**用。

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
| `pgvector-pg16.tar` | `docker save` | PostgreSQL + pgvector 镜像（用于 RAG 向量检索） |
| `tei-cpu-1.5.tar` | `docker save` | HuggingFace TEI Embedding 推理服务镜像 |
| `redis-7-alpine.tar` | `docker save` | Redis 镜像 |
| `nginx-alpine.tar` | `docker save` | Nginx 镜像 |
| `frontend-admin/dist/` | `npm run build` | 管理端静态文件 |
| `models/bge-small-zh-v1.5/` | 从 hf-mirror 下载 | BGE 中文 embedding 模型（含 ONNX 权重） |
| `docker-compose.prod.yml` | `deploy/` 目录 | 生产编排配置（使用 image 引用） |
| `deploy/nginx.conf` | 项目目录 | Nginx 配置 |
| `deploy/sql/schema.sql` | 项目目录 | 数据库建表脚本 |
| `.env.prod` | `deploy/` 目录 | 环境变量配置（含 DB / Redis / **`MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_AUTH_CODE`** 邮件相关） |

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
docker pull pgvector/pgvector:pg16
docker pull redis:7-alpine
docker pull nginx:alpine
# TEI Embedding 服务（国内用南京大学 GHCR 镜像；如果你能直连 ghcr.io 也可改回原仓库）
docker pull ghcr.nju.edu.cn/huggingface/text-embeddings-inference:cpu-1.5

# 导出为 tar 文件
mkdir -p deploy/images
docker save college-backend:1.0.0 -o deploy/images/college-backend-1.0.0.tar
docker save pgvector/pgvector:pg16 -o deploy/images/pgvector-pg16.tar
docker save redis:7-alpine -o deploy/images/redis-7-alpine.tar
docker save nginx:alpine -o deploy/images/nginx-alpine.tar
docker save ghcr.nju.edu.cn/huggingface/text-embeddings-inference:cpu-1.5 \
  -o deploy/images/tei-cpu-1.5.tar
```

> 五个镜像 tar 合计约 1.1GB（TEI cpu 镜像约 800MB 是大头）。`backend/Dockerfile.prod` 直接复制预编译 JAR，无需在服务器上执行 Maven 构建。

### 2.4 预下载 BGE 中文 Embedding 模型

TEI 1.5 自带下载器有 `HF_ENDPOINT` bug，需要预下载模型挂载到容器（详见 §12.1）。在项目根目录执行：

```powershell
# Windows PowerShell
$base = "https://hf-mirror.com/BAAI/bge-small-zh-v1.5/resolve/main"
$xenova = "https://hf-mirror.com/Xenova/bge-small-zh-v1.5/resolve/main"
$dest = ".\models\bge-small-zh-v1.5"
New-Item -ItemType Directory -Path "$dest\onnx", "$dest\1_Pooling" -Force | Out-Null

@(
  "config.json", "tokenizer.json", "tokenizer_config.json", "special_tokens_map.json",
  "vocab.txt", "modules.json", "config_sentence_transformers.json",
  "sentence_bert_config.json", "1_Pooling/config.json"
) | ForEach-Object { curl.exe -fSL --retry 5 -o "$dest\$_" "$base/$_" }

curl.exe -fSL --retry 5 -o "$dest\onnx\model.onnx" "$xenova/onnx/model.onnx"
```

下载完成后 `models/bge-small-zh-v1.5/` 目录约 183MB，包含 10 个配置文件 + 1 个 ONNX 权重，待传输到服务器。

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

# 复制镜像 tar 文件（5 个：backend, pgvector, tei, redis, nginx）
cp college-service-platform/deploy/images/*.tar ./

# 复制前端构建产物
cp -r college-service-platform/frontend-admin/dist/ ./admin/

# 复制 BGE embedding 模型目录（约 183MB，TEI 容器挂载使用）
cp -r college-service-platform/models/ ./models/

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

> 总大小约 1.5-1.8GB（含 TEI 镜像 ~800MB + BGE 模型 ~183MB + 其他），视网络速度需要 20-60 分钟。

### 4.3 服务器上加载 Docker 镜像

```bash
ssh user@10.10.0.27
cd ~/deploy-package

# 加载镜像（5 个）
docker load -i college-backend-1.0.0.tar
docker load -i pgvector-pg16.tar
docker load -i tei-cpu-1.5.tar
docker load -i redis-7-alpine.tar
docker load -i nginx-alpine.tar

# 验证（应看到上述 5 个镜像）
docker images
```

### 4.4 组织项目目录并启动

```bash
sudo mkdir -p /opt/college-service/{sql,admin,models}

# 放置文件
sudo cp ~/deploy-package/docker-compose.yml /opt/college-service/
sudo cp ~/deploy-package/.env /opt/college-service/
sudo cp ~/deploy-package/nginx.conf /opt/college-service/
sudo cp ~/deploy-package/schema.sql /opt/college-service/sql/
sudo cp -r ~/deploy-package/admin/* /opt/college-service/admin/

# 拷贝 BGE 模型到 docker-compose 期望的挂载源目录
sudo cp -r ~/deploy-package/models/bge-small-zh-v1.5 /opt/college-service/models/
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
# 5 个容器都应 Up (healthy)
sudo docker-compose ps

# 测试 embedding 服务（容器内访问，端口 80）
sudo docker-compose exec embedding wget -qO- \
  --post-data='{"input":"测试"}' \
  --header='Content-Type: application/json' \
  http://localhost:80/v1/embeddings | head -c 200
# 应返回 {"data":[{"embedding":[0.028,0.067,...]}]}，数组长度 = 512

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
docker-compose logs -f embedding   # TEI Embedding 日志（启动慢时常看，看 "Ready"）
docker-compose logs -f nginx       # Nginx 日志
docker-compose logs -f             # 所有服务
```

### 6.3 重启服务

```bash
docker-compose restart             # 重启所有
docker-compose restart backend     # 只重启后端
docker-compose restart embedding   # 只重启 Embedding 服务
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
docker-compose exec embedding ls /models/bge-small-zh-v1.5    # 确认模型文件挂载正常
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

# 7. 如果切换了 embedding 模型（如 BGE-small -> BGE-base）
#    需要先修改 application-prod.yml 的 rag.embedding-dim，
#    重新打包后端镜像，并在数据库执行 rag_migrate_*.sql，最后管理端"重新索引"所有文档
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
- Embedding 服务未就绪 → backend 会被 `depends_on: embedding (service_healthy)` 阻塞，先查 embedding 日志
- 端口被占用 → `sudo lsof -i :8080`
- 内存不足 → `free -h`

### Embedding 服务相关问题

完整故障排查见 [§12.6](#126-故障排查)。最常见 3 类问题：

```bash
docker-compose logs embedding | tail -30
```

- `restarting (1)` + 日志 `relative URL without a base` → BGE 模型目录未挂载，检查 `models/bge-small-zh-v1.5` 是否存在
- `Could not start backend: File ".../onnx/model.onnx" does not exist` → 缺 ONNX 权重，重下 `Xenova/bge-small-zh-v1.5` 的 onnx 文件
- 后端日志 `Embedding dimension mismatch` → 配置的 `rag.embedding-dim` 与模型实际维度不符

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
docker load -i pgvector-pg16.tar
docker load -i tei-cpu-1.5.tar
docker load -i redis-7-alpine.tar
docker load -i nginx-alpine.tar

# 2. 部署到 /opt/college-service
sudo mkdir -p /opt/college-service/{sql,admin,models}
sudo cp docker-compose.yml /opt/college-service/
sudo cp .env /opt/college-service/
sudo cp nginx.conf /opt/college-service/
sudo cp schema.sql /opt/college-service/sql/
sudo cp -r admin/* /opt/college-service/admin/
sudo cp -r models/bge-small-zh-v1.5 /opt/college-service/models/

# 3. 启动（5 个容器：postgres + redis + embedding + backend + nginx）
cd /opt/college-service
sudo docker-compose up -d

# 4. 验证
sudo docker-compose ps
# 4.1 embedding 服务
curl -s http://localhost:8081/v1/embeddings -X POST -H "Content-Type: application/json" \
  --data '{"input":"测试"}' | head -c 100
# 4.2 后端登录
curl -s http://localhost:8080/api/auth/login -X POST -H "Content-Type: application/json" \
  -d '{"studentId":"admin","password":"admin123"}' | head -c 100
```

部署完成后访问：`http://10.10.0.27`

---

## 十二、RAG / Embedding 服务部署（BGE-small-zh-v1.5 + TEI）

智能问答模块使用 RAG 检索增强生成，依赖一个 HTTP embedding 服务把中文文本转成 512 维向量。当前选用 **HuggingFace Text Embeddings Inference (TEI)** 容器 + **BAAI BGE-small-zh-v1.5** 模型。

当前开发环境默认：

| 项 | 值 |
|---|---|
| PostgreSQL / pgvector | `localhost:5433` |
| 数据库 | `college_service` |
| 用户名 / 密码 | `postgres / postgres` |
| Embedding API | `http://localhost:8081/v1/embeddings` |
| 后端 API | `http://localhost:8080/api` |

`application-dev.yml` 已开启 `rag.enabled=true`，开发环境切片大小为 450 字符；`application.yml` 保留生产默认 700 字符、120 字符重叠、`top-k=4`、`min-score=0.5`。

### 12.1 国内网络注意事项

| 资源 | 国内访问问题 | 解决方案 |
|---|---|---|
| `ghcr.io/huggingface/text-embeddings-inference` | 经常 timeout / EOF | 改用 `ghcr.nju.edu.cn/huggingface/text-embeddings-inference:cpu-1.5`（南京大学镜像） |
| `huggingface.co` 模型权重 | 直连失败 | 走 `hf-mirror.com` 国内镜像 |
| TEI 1.5 的 `HF_ENDPOINT` 环境变量 | hf-hub 0.3.2 Rust 库存在 bug，下载 `config.json` 时 URL 拼接失败 | **预下载模型挂载到容器**（推荐） |
| TEI CPU 镜像 | 仅支持 ONNX 推理，不支持 safetensors | 使用 `Xenova/bge-small-zh-v1.5` 的 ONNX 转换版 |

### 12.2 预下载 BGE 模型（开发电脑执行）

```powershell
# 在项目根目录执行
$base = "https://hf-mirror.com/BAAI/bge-small-zh-v1.5/resolve/main"
$xenova = "https://hf-mirror.com/Xenova/bge-small-zh-v1.5/resolve/main"
$dest = ".\models\bge-small-zh-v1.5"
New-Item -ItemType Directory -Path "$dest\onnx" -Force | Out-Null
New-Item -ItemType Directory -Path "$dest\1_Pooling" -Force | Out-Null

# 主模型文件（10 个，约 95MB）
@(
  "config.json", "tokenizer.json", "tokenizer_config.json", "special_tokens_map.json",
  "vocab.txt", "modules.json", "config_sentence_transformers.json",
  "sentence_bert_config.json", "1_Pooling/config.json"
) | ForEach-Object {
  curl.exe -fSL --retry 5 -o "$dest\$_" "$base/$_"
}

# ONNX 权重（90MB，来自社区 Xenova 转换版）
curl.exe -fSL --retry 5 -o "$dest\onnx\model.onnx" "$xenova/onnx/model.onnx"
```

下载完毕后 `models/bge-small-zh-v1.5/` 目录约 183MB（已在 `.gitignore`，不会入 Git）。

### 12.3 启动 TEI 服务（开发环境）

`docker-compose.yml` 已配置好 `embedding` 服务，挂载本地模型目录：

```yaml
embedding:
  image: ghcr.nju.edu.cn/huggingface/text-embeddings-inference:cpu-1.5
  command: --model-id /models/bge-small-zh-v1.5 --max-batch-tokens 16384
  ports:
    - "8081:80"
  volumes:
    - ./models/bge-small-zh-v1.5:/models/bge-small-zh-v1.5:ro
    - tei-data:/data
```

启动并验证：

```powershell
docker compose up -d embedding
docker compose logs --tail 20 embedding   # 看到 "Ready" 即可

# 测试调用（用文件传 UTF-8 中文，避免 shell 转义问题）
echo '{"input":"入党流程是什么"}' > req.json
curl -X POST http://localhost:8081/v1/embeddings -H "Content-Type: application/json" --data-binary @req.json
# 应返回 {"data":[{"embedding":[0.028, 0.067, ...]}]}，数组长度=512
```

### 12.4 离线部署服务器

服务器无外网时，本地一次性导出 TEI 镜像 + 传输模型目录：

```bash
# 本地有网电脑
docker pull ghcr.nju.edu.cn/huggingface/text-embeddings-inference:cpu-1.5
docker save ghcr.nju.edu.cn/huggingface/text-embeddings-inference:cpu-1.5 -o deploy/images/tei-cpu-1.5.tar
# models/ 目录已经下载好（约 183MB）

# 传到服务器
scp deploy/images/tei-cpu-1.5.tar user@10.10.0.27:/tmp/
scp -r models/ user@10.10.0.27:/opt/college-service/

# 服务器上
docker load -i /tmp/tei-cpu-1.5.tar
cd /opt/college-service && sudo docker-compose up -d embedding
```

`deploy/docker-compose.prod.yml` 已经包含 `embedding` 服务，backend 通过 `RAG_EMBEDDING_API_URL=http://embedding:80/v1/embeddings` 容器网络调用，无需额外配置。

### 12.5 数据库迁移（如从 384 维 local-hash 切到 512 维 BGE）

老库里的 384 维 hash 伪向量不可与 512 维 BGE 真实向量混用，必须清空重建：

```bash
psql -U postgres -h localhost -p 5432 -d college_service \
  -f deploy/sql/rag_migrate_384_to_512.sql
```

该脚本会：
1. 删除旧 ivfflat 索引
2. `TRUNCATE qa_document_chunk RESTART IDENTITY`（清空所有切片）
3. `ALTER COLUMN embedding TYPE vector(512)`
4. 重建 ivfflat 索引

迁移完成后，**管理端 → 文档管理 → 对每个 PDF/DOCX 点「重新索引」** 让 BGE 重新向量化。

### 12.6 故障排查

| 现象 | 原因 | 解决 |
|---|---|---|
| `Error: Could not download model artifacts` + `relative URL without a base` | TEI 1.5 hf-hub 库的 `HF_ENDPOINT` bug | 预下载模型挂载本地目录（见 12.2） |
| `File "/models/.../onnx/model.onnx" does not exist` | CPU 镜像需要 ONNX 而非 safetensors | 下载 Xenova 转换版的 `onnx/model.onnx` |
| backend 日志 `Embedding HTTP call failed` | TEI 容器未就绪或网络不通 | `docker compose logs embedding`；确认 `RAG_EMBEDDING_API_URL` 指向正确 |
| backend 日志 `Embedding dimension mismatch: expected 512 but got X` | 模型维度与配置不符 | 校对 `rag.embedding-dim` 与模型实际输出维度 |
| 问答返回 `sourceType: manual`（无 RAG） | 数据库无切片或检索 score 都 < min-score | 先索引文档；或临时下调 `rag.min-score` 调试 |
| 节假日、校历、报到类问题未匹配 | 校历文件未导入、分类不对或未重新索引 | 将校历文本作为政策文档导入，分类设为 `校历安排`，管理端点「重新索引」 |

### 12.7 政策文档 / 校历 / 党团文件导入

管理端推荐路径：

1. 进入「智能问答 → 政策文档」。
2. 上传 PDF / DOCX / TXT 文件，分类填写实际业务分类，例如 `新生报到`、`校历安排`、`党团文件`。
3. 上传后点击「重新索引」，后端会解析文本、切片、向量化并写入 `qa_document_chunk`。
4. 在小程序「智能问答」输入问题验证，返回中出现 `sourceType=rag` 或带「依据」片段即表示 RAG 命中。

命令行辅助脚本：

```powershell
# 批量导入党团官方文件
pwsh scripts/import-party-docs.ps1

# 批量导入办公模板
pwsh scripts/import-templates.ps1
```

校历图片不适合直接入 RAG。当前推荐先转成 TXT/Markdown 文本，再作为政策文档导入；OCR 结果中应保留日期、放假、调休、开学、考试周等关键词。

---

## 十三、邮件 SMTP 配置（信息精准推送）

模块三「信息精准推送」需要 SMTP 才能发真实邮件。**授权码不入代码不入库**，仅通过环境变量传入。`deploy/.env.prod` 与 `deploy/docker-compose.prod.yml` 的 `backend.environment` 段已经接通这 4 个变量到容器，无需额外手工配置：

```bash
# deploy/.env.prod (随部署包一起 scp 到服务器, 部署前在 deploy-package/.env 里改)
# 默认不内置真实发件邮箱; 需要真实邮件时部署前手动填写
MAIL_HOST=smtphz.qiye.163.com   # RUC 邮箱; 切 QQ 个人邮箱用 smtp.qq.com
MAIL_PORT=465                   # SSL 端口. 注意: smtphz 系列里 994=IMAP, 995=POP3, 465=SMTP
MAIL_USERNAME=                  # 发件人邮箱 (必须与授权码同源)
MAIL_AUTH_CODE=                 # 客户端授权码 - 部署前手动填入, 留空则邮件渠道降级为 email_sim
```

> 在 `deploy-package/.env` 里把 `MAIL_AUTH_CODE=` 一行填上邮箱后台生成的授权码即可。留空也能部署，仅邮件渠道降级。

**关键约束：**

1. `MAIL_HOST` / `MAIL_PORT` 必须与 `MAIL_USERNAME` 邮箱服务商匹配：
   - **RUC 学校邮箱 (`@ruc.edu.cn`)**：`smtphz.qiye.163.com:465`（网易企业邮杭州节点，**已实测**）
   - QQ 个人邮箱：`smtp.qq.com:465`
   - QQ 企业邮箱：`smtp.exmail.qq.com:465`
   - 网易企业邮（通用）：`smtp.qiye.163.com:465`

   > ⚠️ smtphz 系列的端口对照：**465 = SMTP**、993 = IMAP、995 = POP3、994 不属于这套系统。把 SMTP 写成 994 会直接 `Authentication failed`（实际连到了 IMAP 端口）。
2. `MAIL_AUTH_CODE` 是邮箱后台**生成的客户端专用密码**，不是登录密码。换服务商必须重新生成。
3. 缺失 / 鉴权失败时，`EmailService.isAvailable()` 返回 false，群发自动降级为只写 `email_sim` 类型站内通知，**不阻塞业务**。

**故障排查（后端日志关键字 `发送邮件失败`）：**

| `err=...` 内容 | 原因 | 解决 |
|---|---|---|
| `Authentication failed` | 授权码错 / 没读到 / 与发件人不匹配 | 验证 `echo $env:MAIL_AUTH_CODE` 长度；用邮箱后台重新生成 |
| `Couldn't connect to host, port: xxx; timeout -1` | host/port 不匹配服务商 | 对照上表；注意 yml 里 `socketFactory.port` 也必须用 `${MAIL_PORT}` |
| `Connection reset` | 服务商封了 SMTP 或 IP | 换服务商；或临时切到 STARTTLS 587 端口 |
| 无 warn 日志但 `emailSent=0` | `EmailService.isAvailable()=false` | 检查 `MAIL_USERNAME` 是否传入 |

---

## 十四、大模型 API 配置（可选）

RAG 检索、文档切片、向量库不依赖外部大模型。外部大模型只负责在检索片段基础上组织自然语言回答；不配置时系统仍会返回抽取式依据片段。

生产部署时编辑 `deploy-package/.env`：

```bash
# 不调用大模型
AI_PROVIDER=none
AI_API_URL=
AI_API_KEY=
AI_MODEL=
AI_TIMEOUT_MS=5000

# 调用 OpenAI Chat Completions 兼容接口
AI_PROVIDER=openai
AI_API_URL=https://你的模型服务/v1/chat/completions
AI_API_KEY=sk-xxxx
AI_MODEL=deepseek-chat
AI_TIMEOUT_MS=15000
```

`openai` Provider 的请求格式为标准 Chat Completions：

- Header: `Authorization: Bearer ${AI_API_KEY}`
- Body: `{ model, temperature, messages }`
- 响应读取 `choices[0].message.content`

如果你使用的模型服务不是 Chat Completions 协议，而是自定义网关，可以使用 `AI_PROVIDER=http`。该模式请求体为 `{ question, context }`，响应支持 `answer`、`result`、`data.answer` 或 OpenAI `choices[0].message.content`。

---

## 十五、微信小程序体验版部署

体验版只需要在微信开发者工具上传，不需要单独服务器部署小程序前端。后端仍然部署在服务器上。

### 15.1 构建小程序

```powershell
cd frontend-mp
npm install
$env:VITE_API_BASE_URL = "http://10.10.0.27/api"
npm run build:mp-weixin
```

构建产物目录：

```text
frontend-mp/dist/build/mp-weixin
```

### 15.2 上传体验版

1. 打开微信开发者工具。
2. 导入 `frontend-mp/dist/build/mp-weixin`。
3. 填写小程序 AppID。
4. 本地预览调试时可在「详情 → 本地设置」勾选“不校验合法域名”。
5. 点击「上传」，在微信公众平台把该版本设为体验版，并添加体验成员。

重要限制：

- 真机体验版正式访问后端时，微信通常要求 request 合法域名为 HTTPS 域名，不能只用 `localhost`。
- 如果当前只有内网 IP `http://10.10.0.27`，可用于开发者工具或打开调试的预览；要给非开发者稳定体验，建议给服务器配置域名和 HTTPS，再把 `VITE_API_BASE_URL` 改成 `https://你的域名/api` 后重新构建上传。
- 生产小程序不应使用 `localhost`，否则手机端会请求自己的手机而不是服务器。

---

## 十六、初始化账号

fresh 部署执行 `deploy/sql/schema.sql` 时会自动创建两个演示账号，密码均为 `admin123`：

| 账号 | 角色 | 手机号 | 邮箱 |
|---|---|---|---|
| `admin` | 院领导 / 管理员 | `13800000001` | `admin.demo@example.edu` |
| `20240001` | 普通学生 | `13800000002` | `student.demo@example.edu` |

如果服务器已有旧数据库卷，这些初始数据不会重新执行。需要重建初始数据时，必须走 fresh 部署，或手动执行对应 INSERT。
