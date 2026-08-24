# LumenCS V1.0 技术方案

> 目标：把当前 P0 骨架打磨成完整、可运行、可维护的个人项目。  
> Java 编码习惯对齐脚手架（分层、统一响应、异常、DTO/VO、Knife4j），**不照搬** Nacos / Feign / 数据权限 / 多数据源 / Flyway。SQL 手工执行 `deploy/sql/schema.sql`。  
> 与博客 `lightdiary`：**产品串联、工程分离**。

---

## 一、背景与目标

### 现状

V1.0 已落地：聊天编排、Qdrant RAG、工单落库、SSE 时间线、双 JWT、限流、意图置信度 + 澄清、规则 + LLM 合规 + HITL 收件箱、工单状态机 + Redis 单号锁、RAG LLM 改写/重排、工具真调用与日志入库、博客定时同步、Knife4j。

仍待打磨：

- DTO/VO 已覆盖全部出参接口（工单/审核/知识/会话/消息/追踪）
- 分页已覆盖工单/审核/知识列表；其余列表按需扩展
- 无 Flyway（保持手工 SQL，见 3.2「不采纳」）

### 目标

1. **Java 后端**：按脚手架习惯重组代码，覆盖企业开发常见能力（统一响应、审计字段、分页、事务状态机、缓存/限流、OpenAPI、链路 traceId）。
2. **Agent**：Supervisor 编排完整化，RAG / Tool / 合规 / 记忆 / 可观测全部可演示。
3. **博客串联**：实验室入口 + 站内助手 + 已发布文章进知识库，**不合并仓库、不共享登录**。

不写伪造 QPS / FCR。只写代码里真实存在的能力。

---

## 二、名词解释

| 名称 | 解释 |
| --- | --- |
| Supervisor | 中央编排 Agent，只调度不直接做业务 |
| HITL | Human-in-the-Loop，高风险回复进入人工审核队列 |
| sidecar | Python RAG 进程，仅内网，Java 通过 HTTP 调用 |
| 公开文章 API | lightdiary `GET /lightdiary-api/api/articles`、`GET /api/articles/{slug}` |
| 实验室 | lightdiary 微光实验室 `blog_innovation.demoUrl` |

---

## 三、整体方案

### 3.1 模块关系

```
lightdiary（已有，JDK8 / Boot 2.7）          LumenCS（本仓，JDK21 / Boot 3.4）
  前台 / 后台 / 实验室卡片                      Vue SPA：聊天台 + 控制台
  公开 API：文章列表/详情                       Java API：编排 / 工单 / 知识 / HITL
        │  HTTP 拉取已发布文章                        │
        └──────────── 知识同步 / 检索工具 ────────────┘
        │  实验室 demoUrl / 悬浮助手 iframe
        └──────────── 只跳转或调聊天 API ─────────────┘
```

依赖方向：**LumenCS 依赖博客的公开只读 API**；博客不依赖 LumenCS 代码。博客侧只加：实验室一条记录 + 可选悬浮组件。

### 3.2 从 脚手架 采纳 / 不采纳

| 采纳 | 不采纳（个人项目过重或与 JDK21 栈冲突） |
| --- | --- |
| controller / service / impl / mapper / model.{entity,dto,vo} | Nacos、bootstrap.yml |
| `R<T>`（state / data / msg / traceId / path） | Feign、多数据源、数据权限 |
| `BizException` + `ExceptionCode` + 全局异常 | 开放平台签名（可后置） |
| SuperEntity 审计字段 | Fastjson、JDK8 API |
| DTO 入、VO 出，禁止 Entity 出参 | 动态数据源切换 |
| `@Validated` + `@Valid`；GET query / POST body | mall-tiny 动态权限整套 |
| ServiceImpl + IService | |
| 手工 SQL：`deploy/sql/schema.sql` | Flyway 自动迁移 |
| Knife4j / OpenAPI | |
| PageWrapper 分页 | |
| TraceFilter + MDC traceId | |
| `@RequiredArgsConstructor` + `@Slf4j` | |
| 业务代码少 try-catch，抛 BizException | |

### 3.3 包结构（已落地，对齐脚手架 扁平分层）

```
com.lumencs
  LumenCsApplication
  controller/             @RestController（DTO入、VO出）
  service/                领域服务（工单/知识/审核/聊天/认证/博客同步）
  mapper/                 MyBatis-Plus Mapper（9 个）
  model/
    entity/               表实体（含 TicketStatus 状态机枚举）
    dto/                  入参（LoginRequest/ChatRequest/TicketQueryDTO/ReviewDecideRequest…）
    vo/                   出参（TicketVO/ReviewVO/DocumentVO）
  common/                 R, PageWrapper, PageDto, SuperEntity, ApiResponse, TraceContext
  exception/              BizException, ExceptionCode, GlobalExceptionHandler
  config/                 Security, Knife4j, MybatisMetaHandler, MybatisPlusConfig, TraceFilter…
  security/               JwtService, JwtAuthFilter
  agent/                  Supervisor / IntentRouter / KnowledgeRAG 等编排 Agent
  compliance/             ComplianceCheckerAgent（规则+LLM 合规）
  rag/                    RagClient + RagHit（sidecar 客户端）
  memory/                 工作/短期/长期记忆服务
  lock/                   RedisLockService
  ratelimit/              RateLimitService + RateLimitInterceptor
  tracing/                AgentTracer
  modules/
    workflow/             办事流程（WorkflowAgent / WorkflowCatalog）
    mcp/                  MCP 工具（McpToolServer / BlogClient）
    blogsync/             博客定时同步（BlogSyncScheduler）
```

`context-path`：`/lumencs-api`（对齐脚手架「以 -api 结尾」）。前端代理同步改。

---

## 四、核心逻辑（重点）

一次访客提问：

1. TraceFilter 生成 `traceId`，贯穿日志与 `R`。
2. 限流（IP + session）；超限直接 `R.fail`。
3. 写入短期记忆（Redis List + TTL），**读回最近 N 轮进 Prompt**。
4. Supervisor：Intent（标签 + 置信度 + 实体）→ 低于阈值转 HITL/澄清。
5. 按意图：
   - 知识：Query 改写 → Python/Qdrant Top8 → 重排 Top3 → 生成 + 引用
   - 工单：抽字段 → 分布式锁生成单号 → 事务落库 → 状态机
   - 博客相关：Tool `blog.search` / `blog.get_article` 调 lightdiary 公开 API
   - 安全举报：固定话术 + 建紧急工单
6. 合规：规则快筛 →（非 critical）LLM 深审 → 不通过进 HITL 队列，不直接回复。
7. SSE：`step` / `token`(可选) / `message` / `done`；Span 落库。
8. 控制台可回放该 `traceId`/`sessionId`。

---

## 五、模块功能设计

### 5.1 基础框架（Java 基本功）

| 功能 | 设计 |
| --- | --- |
| 统一响应 | `R.success(data)` / `R.fail(code,msg)`，带 path、timestamp、traceId |
| 异常 | 业务只抛 `BizException`；参数校验进 400；系统异常 500 不把堆栈给前端 |
| 审计 | SuperEntity：createTime/createUser/updateTime/updateUser，MetaObjectHandler 填充 |
| 分页 | Query DTO 继承 PageDto；返回 PageWrapper |
| 文档 | Knife4j，`/doc.html`；Controller `@Tag` + `@Operation` |
| 建表 | 手工执行 `deploy/sql/schema.sql`，**不集成 Flyway** |
| 鉴权 | 简单 JWT（admin 账号密码），有效期 72h。**不做** TokenGranter / refresh 全家桶 |
| 可观测 | TraceId；Micrometer `/actuator/prometheus`；Agent Span 表 |

### 5.2 工单（事务 + 状态机）

状态：`CREATED → PROCESSING → WAITING_HUMAN → RESOLVED → CLOSED`，允许 `ESCALATED`。

- 单号：`TK-yyyyMMdd-XXXX`，Redis 日自增 + 分布式锁，避免并发撞号
- `@Transactional(rollbackFor = Exception.class)`
- 非法流转抛 BizException
- 控制台分页 + 状态筛选（DTO/VO）

### 5.3 知识库 + RAG（Agent 核心）

| 步骤 | 谁做 |
| --- | --- |
| 切分 512/80 | Java |
| 元数据 MySQL | Java |
| embedding + Qdrant upsert/search | Python sidecar |
| Query 改写、LLM 重排 | Java ChatClient（可配置关闭以省 Token） |
| 关键词兜底 | Java，sidecar 超时/5xx 触发 |
| 引用 | chunkId/source/snippet 写入消息表，前端可点 |

文档来源枚举：`MANUAL`（控制台上传）、`BLOG`（同步）、`SEED`。

### 5.4 工具层（体现 Tool Use，不是 HashMap 假调用）

Java 维护 Tool Registry（MCP 风格：name / description / JSON Schema / handler）：

| 工具 | 行为 |
| --- | --- |
| `ticket_create` / `ticket_query` | 调工单领域服务 |
| `kb_search` | 调知识检索 |
| `blog_search` | HTTP GET lightdiary `/api/articles` |
| `blog_get` | HTTP GET `/api/articles/{slug}` |
| `order_query` | Mock 订单（标注为演示数据源） |
| `risk_check` | 规则：金额阈值 → 是否送审 |

Agent 通过 Spring AI Function/ToolCallback 选择调用，调用日志入库，控制台可见。

### 5.5 合规 + HITL

1. 规则：敏感词、PII 正则（毫秒）
2. LLM JSON 审查：越权承诺、歧视、无风险提示
3. critical 规则命中：不调用 LLM，直接拦截
4. 不通过：`cs_review` 待审；访客看到「已转审核」；管理员通过/改写/驳回后才成为对用户可见回复
5. 脱敏结果写回，不再出现「mask 了但原文发出」

### 5.6 记忆（必须能演示）

| 层 | 实现 | 怎么讲 / 怎么看 |
| --- | --- | --- |
| 工作记忆 | Redis Hash `lumencs:working:{sessionId}` | 当前办事流程、槽位、pendingCardId。控制台「记忆」页实时快照 |
| 短期 | Redis List TTL 30min / 20 轮 | 写入后读回 RAG Prompt |
| 长期 | Qdrant + MySQL 文档（含博客同步） | 跨会话复用；记忆页展示文档数 |

工作记忆不是注释：卡片提交会 `mergeSlots`，工具跑完 `clearWorkflow`。奶茶下单成功后写入长期画像；下次「再来一杯」预填卡片，仍需用户确认（借鉴记忆预填，不做校园 L4 群体记忆）。

意图路由：**关键词优先**，未命中再调 LLM；卡片未提交时短句（「大杯」）不打断流程；「收益多少」等问句才切回知识问答。

知识问答：短追问拼上上一轮问句再检索；模型输出走 SSE `token` 增量，网关不支持则回退整段。

### 5.6.1 意图 → 办事流程 → MCP（点奶茶式卡片）

不是「意图后一次 LLM 结束」。流程：

```
意图识别（milk_tea / refund / account_open / ticket_query / complaint / knowledge_rag / compliance_checker）
        │
        ├─ 知识 / 安全：走 RAG 或固定话术
        └─ 办事：WorkflowCatalog 取槽位定义
              ├─ 槽位不全 → SSE event: card（表单 + 选项按钮）
              │              前端可点击选项，也可手输
              │              POST /api/chat/card 回灌工作记忆
              └─ 槽位齐全 → MCP 真调用 tea_order / ticket_create / ticket_query
```

明星演示「工位奶茶局」：加班改 bug 口渴 → 点选饮品/杯型/甜度/冰量/小料，手填杯数和工位号 → `tea_order` 出单号与金额。退款/开户走同一套槽位引擎，只是目录不同。

### 5.7 与博客串联（三层；本仓先做齐，博客仓暂不改）

**L1** 实验室 demoUrl：博客后台以后填 `http://localhost:8088`。LumenCS 无代码依赖。
**L2** 本仓已提供 `/embed` 精简聊天页，博客以后 iframe 即可。
**L3** 配置 `BLOG_BASE_URL` 后：控制台「从博客同步」+ 工具 `blog_search` + RAG 附加博客命中。博客仓零改动。

**L1 入口（博客改动最小）**

- 实验室新增一条 Innovation：`title=LumenCS`，`status=LIVE`，`demoUrl=https://cs.域名/` 或本地 `http://localhost:8088`
- 不改博客后端

**L2 站内助手（博客前台小改）**

- `blog-web` 增加悬浮按钮，iframe 打开 LumenCS `/embed`（聊天精简页，CORS 白名单博客域名）
- 或博客调 `POST {lumencs}/lumencs-api/api/chat`（公开聊天接口 + 限流）
- 用户体系仍分离：博客游客 vs LumenCS sessionId

**L3 知识打通（只改 LumenCS）**

- 配置 `lumencs.blog.base-url=http://博客/lightdiary-api`
- 定时任务 / 控制台按钮：拉 `GET /api/articles` 分页，再拉详情，写入 `source=BLOG` 文档并向量化
- 增量：用 slug + updateTime；文章下架（拉不到）则知识库标记 `DISABLED` 并删 Qdrant 点
- Tool `blog_search` 实时查博客（最新列表），与已同步向量互为补充

**明确不做**

- 合并 Git 仓库
- 共用 ums JWT / 动态权限
- 把 Spring Boot 版本互相升级绑架

### 5.8 前端（保持单 SPA）

| 路由 | 功能 |
| --- | --- |
| `/` | 聊天 + 时间线 + 引用 + **办事卡片** |
| `/embed` | 给博客 iframe 的瘦身聊天（博客仓后置接入） |
| `/console/login` | 简单账号密码 JWT，**不照搬** granter / refresh |
| `/console/memory` | 三层记忆快照 |
| `/console/tools` | MCP 工具列表与调用日志 |
| `/console/overview` | 健康、工单数、sidecar 状态 |
| `/console/knowledge` | 上传 / 列表 / 从博客同步 |
| `/console/tickets` | 工单状态 |
| `/console/traces` | Span 回放 |

接口全部走 `R`；分页走 PageWrapper。前端 axios 按 `state===200` 判断。

---

## 六、接口清单（对齐脚手架约定）

前缀：`/lumencs-api`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/login` | 简单登录 username/password（不照搬 TokenGranter） |
| GET | `/api/health` | 健康检查 |
| POST | `/api/chat` | SSE 聊天（公开） |
| POST | `/api/chat/card` | SSE 提交办事卡片槽位 |
| GET | `/api/admin/memory` | 三层记忆快照 |
| GET | `/api/admin/tools` | MCP 工具与调用日志 |
| POST | `/api/admin/blog/sync` | 拉取博客公开文章写入知识库 |
| GET | `/api/chat/{sessionId}/messages` | 历史 |
| GET | `/admin/knowledge/pageData` | 知识分页 |
| POST | `/admin/knowledge/create` | 上传文档 |
| POST | `/admin/knowledge/delete` | 删除 |
| GET | `/admin/tickets/pageData` | 工单分页 |
| POST | `/admin/tickets/modify` | 改状态 |
| GET | `/admin/reviews/pageData` | HITL 列表 |
| POST | `/admin/reviews/decide` | 通过/改写/驳回 |
| GET | `/admin/traces/pageData` | 追踪 |
| POST | `/admin/blog/sync` | 触发博客同步 |
| GET | `/admin/tools/logs` | 工具调用日志 |

GET = query；POST = JSON body。

---

## 七、数据模型（增量）

在现有表上补齐 SuperEntity 字段；新增：

- `cs_review`：待审回复、原内容、违规项、审核人、决定
- `cs_tool_log`：toolName、arguments、result、duration、success
- `cs_blog_sync`：slug、articleId、lastSyncTime、status
- Token 统计可挂在 `cs_span.detail_json` 或独立 `cs_llm_usage`

---

## 八、实施节奏

| Sprint | 内容 | 状态 |
| --- | --- | --- |
| S1 打磨骨架 | R / 异常 / context-path `/lumencs-api` / traceId；SQL 手工执行 | 已落地 |
| 记忆 + MCP + 卡片办事 | 三层记忆页、Tool 真调用、点奶茶式卡片 | 已落地可演示 |
| 博客串联（不改博客仓） | L3 同步/工具（含定时任务）；L2 `/embed`；L1 由博客后台后配 demoUrl | 已落地可演示 |
| 工单状态机 + 锁 + 事务 | TicketStatus 状态机、`@Transactional`、Redis 日自增单号 + 分布式锁 | 已落地 |
| 限流 | Redis 固定窗口（IP + session），超限 429 | 已落地 |
| 意图置信度 + 澄清 | LLM 结构化输出 intent+confidence，低于阈值澄清 | 已落地 |
| 合规二阶段 + HITL | 规则快筛 + LLM 深审，不通过进 `cs_review` 收件箱 | 已落地 |
| RAG 改写 + 重排 | LLM Query 改写（可关）、向量 Top8、LLM 重排 Top3、引用可点 | 已落地 |
| 双 JWT + 前端续期 | access 30min / refresh 7 天，401 自动刷新 | 已落地 |
| 工具日志落库 | `cs_tool_log` 持久化，控制台可查 | 已落地 |
| 包结构分层 | controller / service / mapper / model.{entity,dto,vo} 扁平分层（对齐脚手架），DTO/VO + 分页覆盖工单/审核/知识 | 已落地 |
| 检查收尾 | 会话/消息/追踪补 VO（消灭 Entity 直出）、死代码清理、单元测试（状态机 6 例 + 切分器 4 例） | 已落地 |
| 后置（未做） | Service 接口 + IService 风格、更全面的自动化测试 | 未做 |

---

## 九、能力边界（诚实口径）

**可以陈述（已落地）：** 多 Agent Supervisor（意图置信度 + 低置信度澄清）；RAG 完整链路（LLM 改写 → 向量 Top8 → LLM 重排 Top3 → 引用可点）；规则 + LLM 合规 + HITL 收件箱；工单状态机 + 事务 + Redis 分布式锁单号；Redis 限流（IP + session）；双 JWT Refresh Token；SSE 时间线；Java 编排 + Python 检索拆分与降级；槽位办事卡片（工位奶茶 / 退款）；与个人博客公开 API 的知识同步（含定时）与工具调用；工具调用日志入库；OpenAPI（Knife4j / springdoc）+ 统一响应 + traceId。

**避免虚构：** 日均 10 万、QPS 500、Milvus 集群、Spring AI Alibaba、Eino、网关鉴权微服务、与博客「统一用户中心」、以及任何没有压测/监控数据的量化指标（FCR/CSAT/Token 节省百分比等）。
