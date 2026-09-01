# LumenCS 架构

个人 AI 服务中枢。对外只有 Java API。Python 不暴露公网，只做向量化与检索。博客仓 LightDiary、盯盘侠 PanWatch 都是独立应用，本仓只走 HTTP，不共用库、不 iframe。

## 一句话

浏览器里说话 → Java Supervisor 判断是闲聊、问笔记，还是办事 → 知识走 RAG，办事要么弹确认卡片再调工具，要么直接查（待办列表 / 博客列表 / 行情）。卡片和行情卡会写入消息历史，刷新会话还能看见。

```
浏览器 :8088（Vue + Nginx）
  ├─ /              对话（会话列表、确认卡片、行情/博客嵌入卡、引用）
  ├─ /embed?slug=   只针对一篇博客问答
  ├─ /apps          已上线应用外链（盯盘侠、博客前台）
  ├─ /playbook      可问场景与示例
  └─ /console       中枢控制台（需登录）
        │
        └─ /lumencs-api  →  Java :8090
              ├─ MySQL    会话、消息（含 embed_json / card_json）、知识正文与切块、待办、审核、Span、工具日志
              ├─ Redis    限流、短期/工作/长期记忆、待办单号、博客定时同步开关
              ├─ HTTP     LightDiary 公开/管理 API、PanWatch 行情 API
              └─ rag-service :8100
                    └─ Qdrant   切块向量（不存完整文件）
```

## 模型网关（聊天 ≠ 向量）

| 用途 | 谁调用 | 网关 | 本机 |
| --- | --- | --- | --- |
| 意图 / 闲聊 / RAG 改写重排 / 合规 / 起草改稿 | Java `ChatClient` | DMX | `https://www.dmxapi.com` · `deepseek-v4-flash` |
| 入库与检索的 embedding | Python | 硅基流动 | `BAAI/bge-m3` · 维数 1024 |

博客写入（可选）：中枢已登录 + `BLOG_WRITE_ENABLED` + LightDiary 管理端 JWT。卡片一次性 `confirmToken` 确认后才 POST。不直连博客 MySQL。

## 一次对话怎么走

1. Filter 打 `traceId`；限流（IP + POST body 里的 sessionId）。
2. 短期记忆写入 Redis；工作记忆 / 画像摘要进 Prompt。
3. Supervisor 调意图路由（关键词优先；低于约 0.55 只澄清，不派业务 Agent）。
   - 有未提交卡片时：默认续接上一流程；用户说改稿则继续该流程；说「先不提交 / 换个话题」或点到另一个办事意图则清卡片再走新意图。
4. 按意图：
   - **闲聊** `chitchat`：不检索。
   - **知识** `knowledge_rag`：Query 改写 → Qdrant Top8 → 重排 Top3 → 流式回答 + 引用。向量挂了降级 MySQL 关键词。带 `articleSlug` 时只搜该文。
   - **取消** `workflow_cancel`：清工作记忆里的 pending 卡片，不调工具。
   - **办事**：见下一节。
5. 合规：规则快筛 → LLM；写博客 / 查行情跳过这步。不通过进 HITL，通过/驳回后写回原会话（聊天页轮询历史，不是同一条 SSE）。
6. SSE：`session / step / card / embed / token / message / done`。Agent 写 `cs_span`，工具写 `cs_tool_log`。
7. 落库：助手消息带 `embed_json`（博客列表 / 书签 / 行情卡）和 `card_json`（待办 / 写博客等确认卡）。历史接口解析后给前端，刷新仍能看见。

## 办事流程怎么实现

`WorkflowCatalog` 是流程目录（槽位、提示、对应工具）。`WorkflowAgent` 执行，`McpToolServer` 是工具注册表（MCP **风格**，不是 MCP 协议服务器）。

两种走法：

| 类型 | 意图 | 行为 |
| --- | --- | --- |
| 确认卡 | `memo` / `todo` / `todo_update` / `milk_tea` / `blog_article` / `blog_bookmark` / `blog_tag` | 填槽 → 弹卡 → 用户确认或对话里改稿 → `confirmToken` 一次性消费 → 调工具 |
| 直接查 | `todo_query` / `blog_list` / `blog_bookmarks` / `blog_sync` / `stock_quote` | 不弹确认卡，立刻调工具；结果用 `embed` 卡展示 |

写博客额外一层：`BlogDraftComposer` 先根据对话起草 Markdown，槽位进卡片。卡片未提交时，下一句当**改稿要求**再调 `revise`，覆盖标题/正文后重新发卡。不想发了走取消。

查行情：`StockInsightService` 调盯盘侠；成功后把 `lastStockSymbol` 写入工作记忆。下一句「这只票可以买吗」不再拿整句去搜名称，而是续用上一只代码，用 K 线打分回答（仅供参考）。

工作记忆 Redis Hash `lumencs:working:{sessionId}`（30 分钟）：`intent` / `workflow` / `slots` / `pendingCardId` / `confirmToken` 哈希 / `lastStock*`。`clearWorkflow` 清流程，不清最近股票。

## 知识存在哪

| 位置 | 存什么 |
| --- | --- |
| MySQL `cs_document` | 标题、来源、**完整正文**、状态、切块数 |
| MySQL `cs_chunk` | 切块原文（关键词兜底、点引用展开） |
| MySQL `cs_message` | 对话；`citations_json`、`embed_json`、`card_json` |
| Qdrant | 子段向量；payload 里带父段上下文、source、document_id |

来源：控制台上传 / 粘贴、对话「帮我记一下」、博客公开 API 同步（`source=blog:{slug}`，可用控制台开关关掉定时任务）。切分在 **Java**。Python 只收已经切好的文字做 embedding。

## 知识缺口与「生成 FAQ 草稿」

控制台「知识缺口」收集两类消息：对话里点了「缺口」，以及知识问答**没有引用**的回复。

「生成 FAQ 草稿」= 用 LLM 根据该会话写成一篇 Markdown。**只出现在页面上，请复制后自己贴。** 不会自动写入 LightDiary，也不会自动 ingest。

## 中枢五环（对话结束之后）

| 环 | 做什么 |
| --- | --- |
| HITL 写回 | 审核决定插入原会话消息 |
| 聊这篇 | `/embed?slug=` 按 `document_id` 过滤 |
| 通知 | 事件箱 + Webhook；`event_id` 去重 |
| 待办 SLA | `WAITING_HUMAN` 超时提醒，同一天同一条只一次 |
| 质量飞轮 | CSAT + 无引用 → 缺口列表 → FAQ Markdown（人工粘贴） |

## 控制台

中枢：总览 / 事件 / 知识缺口 / 通知渠道。  
运营：知识库（含博客定时同步开关）/ 待办 / 安全审核 / 追踪 / 记忆 / 工具。

鉴权：双 JWT。对话页和控制台同一账号（`.env` 的 `ADMIN_USERNAME` / `ADMIN_PASSWORD`）。未登录不能聊天，接口返回 401。

## 和外部应用的边界

| 应用 | 读 | 写 |
| --- | --- | --- |
| LightDiary | 公开文章 / 书签 API | 管理端 JWT；须卡片确认；FAQ 草稿不写博客库 |
| PanWatch | 登录后行情 / K 线 / 新闻 | 本仓不代下单、不绑交易 |

本仓不合并这两个仓库，不共用登录，聊天里用嵌入卡，点「打开」才新开它们自己的前端。

## 和 Agent Skill 的关系

SOP 放在 `backend/src/main/resources/skills/*/SKILL.md`（agentskills.io 形态：YAML `name` + `description` + 正文）。`SkillRegistry` 启动时加载。

| 层 | 何时进上下文 | 内容 |
| --- | --- | --- |
| 1 目录 | 意图 LLM 兜底 | 各 Skill 的 intent + description |
| 2 正文 | 命中该意图后 | 完整 SKILL.md，注入起草 / 闲聊 / 知识问答 Prompt |
| 代码 | 始终 | 槽位表、`confirmToken`、待办状态机、K 线打分、HTTP 工具 |

关键词路由也读 Skill 里的 `triggers` / `rules`，改触发词不必改 Java。工具调用仍是 `McpToolServer`，外部系统仍是 HTTP 客户端。

对照文章里的三件套：

| 文章说法 | 本仓 |
| --- | --- |
| MCP | `BlogClient` / `PanWatchClient`（HTTP，不是 MCP 协议） |
| Tool | `McpToolServer` |
| Skill | `resources/skills/*/SKILL.md` + `SkillRegistry` |
