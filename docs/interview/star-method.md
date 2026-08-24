# STAR 面试话术 — LumenCS 智能客服多Agent系统

> 本话术严格对应代码实现（路径见《技术方案》与 README），不编造指标。
> 每个"Action"都能在代码里找到对应类，追问三层的答案都在 project-qa.md。

---

## 场景一："请介绍一下你最有挑战的项目"

### S（情境）
> 我独立开发了一套智能客服多 Agent 系统（LumenCS）。背景是客服场景的典型诉求：知识问答要可溯源、办事流程要真的能落地（开单/下单）、金融场景回复必须过合规、多轮对话不能丢上下文。单 Agent 或简单 if-else 路由解决不了"先意图、再分派、统一过审查"的编排问题。

### T（任务）
> 我要交付一个能完整演示、能经得起追问的系统：Java 编排主链路 + Python RAG sidecar + 控制台，覆盖从意图识别到合规审查再到人工兜底的完整闭环。

### A（行动）
> 四个关键技术决策：
>
> **第一，Supervisor 编排**。我定义了一个贯穿全程的 AgentState，Supervisor 依次执行：意图路由 →（办事流程卡片 或 知识 RAG）→ 合规审查 → 汇总；每一步通过 SSE 的 step 事件推给前端形成时间线。意图路由不是纯关键词也不是纯 LLM：关键词确定性命中直接走（置信度 0.95），否则调 LLM 结构化输出 intent+confidence，低于阈值就先澄清、不派发业务 Agent。
>
> **第二，RAG 完整链路**。用户短句/指代先做 LLM Query 改写（可配开关，失败退启发式拼接上一轮问题）；sidecar 向量检索 Top8；再用 LLM 按相关性重排取 Top3 注入上下文；回答带引用，前端可点击展开原文。同时做了降级：sidecar 超时或失败自动切关键词检索，保证问答不挂。
>
> **第三，Agent 不是演示壳**。工具层是真 handler：`ticket_create`/`ticket_query` 落 MySQL 工单，`kb_search` 走知识库，`blog_search` 真调博客公开 API，`tea_order` 出真实单号和金额（标注演示数据源）。每次调用写 `cs_tool_log`，Agent 调用写 `cs_span`，控制台能回放。合规是两阶段：规则快筛（敏感词+PII 正则）命中直接拦截；通过后 LLM 深审，判定不通过就进 HITL 收件箱 `cs_review`，由管理员通过/驳回，不直接回复用户。
>
> **第四，工程化补齐**。统一响应 R + 全局异常 + DTO/VO（禁止 Entity 出参）+ PageWrapper 分页 + 审计字段自动填充；双 JWT（access 30min + refresh 7 天，前端 401 自动续期）；Redis 固定窗口限流（IP+session，超限 429）；工单状态机 + `@Transactional` + Redis 日自增单号 + 分布式锁（SET NX EX + Lua 释放）防并发撞号；三层记忆（工作/短期/长期）汇入 Prompt。

### R（结果）
> 系统 Docker Compose 一键可跑（MySQL/Redis/Qdrant/rag-service/backend/web），演示闭环完整：点奶茶办事卡片（槽位抽取+画像预填）→ 真调用工具出单号；触发合规（如"保证收益"）能看规则拦截或 HITL 审核；限流、双 token 续期、分页、审计都有接口和页面。工程完整度是我这个项目最有说服力的部分——面试官想看哪一层都能打开代码讲。

---

## 场景二："你在项目中遇到过什么技术难题？怎么解决的？"

### S（情境）
> 开发中遇到四类典型问题：外部依赖不可用、模型输出不稳定、并发一致性、框架版本坑。

### T（任务）
> 让系统在 sidecar 挂掉、LLM 抽风、并发下单、依赖升级的情况下都不至于崩坏或出错。

### A（行动）
> 1. **RAG sidecar 超时降级**：最初知识问答直接调 sidecar，一旦它超时整个请求就 500。我把 RagClient 配了 3s 连接/20s 读超时，KnowledgeService 捕获异常后走 MySQL `cs_chunk` 关键词匹配兜底，并给文档标 `KEYWORD_ONLY` 状态；健康检查接口也会上报 sidecar 状态。
> 2. **LLM 合规误拦/漏判**：只靠关键词会漏掉"风险很低"这类隐晦表述，直接上 LLM 审查又会偶发误拦正常回复。最终两阶段：规则命中（critical）直接拦截不走 LLM 省钱；规则通过后 LLM 深审；LLM 调用失败时按"通过"处理，避免审查服务抖动误伤用户——真正的兜底是规则引擎和 HITL 人工队列。
> 3. **并发取号撞号**：单号最初用 UUID 后缀，虽然不会撞但不可读。改成 Redis 日自增 `TK-yyyyMMdd-0001`，用分布式锁（SET NX EX + Lua 比对删除）包住取号，锁不可用时再退 UUID，保证唯一；创建工单整体包在 `@Transactional` 里。
> 4. **框架坑**：MyBatis-Plus 3.5.9 把分页拦截器拆到了独立的 `mybatis-plus-jsqlparser` 包，直接用旧写法 `PaginationInnerInterceptor` 编译不过，排查 jar 后补依赖解决；Knife4j 与 springdoc 版本对齐也踩过一次。

### R（结果）
> 四条都有明确产出：sidecar 挂了问答仍可用（关键词兜底）；合规误拦率通过"规则保底 + LLM 失败按通过 + HITL 兜底"得到控制；工单单号可读且并发不撞；编译/运行链路全绿。

---

## 场景三："为什么选择这个技术栈？"

### S（情境）
> 需要决定编排语言、RAG sidecar 形态、向量库选型。

### T（任务）
> 选型要兼顾：个人项目可维护性、简历可讲性、单机可部署、金融客服场景的合规诉求。

### A（行动）
> - **Java 21 + Spring AI 做编排**：岗位方向是 Java 后端，Spring AI 的 ChatClient 支持结构化输出（`.entity()` 直接拿 record），正好做意图置信度和 LLM 审查；MyBatis-Plus + MySQL 落业务数据。相比 Python/LangGraph，Java 栈和公司业务系统同构，面试也更贴岗位。
> - **Python FastAPI + Qdrant 做 RAG sidecar**：embedding 和向量检索生态在 Python 侧最成熟，但不想让 Python 暴露公网，所以只内网暴露 /ingest、/search、/delete 三个 HTTP 接口，Java 侧有超时与降级。这本身就是个可讲的架构决策：检索能力隔离 + 主链路可控。
> - **向量库选 Qdrant 而非 Milvus**：Milvus 是分布式重型组件，单机 demo 过重；Qdrant 官方镜像单节点就能跑，和 Redis/MySQL 一起进 docker-compose。面试讲选型时我会说明：数据量级上来、需要分布式扩展时再换 Milvus 的取舍点在哪。
> - **编排用 Supervisor 而非图框架**：本项目是 Java，LangGraph 是 Python 生态；且我的流程本质是"线性分派 + 统一审查"，Supervisor 顺序编排 + 状态对象贯穿就能表达清楚，不需要引入图引擎。如果将来要并行检索、条件循环，再评估 LangGraph 或 Spring AI 的并行能力。

### R（结果）
> 技术栈全部落在单机 Docker Compose 上可跑；Java/Python 边界清晰（sidecar 只做向量，业务编排全在 Java），面试时每个选型都有明确的 trade-off 可说。

---

## 场景四："项目中你怎么保证质量？"

### S（情境）
> 多 Agent 系统的不确定性来自模型输出：意图可能分错、RAG 可能答非所问、回复可能违规。

### T（任务）
> 建立多层防线：输入侧限流、过程侧可观测、输出侧合规、人工兜底。

### A（行动）
> 1. **输入防护**：Redis 固定窗口限流（IP+session 双维度），超限返回 429；双 JWT 控制管理端访问。
> 2. **过程可观测**：每个请求生成 traceId 贯穿日志与响应；每次 Agent/工具调用写 `cs_span`（agent、method、耗时、参数、状态）和 `cs_tool_log`，控制台按会话回放，出问题能定位到具体是哪一步。
> 3. **输出合规**：规则快筛（违禁金融用语 + 手机号/身份证/银行卡 PII 正则）+ LLM 语义深审；不通过进 HITL 收件箱，管理员通过/驳回留痕（reviewedBy/reviewNote）。
> 4. **状态约束**：工单状态机拒绝非法流转；DTO 校验（@Valid）；分页查询参数有边界（pageSize≤100）。
> 5. **降级设计**：sidecar 超时降关键词、LLM 流式失败回退整段生成、Redis 不可用时记忆/锁/限流均有进程内兜底。

### R（结果）
> 质量防线都是可演示的：连发消息看 429；触发违规看拦截或审核单；控制台回放能看到每次 Agent 调用的参数与耗时。这些比编一个"误判率 <2%"更能体现工程能力——面试官可以直接验。

---

## 万能模板

```
S: 我做的是[项目]，背景是[痛点]...
T: 目标是[可证明的交付]...
A: 我做了[决策1]…选[技术A]而非[技术B]的原因是[真实 trade-off]…
R: 结果是[可演示/可验证的产出]，没有生产数据就不编数字。
```

### 关键技巧
1. 每个 Action 都能指向具体类/文件（SupervisorAgent、IntentRouterAgent、TicketService、McpToolServer…）
2. 没有数据就说"可演示的完整度"，绝不编 QPS/FCR/准确率
3. 主动暴露 trade-off（"重排用 LLM 是因为没有标注集，成本换准确率；生产可换 cross-encoder"）
4. 每个回答准备 2-3 层追问（对照 project-qa.md）
