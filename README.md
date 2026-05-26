# 学院学生综合服务与党团管理平台

中国人民大学信息学院 · 软件工程导论课程项目

---

## 项目简介

一站式学院级学生服务平台，覆盖**智能问答（RAG）、党团流程管理、电子证明审批、学生画像、信息精准推送**五个核心模块，支持四级权限体系（院领导/管理老师/班团骨干/普通学生）。

- **学生端**：微信小程序（uni-app + Vue3）
- **管理端**：PC Web 后台（Vue3 + Element Plus）
- **后端**：Spring Boot 3 + MyBatis-Plus
- **数据库**：Kingbase V8（开发期用 PostgreSQL 16 + pgvector 替代）
- **智能问答 RAG**：HuggingFace TEI + BAAI BGE-small-zh-v1.5（512 维中文向量）
- **邮件**：JavaMailSender + SMTP（默认走 QQ/网易企业邮，授权码通过环境变量传入）

---

## 目录结构

```
college-service-platform/
├── backend/                 # Spring Boot 后端
├── frontend-admin/          # PC 管理端 (Vue3 + Element Plus)
├── frontend-mp/             # 微信小程序端 (uni-app)
├── deploy/
│   ├── sql/schema.sql                  # 数据库建表脚本（含 RAG 向量表与广播表）
│   ├── sql/rag_pgvector.sql            # RAG 切片表增量迁移
│   ├── sql/rag_migrate_384_to_512.sql  # 384 → 512 维迁移
│   ├── sql/qa_template_migrate.sql     # 办公模板增量迁移（doc_type/description 字段）
│   ├── sql/notify_broadcast_migrate.sql # 信息精准推送增量迁移
│   └── nginx.conf
├── models/                  # 预下载的 BGE embedding 模型（.gitignore）
├── scripts/                 # 一键打包 + 离线部署脚本对
├── docs/
│   ├── ARCHITECTURE.md       # 系统架构与通信链路
│   ├── TEAM-COLLABORATION.md # 全部接口清单与分工
│   ├── FILE-REFERENCE.md     # 文件级实现说明
│   ├── DEPLOYMENT.md         # 离线部署手册
│   └── TODO.md               # 进度与待办
├── docker-compose.yml
└── README.md
```

---

## 环境要求

| 工具 | 版本 | 说明 |
|---|---|---|
| JDK | 17+ | 后端运行 |
| Node.js + npm | 18+ / 9+ | 前端构建 |
| PostgreSQL + pgvector | 16 | 开发数据库（替代 Kingbase），需启用 `vector` 扩展 |
| Redis | 7+ | 缓存 |
| Docker + Compose | 最新 | 跑 TEI Embedding 容器、一键部署 |
| 微信开发者工具 | 最新 | 小程序端调试（仅 D 同学需要） |

> Maven 不需要单独装，仓库自带 `mvnw` 包装脚本。

---

## 快速开始

### 1. 克隆与建库

```bash
git clone <仓库地址>
cd college-service-platform

# 最快路径：用 Docker 起一个带 pgvector 的 PG（推荐）
docker run -d --name college-pg \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=college_service \
  -p 5432:5432 \
  pgvector/pgvector:pg16

# 建表（含初始 admin 账号、审批类型、入党/入团流程模板）
docker exec -i college-pg psql -U postgres -d college_service < deploy/sql/schema.sql
```

如果用本地 PG，等价命令：
```bash
psql -U postgres -c "CREATE DATABASE college_service;"
psql -U postgres -d college_service -c "CREATE EXTENSION IF NOT EXISTS vector;"
psql -U postgres -d college_service -f deploy/sql/schema.sql
```

### 2. （可选）启动 TEI Embedding（智能问答模块需要）

```powershell
# 一次性下载 BGE 模型到 ./models/bge-small-zh-v1.5 (约 183MB)
# 详细命令见 docs/DEPLOYMENT.md §12.2
# 不下载也能跑，问答会降级为 manual 兜底

docker compose up -d embedding
docker compose logs --tail 10 embedding   # 看到 "Ready" 即可
```

### 3. 启动 Redis（缓存）

```bash
docker run -d --name college-redis -p 6379:6379 redis:7-alpine
```

### 4. 启动后端

```bash
cd backend

# 邮件功能需要授权码（QQ/网易企业邮的客户端授权码），可选
$env:MAIL_AUTH_CODE = "你的授权码"   # PowerShell
# export MAIL_AUTH_CODE=...           # bash

./mvnw.cmd spring-boot:run             # Windows
./mvnw spring-boot:run                 # macOS / Linux
```

启动成功后：
- API：http://localhost:8080/api
- Swagger 文档：http://localhost:8080/api/doc.html

### 5. 启动管理端

```bash
cd frontend-admin
npm install
npm run dev
```

访问 http://localhost:5173，用 `admin / admin123` 登录。

### 6. 启动小程序端

```bash
cd frontend-mp
npm install
npm run dev:mp-weixin   # 持续运行，文件改动会自动重新编译到 dist/dev/mp-weixin
```

然后用微信开发者工具导入 `frontend-mp/dist/dev/mp-weixin` 目录。需要在 **详情 → 本地设置** 勾选「不校验合法域名」。

---

## 离线生产部署

一行命令构建打包：

```powershell
pwsh scripts/build-deploy-package.ps1
```

部署前编辑 `deploy-package/.env`，把 `MAIL_AUTH_CODE=` 填上你的邮箱授权码（留空则邮件渠道自动降级，**不阻塞部署**）。

一行命令服务器部署：

```bash
scp -r deploy-package user@10.10.0.27:~/
ssh user@10.10.0.27 'cd ~/deploy-package && bash deploy.sh'
```

> **如果服务器有旧部署需要清空重来**（schema 升级后新字段不会自动迁移）：
> ```bash
> ssh user@10.10.0.27 'cd /opt/college-service && sudo docker compose down -v && sudo rm -rf /opt/college-service'
> ```
> 然后重跑上面的 scp + deploy.sh，自动进入 fresh 模式 → schema.sql 一刀切建好 16 张表 + 默认数据。

完整流程、清空步骤、故障排查见 [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)。

---

## 邮件配置（可选）

「信息精准推送」模块依赖 SMTP 发送真实邮件。授权码**不入库**、通过环境变量传入。`deploy/.env.prod` 和 `deploy/docker-compose.prod.yml` 已经把以下 4 个变量接通到 backend 容器：

| 变量 | yml/.env 默认 | 说明 |
|---|---|---|
| `MAIL_HOST` | `smtphz.qiye.163.com` | SMTP 服务器（RUC 学校邮箱 = 网易企业邮杭州节点，**实测可用**） |
| `MAIL_PORT` | `465` | SSL 端口。**注意**：smtphz 系列中 994 是 IMAP、995 是 POP3、465 才是 SMTP，别填错 |
| `MAIL_USERNAME` | `2024201564@ruc.edu.cn` | 发件人邮箱（必须与授权码同源） |
| `MAIL_AUTH_CODE` | (空) | 客户端授权码（在 https://mail.ruc.edu.cn → 设置 → 客户端授权密码 中生成，**不是登录密码**） |

其他邮箱服务商（覆盖默认即可）：QQ 个人 `smtp.qq.com:465` / QQ 企业 `smtp.exmail.qq.com:465` / 网易企业邮通用 `smtp.qiye.163.com:465`。

- 本地开发：在启动后端的 PowerShell 同会话内 `$env:MAIL_AUTH_CODE = "你的授权码"; .\mvnw.cmd spring-boot:run`
- 生产部署：在 `deploy-package/.env` 里编辑 `MAIL_AUTH_CODE=`

授权码缺失或鉴权失败时，邮件渠道自动降级为站内 `email_sim` 通知，不影响其他功能。

---

## 默认账号

`schema.sql` 执行后自动创建：

| 学号 | 密码 | 角色 |
|---|---|---|
| admin | admin123 | 院领导（1 级） |

可在管理端「用户管理」添加更多账号，或直接 INSERT：

```sql
INSERT INTO sys_user (student_id, name, password, role_level, grade, major, class_name, status)
VALUES ('2024001', '李同学',
        '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi',  -- admin123
        4, '2024', '计算机科学', '2024级1班', 1);
```

---

## 团队分工

| 成员 | 模块 | 分支 |
|---|---|---|
| A 同学 | 后端基础、认证、系统管理、邮件/通知、部署 | `feat/backend-base` |
| B 同学 | 智能问答、党团、审批、学生画像 | `feat/backend-biz` |
| C 同学 | PC 管理端 | `feat/frontend-admin` |
| D 同学 | 小程序端 | `feat/frontend-mp` |

Commit 消息规范：`<type>(<scope>): <描述>`，type 用 `feat / fix / docs / refactor / chore`，scope 取模块名（`auth / qa / party / approval / student / system / admin / mp / deploy / db`）。

合并经过 PR Review → `dev` → 里程碑节点合并到 `main`。

---

## 常见问题

**后端启动报 "Cannot connect to database"**
确认 Docker / 本地 PG 已起，`application-dev.yml` 中的 `url/username/password` 与实际一致。

**前端启动后 404 / 跨域**
Vite 已在 `vite.config.js` 中代理 `/api → :8080`，确认后端已启动。

**智能问答返回 `sourceType: manual`（没走 RAG）**
最常见原因：TEI 容器未起 / BGE 模型缺失 / `qa_document_chunk` 表为空。排查清单见 [docs/DEPLOYMENT.md §12.6](docs/DEPLOYMENT.md)。

**邮件群发后 `emailSent: 0`**
`MAIL_AUTH_CODE` 没读到、host/port 不匹配、或授权码与发件邮箱不对应（如腾讯授权码用在网易服务器上）。后端日志搜 `发送邮件失败`，看 `err=` 后的具体异常。

**pgvector 扩展不可用**
最简：用 `pgvector/pgvector:pg16` Docker 镜像。本地装见 [pgvector releases](https://github.com/pgvector/pgvector/releases)。

---

## 相关文档

- [系统架构](docs/ARCHITECTURE.md) — 全景图、通信链路、数据库设计、RAG 检索架构
- [团队协作 & API 接口](docs/TEAM-COLLABORATION.md) — 全部接口清单、状态机、约定
- [文件说明](docs/FILE-REFERENCE.md) — 每个文件的用途与实现进度
- [服务器部署](docs/DEPLOYMENT.md) — 离线部署、TEI/Embedding 服务、故障排查
- [进度与待办](docs/TODO.md) — 各模块完成情况、已知问题、迭代记录
