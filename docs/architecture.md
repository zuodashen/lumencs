# LumenCS 架构

对外只有 Java API。Python 不暴露公网，只提供向量化与检索。

```
浏览器
  └─ Nginx (web:8088)
        ├─ /            Vue SPA
        └─ /api         Java :8090
              ├─ MySQL      会话 / 消息 / 工单 / 文档元数据 / Span / 工具日志 / HITL 审核
              ├─ Redis      限流计数、短期记忆、工作记忆、长期画像、单号自增
              └─ rag-service :8100
                    └─ Qdrant     向量
```

模型网关（OpenAI 兼容，**聊天与向量拆开**）：

- Java `ChatClient` → DMX `https://www.dmxapi.com`，模型 `deepseek-v4-flash`（意图、闲聊、RAG 改写/重排、合规、博客起草）
- Python `rag-service` → 硅基流动 `https://api.siliconflow.cn`，模型 `BAAI/bge-m3`，Qdrant 维数 1024
- 博客写入（可选）→ 需中枢 `ROLE_ADMIN` + LightDiary 管理 API JWT（`BLOG_WRITE_ENABLED`，一次性 confirmToken 确认后才 POST）

编排：`Supervisor → IntentRouter(置信度) → (Workflow卡片 | KnowledgeRAG | 闲聊) → Compliance(规则+LLM) → Synthesize`。

一次访客提问：

1. TraceFilter 生成 `traceId`，贯穿日志与 `R`。
2. 限流（IP + session，Redis 固定窗口），超限 429。
3. 写入短期记忆（Redis List + TTL），读回最近 N 轮进 Prompt；工作记忆与长期画像摘要也注入 Prompt。
4. Supervisor：意图路由（关键词优先，未命中调 LLM 输出 intent+confidence）→ 置信度低于阈值先澄清，不派发业务 Agent。
5. 按意图：
   - 知识：LLM Query 改写（可关）→ Python/Qdrant 向量 Top8 → LLM 重排 Top3 → 生成 + 引用（可点展开原文）；sidecar 超时/失败降级关键词检索
   - 工单/奶茶等办事：抽槽位 → 办事卡片（一次性 confirmToken）→ MCP 工具真调用（`ticket_create` / `ticket_query` / `kb_search` / `blog_search` / `blog_get` / `tea_order`）
   - 写博客/书签/标签：未登录直接回登录提示，不调起草模型；已登录才出确认卡片
   - 安全举报：固定话术 + 紧急工单
6. 合规：规则快筛（敏感词 + PII 正则，命中直接拦截）→ 通过后 LLM 深审 → 不通过进入 HITL 收件箱（`cs_review`），不直接回复。
7. 工单：状态机（CREATED → PROCESSING → WAITING_HUMAN → RESOLVED → CLOSED，可 ESCALATED）+ `@Transactional` + Redis 日自增单号（分布式锁防并发撞号）。
8. SSE：`session / step / card / token / message / done`；Agent 调用写 `cs_span`，MCP 调用写 `cs_tool_log`。
9. 控制台：总览 / 知识库 / 工单（状态机流转）/ 审核收件箱（HITL）/ 追踪回放 / 记忆快照 / MCP 工具与日志。

鉴权：双 JWT（access 30min + refresh 7 天），前端 401 自动续期；`/api/admin/**` 需登录。
