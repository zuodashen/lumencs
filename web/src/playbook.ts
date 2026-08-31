export type Scenario = {
  id: string
  group: string
  title: string
  hint: string
  example: string
  samples: string[]
  tool: string
}

export const SCENARIOS: Scenario[] = [
  {
    id: 'memo',
    group: '日常',
    title: '记一笔',
    hint: '写进本仓知识库，之后可以直接问。',
    example: '帮我记一下：生椰拿铁少糖少冰',
    samples: ['帮我记一下：周五把周报交了', '记一笔：博客草稿先放着'],
    tool: 'memo_save',
  },
  {
    id: 'todo',
    group: '日常',
    title: '加待办',
    hint: '确认卡片后记一条，走状态机。',
    example: '加个待办：周五把周报交了',
    samples: ['提醒我明天续域名', '别忘了给博客改封面'],
    tool: 'ticket_create',
  },
  {
    id: 'todo_query',
    group: '日常',
    title: '查待办',
    hint: '列出最近待办和中文状态。',
    example: '我现在有哪些待办',
    samples: ['待办列表', '查一下 TK- 开头的编号'],
    tool: 'ticket_list',
  },
  {
    id: 'todo_update',
    group: '日常',
    title: '改待办状态',
    hint: '不能跳步，例如已创建不能直接改成已完成。',
    example: '把 TK-20260831-AB12 改成进行中',
    samples: ['这条待办标记为已完成'],
    tool: 'ticket_update',
  },
  {
    id: 'knowledge',
    group: '日常',
    title: '问笔记 / 文档',
    hint: '走 RAG：改写 → 检索 → 重排。',
    example: '我常喝什么咖啡',
    samples: ['这篇笔记的结论是什么', '知识库里有没有部署步骤'],
    tool: 'kb_search',
  },
  {
    id: 'blog_list',
    group: '博客',
    title: '已发布文章列表',
    hint: '卡片里可同步到知识库，或聊这一篇。',
    example: '列出已发布的博客',
    samples: ['博客列表', '最新一篇博客是什么时候'],
    tool: 'blog_list',
  },
  {
    id: 'blog_sync',
    group: '博客',
    title: '同步一篇到知识库',
    hint: '只拉已发布正文。也可在列表上点「同步」。',
    example: '同步这篇博客：hello-world',
    samples: ['把这篇同步到知识库'],
    tool: 'blog_sync_slug',
  },
  {
    id: 'blog_bookmarks',
    group: '博客',
    title: '书签列表',
    hint: '按分组列出前台书签。',
    example: '我的书签',
    samples: ['书签列表', '列出书签'],
    tool: 'blog_bookmarks',
  },
  {
    id: 'blog_article',
    group: '博客',
    title: '写博客草稿',
    hint: '先出卡片，默认存草稿，确认后才写 LightDiary。',
    example: '帮我写一篇博客草稿：这次把 RAG 链路讲清楚',
    samples: ['写成博文', '存一篇草稿'],
    tool: 'blog_article_upsert',
  },
  {
    id: 'blog_bookmark',
    group: '博客',
    title: '添加书签',
    hint: '卡片确认后写入博客书签页。',
    example: '收藏这个链接 https://example.com 到工具分组',
    samples: ['加个书签'],
    tool: 'blog_bookmark_create',
  },
  {
    id: 'stock_quote',
    group: '盯盘侠',
    title: '查行情 / K 线',
    hint: '对话里嵌总览卡。点迷你 K 线可看日 K 明细。',
    example: '查一下酒鬼酒行情',
    samples: ['查询一下远东股份的行情', '000799 的 K 线'],
    tool: 'stock_quote',
  },
  {
    id: 'milk_tea',
    group: '演示',
    title: '工位奶茶',
    hint: '唯一保留的客服式演示，不接下单。',
    example: '来一杯大杯少糖珍珠生椰拿铁，送到 A3',
    samples: ['再来一杯', '点杯美式'],
    tool: 'tea_order',
  },
]

export const FEATURED_PROMPTS = SCENARIOS.filter((s) =>
  ['memo', 'todo', 'blog_list', 'stock_quote'].includes(s.id),
).map((s) => s.example)

export const TOOL_HINTS: Record<string, { title: string; example: string }> = Object.fromEntries(
  SCENARIOS.map((s) => [s.tool, { title: s.title, example: s.example }]),
)
