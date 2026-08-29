# LumenCS · 微光客服工作台

Java 21 编排主链路 + Python RAG sidecar + Vue 3 单页控制台。面向金融 / 电商客服演示，可部署到单机服务器。

| 模块 | 技术 | 职责 |
| --- | --- | --- |
| `backend/` | Spring Boot 3.4 / Java 21 / Spring AI / MyBatis-Plus | Supervisor 编排、SSE、工单状态机、JWT(双 token)、限流、合规+HITL、办事卡片、MCP |
| `rag-service/` | FastAPI + Qdrant | Embedding、向量写入与检索 |
| `web/` | Vue 3 + Vite + Tailwind | 访客聊天台 + 运营控制台 |
| 基础设施 | MySQL 8 / Redis 7 / Qdrant | 会话落库、限流计数、短期/工作/长期记忆、向量库 |

后端分层（对齐脚手架）：`controller / service / mapper / model.{entity,dto,vo}`，DTO 入、VO 出、PageWrapper 分页、审计字段自动填充；Agent 编排层独立于 MVC（`agent / compliance / rag / memory / lock / ratelimit / tracing / modules.*`）。

博客项目 lightdiary 保持独立仓库，本仓先不改博客代码。LumenCS 侧已预留 `/embed`、博客同步与 `blog_search`。

## 能力

- 流式聊天：意图路由（**带置信度，低则澄清**）→ 知识 RAG 或 **办事流程卡片** → MCP 工具真调用 → 规则+**LLM 合规**（不通过进 **HITL 收件箱**）→ 汇总
- 聊天写博客：需先登录中枢；模型起草 Markdown，卡片一次性令牌确认后再调 LightDiary 管理 API（默认草稿）
- 知识问答 **SSE token 逐字输出**（网关不支持流式则回退整段）
- **RAG 完整链路**：LLM Query 改写 → Python/Qdrant 向量 Top8 → LLM 重排 Top3 → 生成 + **引用可点**（点击展开原文）；向量服务不可用时降级关键词检索
- 办事卡片：加班点奶茶；再次「再来一杯」用长期画像预填
- 三层记忆：工作槽位 / 短期对话 / 长期画像（口味预填）+ 知识库；工作记忆与画像摘要注入 Prompt
- **工单状态机**（CREATED → PROCESSING → WAITING_HUMAN → RESOLVED → CLOSED，可 ESCALATED）+ `@Transactional` + **Redis 日自增单号 + 分布式锁**防并发撞号
- **Redis 限流**（IP + session 双维度，固定窗口，超限 429）；**双 JWT**（access 30min + refresh 7 天，前端 401 自动续期）
- **博客定时同步**（默认每 6 小时）+ 控制台手动触发
- 每次 Agent 调用与 **MCP 工具调用写入 `cs_span` / `cs_tool_log`**，控制台可回放与查看
- 统一响应 `R{state,msg,data,traceId}`；traceId 贯穿日志与响应头；context-path `/lumencs-api`
- OpenAPI：Knife4j `/doc.html` 与 springdoc `/swagger-ui.html`

## 模型网关（聊天 ≠ 向量）

两套 OpenAI 兼容接口，**不要混用一把 Key**：

| 用途 | 网关 | 环境变量 | 本机当前值 |
| --- | --- | --- | --- |
| 聊天 / 意图 / RAG 改写与重排 / 合规 / 闲聊 | [DMX API](https://www.dmxapi.com) 国际站 | `OPENAI_API_KEY` · `OPENAI_BASE_URL` · `MODEL_NAME` | `https://www.dmxapi.com` + `deepseek-v4-flash` |
| 向量化（入库 + 检索） | [硅基流动](https://api.siliconflow.cn) | `EMBEDDING_API_KEY` · `EMBEDDING_BASE_URL` · `EMBEDDING_MODEL` · `EMBEDDING_DIM` | `https://api.siliconflow.cn` + `BAAI/bge-m3`（1024 维） |

Java 只打聊天网关；Python `rag-service` 只打向量网关。根地址都 **不要带 `/v1`**。DMX 令牌必须和站点配套（Global Key ↔ `.com`，国内站 ↔ `.cn`）；`MODEL_NAME` 必须是该 Key 允许的模型。换 embedding 维度后要在控制台重新向量化。验收：`GET http://localhost:8100/health` 应看到 `"embedding_model":"BAAI/bge-m3","dim":1024`。

## 本地启动

需要：Docker、一份按上表填好的 `.env`（聊天一把 Key、向量一把 Key）。

本机 `mvn` 需要 **JDK 21**。若机器上只有 JDK 8，请使用 `docker compose up --build`。

**已有 OrbStack / Docker 数据卷怎么增量更新、表怎么补、如何验收**：见 [docs/deployment.md](docs/deployment.md)。

```bash
cp .env.example .env
# 填 OPENAI_*（DMX）和 EMBEDDING_*（硅基流动）；根地址不要带 /v1

docker compose up -d --build
```

访问：

- 聊天台：http://localhost:8088
- 嵌入页（给博客 iframe）：http://localhost:8088/embed
- 控制台：http://localhost:8088/console/login （`admin` / `lumen123`）
- Java API：http://localhost:8090/lumencs-api/api/health
- OpenAPI：http://localhost:8090/lumencs-api/doc.html （Knife4j）
- RAG：http://localhost:8100/health

演示建议：先「帮我记一下：生椰拿铁少糖」写入知识库，再问「我常喝什么」。点奶茶是保留的演示流程。写博客请先登录控制台。

建表：Docker 首次启动 MySQL 会自动执行 `deploy/sql/schema.sql`；已有库以你手工执行为准（**不集成 Flyway**）。`cs_review`（HITL）、`cs_tool_log`（工具日志）为新增表，升级时请执行增量建表语句。

可选：`.env` 里配置 `BLOG_BASE_URL=http://localhost:8081/lightdiary-api` 后，知识库页可「从博客同步」，且每 6 小时自动同步一次（`BLOG_SYNC_CRON` 可改）。

## 目录

```
backend/          Java 主服务
rag-service/      Python 检索服务
web/              Vue 3 SPA
deploy/sql/       MySQL 建表脚本
docs/             架构与部署
```

## 安全

密钥只放 `.env`，不要提交。生产务必修改 `JWT_SECRET`、`ADMIN_PASSWORD`、数据库密码。

MIT License
