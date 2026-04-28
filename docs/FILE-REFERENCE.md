# 项目文件说明文档 — 学院学生综合服务与党团管理平台

> 本文档逐一说明项目中每个文件的用途、已实现的功能，以及后续需要补充的内容。

---

## 一、项目根目录

| 文件 | 用途 | 状态 |
|------|------|------|
| `docker-compose.yml` | 一键启动整个系统（后端+数据库+Redis+Nginx），定义了 4 个容器的编排关系 | 已完成 |
| `.gitignore` | Git 忽略规则，排除 node_modules、target、IDE 配置等 | 已完成 |

---

## 二、后端 (`backend/`)

### 2.1 项目配置

| 文件 | 用途 | 说明 |
|------|------|------|
| `pom.xml` | Maven 依赖清单 | 声明了 Spring Boot 3、MyBatis-Plus、JWT、Hutool、EasyExcel、Knife4j(Swagger)、MinIO、Lombok 等全部依赖。Kingbase 驱动已注释，等拿到 jar 包后取消注释即可 |
| `Dockerfile` | 后端 Docker 镜像构建 | 两阶段构建：第一阶段用 Maven 编译打包，第二阶段用 JRE 运行，最终镜像体积小 |
| `src/main/resources/application.yml` | 主配置文件 | 端口 8080，接口前缀 `/api`，文件上传限制 30MB，JWT 密钥，AI 模型开关（默认 none），MyBatis-Plus 配置，Knife4j 文档开关 |
| `src/main/resources/application-dev.yml` | 开发环境配置 | 连接本地 PostgreSQL（替代 Kingbase 开发），Redis localhost |
| `src/main/resources/application-prod.yml` | 生产环境配置 | 连接 Kingbase 数据库，密码从环境变量读取，关闭 SQL 日志和 Swagger |

### 2.2 启动类

| 文件 | 用途 |
|------|------|
| `CollegeApplication.java` | Spring Boot 启动入口。`@EnableScheduling` 开启了定时任务能力，后续用于党团流程到期自动提醒 |

### 2.3 通用模块 (`common/`)

#### 2.3.1 统一响应 (`common/result/`)

| 文件 | 用途 |
|------|------|
| `Result.java` | 所有接口的统一返回格式 `{code, message, data}`。提供工厂方法：`Result.ok(data)` 成功、`Result.fail(msg)` 失败、`Result.unauthorized()` 未登录(401)、`Result.forbidden()` 无权限(403)。前端只需判断 `code === 200` |
| `PageResult.java` | 分页查询专用响应，包含 `total`(总数)、`page`(当前页)、`size`(每页条数)、`records`(数据列表) |

#### 2.3.2 枚举 (`common/enums/`)

| 文件 | 用途 |
|------|------|
| `RoleLevel.java` | 四级权限的枚举映射。`LEADER(1)` 院领导、`TEACHER(2)` 管理老师、`CADRE(3)` 班团骨干、`STUDENT(4)` 普通学生。数字越小权限越高。提供 `fromLevel(int)` 反查方法 |
| `ApprovalStatus.java` | 审批流程的 6 种状态枚举：`DRAFT` 草稿、`PENDING` 待审批、`APPROVED` 已通过、`REJECTED` 已驳回、`WITHDRAWN` 已撤回、`DOWNLOADED` 已锁定。提供 `fromCode(String)` 反查方法 |

#### 2.3.3 异常处理 (`common/exception/`)

| 文件 | 用途 |
|------|------|
| `BusinessException.java` | 自定义业务异常类，携带 `code` 和 `message`。业务代码中 `throw new BusinessException("学号不存在")` 即可，会被全局拦截器捕获 |
| `GlobalExceptionHandler.java` | 全局异常拦截器（`@RestControllerAdvice`）。捕获所有异常并统一转为 `Result` 格式返回：业务异常→业务错误码、参数校验失败→400、未知异常→500（不暴露堆栈信息给前端） |

#### 2.3.4 安全认证 (`common/security/`)

| 文件 | 用途 |
|------|------|
| `JwtUtil.java` | JWT 工具类。`generateToken(userId, studentId, roleLevel)` 生成 Token，`parseToken(token)` 解析并验签，`getUserId/getStudentId/getRoleLevel` 从 Token 中提取字段。密钥和过期时间从 yml 读取 |
| `LoginUser.java` | 当前登录用户的数据结构，包含 `userId`、`studentId`、`roleLevel`、`name`，存放在 ThreadLocal 中 |
| `UserContext.java` | 基于 ThreadLocal 的用户上下文。`UserContext.set(user)` 存入、`UserContext.getUserId()` 获取当前用户 ID、`UserContext.getRoleLevel()` 获取角色等级、`UserContext.remove()` 请求结束时清理防内存泄漏。**其他所有 Service 通过它获取当前用户** |
| `RequireRole.java` | 权限注解。标在 Controller 方法上 `@RequireRole(minLevel = 2)` 表示"角色等级 ≤ 2 才能访问"。被 AuthInterceptor 读取并校验 |
| `AuthInterceptor.java` | HTTP 请求拦截器。每个请求进来：1)从 Header 提取 Bearer Token → 2)调 JwtUtil 解析 → 3)构建 LoginUser 存入 UserContext → 4)读取方法上的 @RequireRole 注解检查权限 → 5)请求结束后 remove()。登录/注册/Swagger 路径被排除 |

#### 2.3.5 配置 (`common/config/`)

| 文件 | 用途 |
|------|------|
| `WebMvcConfig.java` | 注册 AuthInterceptor 拦截器（排除登录/注册/文档路径）；配置 CORS 跨域（允许所有来源、所有方法、携带 Cookie），使前端开发时不会被浏览器拦截 |
| `MyBatisPlusConfig.java` | 配置 MyBatis-Plus 分页插件，数据库方言设为 PostgreSQL（Kingbase 兼容 PG 协议，所以两者通用） |

#### 2.3.6 操作日志 (`common/log/`)

| 文件 | 用途 |
|------|------|
| `OperationLog.java` | 操作日志注解。标在 Controller 方法上 `@OperationLog(module="审批管理", action="通过申请")`，触发 AOP 自动记录日志 |
| `OperationLogAspect.java` | AOP 切面，拦截所有带 @OperationLog 的方法。方法执行成功后，自动将 `userId + module + action + 参数JSON + IP + 时间` 写入 `sys_operation_log` 表。满足需求中"必须记录所有管理员操作"的要求 |

#### 2.3.7 工具 (`common/util/`)

| 文件 | 用途 |
|------|------|
| `EncryptUtil.java` | AES-256 加密工具。`encrypt("身份证号")` → 密文存数据库，`decrypt(密文)` → 明文取出来用，`desensitize("110101199001011234", 3, 4)` → `"110***********1234"` 脱敏显示。用于身份证号、生源地、户籍地等敏感字段 |

**后续需要补充**：加密密钥应从环境变量或配置中心读取，目前硬编码在代码里仅用于开发。

---

### 2.4 认证模块 (`module/auth/`)

| 文件 | 层级 | 用途 |
|------|------|------|
| `entity/SysUser.java` | Entity | 映射 `sys_user` 表。字段包括：id、学号、姓名、密码(BCrypt)、角色等级、年级、专业、班级、手机号、身份证号(加密)、生源地(加密)、状态 |
| `mapper/SysUserMapper.java` | Mapper | 继承 MyBatis-Plus 的 `BaseMapper<SysUser>`，自动拥有 CRUD、分页、条件查询等方法，无需手写 SQL |
| `service/AuthService.java` | Service | 两个核心方法：`login()` 查学号→BCrypt 验密码→生成 JWT Token→返回用户信息；`register()` 查重→哈希密码→默认角色4(学生)→入库 |
| `controller/AuthController.java` | Controller | 暴露两个接口：`POST /auth/login` 和 `POST /auth/register`，都不需要 Token |

---

### 2.5 智能问答模块 (`module/qa/`)

#### Entity 层

| 文件 | 对应表 | 用途 |
|------|--------|------|
| `entity/QaKnowledge.java` | `qa_knowledge` | 知识库标准问答条目。字段：分类、标准问题、标准答案、关键词(逗号分隔)、官方链接、排序权重 |
| `entity/QaDocument.java` | `qa_document` | 政策文档记录。字段：标题、分类、文件路径、文件大小、文件类型、下载次数 |
| `entity/QaChatLog.java` | `qa_chat_log` | 用户问答日志。字段：用户ID、问题、答案、回答来源(knowledge/ai/manual)、是否命中标准答案。用于后续分析优化知识库 |

#### Mapper 层

| 文件 | 用途 |
|------|------|
| `mapper/QaKnowledgeMapper.java` | 知识库条目的数据访问 |
| `mapper/QaDocumentMapper.java` | 政策文档的数据访问 |
| `mapper/QaChatLogMapper.java` | 问答记录的数据访问 |

#### AI 抽象层 (`service/ai/`)

| 文件 | 用途 |
|------|------|
| `service/ai/AiProvider.java` | AI 大模型的统一接口（Strategy 策略模式）。定义三个方法：`chat(question, context)` 对话、`getName()` 提供者名称、`isAvailable()` 是否可用 |
| `service/ai/AiProviderFactory.java` | AI 工厂类。读取 `application.yml` 中 `ai.provider` 的值（如 wenxin/qianfan/openai），从 Spring 容器中找到对应实现返回；找不到则回退到 NoopAiProvider |
| `service/ai/impl/NoopAiProvider.java` | 空实现（默认）。当没有配置任何 AI 模型时使用，直接返回"建议联系辅导员"，是人工兜底路径 |

**后续需要补充**：实现具体的 AI 提供者，如 `WenxinAiProvider`（百度文心一言）或 `QianfanAiProvider`（千帆），只需 implements AiProvider 并注册为 Spring Bean。

#### Service 层

| 文件 | 用途 |
|------|------|
| `service/QaService.java` | 核心业务逻辑。`chat()` 方法实现了完整的问答流程：1)关键词匹配知识库 → 2)命中则返回标准答案+官方链接 → 3)未命中则调 AI → 4)记录到 chat_log。还包含知识库 CRUD、文档管理、下载计数等功能 |

#### Controller 层

| 文件 | 用途 |
|------|------|
| `controller/QaController.java` | 暴露接口：`POST /qa/chat` 智能问答、`GET /qa/chat/history` 问答历史、`GET/POST/PUT/DELETE /qa/knowledge/*` 知识库管理(需≤2级)、`GET/POST/DELETE /qa/document/*` 文档管理 |

---

### 2.6 党团流程模块 (`module/party/`)

#### Entity 层

| 文件 | 对应表 | 用途 |
|------|--------|------|
| `entity/PartyProcessTemplate.java` | `party_process_template` | 流程模板（如"入党流程"、"入团流程"）。字段：名称、描述、总步骤数、状态 |
| `entity/PartyProcessStep.java` | `party_process_step` | 流程步骤定义。字段：所属模板、步骤序号、名称、描述、预计天数(用于提醒)、所需材料 |
| `entity/PartyProcessInstance.java` | `party_process_instance` | 学生流程实例（某个学生正在走的某个流程）。字段：用户ID、模板ID、当前步骤、开始日期、状态(active/completed/suspended) |
| `entity/PartyStepRecord.java` | `party_step_record` | 步骤完成记录。字段：实例ID、步骤ID、完成时间、备注、操作管理员ID |

#### Mapper 层

| 文件 | 用途 |
|------|------|
| `mapper/PartyProcessTemplateMapper.java` | 流程模板数据访问 |
| `mapper/PartyProcessStepMapper.java` | 流程步骤数据访问 |
| `mapper/PartyProcessInstanceMapper.java` | 流程实例数据访问 |
| `mapper/PartyStepRecordMapper.java` | 步骤记录数据访问 |

#### Service 层

| 文件 | 用途 |
|------|------|
| `service/PartyService.java` | 核心业务。学生端：`getMyProgress()` 获取我的所有流程进度（含步骤条数据）、`getProgressDetail()` 流程详情。管理端：`createTemplate()` 创建模板(事务)、`updateTemplate()` 修改模板(删旧步骤重建)、`createInstance()` 为学生创建流程、`advanceStep()` 推进步骤(记录完成+步骤+1)、`suspendInstance()` 暂停 |

**后续需要补充**：定时任务扫描即将到期的步骤（如积极分子满3个月），调用 SystemService.sendNotification() 发送提醒。

#### Controller 层

| 文件 | 用途 |
|------|------|
| `controller/PartyController.java` | 学生端：`GET /party/templates` 所有模板、`GET /party/my-progress` 我的进度、`GET /party/my-progress/{id}` 流程详情。管理端(≤2级)：模板 CRUD、实例管理、推进/暂停操作。所有管理操作标注了 @OperationLog |

---

### 2.7 审批流程模块 (`module/approval/`)

#### Entity 层

| 文件 | 对应表 | 用途 |
|------|--------|------|
| `entity/ApprovalType.java` | `approval_type` | 审批类型定义（如"在读证明"、"政审证明"）。核心字段 `approvalChain`：审批链，如 `"2"` 表示只需二级审批，`"2,1"` 表示先二级再一级(院领导)审批 |
| `entity/ApprovalApplication.java` | `approval_application` | 审批申请(核心表)。字段：申请编号、用户ID、类型、表单数据(JSONB)、状态、当前审批层级、证明文件路径、下载时间(非空=锁定!)、撤回截止时间 |
| `entity/ApprovalRecord.java` | `approval_record` | 审批操作记录。字段：申请ID、审批人、审批层级、操作(approve/reject/withdraw)、意见 |

#### Mapper 层

| 文件 | 用途 |
|------|------|
| `mapper/ApprovalTypeMapper.java` | 审批类型数据访问 |
| `mapper/ApprovalApplicationMapper.java` | 申请表数据访问 |
| `mapper/ApprovalRecordMapper.java` | 审批记录数据访问 |

#### Service 层

| 文件 | 用途 |
|------|------|
| `service/ApprovalStateMachine.java` | **审批状态机**（纯逻辑，无数据库操作）。定义了合法的状态转换表，提供三个校验方法：`validateTransition(from, to)` 校验状态流转合法性、`validateWithdraw()` 校验撤回(已下载=禁止、超期=禁止)、`validateDownload()` 校验下载(仅 approved 可下载)。这是需求中最核心的业务规则保障 |
| `service/ApprovalService.java` | 核心业务。学生端：`apply()` 提交申请(生成编号+设置审批链第一级)、`getMyApplications()` 我的申请列表、`withdraw()` 撤回(调状态机校验)、`downloadCert()` 下载证明(**触发锁定!** 状态→downloaded + 记录时间)。管理端：`approve()` 通过(多级审批链自动推进)、`reject()` 驳回、`adminWithdraw()` 管理员撤回(状态机校验) |

**后续需要补充**：证明 PDF 生成（根据模板填充学生信息），审批通过/驳回后调通知 Service 给学生发消息。

#### Controller 层

| 文件 | 用途 |
|------|------|
| `controller/ApprovalController.java` | 学生端：`GET /approval/types` 可选类型、`POST /approval/apply` 提交、`GET /approval/my/page` 列表、`PUT /approval/my/{id}/withdraw` 撤回、`GET /approval/my/{id}/download` **下载(触发锁定)**。管理端(≤2级)：待审批列表、通过/驳回/管理员撤回、全部申请查询。关键操作标注了 @OperationLog |

---

### 2.8 学生画像模块 (`module/student/`)

#### Entity + Mapper

| 文件 | 用途 |
|------|------|
| `entity/StudentHonor.java` | 荣誉记录。字段：用户ID、荣誉名称、级别(国家级/校级/院级)、获奖日期、证书文件路径、录入人(只能管理员) |
| `mapper/StudentHonorMapper.java` | 荣誉数据访问 |

#### Service 层

| 文件 | 用途 |
|------|------|
| `service/StudentService.java` | 学生端：`getMyProfile()` 获取个人信息、`getMyHonors()` 获取荣誉列表。管理端：`getStudentPage()` 学生分页列表(**含数据隔离**：3级只看本班、脱敏处理)、`getStudentDetail()` 学生画像详情(含荣誉)、荣誉 CRUD(仅管理员可操作) |

#### Controller 层

| 文件 | 用途 |
|------|------|
| `controller/StudentController.java` | 学生端：`GET /student/profile` 和 `GET /student/honors`。管理端(≤2级或3级)：学生列表/详情/荣誉管理 |

---

### 2.9 系统管理模块 (`module/system/`)

#### Entity + Mapper

| 文件 | 用途 |
|------|------|
| `entity/SysNotification.java` | 通知消息。字段：用户ID、标题、内容、类型(sms_sim 模拟短信/system 系统通知/reminder 流程提醒)、是否已读 |
| `entity/SysOperationLog.java` | 操作日志。字段：操作人ID、模块、操作描述、变更详情(JSON)、IP、时间 |
| `mapper/SysNotificationMapper.java` | 通知数据访问 |
| `mapper/SysOperationLogMapper.java` | 日志数据访问 |

#### Service 层

| 文件 | 用途 |
|------|------|
| `service/SystemService.java` | 综合 Service，包含：用户管理(分页/详情/修改/设置角色/改密码)、数据概览(Dashboard 统计)、操作日志查询(按模块/时间筛选)、通知消息(列表/未读数/标记已读/全部已读)、**sendNotification()** 方法供其他模块调用发通知 |

**后续需要补充**：Excel 批量导入学生名单（使用 EasyExcel 解析 xlsx，逐行创建用户）、数据导出。

#### Controller 层

| 文件 | 用途 |
|------|------|
| `controller/SystemController.java` | 聚合了多个路径前缀的接口。`/system/user/*` 用户管理(≤2级)、`/system/dashboard` 数据概览(≤2级)、`/auth/password` 改密码(全部)、`/log/page` 日志查询(≤1级,院领导专属)、`/notify/*` 通知消息(全部) |

---

## 三、数据库 (`deploy/sql/`)

| 文件 | 用途 |
|------|------|
| `schema.sql` | 完整的建表脚本（兼容 PostgreSQL 和 Kingbase）。包含 **14 张表**、索引、中文注释、初始数据。初始数据包括：1个管理员账号(admin/admin123)、4种审批类型(在读证明等)、入党流程模板(8步)、入团流程模板(5步) |

---

## 四、PC 管理端前端 (`frontend-admin/`)

### 4.1 项目配置

| 文件 | 用途 |
|------|------|
| `package.json` | npm 依赖：Vue3、Vue Router、Pinia、Element Plus、Axios、ECharts、xlsx |
| `vite.config.js` | Vite 构建配置。路径别名 `@` → `src`，开发服务器端口 5173，代理 `/api` 到后端 8080 避免跨域 |
| `index.html` | HTML 入口，标题"学院综合服务管理平台" |

### 4.2 核心架构

| 文件 | 用途 |
|------|------|
| `src/main.js` | 应用入口。创建 Vue 实例，安装 Pinia(状态管理)、Vue Router(路由)、Element Plus(UI组件库+中文语言包)，注册所有 Element Plus 图标 |
| `src/App.vue` | 根组件，只有一个 `<router-view />`，所有页面通过路由渲染 |
| `src/assets/styles/global.scss` | 全局样式。严谨正式风格（微软雅黑字体、#f0f2f5 背景、简洁滚动条） |

### 4.3 API 层

| 文件 | 用途 |
|------|------|
| `src/api/request.js` | Axios 封装。请求拦截器自动注入 Token，响应拦截器统一处理错误（401 跳登录、其他弹 ElMessage 提示）|
| `src/api/index.js` | 全部 API 函数，按模块分组导出：`authApi`(认证)、`systemApi`(用户管理)、`logApi`(日志)、`notifyApi`(通知)、`qaApi`(问答)、`partyApi`(党团)、`approvalApi`(审批)、`studentApi`(学生)、`fileApi`(文件)。C 同学直接 import 使用，不需要关心具体 URL |

### 4.4 状态管理

| 文件 | 用途 |
|------|------|
| `src/stores/user.js` | Pinia Store，管理登录状态。字段：token、userId、name、roleLevel、studentId。`setLoginInfo()` 登录后保存到 localStorage，`logout()` 清空。计算属性：`isLoggedIn`、`isAdmin`(≤2级)、`isLeader`(=1级) |

### 4.5 路由

| 文件 | 用途 |
|------|------|
| `src/router/index.js` | 路由配置。`/login` 登录页(不需要认证)，`/` 下嵌套主布局和全部子页面。每个路由有 `meta.minRole` 控制权限。路由守卫：未登录跳 `/login`，权限不足跳 `/dashboard` |

### 4.6 布局

| 文件 | 用途 |
|------|------|
| `src/layouts/MainLayout.vue` | 管理端主布局。左侧深色导航栏（根据 roleLevel 动态显示/隐藏菜单项，可折叠）+ 顶栏（面包屑、通知铃铛+未读数、用户下拉菜单：改密码/退出）+ 右侧内容区。蓝/灰/白严谨配色 |

### 4.7 页面 (`src/views/`)

| 文件 | 页面 | 功能 |
|------|------|------|
| `views/login/index.vue` | 登录页 | 学号+密码表单，蓝色渐变背景，白色卡片，调 authApi.login 后存 Store 跳转 |
| `views/dashboard/index.vue` | 数据概览 | 四个统计卡片（在校学生/总用户/待审批/进行中流程），调 systemApi.getDashboard |
| `views/qa/KnowledgeList.vue` | 知识库管理 | 分类+关键词筛选、表格列表(分类/问题/答案/关键词)、新增/编辑弹窗、删除确认 |
| `views/qa/DocumentList.vue` | 政策文档管理 | 文档列表(标题/分类/大小/下载次数)、上传按钮(限30MB)、下载/删除 |
| `views/party/TemplateList.vue` | 流程模板管理 | 模板列表(名称/步骤数/描述)、编辑按钮。**后续需要补充**：模板编辑弹窗（动态增删步骤） |
| `views/party/InstanceList.vue` | 学生流程管理 | 按模板/状态筛选、表格列表、推进(弹窗填备注)/暂停操作 |
| `views/approval/PendingList.vue` | 待审批列表 | 申请编号/申请人/类型/时间、通过(填意见)/驳回(必填原因)/查看详情 |
| `views/approval/AllList.vue` | 全部申请 | 按状态筛选(6种)、状态标签颜色区分、管理员撤回(仅 approved 且未下载)、已锁定显示标签 |
| `views/student/StudentList.vue` | 学生信息 | 年级/专业/班级筛选、表格列表(学号/姓名/年级/专业/班级/手机)、查看详情。**后续需要补充**：详情弹窗（画像+荣誉+流程+申请汇总） |
| `views/system/UserList.vue` | 用户管理 | 用户列表(角色标签颜色)、Excel 导入按钮、编辑/设置角色(1-4输入) |
| `views/system/LogList.vue` | 操作日志 | 按模块/时间范围筛选、表格列表(操作人/模块/操作/IP/时间) |

---

## 五、小程序端 (`frontend-mp/`)

### 5.1 项目配置

| 文件 | 用途 |
|------|------|
| `package.json` | npm 依赖：uni-app 系列(@dcloudio/*)、Vue3、Pinia |
| `vite.config.js` | Vite + uni-app 插件 |
| `src/pages.json` | 页面路由配置 + 全局样式(导航栏#1a3a5c深蓝) + TabBar 配置(首页/问答/申请/我的 四个标签页) |
| `src/manifest.json` | 小程序配置：appid(待填)、H5 开发代理(指向 localhost:8080) |

### 5.2 核心架构

| 文件 | 用途 |
|------|------|
| `src/main.js` | SSR 模式创建 Vue 实例，安装 Pinia |
| `src/App.vue` | 根组件，设置全局样式(#f0f2f5 背景、平方字体) |
| `src/api/index.js` | 基于 `uni.request` 封装的请求工具。自动注入 Token，统一错误处理(401 跳登录)。导出全部 API：authApi、qaApi、partyApi、approvalApi、studentApi、notifyApi |
| `src/stores/user.js` | Pinia Store，使用 `uni.getStorageSync/setStorageSync` 替代 localStorage（小程序环境）|

### 5.3 页面 (`src/pages/`)

| 文件 | 页面 | 功能 |
|------|------|------|
| `pages/login/index.vue` | 登录页 | 蓝色渐变顶部 + 白色卡片表单，学号+密码登录后 switchTab 到首页 |
| `pages/index/index.vue` | 首页(TabBar) | 欢迎头部(姓名+学院+通知铃铛)、四宫格入口(智能问答/政策文档/党团进度/我的申请)、最新通知列表 |
| `pages/qa/index.vue` | 智能问答(TabBar) | **对话式聊天界面**。消息气泡(用户右侧蓝色/系统左侧白色)、AI 回答标注"仅供参考"、官方链接可复制、底部输入框+发送按钮 |
| `pages/qa/document.vue` | 政策文档 | 文档列表(标题/分类/大小/下载次数)、点击下载 |
| `pages/party/index.vue` | 党团进度 | 我的所有流程卡片：流程名称+状态标签+进度圆点条(绿=完成/蓝=当前/灰=未到)+当前步骤名 |
| `pages/party/detail.vue` | 流程详情 | 纵向时间线：每个步骤圆点+名称+描述+完成时间/预计天数 |
| `pages/approval/index.vue` | 我的申请(TabBar) | 申请列表(编号+状态标签)。approved 状态显示"下载证明"按钮(**弹窗警告"下载后锁定不可撤回"**)、pending 状态显示"撤回"按钮、downloaded 状态显示"已锁定归档"、底部"提交新申请"按钮 |
| `pages/approval/apply.vue` | 提交申请 | 选择证明类型(卡片选中高亮)→填写表单(用途/份数/备注)→提交 |
| `pages/profile/index.vue` | 个人中心(TabBar) | 个人信息卡片(姓名首字头像+学号+专业+年级+班级)、我的荣誉列表(名称+级别+日期)、退出登录按钮 |
| `pages/notify/index.vue` | 消息中心 | 通知列表(标题/内容/时间)、未读条目左侧蓝色边框、点击标记已读、"全部标记已读"按钮 |

---

## 六、部署配置 (`deploy/`)

| 文件 | 用途 |
|------|------|
| `deploy/nginx.conf` | Nginx 反向代理配置。`/` 指向管理端前端静态文件，`/api/` 代理到后端 8080，client_max_body_size 30m 支持大文件上传，静态资源 7 天缓存 |
| `deploy/sql/schema.sql` | 数据库初始化脚本（见上方详述） |

---

## 七、团队协作文档

| 文件 | 用途 |
|------|------|
| `docs/TEAM-COLLABORATION.md` | 团队分工方案(4人)、全部 API 接口清单(含请求/响应示例)、审批状态机流转图、权限隔离规则、Git 分支策略、开发环境搭建指南、4 轮迭代计划、常见问题 FAQ |

---

## 八、各模块后续需要补充的功能清单

| 模块 | 待补充内容 | 负责人 |
|------|-----------|--------|
| 认证 | 微信小程序 openid 绑定登录 | A |
| 用户管理 | EasyExcel 批量导入学生名单、数据导出 | A |
| 文件服务 | 通用上传/下载 Controller（MinIO 或本地存储） | A |
| 智能问答 | 接入真实 AI 模型（实现 WenxinAiProvider 等） | B |
| 党团流程 | 定时任务扫描到期步骤，自动发送提醒通知 | B |
| 审批流程 | PDF 证明生成（模板填充）、审批结果通知 | B |
| 学生画像 | 画像详情页（汇总荣誉+流程+申请） | B/C |
| 管理端 | 流程模板编辑弹窗、学生详情弹窗、Dashboard 图表(ECharts) | C |
| 小程序端 | TabBar 图标资源、申请详情页、下载文件流处理 | D |
| 部署 | Kingbase Docker 镜像替换、HTTPS 证书、生产环境变量 | A |
