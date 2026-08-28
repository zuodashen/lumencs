# 更新与部署

本文说明 LumenCS 在本机 **OrbStack / Docker Compose** 下如何首次部署、如何在已有数据卷上增量更新，以及这次「客服工作台 → 个人 AI 服务中枢」实际改了什么。

日常一条命令即可：

```bash
# 在仓库根目录，确保已有 .env
docker compose up -d --build
```

OrbStack 会把 `docker compose` 项目显示成一组容器，项目名默认是目录名 **`lumencs`**。

---

## 1. 这次更新了什么

代码侧（需重建镜像）：

| 层 | 改动 | 对应镜像 |
| --- | --- | --- |
| Java 后端 | HITL 写回会话、单文 RAG、通知幂等、SLA 扫描、CSAT / 知识缺口 / FAQ 草稿、启动补表 | `lumencs-backend` |
| Python RAG | 检索支持 `document_id` 过滤（聊这篇） | `lumencs-rag-service` |
| Vue 前端 | 中枢视觉与信息架构、事件 / 缺口 / 渠道页、聊天 CSAT 与 HITL 轮询 | `lumencs-web` |
| Compose | backend / rag 读取 `.env`；透传 SLA、博客同步 cron | 不单独镜像 |

数据侧（**不清库**）：

- 新表：`cs_feedback`、`cs_inbox`、`cs_notify_channel`、`cs_notify_log`
- 已有 MySQL 卷 **不会** 重跑 `schema.sql`（init 只在空库第一次启动时执行）
- 后端启动类 `HubSchemaBootstrap` 会 `CREATE TABLE IF NOT EXISTS` 补齐上表
- 也可手工执行 `deploy/sql/migrate_hub.sql`

基础设施镜像（mysql / redis / qdrant）这次不用换版本，原容器拉起即可。

---

## 2. 运行时拓扑

```
浏览器
  └─ :8088  web (nginx)
        ├─ 静态 Vue SPA
        └─ /lumencs-api/*  反代 → backend:8090   （SSE 关闭缓冲）

backend :8090
  ├─ mysql:3306     会话 / 工单 / 审核 / 中枢表
  ├─ redis:6379     记忆 / 限流 / 锁 / 通知去重
  └─ rag-service:8100
        └─ qdrant:6333   向量库（带 document_id）
```

宿主机默认端口：

| 服务 | 宿主机 | 容器内 |
| --- | --- | --- |
| 前端 | 8088 | 80 |
| Java | 8090 | 8090 |
| RAG | 8100 | 8100 |
| MySQL | 3306 | 3306 |
| Redis | 6379（若被占用，Compose 可能映到如 16379） | 6379 |
| Qdrant | 6333 | 6333 |

容器之间走 **服务名**（`mysql` / `redis` / `rag-service`），不要用 `localhost`。`.env` 里的 `MYSQL_HOST=localhost` 只给本机直接跑 Java 用；Compose 的 `environment` 会覆盖成 `mysql`。

### 模型网关（本机已拆开）

聊天和向量是两家 OpenAI 兼容网关，由 `.env` 注入容器，**密钥不要写进 compose 文件、不要提交 git**。

| 用途 | 进程 | 网关 | 变量 | 本机当前值 |
| --- | --- | --- | --- | --- |
| Chat Completions | `backend`（Spring AI） | DMX 国际站 | `OPENAI_*` · `MODEL_NAME` | `https://www.dmxapi.com` · `deepseek-v4-flash` |
| Embeddings | `rag-service` | 硅基流动 | `EMBEDDING_*` | `https://api.siliconflow.cn` · `BAAI/bge-m3` · `EMBEDDING_DIM=1024` |

核对：

```bash
curl -s http://localhost:8100/health
# 期望含 "embedding_model":"BAAI/bge-m3","dim":1024
```

DMX：Global 控制台的 Key 必须配 `https://www.dmxapi.com`（国内站 Key 配 `.cn`），填错站会 401「无效的令牌」。Key 若限制了模型列表，`MODEL_NAME` 必须在其中。换 embedding 维度后控制台点「重新向量化已有文档」。

持久卷：

| 卷 | 内容 | 更新时 |
| --- | --- | --- |
| `lumencs_mysql_data` | 全部业务表 | **保留**，会话 / 工单 / 账号都在这里 |
| `lumencs_qdrant_data` | 向量点 | **保留**；单文过滤依赖已有 `document_id`。博客正文要重新同步才会按全文切分 |

---

## 3. 首次部署

本机需要：OrbStack（或 Docker Desktop）、仓库根目录的 `.env`。

**不需要本机 JDK 21。** 后端在镜像里用 `maven:3.9-eclipse-temurin-21` 编译。本机只有 JDK 8 时必须走 Compose 构建。

```bash
cd /path/to/lumencs
cp .env.example .env
# 聊天：OPENAI_API_KEY / OPENAI_BASE_URL / MODEL_NAME  → DMX（本机：.com + deepseek-v4-flash）
# 向量：EMBEDDING_API_KEY / EMBEDDING_BASE_URL / EMBEDDING_MODEL / EMBEDDING_DIM
#       → 硅基流动（本机：BAAI/bge-m3 + 1024）
# 根地址不要带 /v1。生产务必改 JWT_SECRET、ADMIN_PASSWORD、数据库密码

docker compose up -d --build
```

空库时 MySQL 入口脚本会执行 `deploy/sql/schema.sql`（已含中枢四张表）。

访问：

- 聊天台：http://localhost:8088
- 嵌入页（聊这篇）：http://localhost:8088/embed?slug=文章slug
- 控制台：http://localhost:8088/console/login
- 健康检查：http://localhost:8090/lumencs-api/api/health
- Knife4j：http://localhost:8090/lumencs-api/doc.html
- RAG：http://localhost:8100/health

控制台账号来自 `.env` 的 `ADMIN_USERNAME` / `ADMIN_PASSWORD`（示例默认 `admin` / `lumen123`）。

---

## 4. 已有 OrbStack 环境如何更新（本次实操）

场景：OrbStack 里已有 `lumencs` 组；常见情况是 **只有 mysql 在跑**，backend / web / rag / redis / qdrant 是 Stopped。数据卷还在。

### 4.1 不要做的事

- 不要 `docker compose down -v`：会删 `mysql_data` / `qdrant_data`
- 不要指望改完 `schema.sql` 就会自动改已有库（init 脚本只跑一次）
- 不要只重启旧容器：backend / rag / web 是本地 `build:` 镜像，代码变了必须 `--build`

### 4.2 推荐步骤

在仓库根目录（有 `docker-compose.yml` 和 `.env`）：

```bash
docker compose ps -a          # 看现有容器
docker compose up -d --build  # 重建业务镜像 + 启动全栈
```

这条命令实际做了：

1. **构建** `backend` / `rag-service` / `web` 三套 Dockerfile（基础设施镜像有则复用）
2. **Recreate** 业务容器（挂上新镜像）
3. **Start** 已停止的 redis / qdrant（mysql 若已在跑则跳过）
4. backend 等 mysql、redis healthy 后再起
5. 后端起来后跑 `HubSchemaBootstrap`，日志出现 `hub tables ready`

第一次全量 Maven 大约 3～5 分钟；前端 Vite 和 RAG pip 层多数能命中缓存，几秒级。

### 4.3 验收

```bash
docker compose ps
curl -s http://127.0.0.1:8090/lumencs-api/api/health
# 期望 rag=up、redis=up

curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8088/
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8100/health

docker compose logs --tail=50 backend | grep -E "Started|hub tables|ERROR"
```

健康接口示例：

```json
{
  "state": 200,
  "data": {
    "service": "lumencs-backend",
    "status": "healthy",
    "rag": "up",
    "redis": "up"
  }
}
```

浏览器打开 http://localhost:8088 ：暖金中枢首页；控制台侧栏有「中枢 / 运营」分组。

### 4.4 只更新其中一层

```bash
docker compose up -d --build backend      # 只重建 Java
docker compose up -d --build web          # 只重建前端
docker compose up -d --build rag-service  # 只重建 RAG
```

改了 `docker-compose.yml` 的环境变量或端口，同样 `up -d`（不必 build，除非 Dockerfile 也变了）。

---

## 5. 数据库怎么升级

| 情况 | 做法 |
| --- | --- |
| 全新 `docker compose up`（空卷） | `schema.sql` 自动执行，含中枢表 |
| 已有 `lumen_cs` 数据卷（本次） | 后端 `HubSchemaBootstrap` 自动 `CREATE TABLE IF NOT EXISTS` |
| 想手工确认 / 无 Java 启动权 | `deploy/sql/migrate_hub.sql` |

手工执行示例：

```bash
docker compose exec -T mysql mysql -ulumencs -plumencs lumen_cs < deploy/sql/migrate_hub.sql
```

确认四张表：

```bash
docker compose exec mysql mysql -ulumencs -plumencs -e "SHOW TABLES LIKE 'cs_%';"
```

应能看到原有 `cs_session` 等，以及新增 `cs_feedback`、`cs_inbox`、`cs_notify_channel`、`cs_notify_log`。

项目 **没有 Flyway**。以后改表结构：要么在 Bootstrap 里补 `IF NOT EXISTS` / 兼容 DDL，要么提供新的 `deploy/sql/migrate_*.sql` 并写进本文。

---

## 6. 镜像是怎么编出来的

### backend（多阶段）

```
maven:3.9-eclipse-temurin-21  →  mvn -DskipTests package
eclipse-temurin:21-jre-alpine →  只拷 jar
```

`COPY pom.xml` + `COPY src` 之后才 `mvn package`，**改 Java 或 pom 都会整段重编**（依赖也会重新拉，约数分钟）。

必须保留 `spring-boot-starter-data-redis`。中枢通知去重、记忆、限流、工单锁都依赖它；缺了会在镜像构建阶段直接编译失败（`StringRedisTemplate` 找不到）。

### rag-service

```
python:3.11-slim → pip install → COPY app
```

改 `app/store.py` / `app/main.py` 会重建最后一层；`requirements.txt` 不变则 pip 层缓存命中。

### web

```
node:22-alpine → npm install → vite build
nginx:1.27-alpine → 静态文件 + nginx.conf
```

浏览器只打到 `:8088`。`/lumencs-api/` 由容器内 nginx 反代到 `backend:8090`，并关闭 proxy 缓冲，保证 SSE 实时。

---

## 7. 环境变量

Compose 对 `backend` / `rag-service` 使用 `env_file: .env`，再用 `environment:` **覆盖容器内主机名**。

和中枢相关、需要进容器的项：

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `SLA_WAITING_HUMAN_MINUTES` | 120 | `WAITING_HUMAN` 超时进事件箱。演示可改 `2` |
| `SLA_SCAN_MS` | 60000 | SLA 扫描间隔 |
| `BLOG_SYNC_CRON` | `0 0 */6 * * *` | 博客同步；空则跳过 |
| `BLOG_BASE_URL` | 空 | 如 `http://8.155.149.208/lightdiary-api` |
| `BLOG_PUBLIC_WEB_URL` | 空 | 如 `http://8.155.149.208/lightdiary-web`，发布后拼 `/post/{slug}` |
| `BLOG_WRITE_ENABLED` | false | `true` 才允许聊天写博客；另需中枢登录 + 一次性 confirmToken |
| `BLOG_ADMIN_USERNAME` / `BLOG_ADMIN_PASSWORD` | 空 | LightDiary 后台账号；只放 `.env`，不要提交 |

改 `.env` 后：

```bash
docker compose up -d backend rag-service
```

不必 `--build`，但要 recreate 容器才会吃到新环境变量。

---

## 8. 更新后怎么验证中枢能力

1. **CSAT**：聊天里随便问一句，点「有用 / 缺口」；控制台 → 知识缺口能看到记录。
2. **HITL**：问「保证收益 100%」→ 控制台审核通过（可填备注）→ 原聊天约 8 秒内出现【审核通过】（前端轮询历史，不是同一条 SSE）。
3. **SLA**：把工单打到 `WAITING_HUMAN`，把 `SLA_WAITING_HUMAN_MINUTES` 临时改成 `2` 后重启 backend，控制台「事件」出现超时。
4. **聊这篇**：知识库点「从博客同步正文」后，打开 `/embed?slug=某篇文章slug`，范围条应显示该文。
5. **聊这篇**：知识库点「从博客同步正文」后，打开 `/embed?slug=某篇文章slug`，范围条应显示该文。
6. **聊天写博客**：`.env` 打开 `BLOG_WRITE_ENABLED` 并填 LightDiary **后台**账号（不是 MySQL）。未登录说「写博客」应直接提示去 `/console/login`。登录后再回首页发「写一篇」→ 改卡片 → 确认。默认草稿，前台看不到。无令牌直接 POST `/api/chat/card` 应提示令牌无效。

博客仓库 **不必合并进本仓**。正文页加一个跳到 `http://localhost:8088/embed?slug=...` 的「问 AI」即可。

---

## 9. 日常命令

```bash
docker compose ps
docker compose logs -f backend          # 跟日志
docker compose logs --tail=100 rag-service

docker compose restart backend          # 不重建镜像
docker compose up -d --build            # 代码更新后的标准动作

docker compose stop                     # 停全部，保留卷
docker compose start                    # 再拉起已有容器（镜像未变时）
docker compose down                     # 删容器、保留 named volume
# docker compose down -v               # 危险：清库
```

OrbStack 图形界面里对 `lumencs` 点 Start，等价于 `compose start`，**不会**带上这次代码；改完代码必须在项目目录执行 `--build`。

---

## 10. 接到已有 Nginx

只暴露 80/443 时，SSE 必须关缓冲：

```nginx
server {
    listen 80;
    server_name cs.example.com;

    location /lumencs-api/ {
        proxy_pass http://127.0.0.1:8090;
        proxy_http_version 1.1;
        proxy_buffering off;
        proxy_read_timeout 180s;
    }

    location / {
        proxy_pass http://127.0.0.1:8088;
    }
}
```

若域名只反代 8088，compose 里的 web 已处理 `/lumencs-api/`。

同机已有 lightdiary 时：博客占用 8081 / `/lightdiary-api`。本项目用 8088 / 8090。MySQL 用独立库 `lumen_cs`。不要和博客共用同一个 Redis DB 的业务 key。

---

## 11. 常见问题

**构建失败：`package org.springframework.data.redis.core does not exist`**  
`pom.xml` 缺少 `spring-boot-starter-data-redis`。补依赖后重新 `--build`。

**健康检查 `rag=down`**  
先看 `docker compose logs rag-service`。Qdrant 未起、或 embedding 网关不可用都会导致检索失败；聊天会降级关键词检索。

**改了 SQL 但表没变**  
已有卷不会跑 `schema.sql`。看 backend 日志有没有 `hub tables ready`，或手工跑 `migrate_hub.sql`。

**单文过滤不生效**  
必须重建过 `rag-service`（带 `document_id` 的代码），并且该文已按 **正文** 同步进 Qdrant。只重启旧 RAG 容器不够。

**本机 6379 被占用**  
宿主机映射可能变成 `16379:6379`。容器内 backend 仍连 `redis:6379`，不受影响。

**Embeddings 维度**  
`EMBEDDING_DIM` 必须与模型一致。本机硅基流动 `BAAI/bge-m3` 为 **1024**（OpenAI `text-embedding-3-small` 才是 1536）。改维度等于换集合，需要清 Qdrant 卷或换 collection 名，并在控制台重新向量化。

**聊天 401 / 浏览器像断网**  
先核对 DMX 站点与 Key 是否配套（`.com` ↔ Global），以及 `MODEL_NAME` 是否在 Key 的模型限制里。Java 已把上游错误写成友好 SSE，不要当成本机网络故障。

---

## 12. 回滚

镜像是 `latest` 标签，Compose 没有保留上一版 digest。回滚方式：

1. `git checkout` 到上一提交
2. `docker compose up -d --build`

中枢四张表是 `IF NOT EXISTS` 新增，回滚 Java 后表会留在库里，不影响旧代码运行。不要为了回滚而 `down -v`。
