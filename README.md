# 学院学生综合服务与党团管理平台

中国人民大学信息学院 · 软件工程导论课程项目

---

## 项目简介

一站式学院级学生服务平台，包含智能问答、党团流程管理、电子证明审批、学生画像四大核心模块，支持四级权限体系（院领导/管理老师/班团骨干/普通学生）。

- **学生端**：微信小程序（uni-app）
- **管理端**：PC Web 后台（Vue3 + Element Plus）
- **后端**：Spring Boot 3 + MyBatis-Plus
- **数据库**：Kingbase V8（开发阶段使用 PostgreSQL 替代）

---

## 目录结构

```
college-service-platform/
├── backend/                 # 后端 Spring Boot 项目
├── frontend-admin/          # PC 管理端（Vue3 + Element Plus）
├── frontend-mp/             # 微信小程序端（uni-app）
├── deploy/
│   ├── sql/schema.sql       # 数据库建表脚本
│   └── nginx.conf           # Nginx 配置
├── docs/                    # 项目文档
│   ├── TEAM-COLLABORATION.md  # 团队协作与 API 接口文档
│   └── FILE-REFERENCE.md     # 文件说明文档
├── docker-compose.yml       # Docker 一键部署
└── README.md
```

---

## 环境要求

| 工具 | 版本 | 说明 |
|------|------|------|
| JDK | 17+ | 后端运行环境 |
| Maven | 3.8+ | 后端构建工具 |
| Node.js | 18+ | 前端构建环境 |
| npm | 9+ | 随 Node.js 安装 |
| PostgreSQL | 14+ | 开发阶段数据库（替代 Kingbase） |
| Redis | 7+ | 缓存 |
| 微信开发者工具 | 最新版 | 小程序端调试（D 同学需要） |
| Docker + Docker Compose | 最新版 | 可选，用于一键部署 |

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
  postgres:16-alpine

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
mvn spring-boot:run

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

| 账号 | 密码 | 角色 |
|------|------|------|
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

## Docker 一键部署（生产环境）

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
- Kingbase：localhost:54321
- Redis：localhost:6379

停止所有服务：
```bash
docker-compose down
```

---

## 开发配置说明

### 后端配置文件

| 文件 | 用途 |
|------|------|
| `application.yml` | 主配置（端口、JWT、AI 开关等） |
| `application-dev.yml` | 开发环境（连 PostgreSQL） |
| `application-prod.yml` | 生产环境（连 Kingbase） |

切换环境：修改 `application.yml` 中的 `spring.profiles.active`：
```yaml
spring:
  profiles:
    active: dev   # dev=开发 / prod=生产
```

修改数据库连接：编辑 `application-dev.yml`：
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/college_service
    username: postgres
    password: postgres    # 改成你的密码
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

| type | 含义 | 示例 |
|------|------|------|
| `feat` | 新功能 | `feat(approval): 实现审批状态机锁定逻辑` |
| `fix` | Bug 修复 | `fix(qa): 修复关键词匹配时的空指针异常` |
| `docs` | 文档更新 | `docs(readme): 添加环境配置说明` |
| `style` | 样式/格式调整（不影响逻辑） | `style(admin): 统一表格列宽` |
| `refactor` | 重构（不改变功能） | `refactor(party): 抽取流程步骤构建方法` |
| `test` | 添加/修改测试 | `test(auth): 添加登录接口单元测试` |
| `chore` | 构建/配置/依赖变更 | `chore: 升级 Element Plus 到 2.7.1` |

**scope 范围**：

| scope | 对应模块 | 负责人 |
|-------|---------|--------|
| `auth` | 认证登录 | A |
| `system` | 系统管理 | A |
| `qa` | 智能问答 | B |
| `party` | 党团流程 | B |
| `approval` | 审批流程 | B |
| `student` | 学生画像 | B |
| `admin` | 管理端前端 | C |
| `mp` | 小程序端前端 | D |
| `deploy` | 部署相关 | A |
| `db` | 数据库变更 | A |

### 冲突解决规则

当合并 `origin/dev` 时遇到冲突：

1. **只改自己模块的文件**：自行解决，保留自己的改动
2. **改到了别人模块的文件**：联系该模块负责人一起解决
3. **公共文件冲突**（如 `pom.xml`、`application.yml`、`router/index.js`）：
   - 通常是新增内容，两边都保留
   - 不确定时在群里沟通后再 commit
4. 冲突解决后，一定要 **本地验证能正常编译/启动** 再推送

### 禁止事项

| 操作 | 原因 |
|------|------|
| `git push origin main` | 直接推送到 main，绕过 Review |
| `git push origin dev` | 直接推送到 dev，绕过 Review |
| `git push --force` | 强制推送会覆盖他人代码 |
| `git reset --hard` 后推送 | 会丢失提交历史 |
| 提交 `node_modules/`、`target/`、`.env` | 这些在 .gitignore 中已排除，不要手动添加 |
| 修改他人分支的代码 | 只改自己分支，通过 PR 合并 |
| 在 PR 未审核通过时自行合并 | 必须等 Review 通过 |

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

| 学号 | 密码 | 角色 | 说明 |
|------|------|------|------|
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

---

## 相关文档

- [团队协作与 API 接口文档](docs/TEAM-COLLABORATION.md) — 分工、全部接口清单、状态机、迭代计划
- [文件说明文档](docs/FILE-REFERENCE.md) — 每个文件的用途与实现说明
