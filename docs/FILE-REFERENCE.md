# 项目文件说明文档 — 学院学生综合服务与党团管理平台

> 本文档逐一说明项目中每个文件的用途、已实现的功能，以及后续需要补充的内容。

---

## 一、项目根目录

| 文件 | 用途 | 状态 |
|------|------|------|
| `docker-compose.yml` | 一键启动整个系统（后端+数据库+Redis+Nginx+TEI Embedding），定义了 5 个容器的编排关系。`embedding` 服务使用 ghcr.nju.edu.cn 镜像 + 挂载本地 `models/bge-small-zh-v1.5` 目录避免 TEI 1.5 的 HF_ENDPOINT bug | 已完成 |
| `.gitignore` | Git 忽略规则，排除 node_modules、target、IDE 配置等 | 已完成 |

---

## 二、后端 (`backend/`)

### 2.1 项目配置

| 文件 | 用途 | 说明 |
|------|------|------|
| `pom.xml` | Maven 依赖清单 | 声明了 Spring Boot 3、MyBatis-Plus、JWT、Hutool、EasyExcel、Knife4j(Swagger)、MinIO、Lombok 等全部依赖。Kingbase 驱动已注释，等拿到 jar 包后取消注释即可 |
| `Dockerfile` | 后端 Docker 镜像构建 | 两阶段构建：第一阶段用 Maven 编译打包，第二阶段用 JRE 运行，最终镜像体积小 |
| `src/main/resources/application.yml` | 主配置文件 | 端口 8080，接口前缀 `/api`，文件上传限制 30MB，JWT 密钥，AI 模型开关（`AI_PROVIDER / AI_API_URL / AI_API_KEY / AI_MODEL`），MyBatis-Plus 配置，Knife4j 文档开关，**RAG 配置**（512 维 BGE / TEI HTTP provider / min-score 0.5 / extractive-confidence 55 / 查询前缀 / rerank-pool-size），**邮件 SMTP 配置**（`spring.mail.*` 默认 smtp.qq.com:465，授权码由 `MAIL_AUTH_CODE` 环境变量注入），**通知群发参数**（`notify.broadcast.*` 24h 撤回窗口 / 5000 上限） |
| `src/main/resources/application-dev.yml` | 开发环境配置 | 连接本地 PostgreSQL（替代 Kingbase 开发），Redis localhost |
| `src/main/resources/application-prod.yml` | 生产环境配置 | 连接 Kingbase 数据库，密码从环境变量读取，关闭 SQL 日志和 Swagger |

### 2.2 启动类

| 文件 | 用途 |
|------|------|
| `CollegeApplication.java` | Spring Boot 启动入口。`@EnableScheduling` 开启了定时任务能力（后续用于党团流程到期自动提醒）；`@EnableAsync` 开启异步执行（邮件批量发送走 `@Async`） |

### 2.3 通用模块 (`common/`)

#### 2.3.1 统一响应 (`common/result/`)

| 文件 | 用途 |
|------|------|
| `Result.java` | 所有接口的统一返回格式 `{code, message, data}`。提供工厂方法：`Result.ok(data)` 成功、`Result.fail(msg)` 失败、`Result.unauthorized()` 未登录(401)、`Result.forbidden()` 无权限(403)。前端只需判断 `code === 200` |
| `PageResult.java` | 预留的分页响应封装类（含 `total`/`page`/`size`/`records`），当前未使用。实际分页接口直接返回 MyBatis-Plus 的 `Page<T>` 对象，响应格式为 `{total, current, size, pages, records}` |

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
| `entity/SysUser.java` | Entity | 映射 `sys_user` 表。字段：id、学号、姓名、密码(BCrypt)、角色等级、年级、专业、班级、**手机号、邮箱**、身份证号(加密)、生源地(加密)、户籍地(加密)、状态 |
| `mapper/SysUserMapper.java` | Mapper | 继承 MyBatis-Plus 的 `BaseMapper<SysUser>` |
| `service/AuthService.java` | Service | `login()` 学号+BCrypt 验密+签发 JWT；`register()` 查重+哈希+默认 4 级学生；`updateMyProfile(email, phone)` 用 `LambdaUpdateWrapper` 显式 SET 修改邮箱/手机（**含正则校验**；传空串清空回落默认派生）|
| `controller/AuthController.java` | Controller | `POST /auth/login`、`POST /auth/register`、`GET /auth/profile`（含派生邮箱）、`PUT /auth/profile`（自助改邮箱/手机） |

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
| `service/ai/impl/HttpAiProvider.java` | 自定义 HTTP 大模型网关。请求体为 `{ question, context }`，响应支持 `answer/result/data.answer/choices[0].message.content` |
| `service/ai/impl/OpenAiCompatibleProvider.java` | OpenAI Chat Completions 兼容 Provider。用于 OpenAI、DeepSeek、通义千问兼容模式等 `/v1/chat/completions` 接口 |

**后续需要补充**：如果要接入非 OpenAI 兼容协议的厂商原生 API，可新增专用 Provider，只需 implements AiProvider 并注册为 Spring Bean。

#### Service 层

| 文件 | 用途 |
|------|------|
| `service/QaService.java` | 核心业务逻辑。`chat()` 方法实现了完整的问答流程：1)关键词匹配知识库 → 2)未命中则做 RAG 文档检索 → 3)拼接知识库候选 + RAG 片段作为 AI 上下文 → 4)调 AI 或 RAG 抽取式回答（无 AI 时）→ 5)记录到 chat_log。还包含知识库 CRUD、文档管理、下载计数等功能 |
| `entity/QaDocumentChunk.java` | 政策文档向量切片，对应表 `qa_document_chunk`。字段：文档ID、标题、分类、切片序号、文本内容、关键词、score（运行时打分） |
| `mapper/QaDocumentChunkMapper.java` | 切片表 CRUD + 余弦相似度检索的原生 SQL（`embedding <=> ?::vector`） |
| `service/rag/EmbeddingService.java` | 向量化服务。两种 provider：`local-hash`（SHA-256 hash 兜底）和 `http`（生产环境，调用 TEI / OpenAI 兼容服务）。区分 `embed()`（文档不加前缀）和 `embedQuery()`（查询加 BGE 前缀）。兼容 OpenAI/TEI 原生/嵌套数组等多种响应格式，HTTP 失败或维度不符自动回退 local-hash |
| `service/rag/DocumentRagService.java` | RAG 主服务。`indexDocument()` 解析 PDF/DOCX/TXT、切片（优先按"第X条"边界，长段落按语义边界切分）、批量调 embedding 写库；`retrieve()` 向量检索 + 查询扩展 + boost 重排；校历/节假日/报到/学期安排等短问题会优先匹配 `校历安排` 分类；`buildContext()` 拼接 prompt 上下文 |
| `service/rag/RagScoringUtil.java` | 共享评分逻辑工具类。提供词项提取/同义词扩展/受众匹配/意图结构评分等，被 QaService 和 DocumentRagService 共用，避免代码重复 |

#### Controller 层

| 文件 | 用途 |
|------|------|
| `controller/QaController.java` | 暴露接口：`POST /qa/chat` 智能问答、`GET /qa/chat/history` 问答历史、`GET/POST/PUT/DELETE /qa/knowledge/*` 知识库管理(需≤2级)、`GET/POST/DELETE /qa/document/*` 文档管理、`POST /qa/document/{id}/index` 触发 RAG 向量入库、`GET /qa/document/chunk/search` 调试用切片相似度检索 |

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
| `service/ApprovalService.java` | 核心业务。学生端：`apply()` 提交申请(校验模板必填字段、生成编号、设置审批链第一级)、`getTemplateFields()` 返回模板表单字段、`getMyApplications()` 我的申请列表、`withdraw()` 撤回(调状态机校验)、`downloadCert()` 下载证明(**触发锁定!** 状态→downloaded + 记录时间)、`downloadCertFile(preview)` 预览/下载 PDF。管理端：`approve()` 通过(多级审批链自动推进并生成证明)、`reject()` 驳回、`adminWithdraw()` 管理员撤回(状态机校验) |
| `service/CertTemplateRegistry.java` | 证明模板注册表。运行时不解析 docx；党员证明、团员证明的正文结构、必填字段、日期格式和学生档案取值都在这里固化。未匹配到注册模板时走通用证明文本生成 |

审批通过/驳回/撤回会写审批记录并发送站内通知；下载后状态锁定为 `downloaded`，禁止继续撤回或重新审批。

#### Controller 层

| 文件 | 用途 |
|------|------|
| `controller/ApprovalController.java` | 学生端：`GET /approval/types` 可选类型、`GET /approval/templates` 办公模板、`GET /approval/templates/{id}/fields` 模板字段、`POST /approval/apply` 提交、`GET /approval/my/page` 列表、`GET /approval/my/{id}` 详情、`PUT /approval/my/{id}/withdraw` 撤回、`GET /approval/my/{id}/download` 旧下载接口、`GET /approval/my/{id}/download-file?preview=true|false` PDF 预览/下载（非 preview 会触发锁定）。管理端(≤2级)：待审批列表、通过/驳回/管理员撤回、全部申请查询。关键操作标注了 @OperationLog |

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
| `service/StudentService.java` | 学生端：`getMyProfile()` 获取个人信息、`getMyHonors()` 获取荣誉列表。管理端：`getStudentPage()` 学生分页列表(**普通学生+学生骨干**, 支持身份/年级/专业/班级筛选; **含数据隔离**：3级只看本班、脱敏处理)、`getStudentDetail()` 学生画像详情(含荣誉)、荣誉 CRUD(仅管理员可操作) |

#### Controller 层

| 文件 | 用途 |
|------|------|
| `controller/StudentController.java` | 学生端：`GET /student/profile` 和 `GET /student/honors`。管理端(≤2级或3级)：学生列表/详情/荣誉管理 |

---

### 2.9 系统管理模块 (`module/system/`)

#### Entity + Mapper

| 文件 | 用途 |
|------|------|
| `entity/SysNotification.java` | 通知消息。字段：用户ID、标题、内容、类型(`sms_sim`/`system`/`reminder`/`email_sim`)、**tags / source / source_url / broadcast_id**（群发关联）、是否已读 |
| `entity/SysNotificationBroadcast.java` | 群发记录。字段：标题/正文/标签/来源、`target_filter`(JSON)、`channels`、`target_count / sent_count / email_sent`、`operator_id`、`withdrawn / withdrawn_at` |
| `entity/SysOperationLog.java` | 操作日志。字段：操作人ID、模块、操作描述、变更详情(JSON)、IP、时间 |
| `mapper/SysNotificationMapper.java` | 通知数据访问 |
| `mapper/SysNotificationBroadcastMapper.java` | 群发记录数据访问 |
| `mapper/SysOperationLogMapper.java` | 日志数据访问 |

#### Service 层

| 文件 | 用途 |
|------|------|
| `service/SystemService.java` | 综合 Service：用户管理(分页[支持身份/年级/专业/班级筛选]/详情/修改/设置角色/改密码)、`getStudentDimensions()` 返回年级/专业/班级 distinct 选项供前端下拉、`exportStudents()` 导出学生名单(普通学生+骨干, 含身份列, 支持按身份/年级/专业/班级)、Dashboard 统计、操作日志查询、通知消息(列表 / 未读数 / 标签聚合 / 标记已读 / 全部已读)、`sendNotification()` 供其他模块调用 |
| `service/EmailService.java` | 邮件发送服务。`resolveEmail(user)` 派生邮箱（user.email ?? 学号@ruc.edu.cn）；`isAvailable()` 判断 SMTP 是否可用（mailSender bean + fromAddress）；`sendBatch()` `@Async` 批量发送（节流 100ms/N 封）；`sendOne()` 同步单封用于精确统计 emailSent。**鉴权失败仅 warn，不抛异常，由 broadcast 决定降级** |
| `service/NotificationBroadcastService.java` | 模块三核心。`resolveTargets()` 按 `roles[]`(多选 4/3/2/1) 解析目标——年级/专业/班级只对学生类(普通学生4+骨干3)生效, 老师/院领导(1/2)整组接收; `broadcast()` 一个事务内完成：筛选目标 → 写 broadcast 记录 → 批量写站内通知 → 真实发邮件（或降级 email_sim）→ 回写 emailSent；`previewTargetBreakdown()` 预览并按角色拆分人数(学生/骨干/老师/院领导)；`withdraw()` 24h 内撤回（按 broadcast_id 删除未读通知）；`distinctTags()` 标签聚合 |

#### Controller 层

| 文件 | 用途 |
|------|------|
| `controller/SystemController.java` | `/system/user/*`（≤2级）、`/system/dimensions`（≤2级, 年级/专业/班级下拉选项）、`/system/dashboard`（≤2级）、`/auth/password`（全部）、`/log/page`（≤1级）、`/notify/*` 通知列表 / 未读数 / 标签 / 群发预览 / 群发 / 群发历史 / 撤回 |
| `controller/FileController.java` | `POST /file/upload` 通用文件上传（≤30MB，限定到配置的 upload-path）、`GET /file/download/{fileId}` 文件下载 |

---

### 2.10 测试 (`backend/src/test/`)

| 文件 | 用途 |
|------|------|
| `module/qa/service/rag/EmbeddingServiceTest.java` | 单元测试 `EmbeddingService` HTTP 响应解析。覆盖 OpenAI/TEI 原生/简单 `embedding` 字段/`embeddings[0]` 嵌套数组 4 种格式，以及维度不匹配、HTTP 错误、query 前缀只用于查询不用于文档的关键不变式。10 个测试用例 |

---

## 三、数据库 (`deploy/sql/`)

| 文件 | 用途 |
|------|------|
| `schema.sql` | 完整的建表脚本（兼容 PostgreSQL 和 Kingbase）。**16 张表**：14 张业务 + `qa_document_chunk`（RAG 向量切片，依赖 pgvector）+ `sys_notification_broadcast`（群发记录）。包含索引、中文注释、初始数据（admin + 3 老师 + 3 骨干 + 测试学生, 密码均 admin123 + 4 种审批类型 + 入党/入团流程模板） |
| `seed_teacher_leader.sql` | 老师(2级)/骨干(3级)种子数据增量脚本。已并入 `schema.sql` 初始数据, 旧库可单独执行补种(幂等 `ON CONFLICT DO NOTHING`) |
| `rag_pgvector.sql` | RAG 增量迁移：创建 `vector` 扩展和 `qa_document_chunk` 表 + ivfflat 索引 |
| `rag_migrate_384_to_512.sql` | 384 → 512 维向量迁移（从 local-hash 切到 BGE）。TRUNCATE 现有切片 + 改列类型 + 重建索引，迁移后需在管理端「重新索引」所有文档 |
| `qa_template_migrate.sql` | qa_document 增量迁移：增加 `doc_type` 与 `description` 字段，已有数据回填为 `policy` |
| `notify_broadcast_migrate.sql` | 信息精准推送增量迁移：sys_user 加 `email`、sys_notification 加 `tags/source/source_url/broadcast_id`、新建 `sys_notification_broadcast` 表 |

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
| `src/layouts/MainLayout.vue` | 管理端主布局。左侧暗红深色导航栏（根据 roleLevel 动态显示/隐藏菜单项，可折叠）+ 顶栏（面包屑、通知铃铛+未读数、用户下拉菜单：改密码/退出）+ 右侧内容区。暗红/灰白/白色严谨配色 |

### 4.7 页面 (`src/views/`)

| 文件 | 页面 | 功能 |
|------|------|------|
| `views/login/index.vue` | 登录页 | 学号+密码表单，低饱和暗红主题，白色表单区，调 authApi.login 后存 Store 跳转 |
| `views/dashboard/index.vue` | 数据概览 | 四个统计卡片（在校学生/总用户/待审批/进行中流程），调 systemApi.getDashboard |
| `views/qa/KnowledgeList.vue` | 知识库管理 | 分类+关键词筛选、表格列表、新增/编辑弹窗、删除确认 |
| `views/qa/DocumentList.vue` | 政策文档管理 | 文档列表、上传(限30MB)、下载、删除、「重新索引」触发 RAG 入库 |
| `views/qa/TemplateList.vue` | 办公模板管理 | 与文档管理同构，但仅显示/操作 `doc_type=template` 的记录（请假条/活动预算表/简报等） |
| `views/party/TemplateList.vue` | 流程模板管理 | 模板列表(名称/步骤数/描述/状态) + 新建/编辑弹窗（含步骤名称/描述/预计天数/所需材料的动态增删 + 前端校验），编辑时通过 `getTemplateDetail` 回填现有步骤 |
| `views/party/InstanceList.vue` | 学生流程管理 | 按模板/状态筛选、表格列表、推进(弹窗填备注)/暂停操作 |
| `views/approval/PendingList.vue` | 待审批列表 | 申请编号/申请人/类型/时间、通过(填意见)/驳回(必填原因)/查看详情 |
| `views/approval/AllList.vue` | 全部申请 | 按状态筛选(6种)、状态标签颜色区分、管理员撤回(仅 approved 且未下载)、已锁定显示标签 |
| `views/student/StudentList.vue` | 学生信息 | 身份(普通学生/骨干)+年级/专业/班级**下拉筛选**、表格列表(学号/姓名/身份标签/年级/专业/班级/手机/邮箱)、查看详情抽屉（画像、荣誉、流程、申请汇总） |
| `views/system/UserList.vue` | 用户管理 | 用户列表(角色标签颜色、邮箱列)、身份+年级/专业/班级**下拉筛选**、Excel 导入按钮、**导出学生名单**(弹确认框说明范围, 普通学生+骨干, 含身份列)、编辑用户基础信息和邮箱、设置角色(1-4) |
| `views/system/LogList.vue` | 操作日志 | 按模块/时间范围筛选、表格列表(操作人/模块/操作/IP/时间) |
| `views/system/NotificationBroadcast.vue` | 通知群发 | 双 tab：**群发新通知**（标题/正文/标签/来源/链接 + 接收对象多选[普通学生/骨干/老师/院领导] + 年级/专业/班级下拉[仅对学生生效] + 渠道勾选 + 预览目标人数[按角色拆分]）；**广播历史**（命中/已读/邮件数 + 状态徽标 + 24h 内撤回按钮） |

---

## 五、小程序端 (`frontend-mp/`)

### 5.1 项目配置

| 文件 | 用途 |
|------|------|
| `package.json` | npm 依赖：uni-app 系列(@dcloudio/*)、Vue3、Pinia |
| `vite.config.js` | Vite + uni-app 插件 |
| `src/pages.json` | 页面路由配置 + 全局样式(导航栏低饱和暗红) + TabBar 配置(首页/问答/申请/我的 四个标签页) |
| `src/manifest.json` | 小程序配置：appid(待填)、H5 开发代理(指向 localhost:8080) |

### 5.2 核心架构

| 文件 | 用途 |
|------|------|
| `src/main.js` | SSR 模式创建 Vue 实例，安装 Pinia |
| `src/App.vue` | 根组件，设置全局样式(#f0f2f5 背景、平方字体) |
| `src/api/index.js` | 基于 `uni.request` 封装的请求工具。自动注入 Token，统一错误处理(401 跳登录)。开发环境默认 `http://localhost:8080/api`，生产环境默认 `http://10.10.0.27/api`，可用 `VITE_API_BASE_URL` 覆盖。导出全部 API：authApi、qaApi、partyApi、approvalApi、studentApi、notifyApi |
| `src/stores/user.js` | Pinia Store，使用 `uni.getStorageSync/setStorageSync` 替代 localStorage（小程序环境）|

### 5.3 页面 (`src/pages/`)

| 文件 | 页面 | 功能 |
|------|------|------|
| `pages/login/index.vue` | 登录页 | 暗红主题品牌区 + 白色表单卡片，密码输入隐藏，学号+密码登录后 switchTab 到首页 |
| `pages/index/index.vue` | 首页(TabBar) | 欢迎头部(姓名+学院+通知铃铛)、四宫格入口(智能问答/政策文档/党团进度/我的申请)、最新通知列表 |
| `pages/qa/index.vue` | 智能问答(TabBar) | **移动端 AI 应用式聊天界面**。消息气泡(用户右侧暗红/系统左侧白色)、快捷问题标签、AI 回答标注"仅供参考"、官方链接可复制、底部输入框+发送按钮 |
| `pages/qa/document.vue` | 政策文档 | 文档列表(标题/分类/大小/下载次数)、点击下载 |
| `pages/party/index.vue` | 党团进度 | 我的所有流程卡片：流程名称+状态标签+进度圆点条(绿=完成/蓝=当前/灰=未到)+当前步骤名 |
| `pages/party/detail.vue` | 流程详情 | 纵向时间线：每个步骤圆点+名称+描述+完成时间/预计天数 |
| `pages/approval/index.vue` | 我的申请(TabBar) | 申请列表(编号+状态标签)。approved 状态显示"下载证明"按钮(**弹窗警告"下载后锁定不可撤回"**)、pending 状态显示"撤回"按钮、downloaded 状态显示"已锁定归档"、底部"提交新申请"按钮 |
| `pages/approval/apply.vue` | 提交申请 | 现代卡片式提交页。先选择证明类型和办公模板，再调用 `getTemplateFields` 动态渲染文本/下拉/日期字段；提交 `typeId + templateDocId + formData`，由后端固定模板生成证明正文 |
| `pages/profile/index.vue` | 个人中心(TabBar) | 头像+姓名+学号；**联系方式卡片**（邮箱/手机，点击 `uni.showModal` 编辑，含正则校验，清空回落默认派生）；荣誉列表；退出登录 |
| `pages/notify/index.vue` | 消息中心 | 通知列表 + **横向标签筛选条**（调 `/notify/tags`）+ 来源徽章 + 标签 chips。未读左侧深红色边框、点击标记已读、「全部标记已读」按钮 |

---

## 六、部署配置 (`deploy/` 与 `scripts/`)

| 文件 | 用途 |
|------|------|
| `deploy/nginx.conf` | Nginx 反向代理：`/` → 管理端 dist，`/api/` → 后端 :8080，client_max_body_size 30m，静态资源 7 天缓存 |
| `deploy/docker-compose.prod.yml` | 生产 5 容器编排（backend + postgres-pgvector + redis + embedding + nginx），全部 `image:` 引用预构建镜像 |
| `deploy/sql/*.sql` | 见上方第三节；`schema.sql` fresh 部署会创建 `admin` 和 `20240001` 两个演示账号 |
| `scripts/build-deploy-package.ps1` | 本地一键打包：mvnw package → docker build/save 5 个镜像 → npm build 前端 → 下载 BGE 模型 → 装配 `deploy-package/`，全程**幂等增量** |
| `scripts/deploy.sh` | 服务器一键部署：自动判定 fresh / 增量 / restart-only 模式 → docker load → rsync 配置/前端/模型 → docker compose up → 120s 健康检查 + 3 端点冒烟 |
| `scripts/import-templates.ps1` / `import-party-docs.ps1` | 批量灌入办公模板 / 党团政策文档的辅助脚本 |

---

## 七、项目文档

| 文件 | 用途 |
|------|------|
| `docs/TEAM-COLLABORATION.md` | 分工、全部 API 接口清单、审批状态机、权限隔离规则、常见问题 |
| `docs/ARCHITECTURE.md` | 系统全景、通信链路、JWT、数据库 ER、RAG 检索架构、邮件/广播架构 |
| `docs/DEPLOYMENT.md` | 离线服务器部署手册、TEI/BGE 部署、故障排查 |
| `docs/TODO.md` | 各模块完成度、迭代回顾、已知问题、待办 |
