# AI-Agent 后端项目流程学习指南

这份文档只讲后端（Spring Boot + LangChain4j + MongoDB + MySQL），不涉及前端、Docker 和部署。

## 1. 先看项目后端结构

后端核心代码都在 src/main/java/com/it/ai/aiagent 下，按职责分层：

- AiAgentApplication.java
  - Spring Boot 启动入口。
- config/
  - 显式注册关键 Bean（例如 ChatMemoryProvider、EmbeddingModel、ContentRetriever）。
- assistant/
  - LangChain4j 的 AI 服务接口（由框架动态生成代理 Bean）。
- controller/
  - HTTP API 入口层。
- service/
  - 业务编排层（计划创建、提醒发送、飞书消息意图处理）。
- store/
  - 持久化访问层（MongoTemplate、JdbcTemplate、MongoRepository）。
- bean/
  - DTO / Entity / ViewModel。

资源配置在 src/main/resources：

- application.yml：数据源、LangChain4j、Pinecone、飞书、定时任务参数。
- schema.sql：MySQL 表初始化。
- prompt-template.txt：对话助手的系统提示词模板。

## 2. Spring 启动主流程（从 main 到 Bean 完成）

### 2.1 启动入口

AiAgentApplication 使用：

- @SpringBootApplication
  - 等价于 @Configuration + @EnableAutoConfiguration + @ComponentScan。
  - 作用：
    - 打开 Spring Boot 自动配置；
    - 从 com.it.ai.aiagent 包向下做组件扫描，自动注册 controller/service/store/config 等 Bean。
- @EnableScheduling
  - 打开定时任务能力，让 @Scheduled 生效。

### 2.2 Bean 注册的 4 个来源（这个项目最重要）

1) 组件扫描注册（你写注解，Spring 自动放进容器）

- @RestController：XiaocController、KnowledgeController、StudyPlanController、FeishuWebhookController、TestStreamController
- @Service：StudyPlanService、ReminderNotificationService、FeishuPlanIntentService
- @Component：MongoChatMemoryStore、StudyReminderScheduler、FeishuLongConnectionService
- @Repository：StudyReminderTaskStore、FeishuEventLogStore、TopicRepository

2) @Configuration + @Bean 显式注册（手动定义）

- AgentConfig
  - memoryProvider（Bean 名称固定为 memoryProvider）
  - 返回 ChatMemoryProvider，底层使用 MessageWindowChatMemory + MongoChatMemoryStore，最大窗口 20 条消息。
- PineconeConfig
  - embeddingModel（被标注 @Primary，优先注入）
  - pineconeEmbeddingStore（有 key 走 Pinecone，无 key 自动降级 InMemoryEmbeddingStore）
  - pineconeContentRetriever（RAG 检索器，maxResults=3，minScore=0.7）

3) 第三方 Starter 自动配置注册（你配 yml，框架帮你建 Bean）

- 来自 langchain4j-open-ai-spring-boot-starter / langchain4j-spring-boot-starter：
  - openAiChatModel
  - openAiStreamingChatModel
  - 以及 @AiService 相关工厂能力。

4) @AiService 动态代理注册

- XiaocAgent、StudyPlanExtractAgent 是接口，不是实现类。
- 在启动时由 LangChain4j 生成代理对象并注册为 Spring Bean。

### 2.3 配置注入

application.yml 中的配置通过两种方式进入运行时：

- Spring Boot 自动配置读取（例如 langchain4j.open-ai.chat-model...）
- @Value 注入到具体字段（例如 feishu.app-id、pinecone.api.key）

## 3. Config 是怎么影响运行链路的

### 3.1 AgentConfig（会话记忆）

核心作用：把 LangChain4j 的会话记忆持久化到 Mongo。

- memoryProvider 被 XiaocAgent 引用。
- 每次 chat(memoryId, userMessage) 调用时：
  - 根据 memoryId 创建/获取 MessageWindowChatMemory；
  - 记忆存储委托给 MongoChatMemoryStore；
  - 自动裁剪为最近 20 条。

### 3.2 PineconeConfig（向量检索）

核心作用：提供 RAG 所需的 embedding + 向量库 + 检索器。

- embeddingModel：用于文本向量化。
- pineconeEmbeddingStore：
  - 若配置了 pinecone.api.key，则连接 Pinecone；
  - 若索引不存在，会尝试自动创建；
  - 失败时降级内存向量库。
- pineconeContentRetriever：
  - 对话时会把用户问题向量检索，拼接上下文给模型。

## 4. 三条主业务流程

## 4.1 对话流程（/agent/chat）

入口：XiaocController.chat

执行步骤：

1. Controller 建立 SseEmitter，设置 no-cache / keep-alive。
2. 调用 xiaocAgent.chat(memoryId, message) 返回 TokenStream。
3. TokenStream 流式回调：
   - onPartialResponse：逐 token 通过 SSE 推给前端；
   - onCompleteResponse：结束 SSE；
   - onError：返回错误并结束。
4. XiaocAgent 背后代理执行时会组合：
   - chatModel=openAiChatModel
   - streamingChatModel=openAiStreamingChatModel
   - chatMemoryProvider=memoryProvider
   - contentRetriever=pineconeContentRetriever
5. 会话消息由 MongoChatMemoryStore 持久化到 chat_messages 集合。

你可以理解成：
用户问题 -> RAG 检索上下文 -> 大模型流式生成 -> SSE 实时返回 -> 对话历史落 Mongo。

## 4.2 历史会话流程

- GET /agent/history/{memoryId}
  - 从 MongoChatMemoryStore.getMessages(memoryId) 读取历史。
  - 过滤 SystemMessage。
  - 对 UserMessage 去除 LangChain4j 注入的 RAG 拼接文本，只保留用户原问题。
- GET /agent/history/sessions
  - 读取所有 session，取首条用户消息生成会话标题。

## 4.3 知识库上传流程（/knowledge/upload）

入口：KnowledgeController.upload

执行步骤：

1. 用 ApacheTikaDocumentParser 解析上传文件（txt/pdf/doc 等）。
2. 给 Document 增加 metadata.topic。
3. EmbeddingStoreIngestor 执行：
   - 文档分块（recursive 1200/100）；
   - embeddingModel 向量化；
   - pineconeEmbeddingStore 入向量库。
4. TopicRepository 维护主题统计（topics 集合中的 docCount）。

## 4.4 学习计划主流程（HTTP）

入口：StudyPlanController

- POST /agent/study-plan/weekly
  - StudyPlanService.createWeeklyPlan
  - 校验 request、解析日期时间、构建 StudyReminderTask 列表。
  - StudyReminderTaskStore.saveBatch 批量写入 MySQL 表 study_reminder_task。
- GET /agent/study-plan/tasks
  - 查询未来 N 天任务并转为 StudyReminderTaskView。
- PATCH /agent/study-plan/tasks/{id}/status
  - 更新任务启用状态。
- POST /agent/study-plan/tasks/{id}/test
  - 走 ReminderNotificationService 做一次测试发送。

## 4.5 飞书消息驱动学习计划流程（Webhook + 长连接）

本项目有两种飞书入口：

1) FeishuWebhookController（/feishu/event）
2) FeishuLongConnectionService（启动后 @PostConstruct 建立长连接）

两条入口最终都汇聚到同一个核心服务：FeishuPlanIntentService.processPlanIntent。

统一处理思路：

1. 事件验签/解析 + 幂等去重（FeishuEventLogStore.tryInsertEvent，基于 event_id）。
2. 提取用户文本（处理 JSON content，清理群聊 @ 占位符）。
3. 意图识别：查询 / 删除 / 修改 / 创建。
4. 创建场景下：
   - 先调用 StudyPlanExtractAgent（LLM）抽取结构化 JSON；
   - 再做规则归一化与兜底（时间默认 20:00、最多 7 天、确保未来时间）。
5. 调用 StudyPlanService 入库。
6. 调用 ReminderNotificationService 回消息给飞书。
7. 更新 feishu_event_log 处理状态。

## 4.6 定时提醒流程（Scheduler）

StudyReminderScheduler.runReminderSchedule 每隔 fixed-delay 执行一次（默认 60000ms）：

1. StudyPlanService.executeDueTasks 查询到期未发送任务。
2. ReminderNotificationService.sendReminder 逐条发送（当前只启用飞书渠道）。
3. 成功：markSent；失败：markFailed 并记录 error_message。

## 5. 数据持久化设计（你要重点理解）

MongoDB：

- chat_messages（ChatMessages）
  - memoryId + content(json) 保存对话历史。
- topics（Topic）
  - 主题名 + 文档计数。

MySQL：

- study_reminder_task
  - 保存学习计划任务、触发时间、发送状态、来源 open_id。
- feishu_event_log
  - 保存飞书事件幂等日志和处理状态。

为什么这么拆：

- 对话历史和知识主题是文档型，适合 Mongo。
- 计划任务和状态流转需要强筛选与更新，适合 MySQL。

## 6. 一次完整请求的“Bean 协作图”

### 6.1 对话请求

XiaocController
-> XiaocAgent(动态代理 Bean)
-> openAiStreamingChatModel + memoryProvider + pineconeContentRetriever
-> MongoChatMemoryStore / Pinecone
-> SseEmitter 返回流。

### 6.2 学习计划创建请求

StudyPlanController
-> StudyPlanService
-> StudyReminderTaskStore(JdbcTemplate)
-> MySQL。

### 6.3 飞书文本消息

FeishuWebhookController 或 FeishuLongConnectionService
-> FeishuPlanIntentService
-> StudyPlanExtractAgent(动态代理 Bean，可选)
-> StudyPlanService
-> StudyReminderTaskStore
-> ReminderNotificationService(WebClient 调飞书 API)
-> FeishuEventLogStore 记录状态。

## 7. 给你的学习顺序建议（Java/Spring 视角）

1. 先看启动与 Bean 注册
   - AiAgentApplication -> AgentConfig -> PineconeConfig。
2. 再看一个最短链路
   - StudyPlanController -> StudyPlanService -> StudyReminderTaskStore。
3. 再看 AI 链路
   - XiaocController -> XiaocAgent -> memoryProvider/contentRetriever。
4. 最后看飞书事件链路
   - FeishuWebhookController + FeishuLongConnectionService -> FeishuPlanIntentService。

按这个顺序看，你会更容易把“Spring 容器里有哪些 Bean、它们如何被注入、一次请求如何在各层流动”建立成完整心智模型。

## 8. 常见排错点（学习时一定会遇到）

- xiaocAgent 无法注入：
  - 检查 langchain4j starter 依赖是否加载。
  - 检查 @AiService wiringMode=EXPLICIT 对应 Bean 名称是否存在。
- 对话无 RAG 效果：
  - 检查知识库是否已上传。
  - 检查 pinecone.api.key 与索引创建日志。
- 学习计划不触发：
  - 检查 @EnableScheduling 是否生效。
  - 检查 scheduler.reminder.fixed-delay-ms。
  - 检查 trigger_time、status、sent_status 是否符合待发送条件。
- 飞书收不到消息：
  - 检查 feishu.app-id / feishu.app-secret。
  - 检查 open_id / chat_id 是否正确。

---

如果你愿意，下一步我可以再给你补一份“按调试断点一步步跟”的学习版（从 Controller 到 Store 每一步该打在哪一行、看哪些变量），更适合你作为大三开发者快速上手。

