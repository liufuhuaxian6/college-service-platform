# 团队协作文档 — 学院学生综合服务与党团管理平台

> 最后更新: 2026-04-28 | 版本: 1.0

---

## 一、团队分工总览

| 成员 | 角色 | 负责模块 | 主要技术栈 |
|------|------|---------|-----------|
| **A 同学** | 后端架构 & 基础服务 | 通用框架、认证鉴权、系统管理、数据库、部署 | Spring Boot, MyBatis-Plus, Redis, Docker |
| **B 同学** | 后端业务开发 | 智能问答(P0)、党团流程(P0)、审批流程(P1)、学生画像(P1) | Spring Boot, MyBatis-Plus, AI接口 |
| **C 同学** | PC 管理端前端 | 管理后台全部页面（教师/领导使用） | Vue3, Element Plus, Vite |
| **D 同学** | 小程序端前端 | 微信小程序全部页面（学生使用） | uni-app(Vue3), 微信开发者工具 |

### 协作关系图

```
  C 同学 (PC管理端)          D 同学 (小程序端)
       │                         │
       │   HTTP/JSON (REST API)  │
       └───────────┬─────────────┘
                   │
                   ▼
         ┌─────────────────┐
         │   Nginx 网关     │
         └────────┬────────┘
                  │
    ┌─────────────┴──────────────┐
    ▼                            ▼
A 同学 (后端基础)          B 同学 (后端业务)
├─ 认证登录 /auth/*        ├─ 问答 /qa/*
├─ 用户管理 /system/*      ├─ 党团 /party/*
├─ 文件上传 /file/*        ├─ 审批 /approval/*
├─ 操作日志 /log/*         └─ 画像 /student/*
└─ 通知消息 /notify/*
```

---

## 二、各成员详细职责

### A 同学 — 后端架构 & 基础服务

**核心职责**: 搭建后端骨架，提供其他成员依赖的基础能力。

| 模块 | 具体任务 | 优先级 |
|------|---------|--------|
| 通用框架 | 统一响应(Result)、全局异常、分页封装 | ★★★ 已完成 |
| 认证鉴权 | JWT 登录、四级权限拦截器、`@RequireRole` | ★★★ 已完成 |
| 用户管理 | 用户 CRUD、Excel 批量导入学生名单、角色分配 | ★★★ |
| 操作日志 | `@OperationLog` AOP 切面、日志查询接口 | ★★☆ 已完成框架 |
| 文件服务 | 通用上传/下载接口 (MinIO 或本地存储) | ★★☆ |
| 通知服务 | 模拟短信、系统通知的发送与查询 | ★☆☆ |
| 数据库 | schema.sql 维护、数据迁移脚本 | ★★★ 已完成 |
| 部署 | Docker Compose + Nginx + Kingbase 对接 | ★★☆ |
| 加密工具 | AES 加密/解密、前端脱敏显示 | ★★★ 已完成 |

**你需要提供给团队的**:
1. `UserContext.getUserId()` — 获取当前登录用户 ID
2. `UserContext.getRoleLevel()` — 获取当前用户角色等级
3. `@RequireRole(minLevel = N)` — 权限控制注解
4. `@OperationLog(module, action)` — 操作日志注解
5. 文件上传/下载通用 Service
6. 通知发送 Service

---

### B 同学 — 后端业务开发

**核心职责**: 实现全部 4 个业务模块的 Controller → Service → Mapper。

| 模块 | 具体任务 | 优先级 |
|------|---------|--------|
| 智能问答 (P0) | 知识库 CRUD、关键词检索、AI 调用、聊天记录 | ★★★ |
| 党团流程 (P0) | 流程模板管理、学生进度追踪、到期提醒 | ★★★ |
| 审批流程 (P1) | 状态机、多级审批链、撤回/锁定、证明生成 | ★★★ |
| 学生画像 (P1) | 学生信息查询、荣誉管理 | ★★☆ |
| 政策文档 (P0) | 文档上传管理、分类、下载计数 | ★★☆ |

**你需要依赖 A 同学的**:
- `UserContext` — 获取当前用户信息
- `@RequireRole` — 接口权限控制
- `@OperationLog` — 关键操作记日志
- 文件上传 Service — 政策文档 / 证明文件存储
- 通知 Service — 审批结果通知、流程提醒

---

### C 同学 — PC 管理端前端

**核心职责**: Element Plus 后台管理系统，供教师/辅导员/院领导使用。

| 页面 | 功能 | 对接接口(B同学) |
|------|------|----------------|
| 登录页 | 学号+密码登录 | `POST /auth/login` |
| 数据概览 | 统计卡片(学生数/待审批/流程中) | `GET /system/dashboard` |
| 知识库管理 | 增删改查标准问答、分类筛选 | `/qa/knowledge/*` |
| 文档管理 | 上传/下载政策文件 | `/qa/document/*` |
| 党团流程管理 | 流程模板编辑、学生进度管理 | `/party/*` |
| 审批管理 | 待审批列表、通过/驳回/撤回 | `/approval/*` |
| 学生信息管理 | 学生列表、画像详情、荣誉录入 | `/student/*` |
| 用户管理 | 角色分配、Excel 导入 | `/system/user/*` |
| 操作日志 | 日志列表、时间/模块筛选 | `/log/*` |

**你需要关注的前端规范**:
- 界面风格: **严谨、正式、朴素**，蓝/灰/白主色调，无花哨动画
- 布局: 左侧树状导航 + 顶栏 + 右侧工作区
- 权限: 根据 `roleLevel` 动态显示/隐藏菜单项
- 脱敏: 身份证等敏感字段用 `***` 显示

---

### D 同学 — 微信小程序端前端

**核心职责**: uni-app 小程序，供学生日常使用。

| 页面 | 功能 | 对接接口(B同学) |
|------|------|----------------|
| 登录页 | 学号+密码登录 | `POST /auth/login` |
| 首页 | 四宫格入口 + 通知列表 | `GET /notify/unread` |
| 智能问答 | 对话式问答界面 | `POST /qa/chat` |
| 政策文档 | 文档列表、分类、下载 | `GET /qa/document/*` |
| 我的党团进度 | 步骤条可视化、当前进度 | `GET /party/my-progress` |
| 我的申请 | 申请列表、提交新申请、下载证明 | `/approval/my/*` |
| 个人信息 | 基本信息、荣誉展示 | `GET /student/profile` |
| 消息中心 | 通知列表、已读标记 | `/notify/*` |

**你需要关注的前端规范**:
- 界面风格: 同样严谨正式，符合高校行政风格
- 交互: 操作流畅，层级扁平，减少跳转
- 学生只能看自己的数据，不能看他人

---

## 三、全部 API 接口清单

> 所有接口前缀: `/api`
> 认证方式: `Authorization: Bearer <token>` (除登录注册外)
> 响应格式: `{ "code": 200, "message": "success", "data": {...} }`

### 3.1 认证模块 — `/auth` (A 同学)

| 方法 | 路径 | 说明 | 权限 | 请求体 | 响应 |
|------|------|------|------|--------|------|
| POST | `/auth/login` | 登录 | 无需Token | `{ studentId, password }` | `{ token, userId, name, roleLevel, studentId }` |
| POST | `/auth/register` | 注册 | 无需Token | `{ studentId, name, password }` | 无 |
| GET | `/auth/profile` | 获取当前用户信息 | 全部角色 | 无 | `SysUser` (脱敏) |
| PUT | `/auth/password` | 修改密码 | 全部角色 | `{ oldPassword, newPassword }` | 无 |

### 3.2 系统管理 — `/system` (A 同学)

| 方法 | 路径 | 说明 | 权限 | 请求体/参数 | 响应 |
|------|------|------|------|------------|------|
| GET | `/system/user/page` | 用户分页列表 | ≤2级 | `?page=1&size=20&grade=&major=` | `PageResult<SysUser>` |
| GET | `/system/user/{id}` | 用户详情 | ≤2级 | 无 | `SysUser` |
| PUT | `/system/user/{id}` | 修改用户信息 | ≤2级 | `SysUser` 部分字段 | 无 |
| PUT | `/system/user/{id}/role` | 设置角色等级 | ≤1级 | `{ roleLevel }` | 无 |
| POST | `/system/user/import` | Excel 批量导入 | ≤2级 | `multipart/form-data (file)` | `{ success: 120, fail: 3, errors: [...] }` |
| GET | `/system/user/export` | 导出学生名单 | ≤2级 | `?grade=&major=` | Excel文件流 |
| GET | `/system/dashboard` | 数据概览 | ≤2级 | 无 | `{ totalStudents, pendingApprovals, activeProcesses, ... }` |

### 3.3 操作日志 — `/log` (A 同学)

| 方法 | 路径 | 说明 | 权限 | 参数 | 响应 |
|------|------|------|------|------|------|
| GET | `/log/page` | 日志分页查询 | ≤1级 | `?page=1&size=20&module=&startDate=&endDate=` | `PageResult<OperationLog>` |

### 3.4 文件服务 — `/file` (A 同学)

| 方法 | 路径 | 说明 | 权限 | 请求体 | 响应 |
|------|------|------|------|--------|------|
| POST | `/file/upload` | 通用文件上传(≤30MB) | ≤2级 | `multipart/form-data` | `{ fileId, filePath, fileName, fileSize }` |
| GET | `/file/download/{fileId}` | 文件下载 | 全部角色 | 无 | 文件流 |

### 3.5 通知消息 — `/notify` (A 同学)

| 方法 | 路径 | 说明 | 权限 | 参数 | 响应 |
|------|------|------|------|------|------|
| GET | `/notify/page` | 我的通知列表 | 全部角色 | `?page=1&size=20&type=` | `PageResult<Notification>` |
| GET | `/notify/unread-count` | 未读数量 | 全部角色 | 无 | `{ count: 5 }` |
| PUT | `/notify/{id}/read` | 标记已读 | 全部角色 | 无 | 无 |
| PUT | `/notify/read-all` | 全部标记已读 | 全部角色 | 无 | 无 |

---

### 3.6 智能问答与知识库 — `/qa` (B 同学)

| 方法 | 路径 | 说明 | 权限 | 请求体/参数 | 响应 |
|------|------|------|------|------------|------|
| **问答对话** |
| POST | `/qa/chat` | 提问(检索+AI) | 全部角色 | `{ question }` | `{ answer, sourceType, sourceUrl }` |
| GET | `/qa/chat/history` | 我的问答记录 | 全部角色 | `?page=1&size=20` | `PageResult<ChatLog>` |
| **知识库管理 (管理端)** |
| GET | `/qa/knowledge/page` | 知识条目列表 | ≤2级 | `?page=1&size=20&category=&keyword=` | `PageResult<QaKnowledge>` |
| GET | `/qa/knowledge/{id}` | 知识条目详情 | ≤2级 | 无 | `QaKnowledge` |
| POST | `/qa/knowledge` | 新增知识条目 | ≤2级 | `{ category, question, answer, keywords, sourceUrl }` | `{ id }` |
| PUT | `/qa/knowledge/{id}` | 修改知识条目 | ≤2级 | 同上 | 无 |
| DELETE | `/qa/knowledge/{id}` | 删除知识条目 | ≤2级 | 无 | 无 |
| **政策文档** |
| GET | `/qa/document/list` | 文档列表(含分类) | 全部角色 | `?category=` | `List<QaDocument>` |
| POST | `/qa/document` | 上传政策文档 | ≤2级 | `multipart/form-data + { title, category }` | `{ id }` |
| GET | `/qa/document/{id}/download` | 下载文档 | 全部角色 | 无 | 文件流(计数+1) |
| DELETE | `/qa/document/{id}` | 删除文档 | ≤2级 | 无 | 无 |

#### 关键数据结构

```json
// POST /qa/chat 响应
{
  "code": 200,
  "data": {
    "answer": "入党流程共8个步骤，首先需要递交入党申请书...",
    "sourceType": "knowledge",   // knowledge | ai | manual
    "sourceUrl": "https://...",  // 官方链接(如有)
    "aiGenerated": false         // true 时前端需标注"AI生成,仅供参考"
  }
}
```

---

### 3.7 党团事务流程 — `/party` (B 同学)

| 方法 | 路径 | 说明 | 权限 | 请求体/参数 | 响应 |
|------|------|------|------|------------|------|
| **学生端** |
| GET | `/party/templates` | 所有流程模板 | 全部角色 | 无 | `List<ProcessTemplate>` (含步骤) |
| GET | `/party/my-progress` | 我的所有流程进度 | 全部角色 | 无 | `List<ProcessInstance>` (含当前步骤) |
| GET | `/party/my-progress/{instanceId}` | 流程详情 | 全部角色 | 无 | `ProcessInstance` (含全部步骤状态) |
| **管理端** |
| GET | `/party/template/page` | 模板分页列表 | ≤2级 | `?page=1&size=20` | `PageResult<ProcessTemplate>` |
| POST | `/party/template` | 创建流程模板 | ≤2级 | `{ name, description, steps: [...] }` | `{ id }` |
| PUT | `/party/template/{id}` | 修改流程模板 | ≤2级 | 同上 | 无 |
| GET | `/party/instance/page` | 学生流程列表 | ≤2级 | `?page=1&size=20&templateId=&userId=&status=` | `PageResult<ProcessInstance>` |
| POST | `/party/instance` | 为学生创建流程 | ≤2级 | `{ userId, templateId, startDate }` | `{ id }` |
| PUT | `/party/instance/{id}/advance` | 推进到下一步 | ≤2级 | `{ remark }` | 无 |
| PUT | `/party/instance/{id}/suspend` | 暂停流程 | ≤2级 | `{ remark }` | 无 |

#### 关键数据结构

```json
// GET /party/my-progress/{id} 响应 — 供前端渲染步骤条
{
  "code": 200,
  "data": {
    "id": 1,
    "templateName": "入党流程",
    "currentStep": 3,
    "status": "active",
    "startDate": "2025-09-01",
    "steps": [
      { "stepOrder": 1, "name": "递交入党申请书", "completed": true, "completedAt": "2025-09-01" },
      { "stepOrder": 2, "name": "确定入党积极分子", "completed": true, "completedAt": "2025-12-01" },
      { "stepOrder": 3, "name": "积极分子培养考察", "completed": false, "durationDays": 365, "expectedEnd": "2026-12-01" },
      { "stepOrder": 4, "name": "确定发展对象", "completed": false },
      ...
    ]
  }
}
```

---

### 3.8 审批流程 — `/approval` (B 同学)

| 方法 | 路径 | 说明 | 权限 | 请求体/参数 | 响应 |
|------|------|------|------|------------|------|
| **学生端** |
| GET | `/approval/types` | 可申请的审批类型 | 全部角色 | 无 | `List<ApprovalType>` |
| POST | `/approval/apply` | 提交申请 | 全部角色 | `{ typeId, formData: {...} }` | `{ id, appNo }` |
| GET | `/approval/my/page` | 我的申请列表 | 全部角色 | `?page=1&size=20&status=` | `PageResult<Application>` |
| GET | `/approval/my/{id}` | 申请详情 | 全部角色 | 无 | `Application` (含审批记录) |
| PUT | `/approval/my/{id}/withdraw` | 撤回申请 | 全部角色 | 无 | 无 |
| GET | `/approval/my/{id}/download` | 下载证明(触发锁定!) | 全部角色 | 无 | 文件流 |
| **管理端** |
| GET | `/approval/pending/page` | 待审批列表 | ≤2级 | `?page=1&size=20&typeId=` | `PageResult<Application>` |
| PUT | `/approval/{id}/approve` | 通过 | ≤2级 | `{ comment }` | 无 |
| PUT | `/approval/{id}/reject` | 驳回 | ≤2级 | `{ comment }` | 无 |
| PUT | `/approval/{id}/admin-withdraw` | 管理员撤回(已通过未下载) | ≤2级 | `{ comment }` | 无 |
| GET | `/approval/all/page` | 全部申请列表 | ≤2级 | `?page=1&size=20&status=&userId=` | `PageResult<Application>` |

#### 审批状态机(重要!)

```
                    ┌──────────┐
                    │  draft   │ 草稿
                    └────┬─────┘
                         │ POST /apply (学生提交)
                    ┌────▼─────┐
               ┌───│ pending  │ 待审批
               │   └────┬─────┘
               │        │ PUT /approve (管理员通过)
               │   ┌────▼─────┐
               │   │ approved │ 已通过 (撤回窗口期: 1-2天)
               │   └────┬─────┘
               │        │ GET /download (学生下载证明)
               │   ┌────▼──────┐
               │   │downloaded │ ★已锁定 — 所有修改操作被禁止
               │   └───────────┘
               │
               │  PUT /reject        PUT /withdraw
               │   ┌──────────┐      ┌───────────┐
               └──►│ rejected │      │ withdrawn │
                   └──────────┘      └───────────┘
                      │                   │
                      └───────┬───────────┘
                              │ 学生可重新编辑后再次提交
                              ▼
                          (回到 draft)
```

**锁定规则**(B 同学务必严格实现):
1. 学生调用 `/download` → 状态变为 `downloaded` + 记录 `downloaded_at`
2. `downloaded` 状态后，**任何** PUT 接口必须拒绝并返回 403
3. 管理员撤回: 仅 `approved` 状态 + `downloaded_at IS NULL` + 未超过 `withdraw_deadline`

#### 关键数据结构

```json
// GET /approval/my/{id} 响应
{
  "code": 200,
  "data": {
    "id": 1,
    "appNo": "CERT-20260428-001",
    "typeName": "在读证明",
    "status": "approved",
    "formData": { "purpose": "考研", "copies": 2 },
    "withdrawDeadline": "2026-04-30 17:00:00",
    "downloadedAt": null,
    "createdAt": "2026-04-28 10:00:00",
    "records": [
      { "approverName": "张老师", "action": "approve", "comment": "同意", "createdAt": "2026-04-28 14:00:00" }
    ]
  }
}
```

---

### 3.9 学生画像与荣誉 — `/student` (B 同学)

| 方法 | 路径 | 说明 | 权限 | 请求体/参数 | 响应 |
|------|------|------|------|------------|------|
| **学生端** |
| GET | `/student/profile` | 我的个人信息 | 全部角色 | 无 | `StudentProfile` |
| GET | `/student/honors` | 我的荣誉列表 | 全部角色 | 无 | `List<Honor>` |
| **管理端** |
| GET | `/student/page` | 学生列表(含筛选) | ≤2级 | `?page=1&size=20&grade=&major=&className=` | `PageResult<StudentProfile>` |
| GET | `/student/{id}/detail` | 学生详情画像 | ≤2级 | 无 | `StudentProfile` (含荣誉/流程/申请) |
| POST | `/student/{id}/honor` | 录入荣誉 | ≤2级 | `{ honorName, honorLevel, awardDate, certFile }` | `{ id }` |
| PUT | `/student/honor/{honorId}` | 修改荣誉 | ≤2级 | 同上 | 无 |
| DELETE | `/student/honor/{honorId}` | 删除荣誉 | ≤2级 | 无 | 无 |

#### 数据隔离规则(重要!)

| 角色 | 可见数据范围 |
|------|-------------|
| 4级(学生) | **仅自己的数据** |
| 3级(班团骨干) | 本班基础信息(学号/姓名/班级)，无敏感字段 |
| 2级(辅导员/管理老师) | 本年级或全院，敏感字段脱敏显示 |
| 1级(院领导) | 全院所有数据 |

---

## 四、统一约定

### 4.1 请求/响应规范

```
# 请求头
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type: application/json

# 统一响应
{
  "code": 200,          // 200=成功, 400=参数错误, 401=未登录, 403=无权限, 500=服务器错误
  "message": "success",
  "data": { ... }       // 成功时的业务数据
}

# 分页响应
{
  "code": 200,
  "data": {
    "total": 156,
    "page": 1,
    "size": 20,
    "records": [ ... ]
  }
}

# 分页请求参数(统一)
?page=1&size=20&sortField=createdAt&sortOrder=desc
```

### 4.2 Git 分支策略

```
main                    ← 稳定版本, 仅合并经过测试的代码
├── dev                 ← 开发集成分支, 所有人往这里合并
│   ├── feat/backend-base    ← A 同学: 后端基础
│   ├── feat/backend-biz     ← B 同学: 后端业务
│   ├── feat/frontend-admin  ← C 同学: 管理端
│   └── feat/frontend-mp     ← D 同学: 小程序端
```

**工作流程**:
1. 各自在自己的分支开发
2. 开发完一个功能，先 `git pull origin dev` 合并最新代码
3. 解决冲突后，提 PR 合并到 `dev`
4. 每周末集中联调，`dev` 测试通过后合并到 `main`

### 4.3 开发环境搭建

```bash
# 后端 (A/B 同学)
cd backend
# 1. 安装 JDK 17 + Maven
# 2. 启动 PostgreSQL (开发替代 Kingbase)
#    创建数据库: CREATE DATABASE college_service;
# 3. 执行建表: psql -d college_service -f ../deploy/sql/schema.sql
# 4. 启动 Redis
# 5. 运行
mvn spring-boot:run

# 管理端 (C 同学)
cd frontend-admin
npm install
npm run dev
# 访问 http://localhost:5173

# 小程序端 (D 同学)
cd frontend-mp
npm install
# 用微信开发者工具导入项目, 或
npm run dev:mp-weixin
```

### 4.4 联调约定

- 后端统一运行在 `http://localhost:8080/api`
- 前端开发时通过 Vite proxy 代理到后端，避免跨域问题
- **接口联调前**: B 同学先用 Knife4j (http://localhost:8080/api/doc.html) 自测
- **Mock 数据**: 前端同学可先用 Mock 数据开发页面，后端接口就绪后切换为真实接口

---

## 五、迭代计划与里程碑

### Sprint 1 (第1-2周) — 基础打通

| 成员 | 任务 | 交付物 |
|------|------|--------|
| A | 后端能跑通: 登录+用户CRUD+文件上传 | 可用的基础 API |
| B | 知识库 CRUD + 问答检索(不含AI) | 知识库相关 API |
| C | 项目初始化 + 登录页 + 基础布局(侧边栏+顶栏) | 可看到的管理端骨架 |
| D | 项目初始化 + 登录页 + 首页四宫格 | 可看到的小程序骨架 |

### Sprint 2 (第3-4周) — P0 功能

| 成员 | 任务 |
|------|------|
| A | Excel 导入导出 + 通知服务 + 操作日志查询 |
| B | 党团流程全部接口 + AI 抽象层对接 |
| C | 知识库管理页 + 文档管理页 + 党团流程管理页 |
| D | 问答对话页 + 文档下载页 + 党团进度页 |

### Sprint 3 (第5-8周) — P1 功能

| 成员 | 任务 |
|------|------|
| A | 数据概览接口 + 部署脚本 |
| B | 审批全流程(状态机+证明生成) + 学生画像 |
| C | 审批管理页 + 学生画像页 + 用户管理页 + 日志页 |
| D | 申请提交页 + 证明下载页 + 个人信息页 + 消息中心 |

### Sprint 4 (第9-12周) — 联调 & 收尾

| 成员 | 任务 |
|------|------|
| A | Kingbase 对接 + Docker 部署 + 性能优化 |
| B | Bug 修复 + 接口补全 + 数据初始化脚本 |
| C | 联调修复 + 界面打磨 + 用户手册(管理端) |
| D | 联调修复 + 界面打磨 + 用户手册(小程序端) |
| **全员** | **API 文档整理 + 答辩 PPT + 演示准备** |

---

## 六、常见问题

**Q: 前端怎么判断用户权限来显示不同菜单?**
登录接口返回 `roleLevel`，存到本地。路由守卫中判断:
```js
// roleLevel: 1=院领导, 2=管理老师, 3=班团骨干, 4=普通学生
if (to.meta.minRole && userStore.roleLevel > to.meta.minRole) {
  return '/403'
}
```

**Q: 后端怎么获取当前登录用户?**
```java
Long userId = UserContext.getUserId();
int role = UserContext.getRoleLevel();
```

**Q: 怎么给接口加权限控制?**
```java
@RequireRole(minLevel = 2)  // ≤2级才能访问
@PostMapping("/import")
public Result<?> importStudents() { ... }
```

**Q: 怎么记录操作日志?**
```java
@OperationLog(module = "审批管理", action = "通过申请")
@PutMapping("/{id}/approve")
public Result<?> approve() { ... }
```

**Q: 敏感字段怎么处理?**
```java
// 存储时加密
user.setIdCardEnc(EncryptUtil.encrypt("110101199001011234"));
// 返回前端时脱敏
String display = EncryptUtil.desensitize(decrypted, 3, 4); // 110***********1234
```
