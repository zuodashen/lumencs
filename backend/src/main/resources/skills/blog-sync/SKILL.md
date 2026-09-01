---
name: blog-sync
intent: blog_sync
priority: 28
description: >-
  同步这篇博客、把某篇文章同步到知识库。按 slug 拉进本仓，不改博客前台。
rules:
  - any: [同步这篇博客, 同步博客, 同步这篇文章, 同步到知识库]
  - any: [博客, 文章]
    all: [同步, 这篇]
  - any: [博客, 文章]
    all: [同步, 该篇]
---

# 同步一篇博客

## 何时使用
用户要把 **LightDiary 已发布文** 拉进本仓知识库（`source=blog:{slug}`）。

## 步骤
1. 从 URL `/post/{slug}` 或「同步这篇博客：slug」抽出 slug。
2. 有 slug → `blog_sync_slug`；没有 → 先列出已发布列表让用户点同步。
3. 不弹确认卡。同步只增改本仓文档，不自动在博客前台下架。
4. 定时同步另有控制台开关，与这句话无关。
