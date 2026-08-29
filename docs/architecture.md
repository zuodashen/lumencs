# LumenCS 架构

个人 AI 服务中枢。对外只有 Java API。Python 不暴露公网，只做向量化与检索。

## 一句话

浏览器里说话 → Java Supervisor 判断是闲聊、问笔记，还是办事（记一笔 / 待办 / 写博客 / 演示奶茶）→ 知识走 RAG，办事走确认卡片再调工具。博客仓 LightDiary 独立，只走 HTTP。

```
浏览器 :8088（Vue + Nginx）
  ├─ /              对话（会话列表、卡片、引用）
  ├─ /embed?slug=   只针对一篇博客问答
  └─ /console       中枢控制台（需登录）
        │
        └─ /lumencs-api  →  Java :8090
              ├─ MySQL    会话、消息、知识正文与切块、待办、审核、Span、工具日志、反馈、事件箱
              ├─ Redis    限流、短期/工作/长期记忆、待办单号、通知去重
              └─ rag-service :8100
                    └─ Qdrant   切块向量（不存完整文件）
```

## 模型网关（聊天 ≠ 向量）

| 用途 | 谁调用 | 网关 | 本机 |
| --- | --- | --- | --- |
| 意图 / 闲聊 / RAG 改写重排 / 合规 / 起草 | Java `ChatClient` | DMX | `https://www.dmxapi.com` · `deepseek-v4-flash` |
| 入库与检索的 embedding | Python | 硅基流动 | `BAAI/bge-m3` · 维数 1024 |

博客写入（可选）：中枢已登录 + `BLOG_WRITE_ENABLED` + LightDiary 管理端 JWT。卡片一次性 `confirmToken` 确认后才 POST。不直连博客 MySQL。

## 一次对话怎么走

1. Filter 打 `traceId`；限流（IP + POST body 里的 sessionId）。
2. 短期记忆写入 Redis；工作记忆 / 画像摘要进 Prompt。
3. Supervisor：关键词优先意图；低于约 0.55 只澄清，不派业务 Agent。
4. 按意图：
   - **闲聊** `chitchat`：不检索。
   - **知识** `knowledge_rag`：Query 改写 → Qdrant Top8 → 重排 Top3 → 流式回答 + 引用。向量挂了降级 MySQL 关键词。带 `articleSlug` 时只搜该文。
   - **办事**：弹卡片（`confirmToken` 一次性）→ 工具真调用。
     - `memo` → `memo_save` 写入本仓知识库
     - `todo` / `todo_query` → 待办状态机（表仍是 `cs_ticket`）
     - `milk_tea` → 演示下单（唯一保留的客服式演示）
     - `blog_*` → 未登录直接提示去 `/console/login`，不调起草
5. 合规：规则快筛 → LLM；不通过进 HITL，通过/驳回后写回原会话（聊天页轮询历史，不是同一条 SSE）。
6. SSE：`session / step / card / token / message / done`。Agent 写 `cs_span`，工具写 `cs_tool_log`。

## 知识存在哪

| 位置 | 存什么 |
| --- | --- |
| MySQL `cs_document` | 标题、来源、**完整正文**、状态、切块数 |
| MySQL `cs_chunk` | 切块原文（关键词兜底、点引用展开） |
| Qdrant | 子段向量；payload 里带父段上下文、source、document_id |

来源：控制台上传 / 粘贴、对话「帮我记一下」、博客公开 API 同步（`source=blog:{slug}`）。切分在 **Java**（空行父段 + 短子段检索）。Python 只收已经切好的文字做 embedding。

## 知识缺口与「生成 FAQ 草稿」

控制台「知识缺口」收集两类消息：对话里点了「缺口」，以及知识问答**没有引用**的回复。

「生成 FAQ 草稿」= 用 LLM 根据该会话写成一篇 Markdown（标题 / 问 / 答）。**只出现在页面上，请复制后自己贴进知识库或博客。** 不会自动写入 LightDiary，也不会自动 ingest。

拿不准意图时的澄清菜单不是缺口；这类消息不应拿去生成 FAQ。

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
运营：知识库 / 待办 / 安全审核 / 追踪 / 记忆 / 工具。

鉴权：双 JWT。对话页和控制台同一账号（`.env` 的 `ADMIN_USERNAME` / `ADMIN_PASSWORD`）。未登录不能聊天，接口返回 401。

## 和博客的边界

读：公开文章 API。写：管理端 JWT。FAQ 草稿不写博客库。本仓不合并 lightdiary，不共用登录。
