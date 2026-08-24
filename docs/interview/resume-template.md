# 简历项目经历模板 — LumenCS 智能客服多Agent系统

> 本模板只写**代码里真实存在**的能力。所有数字都必须来自真实压测/监控；
> 没有数据的指标一律不写（参考《技术方案》"简历可写 vs 禁止写"）。
> 采用 STAR 法则；每个 Action 都要能对应到具体文件/类，经得起追问。

---

## 版本：Java 后端 / AI 应用 方向（通用）

### 智能客服多Agent系统（LumenCS）| 独立开发 | 2025 - 2026

**项目背景**：面向金融/电商客服场景的智能客服工作台，Java 21 + Spring Boot 3.4 + Spring AI 编排主链路，Python FastAPI + Qdrant 做 RAG sidecar，Vue 3 单页控制台，Docker Compose 单机可部署。

**核心职责**：
- 设计并实现 **Supervisor 编排**的多 Agent 架构：意图路由（LLM 结构化输出 **intent + confidence**，低于阈值**先澄清**不派发）→ 办事流程卡片 / 知识 RAG → **规则 + LLM 两阶段合规**（不通过进 **HITL 收件箱**）→ 汇总；全程 **SSE 时间线**（session/step/card/token/message/done）逐字输出
- 实现完整 **RAG 链路**：LLM Query 改写（可配置开关）→ sidecar 向量检索 **Top8** → LLM **重排 Top3** → 上下文注入生成 + **引用可点**（点击拉取原文）；sidecar 超时/失败自动**降级关键词检索**
- 构建**三层记忆**（Redis）：工作记忆 Hash（办事槽位/待提交卡片）、短期记忆 List（20 轮窗口，注入 Prompt）、长期画像（口味/工位跨会话预填），三层摘要汇入 Prompt
- 实现 **MCP 风格工具注册表**并**真实调用**：`ticket_create` / `ticket_query` / `kb_search` / `blog_search` / `tea_order`，调用参数与结果落库 `cs_tool_log`，控制台可查
- 工单领域：**状态机**（CREATED→PROCESSING→WAITING_HUMAN→RESOLVED→CLOSED，可 ESCALATED）+ `@Transactional` + **Redis 日自增单号 + 分布式锁**（SET NX EX + Lua）防并发撞号
- 工程化：统一响应 `R{state,msg,data,traceId}` + 全局异常 + **DTO/VO 分层**（禁止 Entity 出参）+ **PageWrapper 分页** + **审计字段自动填充**（MetaObjectHandler）；**双 JWT**（access 30min + refresh 7 天，前端 401 自动续期）；**Redis 限流**（IP + session 固定窗口，超限 429）；traceId 贯穿日志与响应头；博客公开 API **定时同步**（cron 可配）进知识库
- 可观测：每次 Agent / 工具调用写 `cs_span` / `cs_tool_log`，控制台按会话回放链路

**技术栈**：Java 21 / Spring Boot 3.4 / Spring AI / MyBatis-Plus / MySQL 8 / Redis 7 / FastAPI + Qdrant / Vue 3 / Docker Compose / Knife4j(OpenAPI)

**项目成果**（只写可证明的）：
- 全链路可运行：Docker Compose 一键部署（MySQL / Redis / Qdrant / rag-service / backend / web）
- 演示闭环：点奶茶办事卡片（槽位抽取 + 长期画像预填）→ MCP 真下单出单号；合规拦截 / HITL 审核单可查可处理
- 工程完整度：统一响应、DTO/VO、分页、审计、双 token、限流、状态机、分布式锁、降级、日志链路均已落地并有对应接口/页面

---

## 简历写作要点

### 动词选择（强动词优先）
- 用 "设计并实现" 而非 "参与"
- 用 "搭建" 而非 "协助搭建"
- 用 "优化...提升X%" 而非 "改善了性能"（**但 X 必须真实**）

### 量化原则
- **有数据才写数字**：压测 QPS/P99、真实准确率、线上指标。本项目目前没有生产数据，不要编造
- 没有数字时，用"可证明的完整度"表述：落地了哪些机制、有多少接口/页面、演示闭环是什么

### 技术栈排列（按面试岗位调整顺序）
- Java 岗：Spring Boot / Spring AI / MyBatis-Plus / Redis / MySQL 放前面
- AI 应用岗：Supervisor 编排 / RAG(改写-检索-重排-引用) / 合规+HITL / 记忆 放前面

### 禁止写（代码里不存在）
- 日均 10 万、QPS 500、FCR/CSAT、Token 节省百分比等任何无量测依据的数字
- Milvus、OpenTelemetry、LangGraph、Eino、JSON-RPC 2.0 协议、ReadWriteLock 高并发工单存储
- Flyway（本项目建表为手工 SQL，未集成）

---

## 面试时怎么讲（30 秒电梯版）

> 我独立做了一套智能客服多 Agent 系统：Java 编排 Supervisor 把"意图识别 → 办事流程/知识 RAG → 合规审查 → 汇总"串起来，SSE 推时间线。三个亮点：一是 RAG 完整链路（LLM 改写 → 向量 Top8 → 重排 Top3 → 引用可点，sidecar 挂了降级关键词）；二是 Agent 不是演示壳——工具真实调工单/知识/博客 API，合规不过进 HITL 人工审核收件箱；三是工程化完整——统一响应、DTO/VO、分页、审计字段、双 token 续期、Redis 限流、工单状态机+分布式锁单号，全都能在控制台演示。
