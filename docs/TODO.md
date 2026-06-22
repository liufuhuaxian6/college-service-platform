# 进度与待办

> 最后更新: 2026-06-12
> 记录各模块完成情况、近期迭代回顾、已知问题与未完成事项。

---

## 一、当前完成度

| 模块 | 状态 | 说明 |
|---|---|---|
| 通用框架（Result、异常、JWT、@RequireRole、@OperationLog、AES 工具） | ✅ 已完成 | A |
| 认证（登录/注册/个人资料读写/改密码） | ✅ 已完成 | A |
| 用户管理（CRUD、设置角色、Dashboard 统计） | ✅ 已完成（含 EasyExcel 导入 / 导出 / 前端模板下载） | A |
| 操作日志（@OperationLog AOP + 查询接口） | ✅ 已完成 | A |
| 文件服务（通用上传 30MB / 下载） | ✅ 已完成 | A |
| 通知消息（站内 / 未读数 / tag 筛选 / 标记已读） | ✅ 已完成 | A |
| **信息精准推送（模块三）** — 群发 + 邮件 + 24h 撤回 + 标签筛选 | ✅ 已完成（E2E 通过） | A |
| 智能问答（标准知识库 + RAG + AI 抽象层） | ✅ 已完成（AI 默认 Noop，AI 接入待补） | B |
| RAG（BGE-small-zh-v1.5 / TEI / pgvector 512 维 / 查询扩展 / 重排） | ✅ 已完成 | B |
| 政策文档管理 + 重新索引触发 | ✅ 已完成 | B |
| 办公模板模块（`doc_type=template`） | ✅ 已完成 | B/C |
| 党团流程（模板 CRUD / 实例推进 / 暂停 / 到期提醒定时任务） | ✅ 已完成 | B |
| 审批流程（状态机 / 多级链 / 固定证明模板 / 下载即锁定） | ✅ 已完成（党员/团员证明模板已固化；PDF 优先嵌入本机中文字体，找不到字体时降级） | B |
| 学生画像（个人信息 / 邮箱 / 荣誉 / 数据隔离） | ✅ 已完成（管理端 el-drawer 含 4 块汇总，列表显示邮箱） | B/C |
| PC 管理端 | ✅ v3 信息架构重构完成（红顶栏+分组侧边栏 / 审批中心 / 工作台内联审批 / 模板卡片化） | C |
| 小程序端 | ✅ v3 交互重构完成（服务大厅首页 / 申请三步向导 / 状态筛选 / 阶段手风琴 / 沉浸式问答） | D |
| 离线部署（5 容器 + 一键脚本对） | ✅ 已完成 | A |

---

## 二、近期迭代回顾

### 2026-06-21 · RAG 宽松召回 + 大模型自主决定引用

把"相似度不达标就判未命中、后端机械给引用"改为"宽松召回候选 → 大模型自己判断与引用"：

- `DocumentRagService.retrieveCandidates`：宽松召回(`rag.candidate-min-score` 默认 0.2，远低于严格 `minScore`)，召回更多候选交给大模型，不再因低分直接"未命中"
- `QaService` 大模型分支重构：候选拼成带【编号】资料(`buildNumberedContext`)喂 AI；解析 AI 在答案末尾标注的 `[引用: n]` 编号(`parseAiCitations`)→ 映射回文档(按文档去重，最多 3)生成 `references`，**AI 未引用则为空**；`stripCitationTag` 去掉答案里的引用标记不展示
- `OpenAiCompatibleProvider` systemPrompt：资料不足也别直接拒答 + 末尾按 `[引用: 1,3]` / `[引用: 无]` 标注引用
- **抽取式(未配大模型)分支保持原严格 RAG**；仅大模型分支走"AI 自主引用"
- 前端无需改(已有 references chips + 右滑侧栏)；需配 `AI_PROVIDER=openai` + key 重启后端生效
- 验证：后端 `mvnw compile` ✅

### 2026-06-21 · 问答接入多轮对话上下文

- `AiProvider` 接口加带 `history` 的 default 方法(默认退化单轮, 向后兼容); `OpenAiCompatibleProvider` 把对话历史拼进 `messages`(system + 历史 user/assistant + 当前问题带最新检索上下文)
- `QaService.chat` / `QaController.ChatRequest` 透传 `history`; 前端 `qa/index.vue` 的 `send` 取当前问题前最近 6 条已完成对话(ai→assistant)一并发送
- 仅**大模型分支**生效(知识库精确匹配 / RAG 抽取式不依赖历史, 因其基于单问题检索); 需配 `AI_PROVIDER=openai` + key 重启后端才有多轮效果
- 验证: 后端 `mvnw compile` ✅、`build:mp-weixin` ✅

### 2026-06-21 · 问答来源引用(点击看原文) + 首页通知红点修复

- **问答来源引用**(现代 AI 体验，仿 Perplexity/Claude)：
  - 后端 `QaService.chat` 在 RAG 分支新增结构化 `references`——按文档去重取最高分片段、`focusExtractiveContent` 提取命中段落、最多 3 条，每条 `{documentId, title, category, snippet}`(此前引用只揉在 answer 文本里，前端拿不到结构)
  - 前端 AI 回复底部新增**参考来源 chips**(红序号 + 文档名，可点)；点击**从右滑出侧栏**展示：文档标题 + 分类徽章 + 命中政策原文片段 + **下载原文档**按钮(调 `/qa/document/{id}/download`)
- **修复首页通知红点不消失**：首页未读数原用 `onMounted` 取，tab 页只首次触发——读完通知切回首页不刷新、红点残留；改用 `onShow`(每次显示刷新)，读完即消失、有新消息及时亮
- 验证：后端 `mvnw compile` ✅ + Python(UTF-8) 实测 chat 返回 references 正常；`build:mp-weixin` ✅；H5 巡检确认 chips/侧栏/原文片段/红点均正常

### 2026-06-19 · 小程序巡检 + 智能问答重做为现代 AI 对话界面 + 3 处优化

按手机尺寸逐页截图巡检小程序(H5 真机一致渲染)后改进：

- **智能问答页重做**(qa/index.vue)：仿 DeepSeek/ChatGPT/Claude——居中红圆校徽 logo + 衬线标题 + **2×2 示例提问卡**的欢迎态；对话态为用户红气泡 + AI 圆头像 + 宽内容块，AI 回复做**轻量 markdown 解析**(标题/有序/无序列表/段落)排版 + **打字机逐字效果** + 末尾光标；回复附**参考依据**与**复制**操作；底部**自适应高度输入框 + 圆形发送箭头按钮**，对话态顶部"＋新对话"
- **修复文件徽标 bug**(qa/document.vue)：`fileExt` 此前取 MIME 前 4 字母显示 `APPL/IMAG/TEXT`，改为取真实扩展名(PDF/DOCX/TXT/PNG)，徽标中性灰底与管理端一致
- **通知卡片紧凑化**(notify + 首页)：减小 padding/行高/字号、正文 2 行截断、标签更小，长列表不再拥挤
- **提交申请第 1 步留白优化**：模板少时中部大留白，补一张"办理流程"3 步引导卡
- 验证：`build:mp-weixin` ✅；H5 巡检确认欢迎态/对话态/打字机/列表渲染/徽标/紧凑均正常

### 2026-06-19 · 导入/导出逻辑理顺（归属纠正 + 全角色 + 身份列 + 新增用户）

针对"用户管理与学生信息的导入导出逻辑不合理"，重排为：

- **导出归属纠正**：「学生信息」页新增**导出学生名单**（`GET /student/export`，minRole 3，**3 级骨干受数据隔离只导本班**），支持当前年级/专业/班级/身份多选筛选；学生名单导出从此回归到学生信息页
- **用户管理改全角色导出**：`/system/user/export` 由"只导学生(3/4)"改为**导出全部角色用户**（`exportUsers`，按身份筛选，导出含**角色 / 邮箱 / 状态**列，启用+禁用都导）；前端按钮"导出学生名单"→"导出用户"
- **导入支持身份列**：导入模板新增「身份」列（留空=普通学生，支持 普通学生/学生骨干/老师/院领导）；`importStudents` 按身份列设角色，并加**越级保护**（不能导入与自己同级或更高权限的账号）；下载模板加老师示例行
- **新增"新增用户"入口**（补此前硬缺失）：`POST /system/user` `createUser`（学号查重 + **越级保护** + 默认密码 123456）；UserList 加新增按钮与弹窗，身份下拉只列出操作者**可创建**的角色
- 验证：后端 `mvnw compile` ✅、前端 `npm run build` ✅（接口需重启后端生效）

### 2026-06-19 · 管理端截图巡检 + 配色协调（修复 Element 主色变量未生效根因）

- **根因修复（影响最大）**：发现 `--el-color-primary` 实际仍是 Element 默认蓝 `#409eff`——theme.scss 对 Element 变量的 `:root` 覆盖被 Element 自身 `:root` 按加载顺序盖回，导致**所有原生主色组件（复选框/单选/开关/plain 按钮/分页/输入聚焦/loading）都是蓝色**，与人大红主题割裂（之前看着"红"只是按钮背景被硬覆盖）。用更高特异性 `:root:root` 再覆写一次主色变量，一次性把这些组件统一红化
- **配色收敛**：用户管理「导出学生名单」绿色按钮 → 红描边；办公模板「替换」橙色 → 红描边；global.scss 补 plain 主按钮红化覆写；政策文档文件类型徽标改为**统一中性灰底 + 扩展名文字**（TXT/PDF/PNG 一眼区分，取代几乎全红的"按类型配色"）
- **筛选风格统一**：知识库分类标签条选中态与审批中心分段 Tab 统一为同款红渐变
- **工作台精简**：横幅副标题去掉与 KPI 卡重复的"待审批/进行中流程"数字，改为平台能力概览句
- 巡检方式：playwright-core 驱动系统 Chrome 登录后逐页截图核对（12 页 + 编辑弹窗），确认弹窗遮挡修复有效；临时脚本与依赖已清理
- 验证：`npm run build` ✅

### 2026-06-19 · 测试反馈三个 bug 修复（多选筛选 / 已读统计 / 越级保护）

- **Bug1 筛选不支持多选**：`getUserPage` / `exportStudents`（用户管理）、`getStudentPage`（学生信息）三处接口的 `grade/major/className/roleLevel` 改为接收逗号分隔多值并用 `IN` 查询（`splitCsv`/`splitCsvInt`，兼容单值）；前端 `UserList.vue` / `StudentList.vue` 身份/年级/专业/班级改为 `multiple` 多选下拉（collapse-tags），查询参数以逗号拼接；导出确认逻辑适配多选（只取学生身份 3/4，含老师/院领导时提示忽略）
- **Bug2 已读默认全已读**：广播历史"目标/已读"原显示 `sentCount`（已写入条数≈目标数）。`SysNotificationBroadcast` 新增瞬态 `readCount`，`getBroadcastPage/Detail` 按 `sys_notification(broadcast_id, type='system', is_read=true)` **实时统计真实已读**——群发后即为 0，学生在消息中心点开后才递增；前端列改用 `readCount`（0 灰色、>0 绿色）
- **Bug3 老师越级禁用院领导**：`updateUser` / `setUserRole` 加越级保护——操作者只能管理**角色等级严格低于自己**的账号（数字更大=权限更低），老师(2) 无法修改/禁用院领导(1) 或其它老师(2)；`updateUser` 额外禁止顺带改 `roleLevel`（角色变更只能走受校验的 `setUserRole`，且不得提升到与自己同级/更高）；前端 `UserList.vue` 对不可管理行隐藏编辑/设置角色按钮并显示"不可管理"，"设置角色"仅院领导可见
- 验证：后端 `mvnw compile` ✅、前端 `npm run build` ✅

### 2026-06-12 (七期) · 图标体系替换（去"单个大字"）+ 通知群发表单重排

- **小程序图标体系**：新增 `static/icons/` 6 个 SVG 线条图标（问答气泡/文档/旗帜/审批勾选/证书/奖章）；首页服务卡从"2×2 大字卡 + 大字水印"改为**通栏横排行卡**（淡色圆角图标块 + 标题徽标 + 单行描述 + 箭头）；问答入口"问"字改 CSS 放大镜；推荐问题"问"字章改菱形红点；聊天用户侧"我"头像移除；申请页模板/确认首字图标改证书 SVG；个人中心"奖"字奖章改奖章 SVG
- **管理端图标**：流程模板卡片首字改 `Flag` 图标、办公模板卡片首字改 `Document` 图标（淡色底图标块，占位模板金色调）；知识库"问/答"章改 **Q/A**
- **通知群发**重排为**双栏**：左"通知内容"（label 置顶、来源+链接同行）/ 右"发送设置"（接收对象与渠道分组框、虚线红底**人数预览块**大数字展示、底部重置/确认群发）——替代原通栏超宽表单
- 构建验证：双端 ✅

### 2026-06-12 (六期) · 弹窗裁剪根因修复 + 表单控件统一 + 登录/首页优化

- **弹窗裁剪根因**：`.app-page` 进场动画和路由过渡使用了 `transform` 且 fill-mode 保留终态——祖先存在 transform 时 `position:fixed` 的 el-dialog 遮罩以该元素为包含块而被裁剪。两处动画均改为**仅透明度**过渡，弹窗彻底恢复正常（弹窗体内滚动保留兜底）
- **管理端表单控件统一**：输入框/选择框/文本域统一暖白底 + 8px 圆角 + 悬停描边加深 + **聚焦红色描边与外发光环**；主按钮改红渐变浮起、默认按钮悬停红化、下拉面板选中项红色调
- **小程序登录页**：输入框移除"号/密"前缀字；新增**密码可见性切换**（CSS 眼睛图标，睁眼红色/闭眼带斜杠）；管理端登录密码本就支持 `show-password` 小眼睛
- **小程序首页布局重排**：hero 改两行结构——第一行「校徽 + 中国人民大学/信息学院」品牌区 + 右侧铃铛，第二行大号问候语 + 鎏金下划线 + 副标题；移除右侧悬挂的小校徽（仅保留水印），版面更稳
- 构建验证：双端 ✅

### 2026-06-12 (五期) · 视觉打磨（弹窗修复 / 去头像 / 小程序全页面强化）

- **修复**：管理端弹窗（如 29 步流程模板编辑）超出视口被遮挡——全局 `el-dialog` 改为 7vh 置顶 + 弹窗体 `max-height: 66vh` 内滚动 + 页脚分隔线
- **去头像**：学生信息/用户管理人员行、学生流程卡、画像头卡、顶栏用户块均移除头像圆；顶栏用户块改为描边胶囊（姓名·角色），学生流程卡改左侧红色细条标识
- **小程序全页面视觉强化**：登录页改**全屏人大红沉浸式**（巨型校徽水印 + 居中校徽/中英文校名 + 鎏金"信息学院"分隔 + 底部"实事求是"）；首页问候语放大 + **鎏金短下划线**（各 Tab 页 hero 统一此语言）、问答入口按钮改红色渐变；服务卡新增**右下角衬线大字水印** + 顶部色条；问答欢迎页标题放大 + 推荐问题加红"问"章；通知未读卡加**左侧红色条** + 淡红渐变底、激活标签改红渐变；党团进度百分比改**实心红渐变圆章**、详情页当前阶段头淡红底强调；个人中心 hero 改**居中式**（校徽 + 大号衬线姓名 + 鎏金下划线 + 身份徽章）；文档页 Tab 加高
- 构建验证：双端 ✅

### 2026-06-12 (四期) · 内容展示层重做（列表页全面卡片化/可视化，功能等价）

**交付（管理端）：**
- **知识库**：表格 → **问/答卡片**（红"问"章 + 金"答"章 + 答案截断 + 关键词 chips + 官方链接，悬停浮现操作）
- **政策文档**：表格 → **文档卡片网格**（折角文件类型徽标按扩展名配色 + 分类 chip + 元信息 + 卡内下载/向量入库/删除）
- **办公模板**：表格 → **模板卡片网格**（占位待传卡 = 虚线琥珀边 + 金色图标，区分已上线/待补传）
- **学生流程**：表格 → **进度卡片列表**（学生头像 + 红金渐变进度条 第N步/共M步 + 卡内推进/暂停/恢复/删除）
- **操作日志**：表格 → **审计时间线**（时刻列 + 模块彩色圆点轨道 + 模块徽标）
- **学生信息 / 用户管理**：改为**人员行表格**（头像按角色配色、姓名/学号堆叠、班级/联系方式合并列、状态呼吸点）；学生画像抽屉新增人大红头卡（荣誉/流程/申请汇总数字）
**交付（小程序）：**
- 党团进度卡加**百分比环** + 当前步骤名；我的申请卡片加**左侧状态色条**；个人中心荣誉改**奖章样式**（国家级鎏金/省部级人大红/校级蓝）
- 构建验证：`frontend-admin` `npm run build` ✅、`frontend-mp` `npm run build:mp-weixin` ✅；接口与业务规则不变

### 2026-06-12 (三期) · 双端信息架构与交互重构（v3：全新页面组织，功能等价）

**交付（管理端）：**
- **外壳反转**：旧"深红侧边栏 + 白顶栏"改为**人大红顶部品牌栏**（校徽 + 平台全名 + 校训 + 通知 + 用户块）+ **浅色分组扁平侧边导航**（分组标签直达条目，取代折叠子菜单；按角色过滤整组隐藏）
- **审批中心**：「待审批」「全部申请」两页合并为 `/approval/center` 一页（分段切换 + 待办数量角标），审批动作移入**详情抽屉内完成**（意见输入 + 通过/驳回/撤回重批）；旧路由重定向保持深链兼容
- **工作台**：数据概览升级——右列待办审批支持**页内直接通过/驳回** + 六宫格快捷入口；左列三图表
- **党团流程模板卡片化**（图标章卡片网格 + 虚线新建卡）；**知识库分类标签条**（点击即筛）取代下拉
- 删除被替代的 `PendingList.vue` / `AllList.vue`

**交付（小程序）：**
- **首页服务大厅化**：新增悬浮**搜索式问答入口** + **我的事项速览**（审批中申请数、党团流程进度条，复用既有 API，失败不阻塞）
- **提交申请改三步向导**：选模板 → 填写信息 → 确认提交（步骤指示器 + 汇总确认卡）
- **我的申请**：新增横向状态筛选标签（本地过滤）；**党团详情改阶段手风琴**（默认展开当前阶段）；**问答改沉浸式**（欢迎首屏推荐问题卡 → 纯对话流）
- 构建验证：`frontend-admin` `npm run build` ✅、`frontend-mp` `npm run build:mp-weixin` ✅；全部接口调用与业务规则保持不变

### 2026-06-12 (二期) · 双端 UI 全面焕新（设计令牌 v2）+ 骨干无法进管理端 bug 修复

**交付：**
- **设计令牌 v2**：双端令牌扩充为完整体系（语义色+背景对、红色渐变 `--app-red-gradient`/`--mp-red-gradient`、阴影/圆角/缓动、衬线字体变量），管理端全量覆写 Element Plus 变量；`global.scss` 升级为组件级视觉重写（按钮微动效、红调表头、衬线弹窗标题、`.app-page` 进场动画）
- **管理端**：登录页改为全屏人大红 + 圆环/校徽水印 + "实事求是"校训 + 鎏金顶边登录卡的仪式感设计；MainLayout 新增毛玻璃顶栏、头像用户块、router-view 切换动画、侧边栏底部校训；Dashboard 横幅加日期徽章/快捷操作/校徽水印；公共组件焕新（PageHeader 红金双色条、MetricCard 悬浮动效、DataPanel 菱形标记、StatusTag 圆点徽标、EmptyState/FilterBar）
- **小程序**：App.vue 增加全局通用类（`.mp-hero` 深红横幅 / `.mp-hero-seal` 校徽水印 / `.mp-eyebrow` 等），10 个页面统一接入：hero 衬线标题 + 校徽水印、红金双色 section 标题、ServiceCard 新增彩色图标章与徽标（修复 index 传 `icon/badge` 但组件未声明的问题）、问答页打字指示动画、申请页统计三色化、卡片按压反馈
- **Bug 修复**：① **班团骨干(3级)无法使用管理端**——登录后固定跳 `/dashboard`(minRole 2) 被路由守卫 logout 踢回登录页；现登录与守卫均将骨干引导到 `/student/list`，菜单按角色 computed 过滤（无权限分组整组隐藏）；② 小程序消息中心 `loadList/markRead/markAll` 无异常捕获导致未处理 Promise rejection；③ 移除 Dashboard 死代码 `goPending`
- 构建验证：`frontend-admin` `npm run build` ✅、`frontend-mp` `npm run build:mp-weixin` ✅

### 2026-06-12 · 双端前端视觉重构（人大红主题 + 人大元素）+ 4 个前端 bug 修复

**交付：**
- 双端配色统一升级为**人大红**（Pantone 201C `#9D2235`）+ 暖灰中性色 + 鎏金点缀；设计令牌集中在 `frontend-admin/src/assets/styles/theme.scss` 与 `frontend-mp/src/styles/theme.css`
- 新增人大元素组件 `RucSeal`（渲染中国人民大学**真实校徽** `ruc-logo.svg`，深色背景自动切白色版），应用于管理端登录页/侧边栏/数据概览横幅、小程序登录页/首页
- 管理端：登录页重设计（左品牌面板 + 右登录卡）、侧边栏品牌区、数据概览欢迎横幅（问候 + 日期）、`PageHeader` 红色装饰条、`LogList` 统一为通用组件风格、favicon + 页面标题
- 小程序：导航栏/TabBar 人大红、登录页与首页融入校徽与校名、首页页脚“中国人民大学 · 信息学院”
- **Bug 修复**：① 管理端 `approvalApi.getTypes` 缺失导致待审批页"证明类型"筛选恒为空；② `StatusTag` 不支持 `type/text` 直传导致广播历史状态列显示"-"；③ axios 对 HTTP 401 不清登录态/不跳登录页；④ 小程序 `--mp-text-muted` 变量未定义
- 构建验证：`frontend-admin` `npm run build` ✅、`frontend-mp` `npm run build:mp-weixin` ✅

### 2026-05-26 · 模块三：信息精准推送（站内 + 邮件 + 24h 撤回）

**交付：**
- 新表 `sys_notification_broadcast`；`sys_user` 增 `email`；`sys_notification` 增 `tags/source/source_url/broadcast_id`
- 后端：`EmailService`（SMTP + 派生邮箱 + 降级）、`NotificationBroadcastService`（广播事务 + 撤回 + 标签聚合）
- 管理端：`NotificationBroadcast.vue`（双 tab：群发 / 历史）
- 小程序：`notify/index.vue` 横向标签筛选条 + 来源徽章；`profile/index.vue` 邮箱/手机自助编辑

**E2E 验证（QQ SMTP → RUC 邮箱）：**
- ✅ 预览人数 / 按角色+专业筛选
- ✅ 群发 1 人 → `emailSent=1` → 真实邮件投递成功
- ✅ 24h 内撤回 → `removedCount` 正确（只删未读）
- ✅ 群发历史列表 + `withdrawn` 状态展示
- ✅ tags 聚合接口 / 学生端按 tag 筛选通知
- ✅ 学生改邮箱 / 邮箱格式校验 / 清空邮箱回落默认派生（修复 MP `updateById` 忽略 null 字段 bug）

**踩坑记录：**
1. **SMTP 鉴权失败** —— `MAIL_AUTH_CODE` 在 PowerShell 新窗口中没继承，必须在**启动 mvnw 的同一会话**内设环境变量。
2. **`smtp.qq.com:994` Connection timed out** —— QQ 个人邮箱 SMTP 必须用 `:465`，但残留的 `MAIL_PORT=994` 环境变量（之前给网易设的）覆盖了 yml 默认值。教训：切换邮件服务商时 `Remove-Item Env:MAIL_*` 一遍。
3. **`socketFactory.port` 默认值** —— yml 里这一项被遗漏，仍写着 994。JavaMail 用 `socketFactory.port` 而非 `mail.port` 做实际 TCP 连接，所以即使 `mail.port=465` 也连了 994。修复后两个 port 都从 `${MAIL_PORT:465}` 取。
4. **学校邮箱不是腾讯而是网易托管** —— `ruc.edu.cn` 的 SMTP 地址需在 https://exmail.163.com/ 后台确认，常见为 `smtp.ym.163.com:994` 或 `smtp.qiye.163.com:465`；最终 demo 用学生本人的 QQ 个人邮箱作发件人，向 RUC 邮箱投递成功。
5. **MyBatis-Plus `updateById` 默认忽略 null 字段** —— 邮箱清空（setEmail(null)）写不进库。改用 `LambdaUpdateWrapper.set(getEmail, null)` 显式 SET。

6. **RUC 邮箱真实发件人最终跑通（2026-05-26 收尾）** —— RUC 学校邮箱 `2024201564@ruc.edu.cn` 托管在网易企业邮杭州节点，正确配置是 `smtphz.qiye.163.com:465 SSL`。先后踩了三个坑：
   - 误把 SMTP 端口填成 994（其实那是 IMAP 端口，smtphz 系列里 SMTP=465 / IMAP=993 / POP3=995）
   - 服务端「客户端授权」页面里 SMTP / POP / IMAP 三个开关分开，只开 IMAP 不开 SMTP 会 `Authentication failed`
   - PowerShell 双引号会解析 `$VDM` 这类含 `$` 的授权码字符串，必须用**单引号** `'...'` 包住
   
   最终 broadcastId=10 用 RUC 真实发件人成功投递到 QQ 邮箱。

### 2026-05 · 早期里程碑

- 后端基础框架 + 认证 + 系统管理（A）
- 智能问答 + 党团流程 + 审批状态机（B）
- 管理端全部页面 UI + 暗红色主题改版（C）
- 小程序端全部页面 UI + TabBar 资源（D）
- RAG 从 384 维 local-hash 升级到 512 维 BGE-small-zh-v1.5（B + A）
- 修复 RAG 无关问题误命中（语义假阳性）：抽取式置信度阈值 + 边界片段裁剪
- 办公模板模块（`doc_type` 字段切分政策 / 模板）
- 离线一键部署脚本对（A）
- 证明申请模板从“运行时解析 docx”改为“离线读模板、代码中固化生成结构”，避免部署环境缺模板或 docx 解析差异导致申请失败
- 小程序提交申请页改为按后端字段动态渲染；管理端学生/用户列表补充邮箱列；消息中心标签和最新通知展示修复

---

## 三、待办（按优先级）

### P0 — 演示/答辩必须

- [x] ~~管理端：流程模板编辑弹窗（动态增删步骤）~~ —— 已实现, 见 [TemplateList.vue](../frontend-admin/src/views/party/TemplateList.vue) 第 36-74 行
- [x] ~~管理端：学生详情弹窗（荣誉 + 流程 + 申请汇总）~~ —— 已实现 el-drawer, 见 [StudentList.vue](../frontend-admin/src/views/student/StudentList.vue) 第 49-99 行
- [ ] **管理端：Dashboard 图表增强** —— 当前基础统计和待办区可用，后续可补充趋势图、业务分布图
- [x] ~~审批：固定证明模板字段与正文生成~~ —— 已完成，见 [CertTemplateRegistry.java](../backend/src/main/java/com/ruc/college/module/approval/service/CertTemplateRegistry.java)
- [x] ~~审批：PDF 证明中文支持基础方案~~ —— 已完成，PDFBox 优先加载系统中文字体；无字体时安全降级，见 [ApprovalService.java](../backend/src/main/java/com/ruc/college/module/approval/service/ApprovalService.java)
- [ ] 答辩 PPT + 演示脚本

### P1 — 完整性

- [x] ~~EasyExcel 批量导入学生名单 + 导出~~ —— 已完成 (UserList 前端含下载模板/导入结果明细/筛选导出)
- [x] ~~党团流程到期提醒定时任务~~ —— PartyReminderJob, 每天 09:00 cron, 含 24h 防重 + `POST /party/reminder/run` 手动触发接口
- [ ] 接入真实 AI 模型（实现 `WenxinAiProvider` 或 `QianfanAiProvider`）
- [ ] 审批通过/驳回后给学生发**邮件**通知（站内通知已在，邮件群发通道已可复用）

### P2 — 可选优化

- [ ] AES 加密密钥从环境变量读取（目前硬编码）
- [ ] 微信小程序 openid 绑定登录
- [ ] Kingbase 驱动接入（pom.xml 取消注释、yml 切 driver-class-name）
- [ ] HTTPS 证书 + 正式域名（小程序上架前提）
- [ ] Knife4j 生产关闭策略
- [ ] 群发并发安全：当前 `target_count` 上限 5000，超大群发可拆批

---

## 四、已知问题 / 技术债

| 项 | 状态 | 处理建议 |
|---|---|---|
| `Result.code` 业务异常返回 200 + code=500，PowerShell `Invoke-RestMethod` 无法抛错 | 已知 | 不动后端约定（前端已适配），E2E 脚本要主动判 `res.code !== 200` |
| `socketFactory.port` 与 `mail.port` 在 JavaMail 里两个都得设，否则连错端口 | 已修复 | yml 中两项都使用 `${MAIL_PORT:465}` |
| `MAIL_AUTH_CODE` 必须与 `MAIL_USERNAME` 同一邮箱服务商，跨厂商授权码不通用 | 文档化 | README + DEPLOYMENT 已说明，运维注意 |
| 邮件批量发送目前是同步 + 节流 100ms，5000 人群发耗时 ~ 1-2 分钟 | 可接受 | 答辩规模够用；若放大可切到 `sendBatch()` @Async + MQ |
| BGE 模型未入 Git（`.gitignore`），新成员需手动下载 | 文档化 | DEPLOYMENT §12.2 给了一键下载脚本 |
| 学校 `ruc.edu.cn` 邮箱托管在网易，真实生产想用学校邮箱作发件人，需重新生成网易客户端授权码 | 已验证 | README / DEPLOYMENT 已记录 `smtphz.qiye.163.com:465 SSL` |
| PDF 中文字体依赖运行环境 | 已知 | Windows 本机通常可加载系统中文字体；Linux 生产建议安装 Noto CJK 或把字体随部署包放入容器 |

---

## 五、当前分支状态

- 主开发分支：`feat/backend-base`
- 已合并的功能分支：`feat/backend-biz`、`feat/frontend-admin`、`feat/frontend-mp`（通过 PR → dev → main 流程）
- 下一里程碑：`feat/backend-base` 整体合并到 `dev`，dev 联调通过后合 `main` 打 `v1.x` Tag
