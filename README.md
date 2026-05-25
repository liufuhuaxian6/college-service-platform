# 学院学生综合服务与党团管理平台

中国人民大学信息学院 · 软件工程导论课程项目

---

## 项目简介

一站式学院级学生服务平台，包含智能问答、党团流程管理、电子证明审批、学生画像四大核心模块，支持四级权限体系（院领导/管理老师/班团骨干/普通学生）。

- **学生端**：微信小程序（uni-app）
- **管理端**：PC Web 后台（Vue3 + Element Plus）
- **后端**：Spring Boot 3 + MyBatis-Plus
- **数据库**：Kingbase V8（开发阶段使用 PostgreSQL + pgvector 替代）
- **智能问答 RAG**：HuggingFace TEI + BAAI BGE-small-zh-v1.5（512 维中文向量）

---

## 目录结构

```
college-service-platform/
├── backend/                 # 后端 Spring Boot 项目
├── frontend-admin/          # PC 管理端（Vue3 + Element Plus）
├── frontend-mp/             # 微信小程序端（uni-app）
├── deploy/
│   ├── sql/schema.sql                  # 数据库建表脚本（15 张表，含 RAG 向量表）
│   ├── sql/rag_pgvector.sql            # RAG 切片表增量迁移脚本
│   ├── sql/rag_migrate_384_to_512.sql  # 384 维 → 512 维向量维度迁移
│   └── nginx.conf                      # Nginx 配置
├── models/                  # 预下载的 BGE embedding 模型（.gitignore，按需下载）
├── docs/                    # 项目文档
│   ├── TEAM-COLLABORATION.md  # 团队协作与 API 接口文档
│   ├── FILE-REFERENCE.md     # 文件说明文档
│   ├── ARCHITECTURE.md       # 系统架构与通信方式（含 RAG 检索链路）
│   └── DEPLOYMENT.md         # 服务器部署文档（含 TEI 离线部署、故障排查）
├── docker-compose.yml       # Docker 一键部署（含 TEI embedding 服务）
└── README.md
```

---

## 环境配置完整指南（新成员必读）

> 本指南假设你的电脑上**什么开发工具都没有**，从零开始配置。
> 全程约 30-60 分钟（取决于网速）。请按顺序执行，不要跳步。

---

### 第一步：安装 Git

Git 是代码版本管理工具，用来克隆仓库和提交代码。

#### 检查是否已安装

```bash
git --version
# 如果输出 git version 2.x.x 就跳过这步
```

#### Windows 安装

1. 下载：https://git-scm.com/download/win
2. 双击安装，**全部默认选项**一路 Next 即可
3. 安装完成后重新打开终端，输入 `git --version` 验证

> 安装时会自带一个 **Git Bash** 终端，后续所有命令都可以在 Git Bash 里执行。

#### macOS 安装

```bash
# 方式一：Xcode 命令行工具（推荐，自带 Git）
xcode-select --install

# 方式二：Homebrew
brew install git
```

---

### 第二步：安装 JDK（Java 开发工具包）

后端是 Java 项目，需要 JDK 17 或更高版本来编译和运行。

#### 检查是否已安装

```bash
java -version
# 如果输出 openjdk version "17.x.x" 或更高，跳过这步
```

#### Windows 安装

**方式一：winget（推荐）**

```powershell
# 打开 PowerShell，执行：
winget install Microsoft.OpenJDK.17
```

**方式二：手动下载**

1. 访问 https://adoptium.net
2. 选择 **JDK 17** → **Windows x64** → **.msi** 安装包
3. 双击安装，勾选 **Set JAVA_HOME variable** 和 **Add to PATH**

#### macOS 安装

```bash
brew install openjdk@17

# 设置环境变量
echo 'export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

#### 验证

```bash
java -version
# 应输出 openjdk version "17.x.x" 或更高

javac -version
# 应输出 javac 17.x.x 或更高
```

> **注意**：Maven **不需要单独安装**！项目自带 `mvnw.cmd`（Windows）/ `mvnw`（Mac）脚本，首次运行时自动下载 Maven。

---

### 第三步：安装 Node.js 和 npm

前端项目（管理端 + 小程序端）使用 Node.js 构建。npm 是 Node.js 的包管理器，随 Node.js 一起安装。

#### 检查是否已安装

```bash
node -v
# 如果输出 v18.x.x 或更高，跳过这步

npm -v
# 如果输出 9.x.x 或更高，跳过这步
```

#### Windows 安装

**方式一：winget（推荐）**

```powershell
winget install OpenJS.NodeJS.LTS
```

**方式二：手动下载**

1. 访问 https://nodejs.org
2. 下载 **LTS 版本**（长期支持版）
3. 双击安装，**全部默认**一路 Next

#### macOS 安装

```bash
brew install node@18
```

#### 验证

安装完成后**重新打开终端**（必须重开，否则 PATH 没生效）：

```bash
node -v    # 应输出 v18.x.x 或更高
npm -v     # 应输出 9.x.x 或更高
```

---

### 第四步：安装 PostgreSQL 数据库

系统的所有数据（用户、知识库、审批等）存储在 PostgreSQL 中。

#### 检查是否已安装

```bash
psql --version
# 如果输出 psql (PostgreSQL) 14.x 或更高，跳过安装步骤
```

#### Windows 安装

**方式一：winget（推荐）**

```powershell
winget install PostgreSQL.PostgreSQL.16
```

安装过程中弹出对话框，要求设置 superuser 密码 → **输入 `postgres`**（必须是这个，与项目配置一致）。

**方式二：手动下载**

1. 访问 https://www.enterprisedb.com/downloads/postgres-postgresql-downloads
2. 选择 **Windows x86-64**，版本 **16.x**
3. 双击安装
4. 密码设置为 `postgres`
5. 端口保持默认 `5432`
6. 其余全部默认 Next

#### macOS 安装

```bash
brew install postgresql@16
brew services start postgresql@16

# 创建 postgres 用户并设置密码
createuser -s postgres
psql -U postgres -c "ALTER USER postgres PASSWORD 'postgres';"
```

#### 配置 PATH（Windows）

安装完成后，`psql` 命令可能找不到，需要把 PostgreSQL 加到系统 PATH：

```powershell
# 永久生效（PowerShell 管理员模式执行）
[Environment]::SetEnvironmentVariable("Path", $env:Path + ";C:\Program Files\PostgreSQL\16\bin", "Machine")
```

然后**重新打开终端**。

#### 验证安装和连接

```bash
psql -U postgres -c "SELECT version();"
# 输入密码: postgres
# 应输出 PostgreSQL 16.x.x 的版本信息
```

#### 验证服务是否在运行

```powershell
# Windows PowerShell
Get-Service postgresql*
# Status 应为 Running

# 如果没在运行
Start-Service postgresql-x64-16
```

```bash
# macOS
brew services list | grep postgresql
# 应显示 started
```

> PostgreSQL 安装后注册为系统服务，**开机自动启动**，后续不需要手动管理。

---

### 第五步：安装 Redis

Redis 是内存缓存，用于加速热点数据读取和会话管理。

#### Windows 安装

```powershell
winget install Redis.Redis
```

> 安装完成后提示 "IMPORTANT: ReStart your Terminal"，**必须重新打开终端**。

#### macOS 安装

```bash
brew install redis
brew services start redis
```

#### 验证

```bash
# Windows
"C:\Program Files\Redis\redis-cli.exe" ping
# 应返回 PONG

# macOS
redis-cli ping
# 应返回 PONG
```

#### 验证服务是否在运行

```powershell
# Windows PowerShell
Get-Service Redis
# Status 应为 Running

# 如果没在运行
Start-Service Redis
```

> Redis 同样是系统服务，**开机自动启动**。

---

### 第六步：安装微信开发者工具（仅 D 同学需要）

用于调试微信小程序。其他同学可以跳过这步。

1. 下载：https://developers.weixin.qq.com/miniprogram/dev/devtools/download.html
2. 选择 **稳定版 Stable Build**
3. 下载 Windows 64 或 macOS 版本
4. 安装后用微信扫码登录

---

### 第七步：克隆项目

```bash
git clone git@github.com:liufuhuaxian6/college-service-platform.git
cd college-service-platform
```

如果提示 SSH key 未配置，改用 HTTPS：

```bash
git clone https://github.com/liufuhuaxian6/college-service-platform.git
cd college-service-platform
```

切到开发分支：

```bash
git checkout dev
```

---

### 第八步：初始化数据库

```bash
# 1. 创建数据库
psql -U postgres -c "CREATE DATABASE college_service;"
# 输入密码: postgres

# 2. 执行建表脚本
psql -U postgres -d college_service -f deploy/sql/schema.sql
# 输入密码: postgres
```

成功后会看到大量 `CREATE TABLE`、`CREATE INDEX`、`INSERT` 输出，最后几行是：
```
INSERT 0 1    (管理员账号)
INSERT 0 4    (审批类型)
INSERT 0 1    (入党流程模板)
INSERT 0 8    (入党流程步骤)
INSERT 0 1    (入团流程模板)
INSERT 0 5    (入团流程步骤)
```

> 这一步**只需要做一次**。后续重新启动项目不需要重新建表。
> 如果要重建数据库（清空所有数据），先执行 `psql -U postgres -c "DROP DATABASE college_service;"` 再重复上面两步。

> **注意**：建表脚本中的 `qa_document_chunk` 表依赖 `pgvector` 扩展。
> 如果你装的是普通 PostgreSQL（非 pgvector 镜像），执行前需先：
>
> ```bash
> # Windows：从 https://github.com/pgvector/pgvector/releases 下载安装
> # macOS：brew install pgvector
> psql -U postgres -d college_service -c "CREATE EXTENSION IF NOT EXISTS vector;"
> ```
>
> 或者推荐做法：用 Docker 跑带 pgvector 的 PostgreSQL（见下方"快速开始 - 方式二"）。

---

### 第八点五步：下载 BGE 中文 Embedding 模型 + 启动 TEI 服务

智能问答模块使用 RAG 检索增强生成，需要一个 HTTP embedding 服务把中文转成 512 维向量。当前选用 **HuggingFace TEI 容器 + BAAI BGE-small-zh-v1.5 模型**。

> 仅做后端 / 智能问答相关的同学需要这一步。其他同学这一步可以跳过，问答会自动降级为"未在政策文件中找到明确依据，请联系辅导员确认"。

#### 1) 一次性下载 BGE 模型到本地（约 183MB）

PowerShell 在项目根目录执行：

```powershell
$base = "https://hf-mirror.com/BAAI/bge-small-zh-v1.5/resolve/main"
$xenova = "https://hf-mirror.com/Xenova/bge-small-zh-v1.5/resolve/main"
$dest = ".\models\bge-small-zh-v1.5"
New-Item -ItemType Directory -Path "$dest\onnx" -Force | Out-Null
New-Item -ItemType Directory -Path "$dest\1_Pooling" -Force | Out-Null

# 模型配置文件（10 个，约 600KB）
@(
  "config.json", "tokenizer.json", "tokenizer_config.json", "special_tokens_map.json",
  "vocab.txt", "modules.json", "config_sentence_transformers.json",
  "sentence_bert_config.json", "1_Pooling/config.json"
) | ForEach-Object {
  curl.exe -fSL --retry 5 -o "$dest\$_" "$base/$_"
}

# ONNX 推理权重（来自社区 Xenova 转换版，约 90MB）
curl.exe -fSL --retry 5 -o "$dest\onnx\model.onnx" "$xenova/onnx/model.onnx"
```

下载结束后 `models/bge-small-zh-v1.5/` 目录已被 `.gitignore` 排除，不会入库。

#### 2) 拉取并启动 TEI 容器

```powershell
# 拉镜像（约 700MB，国内用南京大学 GHCR 镜像，比直连 ghcr.io 稳定）
docker compose pull embedding

# 启动（首次约 30 秒加载模型）
docker compose up -d embedding

# 等待 30 秒后验证
docker compose logs --tail 10 embedding   # 应看到 "Ready"
```

#### 3) 测试调用

```powershell
# 用文件传 UTF-8 中文（避免 PowerShell 转义问题）
'{"input":"入党流程是什么"}' | Out-File -Encoding utf8 req.json
curl.exe -X POST http://localhost:8081/v1/embeddings `
  -H "Content-Type: application/json" --data-binary "@req.json"
# 应返回 {"data":[{"embedding":[0.028, 0.067, ...]}]}，数组长度 = 512
Remove-Item req.json
```

> **遇到问题？** TEI 1.5 有个 `HF_ENDPOINT` bug 导致它自己下载模型会失败，所以本项目改成"预下载 + 挂载本地目录"。详细的故障排查清单见 [docs/DEPLOYMENT.md § 12.6](docs/DEPLOYMENT.md)。

---

### 第九步：启动后端

```bash
cd backend

# Windows
.\mvnw.cmd spring-boot:run

# macOS / Linux
./mvnw spring-boot:run
```

**首次启动**会自动下载 Maven 3.9.9 和所有 Java 依赖包（约 200MB），需要 5-15 分钟，耐心等待。

看到以下输出表示启动成功：
```
Started CollegeApplication in X.XXX seconds
```

#### 快速验证

打开**另一个终端窗口**：
```bash
curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"studentId\":\"admin\",\"password\":\"admin123\"}"
```

看到 `{"code":200, ... "token":"eyJ..."}` 就是成功。

也可以浏览器直接访问 API 文档：http://localhost:8080/api/doc.html

---

### 第十步：启动管理端前端

管理端使用 **Vite** 作为构建工具。Vite 不需要单独安装，`npm install` 会自动安装它。

```bash
cd frontend-admin
npm install          # 首次需要，约 1-2 分钟。会自动安装 Vite、Vue3、Element Plus 等所有依赖
npm run dev          # 启动 Vite 开发服务器
```

看到 `Local: http://localhost:5173/` 后，浏览器打开该地址。

登录：**admin** / **admin123**

> **Vite 的作用**：它是前端开发服务器，做了两件事：
> 1. 把 Vue 源码实时编译成浏览器能运行的 JS
> 2. 把 `/api` 开头的请求代理转发到后端 `localhost:8080`（解决跨域问题）
>
> 所以开发时浏览器访问的是 Vite（:5173），但 API 请求实际到了后端（:8080）。

---

### 第十一步：启动小程序端（仅 D 同学）

```bash
cd frontend-mp
npm install          # 首次需要
npm run dev:mp-weixin
```

保持这个终端不要关。然后在微信开发者工具中：

1. 选择 **导入项目**
2. 目录选择：`frontend-mp/dist/dev/mp-weixin`
   > **注意**：是 `dist/dev/mp-weixin` 目录，**不是** `frontend-mp` 根目录！
3. AppID 选 **测试号**
4. 点确定
5. 右上角 **详情** → **本地设置** → 勾选 **不校验合法域名**

---

### 环境配置检查清单

配置完成后，对照此表确认所有环境正常：

| # | 检查项 | 检查命令 | 期望结果 |
|---|--------|---------|----------|
| 1 | Git | `git --version` | `git version 2.x.x` |
| 2 | JDK | `java -version` | `openjdk version "17.x.x"` 或更高 |
| 3 | Node.js | `node -v` | `v18.x.x` 或更高 |
| 4 | npm | `npm -v` | `9.x.x` 或更高 |
| 5 | PostgreSQL | `psql -U postgres -c "SELECT 1;"` | 返回 `1` |
| 6 | Redis | `redis-cli ping` 或 `"C:\Program Files\Redis\redis-cli.exe" ping` | 返回 `PONG` |
| 7 | 数据库已建表 | `psql -U postgres -d college_service -c "SELECT count(*) FROM sys_user;"` | 返回 `1`（管理员账号） |
| 8 | pgvector 扩展已启用 | `psql -U postgres -d college_service -c "\dx vector"` | 列表中能看到 `vector` |
| 9 | TEI Embedding 服务（仅后端同学需要） | `docker compose ps embedding` | 状态 `running (healthy)` |
| 10 | TEI 输出 512 维向量 | 见上方第八点五步 § 3) | 数组长度 = 512 |
| 11 | 后端能启动 | 启动后访问 http://localhost:8080/api/doc.html | 看到 Swagger 文档页 |
| 12 | 后端登录可用 | curl 登录接口 | 返回 `{"code":200, ...}` |
| 13 | 管理端能访问 | http://localhost:5173 | 看到登录页面 |

---

### 各角色需要启动什么

| 你是谁 | 需要启动的服务 | 需要安装的额外工具 |
|--------|--------------|-------------------|
| **A 同学**（后端基础） | 后端 + TEI Embedding | Docker Desktop |
| **B 同学**（后端业务） | 后端 + TEI Embedding | Docker Desktop |
| **C 同学**（管理端前端） | 后端 + 管理端前端 | — |
| **D 同学**（小程序前端） | 后端 + 小程序编译 | 微信开发者工具 |

> TEI Embedding 服务只有做后端业务（智能问答 / 政策文档 / RAG）的同学必须启动；前端同学如果只是调登录、用户列表等非问答接口，不开 TEI 也能正常工作。

> 所有人都需要安装：Git + JDK + Node.js + npm + PostgreSQL + Redis
> 后端 / 智能问答相关同学还需要：Docker Desktop（用于跑 TEI Embedding 服务）
> 前端同学也需要启动后端，否则页面请求接口会全部报错。

---

### 每日开发启动流程

PostgreSQL 和 Redis 是系统服务，**开机自动运行**，不用管。

每次开发只需打开终端执行：

```bash
# 启动 TEI Embedding 容器（后端 / 智能问答同学需要，容器后台跑，启动一次即可）
docker compose up -d embedding

# 终端 1：启动后端（所有人）
cd college-service-platform/backend
.\mvnw.cmd spring-boot:run          # Windows
./mvnw spring-boot:run              # macOS
# 等看到 "Started CollegeApplication" 再继续

# 终端 2：启动管理端前端（C 同学或需要看页面的同学）
cd college-service-platform/frontend-admin
npm run dev

# 终端 3：启动小程序编译（D 同学）
cd college-service-platform/frontend-mp
npm run dev:mp-weixin
# 然后打开微信开发者工具
```

### 关闭所有服务

在各终端窗口按 `Ctrl+C` 即可停止。

如果端口被占用（提示 Port 8080 already in use）：

```powershell
# Windows PowerShell：查看谁占用了端口
netstat -ano | findstr :8080

# 杀掉进程（替换 <PID> 为实际数字）
taskkill /F /PID <PID>
```

```bash
# macOS
lsof -i :8080
kill -9 <PID>
```

### 数据库和 Redis 没在运行怎么办

```powershell
# Windows PowerShell
Get-Service postgresql*    # 查看状态
Get-Service Redis

# 手动启动
Start-Service postgresql-x64-16
Start-Service Redis
```

```bash
# macOS
brew services start postgresql@16
brew services start redis
```

---

## 环境要求（汇总）

| 工具                    | 版本   | 说明                                          |
| ----------------------- | ------ | --------------------------------------------- |
| JDK                     | 17+    | 后端运行环境                                  |
| Maven                   | 3.8+   | 后端构建工具                                  |
| Node.js                 | 18+    | 前端构建环境                                  |
| npm                     | 9+     | 随 Node.js 安装                               |
| PostgreSQL + pgvector   | 14+    | 开发阶段数据库（替代 Kingbase），需启用 vector 扩展 |
| Redis                   | 7+     | 缓存                                          |
| Docker + Docker Compose | 最新版 | 跑 TEI Embedding 容器；生产环境一键部署       |
| 微信开发者工具          | 最新版 | 小程序端调试（D 同学需要）                    |

---

## 快速开始

### 1. 克隆项目

```bash
git clone <仓库地址>
cd college-service-platform
```

### 2. 数据库初始化

#### 方式一：本地 PostgreSQL

```bash
# 1. 启动 PostgreSQL，创建数据库
psql -U postgres -c "CREATE DATABASE college_service;"

# 2. 执行建表脚本
psql -U postgres -d college_service -f deploy/sql/schema.sql
```

#### 方式二：Docker 启动 PostgreSQL

```bash
# 启动 PostgreSQL 容器
docker run -d \
  --name college-pg \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=college_service \
  -p 5432:5432 \
  pgvector/pgvector:pg16

# 等待几秒后执行建表
docker exec -i college-pg psql -U postgres -d college_service < deploy/sql/schema.sql
```

### 3. 启动 Redis

```bash
# 本地安装的 Redis
redis-server

# 或 Docker
docker run -d --name college-redis -p 6379:6379 redis:7-alpine
```

### 4. 启动后端

```bash
cd backend

# 安装依赖并启动（首次会下载依赖，耐心等待）
./mvnw.cmd spring-boot:run

# 或者先编译再运行
mvn clean package -DskipTests
java -jar target/college-service-platform-1.0.0-SNAPSHOT.jar
```

启动成功后：

- 后端接口：http://localhost:8080/api
- API 文档：http://localhost:8080/api/doc.html

### 5. 启动管理端前端

```bash
cd frontend-admin

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

访问 http://localhost:5173 ，使用以下账号登录：

| 账号  | 密码     | 角色           |
| ----- | -------- | -------------- |
| admin | admin123 | 院领导（一级） |

### 6. 启动小程序端

```bash
cd frontend-mp

# 安装依赖
npm install

# 编译为微信小程序
npm run dev:mp-weixin

# 或编译为 H5 版本（浏览器调试）
npm run dev:h5
```

**微信小程序调试**：

1. 打开微信开发者工具
2. 导入项目，选择 `frontend-mp/dist/dev/mp-weixin` 目录
3. AppID 可使用测试号，或在 `src/manifest.json` 中填入正式 AppID

**H5 调试**：

- 访问终端输出的地址（默认 http://localhost:5173）

---

## Docker 部署（生产环境）

> 详细的服务器部署流程（含离线环境）请参阅 [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)

```bash
# 1. 先构建管理端前端
cd frontend-admin
npm install && npm run build
cd ..

# 2. 一键启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看后端日志
docker-compose logs -f backend
```

启动后：

- 管理端：http://localhost（Nginx 80 端口）
- 后端 API：http://localhost/api
- PostgreSQL / Kingbase：localhost:5432
- Redis：localhost:6379

停止所有服务：

```bash
docker-compose down
```

---

## 开发配置说明

### 后端配置文件

| 文件                   | 用途                           |
| ---------------------- | ------------------------------ |
| `application.yml`      | 主配置（端口、JWT、AI 开关等） |
| `application-dev.yml`  | 开发环境（连 PostgreSQL）      |
| `application-prod.yml` | 生产环境（连 Kingbase）        |

切换环境：修改 `application.yml` 中的 `spring.profiles.active`：

```yaml
spring:
  profiles:
    active: dev # dev=开发 / prod=生产
```

修改数据库连接：编辑 `application-dev.yml`：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/college_service
    username: postgres
    password: postgres # 改成你的密码
```

### 前端代理配置

管理端 `frontend-admin/vite.config.js` 已配置代理，开发时自动转发 `/api` 请求到后端：

```js
proxy: {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true,
  },
}
```

小程序端 H5 模式在 `src/manifest.json` 中配置了相同的代理。微信小程序模式需要在微信开发者工具中配置"不校验合法域名"。

---

## Git 协作规范

### 分支结构

```
main                         # 稳定版本，只接受从 dev 合并的 PR
├── dev                      # 开发集成分支，所有人的代码先汇聚于此
│   ├── feat/backend-base    # A 同学：后端基础
│   ├── feat/backend-biz     # B 同学：后端业务
│   ├── feat/frontend-admin  # C 同学：管理端前端
│   └── feat/frontend-mp     # D 同学：小程序端前端
```

### 第一次加入项目：创建自己的分支

```bash
# 1. 克隆仓库
git clone <仓库地址>
cd college-service-platform

# 2. 切换到 dev 分支（所有开发的基准）
git checkout dev

# 3. 基于 dev 创建自己的功能分支
#    A 同学：
git checkout -b feat/backend-base
#    B 同学：
git checkout -b feat/backend-biz
#    C 同学：
git checkout -b feat/frontend-admin
#    D 同学：
git checkout -b feat/frontend-mp

# 4. 将自己的分支推送到远程
git push -u origin feat/backend-base   # 换成你的分支名
```

### 日常开发流程

```bash
# 1. 确保在自己的分支上
git checkout feat/backend-base

# 2. 开始前先同步最新的 dev（避免冲突堆积）
git fetch origin
git merge origin/dev

# 3. 开发、修改文件...

# 4. 查看改了什么
git status
git diff

# 5. 暂存改动（建议按文件添加，不要用 git add .）
git add backend/src/main/java/com/ruc/college/module/auth/service/AuthService.java
git add backend/src/main/java/com/ruc/college/module/auth/controller/AuthController.java

# 6. 提交（遵循 Commit 消息规范）
git commit -m "feat(auth): 添加 Excel 批量导入学生功能"

# 7. 推送到远程
git push origin feat/backend-base
```

### 合并到 dev：提交 Pull Request

**绝对不要直接 push 到 dev 或 main！** 必须通过 PR 合并。

```bash
# 1. 推送前先同步 dev，在本地解决冲突
git fetch origin
git merge origin/dev
# 如果有冲突，手动解决后 git add + git commit

# 2. 推送
git push origin feat/backend-base

# 3. 在 GitHub/GitLab 上创建 Pull Request
#    - Source: feat/backend-base → Target: dev
#    - 标题：简要描述改了什么
#    - 描述：列出改动要点
#    - 指定至少 1 名团队成员 Review
```

**PR 合并条件**（必须全部满足）：

1. 至少 **1 名其他成员** 审核通过（Code Review）
2. 没有未解决的 Review 评论
3. 与 dev 分支 **无冲突**（有冲突先在本地解决再推送）
4. 后端代码改动必须 **编译通过**（`mvn compile` 无报错）
5. 前端代码改动必须 **构建通过**（`npm run build` 无报错）

### 从 dev 合并到 main：版本发布

**仅在里程碑节点操作**（如 Sprint 结束、联调通过后），由团队负责人执行：

```bash
# 1. 确保 dev 分支测试通过
git checkout dev
git pull origin dev

# 2. 创建 PR: dev → main
#    在 GitHub/GitLab 上操作
#    标题格式: "Release: Sprint N - xxx功能上线"
```

**main 分支合并条件**：

1. **全员确认**：4 名成员均审核通过
2. **全部功能可用**：后端启动正常，前端页面可访问，核心流程跑通
3. **无已知严重 Bug**
4. 合并后在 main 上打 **Tag** 标记版本：
   ```bash
   git tag -a v1.0.0 -m "Sprint 1: 基础框架 + 智能问答上线"
   git push origin v1.0.0
   ```

### 分支保护规则（仓库管理员设置）

在 GitHub 仓库 Settings → Branches → Add rule 中配置：

**`main` 分支**：

- [x] Require a pull request before merging
- [x] Require approvals: **2**（至少 2 人审核）
- [x] Require status checks to pass（如配置了 CI）
- [x] Do not allow force pushes
- [x] Do not allow deletions

**`dev` 分支**：

- [x] Require a pull request before merging
- [x] Require approvals: **1**（至少 1 人审核）
- [x] Do not allow force pushes

### Commit 消息规范

格式：

```
<type>(<scope>): <简要描述>
```

**type 类型**：

| type       | 含义                        | 示例                                     |
| ---------- | --------------------------- | ---------------------------------------- |
| `feat`     | 新功能                      | `feat(approval): 实现审批状态机锁定逻辑` |
| `fix`      | Bug 修复                    | `fix(qa): 修复关键词匹配时的空指针异常`  |
| `docs`     | 文档更新                    | `docs(readme): 添加环境配置说明`         |
| `style`    | 样式/格式调整（不影响逻辑） | `style(admin): 统一表格列宽`             |
| `refactor` | 重构（不改变功能）          | `refactor(party): 抽取流程步骤构建方法`  |
| `test`     | 添加/修改测试               | `test(auth): 添加登录接口单元测试`       |
| `chore`    | 构建/配置/依赖变更          | `chore: 升级 Element Plus 到 2.7.1`      |

**scope 范围**：

| scope      | 对应模块     | 负责人 |
| ---------- | ------------ | ------ |
| `auth`     | 认证登录     | A      |
| `system`   | 系统管理     | A      |
| `qa`       | 智能问答     | B      |
| `party`    | 党团流程     | B      |
| `approval` | 审批流程     | B      |
| `student`  | 学生画像     | B      |
| `admin`    | 管理端前端   | C      |
| `mp`       | 小程序端前端 | D      |
| `deploy`   | 部署相关     | A      |
| `db`       | 数据库变更   | A      |

### 冲突解决规则

当合并 `origin/dev` 时遇到冲突：

1. **只改自己模块的文件**：自行解决，保留自己的改动
2. **改到了别人模块的文件**：联系该模块负责人一起解决
3. **公共文件冲突**（如 `pom.xml`、`application.yml`、`router/index.js`）：
   - 通常是新增内容，两边都保留
   - 不确定时在群里沟通后再 commit
4. 冲突解决后，一定要 **本地验证能正常编译/启动** 再推送

### 禁止事项

| 操作                                    | 原因                                     |
| --------------------------------------- | ---------------------------------------- |
| `git push origin main`                  | 直接推送到 main，绕过 Review             |
| `git push origin dev`                   | 直接推送到 dev，绕过 Review              |
| `git push --force`                      | 强制推送会覆盖他人代码                   |
| `git reset --hard` 后推送               | 会丢失提交历史                           |
| 提交 `node_modules/`、`target/`、`.env` | 这些在 .gitignore 中已排除，不要手动添加 |
| 修改他人分支的代码                      | 只改自己分支，通过 PR 合并               |
| 在 PR 未审核通过时自行合并              | 必须等 Review 通过                       |

### 紧急修复流程（Hotfix）

生产环境发现紧急 Bug 时：

```bash
# 1. 基于 main 创建 hotfix 分支
git checkout main
git pull origin main
git checkout -b hotfix/fix-approval-lock

# 2. 修复 Bug，提交
git commit -m "fix(approval): 修复下载后仍可撤回的严重Bug"

# 3. 推送，分别创建 PR 到 main 和 dev
git push origin hotfix/fix-approval-lock
# PR1: hotfix/fix-approval-lock → main（紧急合并）
# PR2: hotfix/fix-approval-lock → dev（同步修复到开发分支）

# 4. main 合并后打补丁版本 Tag
git tag -a v1.0.1 -m "Hotfix: 修复审批锁定漏洞"
git push origin v1.0.1
```

---

## 默认账号

执行 `schema.sql` 后自动创建：

| 学号  | 密码     | 角色           | 说明         |
| ----- | -------- | -------------- | ------------ |
| admin | admin123 | 一级（院领导） | 拥有全部权限 |

可通过管理端"用户管理"页面或直接操作数据库添加更多测试账号：

```sql
-- 添加一个二级管理员（辅导员）
INSERT INTO sys_user (student_id, name, password, role_level, grade, major, status)
VALUES ('teacher01', '张老师',
        '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi',
        2, NULL, NULL, 1);

-- 添加一个四级普通学生
INSERT INTO sys_user (student_id, name, password, role_level, grade, major, class_name, status)
VALUES ('2024001', '李同学',
        '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi',
        4, '2024', '计算机科学', '2024级1班', 1);
```

> 以上密码哈希对应明文 `admin123`。

---

## 常见问题

### 后端启动报错 "Cannot connect to database"

确认 PostgreSQL 已启动，且 `application-dev.yml` 中的连接信息正确：

```bash
# 测试连接
psql -U postgres -d college_service -c "SELECT 1;"
```

### 后端启动报错 "Cannot connect to Redis"

确认 Redis 已启动：

```bash
redis-cli ping
# 应返回 PONG
```

### 前端启动报 "ENOENT" 或依赖错误

删除 node_modules 重新安装：

```bash
rm -rf node_modules package-lock.json
npm install
```

### 小程序端编译后在微信开发者工具中报网络错误

1. 微信开发者工具 → 详情 → 本地设置 → 勾选"不校验合法域名"
2. 确认后端已启动在 8080 端口

### API 文档打不开

确认后端已启动，访问 http://localhost:8080/api/doc.html 。生产环境下 Swagger 默认关闭，需在 `application-prod.yml` 中设置 `knife4j.enable: true` 临时开启。

### TEI Embedding 服务起不来 / 智能问答返回 `sourceType: manual`

按可能性从高到低排查：

1. `docker compose ps embedding` 状态是 `restarting` —— 模型文件没下载完整，重新执行第八点五步的下载命令
2. 日志报 `relative URL without a base` —— TEI 1.5 的 HF_ENDPOINT bug，确认本地 `models/bge-small-zh-v1.5/` 目录存在且 docker-compose.yml 挂载到 `/models/...:ro`
3. 日志报 `model.onnx does not exist` —— ONNX 权重缺失，重新下载 `Xenova/bge-small-zh-v1.5` 的 `onnx/model.onnx`
4. 后端日志 `Embedding HTTP call failed` —— `application-dev.yml` 中 `rag.embedding.api-url` 应为 `http://localhost:8081/v1/embeddings`
5. 数据库 `qa_document_chunk` 表是空的 —— 管理端 → 文档管理 → 对每个 PDF/DOCX 点「重新索引」让 BGE 重新向量化

完整故障排查表见 [docs/DEPLOYMENT.md § 12.6](docs/DEPLOYMENT.md)。

### pgvector 扩展未安装

执行 `psql -U postgres -d college_service -c "CREATE EXTENSION IF NOT EXISTS vector;"` 报错 `extension "vector" is not available`，说明 PostgreSQL 没装 pgvector。三种解法：

1. **推荐**：用 Docker 跑带 pgvector 的镜像（`pgvector/pgvector:pg16`），见"快速开始 - 方式二"
2. Windows 手动装：从 https://github.com/pgvector/pgvector/releases 下载与 PG 版本对应的 dll
3. macOS：`brew install pgvector`

---

## 相关文档

- [项目运行架构与通信方式](docs/ARCHITECTURE.md) — 系统全景图、各端通信方式、请求生命周期
- [团队协作与 API 接口文档](docs/TEAM-COLLABORATION.md) — 分工、全部接口清单、状态机、迭代计划
- [文件说明文档](docs/FILE-REFERENCE.md) — 每个文件的用途与实现说明
- [服务器部署文档](docs/DEPLOYMENT.md) — 离线环境部署流程、Docker 镜像导入、运维操作
