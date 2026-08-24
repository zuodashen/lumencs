# 项目深度问答 — LumenCS 面试官追问 & 标准回答

> 每个问题按"问题 → 标准回答 → 追问升级"组织。
> 所有回答都对应代码真实实现，不编造指标（没有生产数据，就讲机制与 trade-off）。

---

## Q1: "你的 Supervisor 编排和简单的 if-else 路由有什么区别？"

### 标准回答

只做意图路由这一步，确实像 if-else（`SupervisorAgent.dispatch` 按 intent 分派）。但 Supervisor 的价值在**编排维度**：

1. **共享状态贯穿**：`AgentState` 从意图、置信度、子结果、引用、工单号到审核单号全程共享，各 Agent 只读写这个状态对象（`supervisor → intent_router → workflow/knowledge → compliance → synthesize`）。
2. **统一的审查汇聚点**：无论走办事流程还是知识问答，最终都汇聚到合规审查再合成回复——如果每个分支自己写合规逻辑，加一条分支就要改 N 处；放编排层就是一条路径。
3. **过程可观测**：每一步通过 `AgentEventSink.step()` 推 SSE 时间线，前端能看到 intent → 卡片/检索 → 工具 → 合规 的完整链路；同时 `AgentTracer` 把每次编排落 `cs_span`，控制台可回放。
4. **分支决策可扩展**：低置信度澄清（`needsClarification` 时整体跳过派发）、HITL（`reviewPending` 时改变最终回复）都是编排层的横切逻辑，不需要改业务 Agent。

### 追问升级：为什么不用 LangGraph / 图编排？

本项目是 Java 21 + Spring AI，LangGraph 是 Python 生态；而且我的流程本质是**顺序分派 + 统一审查**，图引擎的并行/循环能力用不上，用图框架反而增加心智负担。若以后要并行检索、条件重试，再评估引入图编排或 Spring AI 的并行能力——这是个可以讲清 trade-off 的选型。

---

## Q2: "三层记忆是怎么协作的？举个例子"

### 标准回答

以"加班口渴，帮我点杯奶茶"为例：

- **工作记忆**（Redis Hash，`lumencs:working:{sessionId}`）：意图路由后写入 `intent=milk_tea`、`workflow=milk_tea`；卡片提交后 `mergeSlots` 合并槽位（drink/size/sweetness/ice/topping/count/desk）；工具跑完 `clearWorkflow` 清掉。
- **短期记忆**（Redis List，20 轮窗口 / TTL 30min）：每轮用户与助手消息入列；知识问答时 `contextWindow()` 读回注入 RAG Prompt；短追问（≤12 字）会拼上一轮用户问题再检索。
- **长期画像**（Redis String，14 天）：奶茶下单成功后 `remember(userLabel, {drink,size,...})` 写入画像；下次同一访客说"再来一杯"，`WorkflowAgent` 从 `profile()` 读出口味预填卡片，仍需用户确认。

三层合流：Supervisor 把工作记忆快照 + 画像摘要拼成 `memoryContext` 注入各 Agent Prompt（`fillMemoryContext`），短期记忆由 RAG Agent 自行读取。控制台"记忆"页可实时看三层快照。

### 追问升级：Redis 挂了怎么办？

三个记忆服务都有进程内 ConcurrentHashMap 兜底（`put`/`remember` 捕获异常写本地），单机 demo 下 Redis 重启不丢当前会话；长期画像会丢（本来就只有 14 天 TTL）。生产会换持久化存储，但兜底逻辑保证了"降级不崩"。

---

## Q3: "合规审查怎么做？误判怎么控制？"

### 标准回答

两阶段（`ComplianceCheckerAgent`）：

1. **规则快筛**：违禁金融用语（保证收益/稳赚不赔/零风险…）+ PII 正则（手机号/身份证/银行卡）。规则命中属于 critical，**直接拦截，不调用 LLM**——既省 Token 又保证绝对不放行。
2. **LLM 深审**：规则通过后，把待发送文本交给 LLM 做语义级审查（越权承诺、误导、歧视、索要密码验证码等），结构化输出 `{passed, violations, reason}`。
3. **HITL 兜底**：LLM 判定不通过 → 写入 `cs_review` 待审队列（PENDING），**不直接回复用户**，访客看到"已转人工审核"；管理员在"审核收件箱"通过/驳回并留备注。

误判控制的关键取舍：**LLM 审查失败时按"通过"处理**，防止审查服务抖动误伤正常回复——真正的兜底是规则引擎（高召回）和 HITL（人工兜底），而不是指望 LLM 每次都稳。

### 追问升级：规则命中直接拦截会不会太激进？

金融场景合规是底线：宁可误拦转人工，不能漏放违规承诺。规则词库是白名单式的绝对用语，命中即确凿违规，误拦成本远低于漏放（监管风险）。

---

## Q4: "RAG sidecar 超时了 / Agent 挂了怎么办？"

### 标准回答

三层防御：

1. **超时控制**：`ragRestClient` 连接 3s / 读 20s；LLM 流式生成 `.blockLast(90s)` 超时。
2. **降级**：`KnowledgeService.search` 捕获 sidecar 异常 → MySQL `cs_chunk` 关键词匹配兜底（返回关键词命中的 chunk）；ingest 向量化失败 → 文档标 `KEYWORD_ONLY`，关键词检索仍可用；博客 API 8s 读超时失败只记日志返回空，不影响主链路；LLM 流式失败回退一次性整段生成。
3. **状态上报**：`/api/health` 返回 rag up/down、redis up/down，控制台总览可见，问题能先被发现。

### 追问升级：为什么关键词兜底返回的结果质量能接受？

关键词匹配命中 `cs_chunk` 的 content 字段，按命中词数排序取 topK——对产品名、政策术语这类精确词效果可接受；向量检索恢复后自动切回。这是个"可用性优先"的工程取舍：宁可答案糙一点，不能问答挂掉。

---

## Q5: "RAG 检索准确率怎么评估？"

### 标准回答（诚实版）

目前**没有标注数据集**，所以我不会给"准确率 92%"这种数字。我做了三件事保证可验证：

1. **链路可人工核验**：检索 8 条 → 重排 3 条 → 回答带引用，引用可点击展开原文——每个回答都能人工核对"引用是否支撑了回答"（faithfulness 的人工版）。
2. **指标口径明确**：如果要上评估，标准做法是 Recall@K / MRR 评检索、faithfulness / answer relevance 评生成（LLM-as-Judge），用标注的 query-document 对测试集跑。
3. **可配置的成本控制**：改写和重排走 LLM 耗 Token，都有开关（`RAG_REWRITE_ENABLED` / `RAG_RERANK_ENABLED`），生产可以关掉重排省成本、保留关键词兜底。

### 追问升级：重排为什么用 LLM 而不是 cross-encoder？

没有标注集训练/微调 cross-encoder，LLM 重排零训练成本、可解释（能看它选的 id）；代价是延迟和 Token。面试我会说：如果数据量上来且预算允许，换 cross-encoder（如 bge-reranker）是明确的优化路径。

---

## Q6: "Prompt 注入怎么防？"

### 标准回答

多层防御（对应真实代码）：

1. **意图白名单**：意图路由结构化输出，intent 不在白名单直接丢弃回退关键词（`INTENTS.contains` 校验）。
2. **系统/用户隔离**：System Prompt 固定角色与规则，用户输入只进 user 段（Spring AI 的 ChatClient 天然分层）。
3. **工具权限边界**：工具不是模型自由调用的函数列表，而是**代码注册表**（`McpToolServer.dispatch`），模型不参与工具选择——WorkflowAgent 按意图绑定固定工具，参数也只取槽位字段，注入内容无法改变工具行为。
4. **输出合规**：即使模型被诱导，合规 Agent 会对最终回复做语义审查，不通过进 HITL。

---

## Q7: "工具调用为什么是 MCP 风格而不是 Function Calling？"

### 标准回答

Spring AI 支持 Function Calling，但演示场景下我做了更可控的选择：`McpToolServer` 是**代码内工具注册表**（name/description/params/handler），WorkflowAgent 在槽位齐备后按流程定义**显式调用**对应工具——不是让模型决定调什么，而是流程决定调什么。好处：可解释（时间线能看到 tool 调用与参数）、可控（模型拿不到工具执行权）、可观测（每次调用写 `cs_tool_log`）。MCP 是协议化的同构思路，将来对接外部服务可以直接把 dispatch 换成 MCP client。

### 追问升级：那模型怎么参与决策？

模型参与的是**意图与槽位语义**（意图置信度、`extractSlots` 从自然语言抠选项），工具执行是确定性的。这是"模型做理解、代码做执行"的分工，也是金融客服场景更稳的形态。

---

## Q8: "工单单号为什么需要分布式锁？"

### 标准回答

单号格式 `TK-yyyyMMdd-0001`（日自增）。并发下两个请求同时取号：INCR 本身原子，但"先判断是否新的一天再决定是否重置计数器"这一步需要互斥，否则会出现同日号段重复或覆盖。我用 `RedisLockService`（SET key token NX EX + Lua 比对删除）包住取号流程；锁不可用时退化 UUID 后缀保证唯一。创建工单整体 `@Transactional`，单号与记录同事务落库。

### 追问升级：为什么不用数据库唯一索引兜底？

`ticket_no` 有 UNIQUE 约束兜底（重复插入会报错），分布式锁是为了**正常路径不撞号**而不是靠异常兜底——面试可以提"锁保证无冲突、唯一索引保证绝对不重复"的双保险。

---

## Q9: "双 JWT 是怎么设计的？"

### 标准回答

`JwtService` 签发两种 token，claim 里带 `type`：access（30min）用于接口鉴权，`JwtAuthFilter` 只认 type=access；refresh（7 天）只允许调 `POST /api/auth/refresh` 换新 token 对（轮换）。前端 `adminFetch` 遇 401 先用 refresh 静默续期并重试一次，失败才跳登录。refresh 不参与业务鉴权，缩小了长期凭证的暴露面。

### 追问升级：refresh token 被偷怎么办？

演示项目未做服务端撤销（无状态 JWT）；生产会加 refresh token 轮换 + 设备绑定 + 黑名单/短 TTL。这是我会主动交代的已知简化。

---

## Q10: "限流是怎么做的？"

### 标准回答

`RateLimitService` 用 Redis 固定窗口：`INCR` + 首次设置过期，key 按窗口桶分片（`ip:{ip}:{bucket}`、`session:{sessionId}:{bucket}`），超限返回 429 + 统一 R 结构。挂在 `RateLimitInterceptor` 上，作用于聊天接口（IP + session 双维度）。Redis 不可用时有进程内计数器兜底。固定窗口的缺陷是窗口边界突发，讲清楚这点比假装用了令牌桶更可信。

---

## Q11: "这个项目你踩过什么坑？"（诚实版）

### 标准回答

1. **MyBatis-Plus 3.5.9 分页插件拆分**：`PaginationInnerInterceptor` 被拆到独立 `mybatis-plus-jsqlparser` 包，旧写法编译不过——排查 jar 定位后补依赖解决（这也是我后来加分页时踩的）。
2. **RAG sidecar 不可用导致整体 500**：最初没有降级，后来在 KnowledgeService 加 catch → 关键词兜底 + 文档状态标记。
3. **LLM 合规偶发误拦正常回复**：加"LLM 失败按通过 + 规则保底 + HITL 兜底"的策略。
4. **审计字段与手工建表冲突**：SuperEntity 引入 create_user/update_user 需要给已有库补列，我提供了 `deploy/sql/upgrade-v2.sql` 增量脚本而不是直接改表结构不管存量。

这些都是真实的开发过程，比编造线上事故更经得起追问。

---

## Q12: "如果重来，你会改什么？"

### 标准回答

1. **DTO/VO 全面化**：目前只对工单/审核/知识做了 DTO/VO 与分页，会话/消息/追踪还是 Entity 直出；重来会一开始就定好分层规范。
2. **重排模型化**：LLM 重排改成可插拔接口，预算允许时切 cross-encoder。
3. **记忆持久化**：长期画像目前是 Redis 14 天 TTL，生产会落 MySQL/向量库。
4. **测试补齐**：目前没有自动化测试，重来会为状态机、限流、合规路由补单测。
