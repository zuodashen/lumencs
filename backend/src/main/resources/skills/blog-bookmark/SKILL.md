---
name: blog-bookmark
intent: blog_bookmark
priority: 36
description: >-
  收藏链接、加书签、收藏这个网址。新建一条博客书签，不是列出已有书签。
triggers:
  - 加书签
  - 收藏这个
  - 收藏链接
  - 添加书签
  - 收藏网址
card_hint: 核对链接和分组后再写入博客书签页。
---

# 添加书签

## 何时使用
用户要把一个 URL 写进 LightDiary 书签，不是「我的书签有哪些」。

## 步骤
1. 从消息里抽出 `https?://` 链接、名称、分组。
2. 弹出确认卡片。
3. 确认后 `blog_bookmark_create`（管理端 JWT）。
4. 「书签列表 / 我的书签」走 blog-bookmarks，不要发卡。
