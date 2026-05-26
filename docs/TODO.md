# 进度与待办

> 最后更新: 2026-05-26
> 记录各模块完成情况、近期迭代回顾、已知问题与未完成事项。

---

## 一、当前完成度

| 模块 | 状态 | 说明 |
|---|---|---|
| 通用框架（Result、异常、JWT、@RequireRole、@OperationLog、AES 工具） | ✅ 已完成 | A |
| 认证（登录/注册/个人资料读写/改密码） | ✅ 已完成 | A |
| 用户管理（CRUD、设置角色、Dashboard 统计） | 🟡 EasyExcel 批量导入 / 导出待补 | A |
| 操作日志（@OperationLog AOP + 查询接口） | ✅ 已完成 | A |
| 文件服务（通用上传 30MB / 下载） | ✅ 已完成 | A |
| 通知消息（站内 / 未读数 / tag 筛选 / 标记已读） | ✅ 已完成 | A |
| **信息精准推送（模块三）** — 群发 + 邮件 + 24h 撤回 + 标签筛选 | ✅ 已完成（E2E 通过） | A |
| 智能问答（关键词匹配 + RAG + AI 抽象层） | ✅ 已完成（AI 默认 Noop，AI 接入待补） | B |
| RAG（BGE-small-zh-v1.5 / TEI / pgvector 512 维 / 重排） | ✅ 已完成 | B |
| 政策文档管理 + 重新索引触发 | ✅ 已完成 | B |
| 办公模板模块（`doc_type=template`） | ✅ 已完成 | B/C |
| 党团流程（模板 CRUD / 实例推进 / 暂停） | 🟡 到期提醒定时任务待补 | B |
| 审批流程（状态机 / 多级链 / 下载即锁定） | 🟡 PDF 证明模板填充待完善 | B |
| 学生画像（个人信息 / 荣誉 / 数据隔离） | 🟡 管理端详情弹窗待补 | B/C |
| PC 管理端 | 🟡 流程模板编辑弹窗、Dashboard ECharts 图表待补 | C |
| 小程序端 | ✅ 全页面 UI 完成；TabBar 图标资源已替换 | D |
| 离线部署（5 容器 + 一键脚本对） | ✅ 已完成 | A |

---

## 二、近期迭代回顾

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

### 2026-05 · 早期里程碑

- 后端基础框架 + 认证 + 系统管理（A）
- 智能问答 + 党团流程 + 审批状态机（B）
- 管理端全部页面 UI + 暗红色主题改版（C）
- 小程序端全部页面 UI + TabBar 资源（D）
- RAG 从 384 维 local-hash 升级到 512 维 BGE-small-zh-v1.5（B + A）
- 修复 RAG 无关问题误命中（语义假阳性）：抽取式置信度阈值 + 边界片段裁剪
- 办公模板模块（`doc_type` 字段切分政策 / 模板）
- 离线一键部署脚本对（A）

---

## 三、待办（按优先级）

### P0 — 演示/答辩必须

- [ ] 管理端：流程模板编辑弹窗（动态增删步骤）
- [ ] 管理端：Dashboard ECharts 图表
- [ ] 管理端：学生详情弹窗（荣誉 + 流程 + 申请汇总）
- [ ] 审批：PDF 证明模板填充（已通过 → 学生下载真实 PDF）
- [ ] 答辩 PPT + 演示脚本

### P1 — 完整性

- [ ] EasyExcel 批量导入学生名单 + 导出
- [ ] 党团流程到期提醒定时任务（@Scheduled 扫描 → 调用 sendNotification）
- [ ] 接入真实 AI 模型（实现 `WenxinAiProvider` 或 `QianfanAiProvider`）
- [ ] 审批通过/驳回后给学生发通知（站内 + 邮件可选）

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
| 学校 `ruc.edu.cn` 邮箱托管在网易，真实生产想用学校邮箱作发件人，需重新生成网易客户端授权码 | 待定 | 当前 demo 用 QQ 邮箱作发件人即可 |

---

## 五、当前分支状态

- 主开发分支：`feat/backend-base`
- 已合并的功能分支：`feat/backend-biz`、`feat/frontend-admin`、`feat/frontend-mp`（通过 PR → dev → main 流程）
- 下一里程碑：`feat/backend-base` 整体合并到 `dev`，dev 联调通过后合 `main` 打 `v1.x` Tag
