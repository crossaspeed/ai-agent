# AI Agent

一个面向学习场景的 AI 智能体项目，支持流式问答、知识库检索（RAG）、学习计划提醒、飞书消息处理。

后端采用 Spring Boot + LangChain4j，前端采用 Next.js App Router，并通过 Nginx 统一入口完成前后端聚合部署。

## 功能概览

- 流式对话（SSE，逐 token 返回）
- 历史会话管理（按 memoryId 存取）
- 文档上传与向量检索（Pinecone，可降级内存向量库）
- 学习计划创建、查询、启停、测试发送
- 飞书消息处理（Webhook 兼容 + 长连接主链路）
- Docker Compose 一键部署（含 HTTPS 脚本）

## 技术栈

- 后端：Java 17、Spring Boot 3.2.6、LangChain4j 1.0.0-beta3、MongoDB、MySQL、Redis
- 前端：Next.js 16、React 19、TypeScript、Tailwind CSS v4
- 部署：Docker Compose、Nginx、Certbot

## 项目结构

```text
.
├─ src/main/java/com/it/ai/aiagent
│  ├─ controller/   # REST API 与 SSE
│  ├─ service/      # 业务编排（计划、提醒、飞书路由）
│  ├─ store/        # 持久化访问（MongoTemplate/JdbcTemplate/Repository）
│  ├─ assistant/    # LangChain4j @AiService 接口
│  ├─ config/       # Bean 配置（memory、embedding、retriever）
│  └─ bean/         # DTO / Entity / View
├─ src/main/resources
│  ├─ application.yml
│  ├─ application-prod.yml
│  ├─ schema.sql
│  └─ prompt-template.txt
├─ frontend
│  ├─ src/app
│  ├─ src/components
│  └─ src/lib
├─ deploy
│  ├─ docker/
│  ├─ nginx/
│  └─ scripts/
├─ docker-compose.yml
└─ docker-compose.managed.yml
```

## 架构说明

### 对话链路

1. 前端请求 `POST /api/agent/chat`
2. Next.js rewrite 转发到后端 `/agent/chat`
3. `XiaocController` 通过 `XiaocAgent` 调用模型并流式输出
4. 会话历史持久化到 MongoDB
5. 前端实时渲染 SSE 内容

### 学习计划链路

1. 前端提交 `POST /agent/study-plan/weekly`
2. `StudyPlanService` 校验与组装任务
3. `StudyReminderTaskStore` 写入 MySQL
4. 定时任务和 Redis 队列驱动提醒发送

### 飞书链路

- 主链路：`FeishuLongConnectionService` 长连接消费
- 兼容链路：`POST /feishu/event` Webhook
- 统一由 `FeishuMessageRouterService` 路由到计划/问答/帮助策略

## 快速开始

### 方式 A：Docker Compose 一键启动（推荐）

1. 复制环境变量模板

```bash
cp .env.example .env
```

Windows PowerShell 可使用：

```powershell
Copy-Item .env.example .env
```

2. 填写 `.env` 中的密钥和数据库口令（至少补齐 LLM、Embedding、Pinecone）

3. 启动服务

```bash
docker compose up -d --build
```

4. 访问

- 前端：http://localhost
- 后端（经 Nginx 转发）：http://localhost/api
- 后端直连（容器内）：http://backend:8080

5. 常用排查命令

```bash
docker compose ps
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f nginx
```

### 方式 B：本地源码开发

前置要求：

- Java 17+
- Maven 3.9+
- Node.js 20+

步骤：

1. 启动依赖（可选，若本机已安装 MySQL/MongoDB/Redis 可跳过）

```bash
docker compose up -d mysql mongodb redis
```

2. 启动后端（仓库根目录）

```bash
mvn compile test-compile
mvn spring-boot:run
```

3. 启动前端（`frontend` 目录）

```bash
cd frontend
npm ci
npm run dev
```

4. 访问

- 前端：http://localhost:3000
- 后端：http://localhost:8080

## 托管数据库模式

当 MySQL/MongoDB 使用云托管服务时：

1. 复制模板

```bash
cp .env.managed.example .env
```

Windows PowerShell 可使用：

```powershell
Copy-Item .env.managed.example .env
```

2. 填写托管数据库连接信息

- `MYSQL_HOST` `MYSQL_PORT` `MYSQL_DB` `MYSQL_USER` `MYSQL_PASSWORD`
- `MONGODB_URI`

3. 使用托管模式编排文件启动

```bash
docker compose -f docker-compose.managed.yml up -d --build
```

## 配置说明

### Profile 约定

- `application.yml`：本地开发历史配置（仅建议本地调试使用）
- `application-prod.yml`：生产配置模板（通过环境变量注入）

生产部署建议固定使用 `SPRING_PROFILES_ACTIVE=prod`。

### 核心环境变量

| 变量 | 说明 | 必填 |
| --- | --- | --- |
| `DEEPSEEK_API_KEY` | 对话模型密钥 | 是 |
| `CHAT_MODEL_BASE_URL` | 对话模型地址，默认 `https://api.deepseek.com` | 否 |
| `CHAT_MODEL_NAME` | 对话模型名，默认 `deepseek-chat` | 否 |
| `EMBEDDING_API_KEY` | 向量化模型密钥 | 是 |
| `EMBEDDING_BASE_URL` | 向量化模型地址 | 否 |
| `PINECONE_API_KEY` | Pinecone 密钥 | 建议 |
| `PINECONE_INDEX_NAME` | Pinecone 索引名，默认 `agent-index` | 否 |
| `MYSQL_PASSWORD` | MySQL 密码 | 是 |
| `MYSQL_ROOT_PASSWORD` | 本地 compose 的 root 密码 | 仅本地 compose |
| `MONGODB_URI` | Mongo 连接串 | 托管模式必填 |
| `FEISHU_APP_ID` | 飞书应用 ID | 飞书功能必填 |
| `FEISHU_APP_SECRET` | 飞书应用密钥 | 飞书功能必填 |
| `FEISHU_BOT_OPEN_ID` | 机器人 open_id（群聊 @ 识别） | 建议 |
| `FEISHU_CALLBACK_TOKEN` | Webhook 校验 token | 建议 |

完整示例见：[.env.example](.env.example) 和 [.env.managed.example](.env.managed.example)

## API 速览

### 对话与历史

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/agent/chat` | SSE 对话流 |
| `GET` | `/agent/history/{memoryId}` | 指定会话历史 |
| `GET` | `/agent/history/sessions` | 会话列表 |

`POST /agent/chat` 请求体示例：

```json
{
	"memoryId": 1,
	"message": "帮我解释一下 RAG 的召回和重排",
	"type": "qa"
}
```

### 知识库

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/knowledge/topics` | 获取主题列表 |
| `POST` | `/knowledge/upload` | 上传文档并向量化 |

上传示例：

```bash
curl -X POST "http://localhost:8080/knowledge/upload" \
	-F "topic=计算机网络" \
	-F "file=@./docs/network-notes.pdf"
```

### 学习计划

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/agent/study-plan/weekly` | 创建周计划 |
| `GET` | `/agent/study-plan/tasks?days=14` | 查询任务 |
| `PATCH` | `/agent/study-plan/tasks/{id}/status?enabled=true` | 启停任务 |
| `POST` | `/agent/study-plan/tasks/{id}/test` | 测试发送 |

创建周计划示例：

```json
{
	"planName": "一周 RAG 学习计划",
	"timezone": "Asia/Shanghai",
	"channels": ["feishu"],
	"feishuOpenId": "ou_xxx",
	"days": [
		{
			"date": "2026-04-07",
			"reminderTime": "20:00",
			"ragTopic": "向量检索",
			"studyContent": "学习向量化、索引与召回流程"
		}
	]
}
```

### 飞书回调与调试

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/feishu/event` | 飞书 Webhook 兼容入口 |
| `GET` | `/agent/test-stream` | SSE 调试接口 |

## 数据存储

- MongoDB
	- `chat_messages`：会话历史
	- `topics`：知识主题与文档计数
- MySQL
	- `study_reminder_task`：学习任务与发送状态
	- `feishu_event_log`：飞书事件幂等日志

表结构见 [src/main/resources/schema.sql](src/main/resources/schema.sql)。

## 测试与质量检查

后端测试：

```bash
mvn test
mvn test -Dtest="XiaocControllerTest"
```

前端检查：

```bash
cd frontend
npm run lint
npm run build
```

## 常见问题

### 1. 前端收不到流式响应或明显延迟

- 检查后端是否返回 `text/event-stream`
- 检查 Nginx 是否禁用缓存转发（`proxy_buffering off`）
- 后端已设置 `X-Accel-Buffering: no`，若自行改代理配置需保持一致

### 2. 学习计划创建失败

- `channels` 目前仅支持 `feishu`
- 选择飞书渠道时必须传 `feishuOpenId`
- `date` 必须是 `yyyy-MM-dd`，`reminderTime` 必须是 `HH:mm`

### 3. Pinecone 连接异常

- 检查 `PINECONE_API_KEY` 与 `PINECONE_INDEX_NAME`
- 确认部署环境可访问 Pinecone 服务

### 4. IDE 提示 Lombok getter/setter 不存在

- 先以 Maven 编译结果为准
- 检查 IDE 注解处理器是否启用

### 5. 中文写入 MySQL 报编码错误

- 确保 MySQL 与表使用 `utf8mb4`
- 本项目 `schema.sql` 已包含 `utf8mb4` 相关设置

## 安全说明（强烈建议）

- 不要在仓库提交真实 API Key/数据库密码
- 生产环境统一使用环境变量注入
- 若历史提交中已泄露密钥：立即轮换，并重写 Git 历史

扫描敏感信息示例：

```bash
git grep -nE "(sk-|pcsk_|app-secret|api-key|password\s*:|MYSQL_PASSWORD|PINECONE_API_KEY)"
```

## 文档导航

- 项目协作规范：[CONTRIBUTING.md](CONTRIBUTING.md)
- 后端流程讲解：[BACKEND_PROJECT_FLOW.md](BACKEND_PROJECT_FLOW.md)
- 飞书长连接阅读导图：[docs/feishu-callback-reading-map.md](docs/feishu-callback-reading-map.md)
- 部署与 HTTPS：[deploy/README.md](deploy/README.md)
- 前端说明：[frontend/README.md](frontend/README.md)
- 安全策略：[SECURITY.md](SECURITY.md)
- 变更记录：[CHANGELOG.md](CHANGELOG.md)
- 发布模板：[.github/release-template.md](.github/release-template.md)

## 性能优化待办

- [ ] 检索 RAG 时返回条数（当前为 3）按场景可配置
- [ ] 对话上下文窗口长度（当前偏保守）优化
- [ ] 评估更快模型或多模型路由策略

## License

本项目使用 [MIT License](LICENSE)。
