# 项目运行架构与通信方式说明

> 本文档面向所有团队成员，帮助你理解整个系统是怎么跑起来的、各部分之间怎么通信。

---

## 一、系统全景图

```
┌──────────────────────────────────────────────────────────────────────────┐
│                           用户设备                                       │
│                                                                          │
│   📱 学生手机                              💻 教师/管理员电脑              │
│   ┌──────────────────┐                    ┌──────────────────────┐       │
│   │  微信小程序端      │                    │  PC 管理端浏览器      │       │
│   │  (frontend-mp)   │                    │  (frontend-admin)    │       │
│   │                  │                    │                      │       │
│   │  uni-app (Vue3)  │                    │  Vue3 + Element Plus │       │
│   │  运行在微信内      │                    │  运行在 Chrome 等     │       │
│   └────────┬─────────┘                    └──────────┬───────────┘       │
│            │                                         │                   │
└────────────┼─────────────────────────────────────────┼───────────────────┘
             │          HTTPS (JSON)                   │
             │                                         │
┌────────────┼─────────────────────────────────────────┼───────────────────┐
│            ▼                                         ▼                   │
│   ┌──────────────────────────────────────────────────────────┐           │
│   │                    Nginx 反向代理                          │           │
│   │                                                          │           │
│   │   /           → 管理端前端静态文件 (dist/index.html)       │           │
│   │   /api/*      → 转发到后端 Spring Boot :8080              │           │
│   │                                                          │           │
│   └──────────────────────────┬───────────────────────────────┘           │
│                              │                                           │
│   ┌──────────────────────────▼───────────────────────────────┐           │
│   │              Spring Boot 后端 (:8080)                     │           │
│   │                                                          │           │
│   │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌─────────┐ │           │
│   │  │ 认证鉴权  │  │ 智能问答  │  │ 党团流程  │  │ 审批流程 │ │           │
│   │  │ (JWT)    │  │ (AI接口)  │  │          │  │ (状态机) │ │           │
│   │  └──────────┘  └──────────┘  └──────────┘  └─────────┘ │           │
│   │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌─────────┐ │           │
│   │  │ 学生画像  │  │ 用户管理  │  │ 文件服务  │  │ 通知消息 │ │           │
│   │  └──────────┘  └──────────┘  └──────────┘  └─────────┘ │           │
│   │                                                          │           │
│   └───────┬──────────────────┬───────────────────┬───────────┘           │
│           │                  │                   │                       │
│   ┌───────▼──────┐   ┌──────▼───────┐   ┌──────▼───────┐               │
│   │  PostgreSQL  │   │    Redis     │   │  文件系统     │               │
│   │  / Kingbase  │   │    缓存      │   │  (uploads/)  │               │
│   │  :5432       │   │   :6379      │   │              │               │
│   └──────────────┘   └──────────────┘   └──────────────┘               │
│                                                                          │
│                         服务器                                            │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 二、各端是什么、跑在哪里

### 2.1 小程序端 (`frontend-mp`)

```
技术: uni-app (Vue3)
运行位置: 学生的手机微信内
打包产物: 微信小程序代码包（上传到微信平台）

开发时:
  源码目录:  frontend-mp/src/
  编译命令:  npm run dev:mp-weixin
  编译产物:  frontend-mp/dist/dev/mp-weixin/
  调试工具:  微信开发者工具导入 dist/dev/mp-weixin 目录
```

小程序是一个**纯前端应用**，本身没有服务器能力。它所有的数据（用户信息、问答结果、审批状态等）都是通过 HTTP 请求从后端获取的。

### 2.2 PC 管理端 (`frontend-admin`)

```
技术: Vue3 + Vite + Element Plus
运行位置: 教师电脑的浏览器中 (Chrome/Edge)
打包产物: 静态 HTML/CSS/JS 文件

开发时:
  源码目录:  frontend-admin/src/
  启动命令:  npm run dev
  访问地址:  http://localhost:5173
  
生产部署:
  构建命令:  npm run build
  产物目录:  frontend-admin/dist/
  部署方式:  放到 Nginx 静态文件目录下
```

管理端同样是**纯前端应用**，和小程序一样，所有数据来自后端 API。

### 2.3 后端 (`backend`)

```
技术: Spring Boot 3 + MyBatis-Plus
运行位置: 服务器上 (开发时是你的电脑)
暴露端口: 8080

启动命令:  cd backend && ./mvnw.cmd spring-boot:run
API 前缀:  http://localhost:8080/api/
API 文档:  http://localhost:8080/api/doc.html
```

后端是整个系统的**大脑**，负责：
- 接收前端请求，处理业务逻辑，返回 JSON 数据
- 连接数据库读写数据
- 管理用户认证和权限
- 调用 AI 大模型
- 生成证明文件

### 2.4 数据库与中间件

```
PostgreSQL (:5432)  — 存储所有业务数据（用户、知识库、流程、审批等14张表）
Redis (:6379)       — 缓存热点数据、Session
文件系统 (uploads/) — 存储上传的政策文档、证明文件
```

---

## 三、通信方式详解

### 3.1 前后端通信: RESTful HTTP + JSON

小程序端和管理端与后端之间使用完全相同的通信协议：

```
前端                              后端
  │                                │
  │   POST /api/auth/login         │
  │   {studentId, password}        │
  │──────────────────────────────→ │
  │                                │  验证密码
  │                                │  生成 JWT Token
  │   200 OK                       │
  │   {token, userId, name, ...}   │
  │ ←────────────────────────────  │
  │                                │
  │   GET /api/qa/knowledge/page   │
  │   Authorization: Bearer xxx    │
  │──────────────────────────────→ │
  │                                │  解析 Token
  │                                │  检查权限
  │                                │  查询数据库
  │   200 OK                       │
  │   {total, records: [...]}      │
  │ ←────────────────────────────  │
```

**关键点：**
- 协议: HTTP(S)
- 数据格式: JSON
- 认证方式: JWT Token（放在请求头 `Authorization: Bearer xxx`）
- 所有接口以 `/api` 开头

### 3.2 JWT 认证流程

```
1. 登录
   前端 → POST /api/auth/login {studentId, password}
   后端 → 验证通过，返回 JWT Token
   前端 → 将 Token 存到本地 (localStorage / uni.setStorage)

2. 后续每个请求
   前端 → 请求头带上 Authorization: Bearer <token>
   后端 → AuthInterceptor 拦截
          → JwtUtil.parseToken() 解析出 userId, roleLevel
          → 存入 UserContext (ThreadLocal)
          → 检查 @RequireRole 权限
          → 放行或拒绝 (401/403)

3. Token 过期
   后端 → 返回 401
   前端 → 拦截到 401，清除本地 Token，跳转到登录页
```

### 3.3 两个前端调用同一套后端 API

```
┌────────────┐        ┌────────────┐
│  小程序端   │        │  管理端     │
│  (学生用)   │        │  (教师用)   │
└─────┬──────┘        └─────┬──────┘
      │                     │
      │  同一套 REST API     │
      │  同一个后端服务       │
      │  同一个数据库         │
      │                     │
      └──────────┬──────────┘
                 │
          ┌──────▼──────┐
          │  后端 :8080  │
          └──────┬──────┘
                 │
          ┌──────▼──────┐
          │  PostgreSQL  │
          └─────────────┘
```

虽然两个前端用户界面完全不同，但它们：
- 调用的是**同一个后端**的**同一套接口**
- 读写的是**同一个数据库**
- 权限由后端通过 `roleLevel` 控制（不是由前端决定的）

**举例**：管理员在 PC 端通过审批后，学生在小程序端刷新就能看到状态变化，因为数据都存在同一个数据库里。

### 3.4 前端请求的封装方式

#### 管理端 (Axios)

```javascript
// frontend-admin/src/api/request.js
const request = axios.create({ baseURL: '/api' })

// 自动注入 Token
request.interceptors.request.use(config => {
  config.headers.Authorization = `Bearer ${token}`
  return config
})

// 调用示例
const res = await request.get('/qa/knowledge/page', { params: { page: 1 } })
// 实际请求: GET http://localhost:5173/api/qa/knowledge/page?page=1
//           → Vite 代理转发到 http://localhost:8080/api/qa/knowledge/page?page=1
```

#### 小程序端 (uni.request)

```javascript
// frontend-mp/src/api/index.js
uni.request({
  url: '/api/auth/login',
  method: 'POST',
  header: { Authorization: `Bearer ${token}` },
  data: { studentId, password },
  success: (res) => { ... }
})
```

两者做的事情完全一样：**发 HTTP 请求到后端，带上 Token，拿回 JSON 数据**。只是用的库不同（浏览器用 Axios，小程序用 uni.request）。

---

## 四、开发时的代理机制

开发时前端和后端跑在不同端口上，浏览器有**跨域限制**，所以需要代理：

```
                    开发环境
                    
浏览器请求            Vite 开发服务器            后端
http://localhost:5173  → :5173                 → :8080

  GET /api/qa/...
      │
      ▼
  Vite (:5173) 收到请求
  发现路径以 /api 开头
  根据 vite.config.js 中的 proxy 配置
  转发到 http://localhost:8080/api/qa/...
      │
      ▼
  后端 (:8080) 收到请求
  正常处理，返回 JSON
      │
      ▼
  Vite 把响应透传回浏览器
```

配置位置:
```javascript
// frontend-admin/vite.config.js
server: {
  proxy: {
    '/api': { target: 'http://localhost:8080' }
  }
}
```

**生产环境**不需要 Vite，由 Nginx 同时负责静态文件和 API 代理。

---

## 五、生产部署架构

```
用户手机/电脑
     │
     ▼
┌──────────────────────────────────────────────┐
│           Nginx (:80 / :443)                 │
│                                              │
│   请求路径         →  转发目标                 │
│   ─────────────────────────────────────────  │
│   /               →  /usr/share/nginx/html/  │
│                      (管理端 dist 静态文件)    │
│                                              │
│   /api/*          →  http://backend:8080     │
│                      (Spring Boot 后端)       │
│                                              │
│   小程序直接请求 →  https://你的域名/api/*     │
│                      (同上)                   │
└───────────────────────────┬──────────────────┘
                            │
              ┌─────────────┼──────────────┐
              ▼             ▼              ▼
         ┌────────┐   ┌────────┐   ┌────────────┐
         │ Spring │   │ Redis  │   │ PostgreSQL │
         │ Boot   │   │        │   │ / Kingbase │
         │ :8080  │   │ :6379  │   │ :5432      │
         └────────┘   └────────┘   └────────────┘
```

Docker Compose 一键启动所有容器：
```bash
docker-compose up -d
# 启动 4 个容器: nginx, backend, redis, kingbase
```

---

## 六、一次完整请求的生命周期

以**学生在小程序上提交证明申请**为例，走完整个链路：

```
1. 学生操作
   学生在小程序的"提交申请"页面选择"在读证明"，填写表单，点击提交

2. 小程序发请求
   POST /api/approval/apply
   Header: Authorization: Bearer eyJhbG...
   Body: { "typeId": 1, "formData": { "purpose": "考研", "copies": 2 } }

3. Nginx 转发 (生产环境) / Vite 代理 (开发环境)
   → http://localhost:8080/api/approval/apply

4. AuthInterceptor 拦截
   解析 JWT Token → 得到 userId=2, roleLevel=4(学生)
   存入 UserContext

5. ApprovalController.apply() 接收请求
   调用 ApprovalService.apply()

6. ApprovalService.apply() 处理业务
   a. 查询 approval_type 表获取审批链 → "2" (只需二级审批)
   b. 生成申请编号 "CERT-20260509-0001"
   c. 创建 ApprovalApplication 记录
      status = "pending", currentApproverLevel = 2
   d. INSERT INTO approval_application (...)
   e. 返回 { id: 5, appNo: "CERT-20260509-0001" }

7. Controller 封装响应
   { "code": 200, "message": "success", "data": { "id": 5, "appNo": "CERT-20260509-0001" } }

8. 响应返回小程序
   小程序收到 200，跳转到"我的申请"列表页

9. 后续: 管理员在 PC 端审批
   GET /api/approval/pending/page → 看到这条待审批
   PUT /api/approval/5/approve → 状态变为 "approved"

10. 学生在小程序刷新
    GET /api/approval/my/page → 看到状态变为"已通过"
    GET /api/approval/my/5/download → 下载证明 → 状态锁定为 "downloaded"
```

---

## 七、各端开发者需要关心什么

### A 同学 (后端基础)

```
你的代码在:  backend/src/main/java/com/ruc/college/common/
             backend/src/main/java/com/ruc/college/module/auth/
             backend/src/main/java/com/ruc/college/module/system/

你提供给其他人的:
  → B 同学调用你的 UserContext、@RequireRole、@OperationLog、通知 Service
  → C/D 同学调用你写的 /auth、/system、/notify、/file 接口
```

### B 同学 (后端业务)

```
你的代码在:  backend/src/main/java/com/ruc/college/module/qa/
             backend/src/main/java/com/ruc/college/module/party/
             backend/src/main/java/com/ruc/college/module/approval/
             backend/src/main/java/com/ruc/college/module/student/

你依赖 A 同学的:
  → UserContext.getUserId() 获取当前用户
  → @RequireRole 控制权限
  → SystemService.sendNotification() 发通知

你提供给 C/D 同学的:
  → /qa、/party、/approval、/student 全部接口
```

### C 同学 (管理端前端)

```
你的代码在:  frontend-admin/src/

你调用的接口:
  → 全部后端 API (通过 src/api/index.js 封装)
  → 不直接访问数据库，一切数据通过 HTTP 获取

你不需要关心:
  → 后端怎么连数据库
  → 权限怎么校验（后端会返回 403，你处理错误即可）
  → 小程序端长什么样
```

### D 同学 (小程序端前端)

```
你的代码在:  frontend-mp/src/

你调用的接口:
  → 后端 API 的子集 (学生可用的接口)
  → 通过 src/api/index.js 封装

特别注意:
  → 源码改了后 uni-app 自动重新编译到 dist/dev/mp-weixin/
  → 微信开发者工具导入的是 dist 目录，不是 src 目录
  → 开发时勾选"不校验合法域名"，否则请求发不出去
```

---

## 八、数据库设计

### 8.1 总览

系统共 16 张表，分为 4 个业务域 + 1 个系统域：

```
┌─────────────────────────────────────────────────────────────────┐
│                        PostgreSQL / Kingbase                     │
│                        数据库: college_service                    │
│                                                                  │
│  ┌─ 系统域 ─────────────────────────────────────────────────┐   │
│  │  sys_user                    用户表 (含 email/phone)       │   │
│  │  sys_operation_log           操作日志                      │   │
│  │  sys_notification            通知消息 (站内/邮件模拟/短信) │   │
│  │  sys_notification_broadcast  群发记录 (24h 撤回窗口)       │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌─ 智能问答域 ────────────────────────────────────────────┐    │
│  │  qa_knowledge         知识库标准问答                      │    │
│  │  qa_document          政策文档/办公模板 (doc_type 区分)   │    │
│  │  qa_document_chunk    RAG 向量切片 (vector(512))          │    │
│  │  qa_chat_log          用户问答记录                        │    │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌─ 党团流程域 ────────────────────────────────────────────┐    │
│  │  party_process_template   流程模板 (入党/入团)            │    │
│  │  party_process_step       步骤定义                        │    │
│  │  party_process_instance   学生流程实例                     │    │
│  │  party_step_record        步骤完成记录                     │    │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌─ 审批流程域 ────────────────────────────────────────────┐    │
│  │  approval_type            审批类型定义                     │    │
│  │  approval_application     审批申请 ★核心表                │    │
│  │  approval_record          审批操作记录                     │    │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌─ 学生画像域 ────────────────────────────────────────────┐    │
│  │  student_honor            荣誉记录                        │    │
│  └──────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────┘
```

### 8.2 表关系 (ER 关系图)

```
sys_user (用户)
  │
  ├──1:N──→ party_process_instance (一个学生可以有多个流程)
  │              │
  │              └──1:N──→ party_step_record (一个流程有多条步骤记录)
  │
  ├──1:N──→ approval_application (一个学生可以提多个申请)
  │              │
  │              └──1:N──→ approval_record (一个申请有多条审批记录)
  │
  ├──1:N──→ student_honor (一个学生有多个荣誉)
  │
  ├──1:N──→ qa_chat_log (一个用户有多条问答记录)
  │
  ├──1:N──→ sys_notification (一个用户有多条通知)
  │
  └──1:N──→ sys_operation_log (一个管理员有多条操作日志)


party_process_template (流程模板)
  │
  └──1:N──→ party_process_step (一个模板包含多个步骤)


approval_type (审批类型)
  │
  └──1:N──→ approval_application (一种类型下有多个申请)
```

### 8.3 各表字段说明

#### 系统域

**sys_user** — 用户表（全部角色共用一张表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | 自增主键 |
| student_id | VARCHAR(20) UNIQUE | 学号，登录凭证 |
| name | VARCHAR(50) | 姓名 |
| password | VARCHAR(255) | 密码（BCrypt 哈希，不存明文） |
| role_level | SMALLINT | 角色：1=院领导 2=管理老师 3=班团骨干 4=普通学生 |
| grade | VARCHAR(10) | 年级 |
| major | VARCHAR(50) | 专业 |
| class_name | VARCHAR(50) | 班级 |
| phone | VARCHAR(20) | 手机号（学生端可自助修改） |
| email | VARCHAR(100) | 邮箱（为空时派生 `学号@ruc.edu.cn`，学生端可自助修改） |
| id_card_enc | VARCHAR(255) | 身份证号（**AES 加密存储**） |
| origin_enc | VARCHAR(255) | 生源地（**AES 加密存储**） |
| hukou_enc | VARCHAR(255) | 户籍地（**AES 加密存储**） |
| status | SMALLINT | 1=启用 0=禁用 |

> 敏感字段（身份证、生源地、户籍地）在数据库中是密文，后端读取时解密，返回前端时脱敏显示（如 `110***********1234`）。

**sys_operation_log** — 操作日志

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| user_id | BIGINT | 操作人 |
| module | VARCHAR(50) | 哪个模块（审批管理/知识库/用户管理...） |
| action | VARCHAR(200) | 做了什么（通过申请/删除知识条目...） |
| detail | TEXT | 操作参数 JSON（记录具体改了什么） |
| ip | VARCHAR(50) | 操作者 IP |
| created_at | TIMESTAMP | 操作时间 |

> 通过 `@OperationLog` 注解自动记录，不需要手动写入。用于出问题时溯源"谁在什么时候改了什么"。

**sys_notification** — 通知消息

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| user_id | BIGINT | 接收人 |
| title | VARCHAR(200) | 标题 |
| content | TEXT | 内容 |
| type | VARCHAR(20) | sms_sim=模拟短信  system=系统通知  reminder=流程提醒  email_sim=邮件降级 |
| tags | VARCHAR(200) | 标签（逗号分隔，用于学生端 tag 筛选） |
| source | VARCHAR(50) | 来源（学院/就业办/...） |
| source_url | VARCHAR(500) | 公众号原文链接 |
| broadcast_id | BIGINT | 关联的群发记录（撤回时按此过滤） |
| is_read | BOOLEAN | 是否已读 |

**sys_notification_broadcast** — 群发记录

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| title / content | — | 群发原文 |
| tags / source / source_url | — | 同 sys_notification |
| target_filter | TEXT | 目标筛选 JSON：`{roleLevel, grades, majors, classNames}` |
| channels | VARCHAR(50) | 已选渠道列表，如 `system,email` |
| target_count / sent_count / email_sent | INT | 命中人数 / 站内已写入 / 邮件已发送 |
| operator_id | BIGINT | 群发操作人 |
| withdrawn | BOOLEAN | 是否已撤回 |
| withdrawn_at | TIMESTAMP | 撤回时间 |

> 24h 内可撤回；撤回时仅删除目标用户中**未读**的关联通知，已读保留留痕。

#### 智能问答域

**qa_knowledge** — 知识库标准问答

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| category | VARCHAR(50) | 分类（入党/奖学金/日常事务...） |
| question | TEXT | 标准问题 |
| answer | TEXT | 标准答案 |
| keywords | VARCHAR(500) | 关键词（逗号分隔，用于模糊匹配） |
| source_url | VARCHAR(500) | 官方政策链接 |
| sort_order | INT | 排序权重（越大越靠前） |

> 学生提问时，后端先用关键词匹配这张表。命中则返回标准答案（可信），未命中才调 AI（需标注"仅供参考"）。

**qa_document** — 政策文档 / 办公模板

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| title | VARCHAR(200) | 文档标题 |
| category | VARCHAR(50) | 分类 |
| doc_type | VARCHAR(20) | `policy`=政策文件，`template`=办公模板（请假条/活动预算表/简报…） |
| description | TEXT | 适用范围 / 填写说明（主要用于模板） |
| file_path | VARCHAR(500) | 服务器上的存储路径 |
| file_size | BIGINT | 文件大小（字节，≤30MB） |
| download_count | INT | 下载次数（每次下载 +1） |

**qa_chat_log** — 问答记录

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| user_id | BIGINT | 提问人 |
| question | TEXT | 用户的问题 |
| answer | TEXT | 返回的答案 |
| source_type | VARCHAR(20) | knowledge=标准答案 ai=AI生成 manual=人工 |
| matched | BOOLEAN | 是否命中标准答案 |

> 用于后续分析"哪些问题经常问但知识库里没有"，管理员据此补充知识库。

#### 党团流程域

**party_process_template** — 流程模板

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| name | VARCHAR(100) | 如"入党流程"、"入团流程" |
| total_steps | INT | 总步骤数 |

> 初始数据已预置入党流程（29 步，按《发展党员工作程序》）和入团流程（5 步）。

**party_process_step** — 步骤定义

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| template_id | BIGINT FK | 所属模板 |
| step_order | INT | 步骤序号（1, 2, 3...） |
| name | VARCHAR(100) | 如"递交入党申请书"、"积极分子培养考察" |
| duration_days | INT | 预计天数（用于到期提醒，如积极分子满 365 天） |
| required_materials | TEXT | 所需材料说明 |

**party_process_instance** — 学生流程实例

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| user_id | BIGINT FK | 哪个学生 |
| template_id | BIGINT FK | 走的哪个流程 |
| current_step | INT | 当前在第几步 |
| start_date | DATE | 开始日期 |
| status | VARCHAR(20) | active=进行中 completed=已完成 suspended=已暂停 |

> 管理员为学生创建流程实例，每次推进 `current_step + 1`，到最后一步自动变为 completed。

**party_step_record** — 步骤完成记录

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| instance_id | BIGINT FK | 哪个流程实例 |
| step_id | BIGINT FK | 哪个步骤 |
| completed_at | TIMESTAMP | 完成时间 |
| remark | TEXT | 备注 |
| operator_id | BIGINT | 操作管理员 |

#### 审批流程域

**approval_type** — 审批类型

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| name | VARCHAR(100) | 如"在读证明"、"政审证明" |
| approval_chain | VARCHAR(50) | 审批链，如 `"2"` 或 `"2,1"` |
| template_path | VARCHAR(500) | 证明 PDF 模板路径 |

> `approval_chain = "2,1"` 表示先由 2 级（管理老师）审批，通过后再由 1 级（院领导）审批。

**approval_application** — 审批申请 ★核心表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| app_no | VARCHAR(50) UNIQUE | 申请编号（如 CERT-20260509-0001） |
| user_id | BIGINT FK | 申请人 |
| type_id | BIGINT FK | 审批类型 |
| form_data | **JSONB** | 申请表单（灵活结构，如 `{"purpose":"考研","copies":2}`） |
| status | VARCHAR(20) | 当前状态（见下方状态机） |
| current_approver_level | SMALLINT | 当前轮到哪级审批 |
| cert_file_path | VARCHAR(500) | 生成的证明文件路径 |
| **downloaded_at** | TIMESTAMP | **下载时间（非空=已锁定！）** |
| withdraw_deadline | TIMESTAMP | 撤回截止时间（通过后 +2 天） |

> `form_data` 用 JSONB 类型，不同类型的申请可以有不同的表单字段，无需改表结构。

**审批状态机**（status 字段的流转规则）：

```
    draft(草稿)
      │ 学生提交
      ▼
    pending(待审批)
      │                    │
      │ 管理员通过          │ 管理员驳回
      ▼                    ▼
    approved(已通过)     rejected(已驳回)
      │                    │
      │ 学生下载证明        │ 学生重新编辑
      ▼                    ▼
    downloaded(已锁定)    draft(回到草稿)
    ████████████████
    ██ 终态！不可 ██    撤回规则:
    ██ 做任何修改 ██    approved 状态下，2天内且未下载 → 可撤回
    ████████████████    downloaded 状态 → 严禁任何操作
```

**approval_record** — 审批操作记录

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| application_id | BIGINT FK | 哪个申请 |
| approver_id | BIGINT FK | 审批人 |
| approver_level | SMALLINT | 审批人角色等级 |
| action | VARCHAR(20) | approve=通过 reject=驳回 withdraw=撤回 |
| comment | TEXT | 审批意见 |

#### 学生画像域

**student_honor** — 荣誉记录

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | |
| user_id | BIGINT FK | 哪个学生 |
| honor_name | VARCHAR(200) | 如"国家奖学金"、"校优秀团员" |
| honor_level | VARCHAR(50) | 国家级/校级/院级 |
| award_date | DATE | 获奖日期 |
| cert_file | VARCHAR(500) | 证书扫描件路径 |
| created_by | BIGINT | 录入人（**只能管理员录入，学生不能自己添加**） |

### 8.4 数据安全设计

```
敏感数据处理流程:

  管理员 Excel 导入                     学生查看个人信息
  "身份证号: 110101199001011234"        "身份证号: 110***********1234"
         │                                      ▲
         ▼                                      │
  EncryptUtil.encrypt()              EncryptUtil.decrypt() + desensitize()
         │                                      ▲
         ▼                                      │
  数据库存储: "QkFTRTY0X0FVS..."      数据库读取: "QkFTRTY0X0FVS..."
  (AES-256 密文)                      (AES-256 密文)
```

| 数据分级 | 字段 | 存储方式 | 学生可见 | 管理员可见 |
|---------|------|---------|---------|-----------|
| 极敏感 | 身份证号、生源地、户籍地 | AES 加密 | 不可见 | 脱敏显示 |
| 敏感 | 手机号、导师信息 | 明文 | 仅看自己 | 可见 |
| 基础 | 学号、姓名、班级、年级 | 明文 | 仅看自己 | 可见 |

### 8.5 初始数据

`schema.sql` 执行后自动创建：

| 数据 | 内容 |
|------|------|
| 管理员账号 | admin / admin123（一级，院领导） |
| 审批类型 | 在读证明、成绩证明、政审证明、离校证明 |
| 入党流程模板 | 29 步（按《发展党员工作程序》: 教育引导 → 入党积极分子确定 → 培养教育 → 发展对象 → 政治审查 → 短期培训 → 支部大会 → 上级审批 → 预备党员 → 转正 → 存档） |
| 入团流程模板 | 5 步（递交申请书 → 审查 → 团课学习 → 表决 → 审批） |

### 8.6 后端怎么操作数据库

后端使用 **MyBatis-Plus** ORM 框架，不需要手写 SQL：

```java
// Entity 类通过 @TableName 映射到表
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String studentId;  // 自动映射到 student_id 列 (驼峰→下划线)
    ...
}

// Mapper 继承 BaseMapper，自动获得 CRUD
public interface SysUserMapper extends BaseMapper<SysUser> {}

// Service 中直接调用
SysUser user = userMapper.selectById(1);                    // SELECT * FROM sys_user WHERE id = 1
userMapper.insert(user);                                     // INSERT INTO sys_user (...)
userMapper.selectPage(new Page<>(1, 20), queryWrapper);      // 分页查询

// 条件构造器
List<SysUser> students = userMapper.selectList(
    new LambdaQueryWrapper<SysUser>()
        .eq(SysUser::getRoleLevel, 4)       // WHERE role_level = 4
        .eq(SysUser::getGrade, "2024")      // AND grade = '2024'
        .orderByAsc(SysUser::getStudentId)  // ORDER BY student_id
);
```

---

## 九、端口与服务速查

### 开发环境

| 服务 | 端口 | 启动命令 | 说明 |
|------|------|---------|------|
| 后端 | 8080 | `cd backend && ./mvnw.cmd spring-boot:run` | API + Swagger 文档 |
| 管理端 | 5173 | `cd frontend-admin && npm run dev` | Vite 开发服务器 |
| 小程序 | — | `cd frontend-mp && npm run dev:mp-weixin` | 编译到 dist，用微信开发者工具打开 |
| PostgreSQL | 5432 | 系统服务自动启动 | 数据库 |
| Redis | 6379 | 系统服务自动启动 | 缓存 |

### 生产环境 (Docker)

| 服务 | 端口 | 容器名 |
|------|------|--------|
| Nginx | 80 | nginx |
| 后端 | 8080 (内部) | backend |
| Kingbase | 54321 | kingbase |
| Redis | 6379 | redis |

---

## 九点五、智能问答 RAG 检索架构

智能问答 `POST /api/qa/chat` 的回答来源分三级降级：

```
1. 知识库标准答案（qa_knowledge 关键词匹配命中）
        ↓ 未命中
2. RAG 检索 + AI 大模型基于政策文档片段回答
        ↓ AI 未配置 / 检索为空
3. 抽取式回答（直接返回政策文档原文片段，无需 AI）
        ↓ 完全无依据
4. manual 兜底："未在现有政策文件中找到明确依据，请联系辅导员确认。"
```

### 检索全链路

```
用户问题 "学生最长可以读多少年"
         │
         ▼
┌─────────────────────────────────────────┐
│ QaService.chat(question)                │
│  ├─ extractTokens → 关键词匹配 qa_knowledge │ ← 命中则直接返回标准答案
│  └─ 未命中 → DocumentRagService.retrieve │
└─────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────┐
│ EmbeddingService.embedQuery()           │
│  ├─ 加 BGE 查询前缀                       │
│  │   "为这个句子生成表示以用于检索相关文章："  │
│  └─ HTTP POST → TEI :8081/v1/embeddings  │
└─────────────────────────────────────────┘
         │ 返回 512 维浮点数组（L2 归一化）
         ▼
┌─────────────────────────────────────────┐
│ pgvector 余弦相似度检索                   │
│  SELECT *, (1 - embedding <=> ?) AS score│
│  FROM qa_document_chunk                  │
│  ORDER BY embedding <=> ?                │
│  LIMIT topK * rerank-pool-size           │
└─────────────────────────────────────────┘
         │ 返回 80 条候选片段
         ▼
┌─────────────────────────────────────────┐
│ DocumentRagService.boostScore() 重排     │
│  在余弦相似度基础上叠加：                  │
│   + 0.55 × 内容关键词加权重叠               │
│   + 0.25 × 元数据（标题/分类/keywords）重叠  │
│   + 0.35 × 意图结构匹配（"多久"→年限词）     │
│   + 0.45 × 受众范围匹配（"本科生"→标题）     │
│  按总分降序取 topK 条                       │
└─────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────┐
│ QaService.buildExtractiveRagAnswer()    │
│  ├─ 最多拼接 3 条「依据 N」                 │
│  ├─ 次级片段需 ≥ 最佳分数的 60% 才纳入       │
│  └─ 命中"第X条"边界时返回完整条款（不截断）   │
└─────────────────────────────────────────┘
         │
         ▼
返回 { sourceType: "rag", answer: "【依据1】《...》第十条 ..." }
```

### 文档入库流程（管理端「重新索引」按钮）

```
PDF / DOCX / TXT 文件 (≤ 30MB)
         │
         ▼
DocumentRagService.indexDocument(docId)
  ├─ extractText: PDFBox / Apache POI / 直接读 UTF-8
  ├─ splitText: 优先按"第X条"切分；否则按段落保留语义边界
  └─ 每个切片调 EmbeddingService.embed() 生成 512 维向量
         │
         ▼
INSERT INTO qa_document_chunk (..., embedding) VALUES (..., '[0.1,0.2,...]'::vector)
         │
         ▼
ivfflat 余弦索引自动维护
```

### 配置项（`application.yml` 的 `rag` 节）

| 配置 | 默认值 | 含义 |
|---|---|---|
| `enabled` | true | RAG 总开关 |
| `embedding-dim` | 512 | 向量维度，必须与模型匹配（BGE-small-zh=512） |
| `chunk-size` | 700 | 长段落切片最大字符数 |
| `chunk-overlap` | 120 | 长段落切片之间的重叠字符数 |
| `top-k` | 4 | 最终返回的片段数 |
| `min-score` | 0.3 | 余弦相似度阈值（local-hash 时期为 0.05） |
| `rerank-pool-size` | 20 | 从 pgvector 召回 `topK × 此值` 条候选送入重排 |
| `embedding.provider` | http | `local-hash` 或 `http`（生产用 http） |
| `embedding.api-url` | http://localhost:8081/v1/embeddings | TEI 服务地址，可由 `RAG_EMBEDDING_API_URL` 环境变量覆盖 |
| `embedding.query-prefix` | 「为这个句子生成表示以用于检索相关文章：」 | BGE 系列查询前缀，仅对查询添加，文档索引时不加 |

### Embedding 模型选型

当前生产配置：**BGE-small-zh-v1.5**（智源研究院 BAAI 出品，中文专项 BERT，4 层 8 头）

| 模型 | 维度 | 中文质量 | 体积 | 用途 |
|---|---|---|---|---|
| **BGE-small-zh-v1.5** ⭐ | 512 | ★★★★ | 90MB | **当前选用**，CPU 推理 50ms/句 |
| BGE-base-zh-v1.5 | 768 | ★★★★★ | 400MB | 服务器内存充足时升级 |
| BGE-large-zh-v1.5 | 1024 | ★★★★★ | 1.3GB | 答辩演示效果最佳 |
| local-hash（兜底） | 512 | ★ | 0 | TEI 不可用时回退，仅字面匹配 |

切换模型时同步改 `rag.embedding-dim` 和 SQL `vector(N)` 列类型；旧向量不可复用，需 `TRUNCATE qa_document_chunk` 后重建索引。

---

## 九点六、信息精准推送 / 邮件投递架构

模块三「信息精准推送」由管理员发起群发，覆盖**站内通知 + 真实邮件 + 短信模拟**三条通道，所有发送都通过 `NotificationBroadcastService.broadcast()` 在一个事务中完成：

```
管理员提交群发请求 (admin 端)
     │  POST /api/notify/broadcast
     │  { title, content, tags[], channels[], filter }
     ▼
┌─────────────────────────────────────────────────────────────────┐
│ NotificationBroadcastService.broadcast()                         │
│  1) 按 filter 查询 sys_user → targets (≤ notify.broadcast.max)   │
│  2) INSERT sys_notification_broadcast                            │
│  3) 站内通道: 批量 INSERT sys_notification (type=system)         │
│  4) email 通道:                                                  │
│     ├─ EmailService.isAvailable() = false                        │
│     │    → 写 sys_notification (type=email_sim) 降级             │
│     └─ true → 逐人 sendOne() 同步发送, 统计 emailSent             │
│  5) sms_sim 通道: 批量 INSERT sys_notification (type=sms_sim)    │
│  6) UPDATE broadcast SET sent_count, email_sent                  │
└─────────────────────────────────────────────────────────────────┘
     │
     ▼
EmailService.sendOne()  ──SMTP─→  smtp.qq.com:465 / smtp.ym.163.com:994
     │
     ▼  (失败时 warn 日志 + 返回 false, 不抛异常)
真实邮件投递到目标邮箱 (派生规则: sys_user.email || 学号@ruc.edu.cn)
```

**关键设计点：**

- **授权码隔离**：`MAIL_AUTH_CODE` 仅通过环境变量传入 JVM，不入库、不入 yml、不进日志
- **邮件降级**：`JavaMailSender` bean 缺失或鉴权失败时，broadcast 不报错，自动改写 `email_sim` 类型通知，前端展示为「邮件(模拟)」前缀
- **24h 撤回**：`sys_notification` 表的 `broadcast_id` 把站内通知与群发关联起来，撤回时按 `(broadcast_id, is_read=false)` 删除
- **节流**：`EmailService.sendBatch()` 每发 `notify.email.batch-size` 封 sleep 100ms，避免 SMTP 限流

配置项（`application.yml`）：

| 配置 | 默认 | 说明 |
|---|---|---|
| `spring.mail.host` | `smtp.qq.com` | SMTP 服务器（可通过 `MAIL_HOST` 覆盖） |
| `spring.mail.port` | `465` | SSL 端口（`MAIL_PORT` 覆盖） |
| `spring.mail.username` | `3523698178@qq.com` | 发件人（`MAIL_USERNAME` 覆盖） |
| `spring.mail.password` | (空) | 客户端授权码（`MAIL_AUTH_CODE` 必传，否则降级） |
| `notify.email.default-domain` | `ruc.edu.cn` | 邮箱派生域 |
| `notify.email.batch-size` | 50 | 每 N 封小睡 100ms |
| `notify.broadcast.withdraw-window-hours` | 24 | 撤回窗口 |
| `notify.broadcast.max-targets` | 5000 | 单次群发最大目标人数 |

### 数据流总览

```
qa_knowledge        ── 关键词匹配 ───┐
                                    ├──► QaService.chat()
qa_document_chunk   ── 向量检索 ─────┤      │
  + ivfflat 索引                    │      ▼
                                    │   返回答案
qa_chat_log         ◄── 写入 ───────┘
```

`qa_document_chunk` 字段：`document_id`、`title`、`category`、`chunk_index`、`content`、`keywords`（关键词自动提取）、`embedding vector(512)`。

---

## 十、常见误解澄清

**Q: 小程序和管理端是不是连的不同后端？**
不是。两个前端连的是同一个后端、同一个数据库。管理员在 PC 端改了数据，学生在小程序端刷新就能看到。

**Q: 前端能直接读数据库吗？**
不能。前端（无论是小程序还是管理端）只能通过 HTTP 请求调后端 API。后端再去查数据库。这是安全性的基本保障。

**Q: 权限是前端控制的还是后端控制的？**
**后端控制**。前端根据 `roleLevel` 隐藏菜单只是为了体验（不让用户看到自己用不了的功能），但真正的权限校验在后端的 `@RequireRole` 拦截器。即使有人伪造请求绕过前端，后端也会拒绝。

**Q: 为什么开发时需要代理？**
浏览器的同源策略禁止 `localhost:5173`（前端）直接请求 `localhost:8080`（后端）。Vite 代理把 `/api` 开头的请求转发到后端，浏览器认为请求还是发给 5173 的，就不会拦截了。生产环境由 Nginx 统一入口，不存在跨域问题。

**Q: 小程序的代码是怎么到用户手机上的？**
开发完成后，通过微信开发者工具上传代码到微信服务器，提交审核。审核通过后用户搜索小程序即可使用。小程序代码运行在微信的沙箱环境中，不是普通的网页。
