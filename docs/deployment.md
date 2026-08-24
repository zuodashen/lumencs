# 部署

## Docker Compose（推荐）

在项目根目录：

```bash
cp .env.example .env
# 填写 OPENAI_API_KEY、OPENAI_BASE_URL、JWT_SECRET、ADMIN_PASSWORD
docker compose up -d --build
```

默认端口：

| 服务 | 端口 |
| --- | --- |
| 前端 Nginx | 8088 |
| Java | 8090 |
| RAG | 8100 |
| MySQL | 3306 |
| Redis | 6379 |
| Qdrant | 6333 |

与 lightdiary 同机时：博客已占用 8081 / `/lightdiary-api`。本项目使用 8088/8090，不要复用同一 Redis DB 的业务 key。MySQL 使用独立库 `lumen_cs`。

## 接到已有 Nginx

```nginx
server {
    listen 80;
    server_name cs.example.com;

    location /lumencs-api/ {
        proxy_pass http://127.0.0.1:8090;
        proxy_buffering off;
        proxy_read_timeout 180s;
    }

    location / {
        proxy_pass http://127.0.0.1:8088;
    }
}
```

若只想暴露 8088（compose 里 web 已反代 `/api`），域名根路径指到 8088 即可。

## 注意

- Embeddings 与 Chat 需同一网关可用。`EMBEDDING_DIM` 必须与模型维度一致（`text-embedding-3-small` 为 1536）。
- MySQL 初始化脚本只在数据卷首次创建时执行。改表后需手工迁移或清卷重建。
- 生产环境关闭默认密码，限制 3306/6333/8100 仅内网。
