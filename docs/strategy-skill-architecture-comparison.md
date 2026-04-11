# 策略路由 + Skill 编排 对比说明

## 1. 先给结论

你的当前实现思路是对的：
- 用策略模式做路由（help、plan、qa）是合理且稳定的。
- 在 plan 内再做规则识别 + AI 意图回退，也很工程化。

所谓“策略做路由，skill 做能力编排”，并不是推翻现有架构，而是把“每个策略内部的大方法”拆成可复用、可观测、可组合的能力步骤。

一句话：
- 路由层不动（保留确定性分发）
- 执行层升级（由单体服务逻辑升级为 Skill Pipeline）

---

## 2. 与现有方式相比的优势

### 2.1 解耦更清晰
- 现有：路由决策、意图识别、参数抽取、业务执行、回复拼装，很多逻辑集中在同一个服务里。
- 新方式：路由只负责“去哪里”，Skill 只负责“怎么做”。

### 2.2 能力复用率更高
- 例如“时间标准化”“澄清问题生成”“风险校验”可以在 plan、qa、未来新模式中复用。
- 避免每个策略内部复制一套类似流程。

### 2.3 可观测性更好
- 可以按 Skill 记录成功率、澄清率、回退率、平均耗时。
- 出问题时能定位到具体步骤（意图识别失败、抽取失败、校验失败），而不是只看到“整个请求失败”。

### 2.4 扩展成本更低
- 新增模式时，不必复制整套服务流程；只需组合已有 Skill + 少量新 Skill。
- 适合后续增加“提醒总结”“周报生成”“错题复盘”等能力。

### 2.5 风险控制更精细
- 对高风险操作（删除、批量修改）可以挂强校验 Skill 或二次确认 Skill。
- 比在大方法里 scattered if/else 更容易维护。

---

## 3. 到底替换了原有代码的什么

不是替换路由框架本身，而是替换策略内部的执行组织方式。

### 3.1 保留不变的部分
- 策略接口与路由主干保留：
  - [src/main/java/com/it/ai/aiagent/service/FeishuMessageRouteStrategy.java](../src/main/java/com/it/ai/aiagent/service/FeishuMessageRouteStrategy.java)
  - [src/main/java/com/it/ai/aiagent/service/FeishuMessageRouterService.java](../src/main/java/com/it/ai/aiagent/service/FeishuMessageRouterService.java)
- 现有路由策略类保留：
  - [src/main/java/com/it/ai/aiagent/service/FeishuHelpRouteStrategy.java](../src/main/java/com/it/ai/aiagent/service/FeishuHelpRouteStrategy.java)
  - [src/main/java/com/it/ai/aiagent/service/FeishuPlanRouteStrategy.java](../src/main/java/com/it/ai/aiagent/service/FeishuPlanRouteStrategy.java)
  - [src/main/java/com/it/ai/aiagent/service/FeishuQaPrefixedRouteStrategy.java](../src/main/java/com/it/ai/aiagent/service/FeishuQaPrefixedRouteStrategy.java)
  - [src/main/java/com/it/ai/aiagent/service/FeishuQaFallbackRouteStrategy.java](../src/main/java/com/it/ai/aiagent/service/FeishuQaFallbackRouteStrategy.java)

### 3.2 主要替换的部分
- 目前集中在 plan 处理服务中的流程编排逻辑，会被拆为可组合 Skill：
  - [src/main/java/com/it/ai/aiagent/service/FeishuPlanIntentService.java](../src/main/java/com/it/ai/aiagent/service/FeishuPlanIntentService.java)

可拆分示例：
- IntentResolveSkill：规则识别 + AI 回退（含置信度阈值）
- ExtractPlanSkill：结构化抽取
- NormalizeScheduleSkill：日期时间标准化与顺延
- ValidateRiskSkill：删除/修改等高风险检查
- ExecutePlanCommandSkill：调用 StudyPlanService 执行业务
- RenderReplySkill：统一回复文案

### 3.3 Agent 的定位变化
- [src/main/java/com/it/ai/aiagent/assistant/StudyPlanIntentAgent.java](../src/main/java/com/it/ai/aiagent/assistant/StudyPlanIntentAgent.java)
- [src/main/java/com/it/ai/aiagent/assistant/StudyPlanExtractAgent.java](../src/main/java/com/it/ai/aiagent/assistant/StudyPlanExtractAgent.java)

它们不再直接被大服务自由调用，而是作为某些 Skill 的依赖步骤被编排。

---

## 4. 前后对比（简表）

| 维度 | 现有方式 | 策略 + Skill 编排 |
|---|---|---|
| 路由 | 策略模式，已很好 | 保持不变 |
| 执行组织 | 服务内大方法编排 | Skill Pipeline 编排 |
| 复用性 | 中等，跨场景复用难 | 高，步骤可复用 |
| 可观测性 | 粗粒度（请求级） | 细粒度（Skill 级） |
| 扩展新功能 | 往现有服务继续堆逻辑 | 组合 Skill，增量扩展 |
| 风险控制 | 分散在 if/else 中 | 可插拔 Guard Skill |
| 维护成本（长期） | 逐步上升 | 更可控 |

---

## 5. 原有项目分层图（现状）

### 5.1 分层
- 接入层：飞书回调/控制器
- 路由层：FeishuMessageRouterService + 多个 RouteStrategy
- 业务编排层：FeishuPlanIntentService（规则 + Agent + 执行 + 回复）
- 领域服务层：StudyPlanService、FeishuQaService
- 模型能力层：StudyPlanIntentAgent、StudyPlanExtractAgent
- 数据层：Repository / Mongo / MySQL

### 5.2 流程图（文本）

用户消息
  -> 路由层（按 type 或顺序命中策略）
  -> Plan 策略
  -> FeishuPlanIntentService
     -> 规则意图识别
     -> AI 意图回退
     -> AI 抽取
     -> 标准化/兜底
     -> 调用 StudyPlanService
     -> 拼装回复
  -> 返回结果

---

## 6. 新方法分层图（策略路由 + Skill 编排）

### 6.1 分层
- 接入层：飞书回调/控制器
- 路由层：FeishuMessageRouterService + RouteStrategy（保持不变）
- 编排层：SkillOrchestrator（按模式选择 Pipeline）
- Skill 层：IntentResolveSkill、ExtractSkill、NormalizeSkill、ValidateSkill、ExecuteSkill、RenderSkill
- 领域服务层：StudyPlanService、FeishuQaService
- 模型能力层：IntentAgent、ExtractAgent（作为 Skill 依赖）
- 数据层：Repository / Mongo / MySQL

### 6.2 流程图（文本）

用户消息
  -> 路由层（策略）
  -> Plan 策略
  -> PlanSkillPipeline
     -> IntentResolveSkill
     -> ExtractPlanSkill
     -> NormalizeScheduleSkill
     -> ValidateRiskSkill
     -> ExecutePlanCommandSkill
     -> RenderReplySkill
  -> 返回结果

---

## 7. 迁移建议（低风险增量）

1. 先只拆 Plan 场景，不动 Help 和 QA。
2. 第一阶段只抽 2 个 Skill：IntentResolveSkill、ExtractPlanSkill。
3. 第二阶段再抽 Normalize 和 Validate。
4. 最后统一回复渲染 Skill，并加指标埋点。

这样可以在不影响现网稳定性的前提下，逐步把“服务内大方法”演进为“可组合能力编排”。
