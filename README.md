# LumenCS · 个人 AI 服务中枢

Java 21 编排 + Python RAG sidecar + Vue 3。给自己用的对话中枢：问笔记、记一笔、待办、写博客草稿。可单机 Docker 部署。

| 模块 | 技术 | 职责 |
| --- | --- | --- |
| `backend/` | Spring Boot 3.4 / Java 21 / Spring AI / MyBatis-Plus | Supervisor、SSE、待办状态机、JWT、限流、合规+HITL、办事卡片、MCP 风格工具 |
| `rag-service/` | FastAPI + Qdrant | Embedding、向量写入与检索 |
| `web/` | Vue 3 + Vite + Tailwind | 对话台 + 中枢控制台 |
| 基础设施 | MySQL 8 / Redis 7 / Qdrant | 会话与知识正文、记忆与限流、向量 |

博客 [lightdiary](https://github.com/) 独立仓库。本仓只 HTTP：读公开 API，写管理端 JWT，不直连博客库。

## 能力

- 流式对话：意图（低置信澄清）→ 知识 RAG 或办事卡片 → 工具真调用 → 规则+LLM 合规（不通过进 HITL）
- 会话列表可切换、可删除
- 记一笔进知识库；待办走状态机（控制台「待办」）
- 聊天写博客：先登录中枢，卡片一次性令牌确认，默认存草稿
- RAG：改写 → 向量 Top8 → 重排 Top3 → 引用可点；向量挂了降级关键词
- 知识库：上传/粘贴/博客同步；Java 段落切分；召回测试；MySQL 存文，Qdrant 存向量
- 知识缺口：「生成 FAQ 草稿」只出 Markdown，需人工复制，不自动发博客
- 三层记忆、双 JWT、Redis 限流、SSE、Knife4j
- 点奶茶：唯一保留的演示流程

## 模型网关（聊天 ≠ 向量）

| 用途 | 网关 | 环境变量 | 本机 |
| --- | --- | --- | --- |
| 对话 / 意图 / 改写重排 / 合规 / 闲聊 | [DMX](https://www.dmxapi.com) | `OPENAI_*` · `MODEL_NAME` | `deepseek-v4-flash` |
| 向量 | [硅基流动](https://api.siliconflow.cn) | `EMBEDDING_*` · `EMBEDDING_DIM` | `BAAI/bge-m3` · 1024 |

根地址不要带 `/v1`。架构详见 [docs/architecture.md](docs/architecture.md)，部署见 [docs/deployment.md](docs/deployment.md)，模块讲解见 [docs/学习指南.md](docs/学习指南.md)。

## 本地启动

```bash
cp .env.example .env
# 填 OPENAI_*（DMX）和 EMBEDDING_*（硅基流动）

docker compose up -d --build
```

- 打开 http://localhost:8088 会先登录，账号和控制台相同（`.env` 的 `ADMIN_USERNAME` / `ADMIN_PASSWORD`）
- 控制台：http://localhost:8088/console
- Java：http://localhost:8090/lumencs-api/api/health
- RAG：http://localhost:8100/health

建议：用同一账号登录后，先「帮我记一下：生椰拿铁少糖」，再问「我常喝什么」。

密钥只放 `.env`，不要提交。

MIT License
