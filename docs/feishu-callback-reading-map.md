# 飞书回调阅读导图（调用顺序 + 关键断点）

这份文档用于快速理解当前项目里“飞书消息 -> 学习计划入库 -> 飞书回执”的完整链路。

## 1. 总览（主链路）

```mermaid
flowchart TD
    A[飞书长连接事件到达] --> B[FeishuLongConnectionService.start 注册处理器]
    B --> C[onP2MessageReceiveV1.handle]
    C --> D[processMessageEvent(raw)]
    D --> E{消息类型/会话类型是否可处理}
    E -- 否 --> F[markStatus=2 ignored]
    E -- 是 --> G[normalizeUserText 清洗消息]
    G --> H[FeishuPlanIntentService.processPlanIntent]
    H --> I[LLM 抽取 + 规则兜底 + 时间顺延]
    I --> J[StudyPlanService.createWeeklyPlan]
    J --> K[StudyReminderTaskStore.saveBatch 入库]
    K --> L[buildSuccessReply 构建7天明细回执]
    L --> M[sendReply 发回私聊或群聊]
    M --> N[markStatus=1 success]
```

## 2. 阅读入口（建议顺序）

1. 事件入口：src/main/java/com/it/ai/aiagent/service/FeishuLongConnectionService.java
2. 意图解析：src/main/java/com/it/ai/aiagent/service/FeishuPlanIntentService.java
3. 计划落库：src/main/java/com/it/ai/aiagent/service/StudyPlanService.java
4. JDBC 入库：src/main/java/com/it/ai/aiagent/store/StudyReminderTaskStore.java
5. 回执发送：src/main/java/com/it/ai/aiagent/service/ReminderNotificationService.java
6. 兼容路径（可选）：src/main/java/com/it/ai/aiagent/controller/FeishuWebhookController.java

## 3. 关键断点清单（按执行先后）

### BP-1：长连接初始化
- 位置：FeishuLongConnectionService.start
- 目的：确认 SDK 长连接是否真的启动。
- 重点变量：feishuAppId, feishuAppSecret, longConnectionEnabled, workerThreads
- 预期：日志出现“飞书长连接客户端已启动”。

### BP-2：飞书消息进入处理器
- 位置：FeishuLongConnectionService.start 内 onP2MessageReceiveV1.handle
- 目的：确认飞书事件确实投递到你的进程。
- 重点变量：event（SDK对象）, raw
- 预期：raw 不为空，能看到 message/chat_type/message_type。

### BP-3：事件解析与基础字段提取
- 位置：FeishuLongConnectionService.processMessageEvent（前半段）
- 目的：确认 eventId/messageId/openId/chatId 等解析正确。
- 重点变量：eventId, messageId, openId, chatType, messageType, content
- 预期：群聊应为 chatType=group，私聊为 p2p。

### BP-4：幂等去重
- 位置：FeishuEventLogStore.tryInsertEvent
- 目的：确认不会重复消费同一个事件。
- 重点变量：eventId, rows
- 预期：首次 rows>0，重复消息 rows=0。

### BP-5：可处理性判断
- 位置：FeishuLongConnectionService.processMessageEvent 中
  - 文本判断：messageType
  - 群聊@判断：isGroupMentioned
- 目的：定位“群里@了但没反应”的核心分支。
- 重点变量：messageType, chatType, mentions, feishuBotOpenId
- 预期：
  - 私聊：直接通过。
  - 群聊：mentions 命中机器人后通过。

### BP-6：文本清洗
- 位置：FeishuLongConnectionService.normalizeUserText
- 目的：确认 @占位符已去掉，模型能吃到干净输入。
- 重点变量：text（清洗前/后）, mentions

### BP-7：意图识别
- 位置：FeishuPlanIntentService.processPlanIntent -> isPlanIntent
- 目的：确认消息被识别为“学习计划意图”。
- 重点变量：userText, normalized

### BP-8：模型抽取与规则兜底
- 位置：FeishuPlanIntentService.tryExtractByAi / normalizeDays / parseTopicsFromText
- 目的：定位“抽取失败、只抽1条、主题丢失”的问题。
- 重点变量：extracted, inputDays, topicList, defaultTime
- 预期：
  - 你的句式“主题依次是 A、B...”会被 topicList 抽成最多7条。

### BP-9：时间未来化
- 位置：FeishuPlanIntentService.ensureFutureSchedule
- 目的：避免被 StudyPlanService 过滤成“没有可保存任务”。
- 重点变量：trigger, now, lastTrigger
- 预期：每条 trigger 都在未来，且严格递增。

### BP-10：入库前校验
- 位置：StudyPlanService.createWeeklyPlan
- 目的：确认 request.days 合法、channels=feishu。
- 重点变量：request, tasks
- 预期：tasks.size > 0。

### BP-11：批量入库
- 位置：StudyReminderTaskStore.saveBatch
- 目的：确认最终 SQL 参数都正确。
- 重点变量：task.planName, task.studyDate, task.reminderTime, task.feishuOpenId

### BP-12：回执发消息
- 位置：
  - FeishuPlanIntentService.buildSuccessReply
  - FeishuLongConnectionService.sendReply
  - ReminderNotificationService.sendFeishuText / sendFeishuChatText
- 目的：确认回执文案、回执目标（私聊/群聊）正确。
- 重点变量：reply 文本、chatType、openId、chatId

## 4. 状态码对照（feishu_event_log.process_status）

- 0：待处理（插入后尚未更新）
- 1：成功（已入库并已回复）
- 2：忽略（例如不是文本、群里没@）
- 3：失败（处理中抛异常）

## 5. 快速排障路径

1. 飞书平台显示 SUCCESS，但系统没反应
- 先看 BP-2 是否命中（是否到进程）
- 再看 BP-5（是否被“非文本/未@”分支忽略）

2. 命中意图但报“没有可保存任务”
- 看 BP-8/BP-9（主题是否抽取、时间是否已顺延到未来）

3. 数据入库了但飞书没回消息
- 看 BP-12（sendReply 走的是 open_id 还是 chat_id，是否有权限）

## 6. 练习建议（一次完整调试）

1. 在 BP-2、BP-5、BP-8、BP-10、BP-12 打断点。
2. 群里 @机器人 发送：
   帮我安排接下来一周学习计划，每天20:00，主题依次是 RAG基础、向量检索、重排、评测、优化、实战、复盘
3. 逐步观察：
   - mentions 是否命中
   - topicList 是否 7 条
   - tasks.size 是否 > 0
   - reply 是否包含 7 天明细

## 7. 补充

- 当前主链路建议使用长连接（FeishuLongConnectionService）。
- FeishuWebhookController 是兼容路径，可用于理解传统回调模式。
