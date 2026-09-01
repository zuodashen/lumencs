---
name: blog-tag
intent: blog_tag
priority: 38
description: >-
  新建标签、创建一个文章标签。给博客文章用的标签，不是给书签打标签。
triggers:
  - 新建标签
  - 创建标签
  - 加个标签
card_hint: 在博客里创建一个可用于文章的标签。
---

# 新建文章标签

## 何时使用
用户要在 LightDiary 里创建一个**文章标签**。

## 步骤
1. 抽出标签名（尽量短，不超过 20 字）。
2. 确认卡片后 `blog_tag_create`。
3. 已存在则复用，不要报错成失败。
