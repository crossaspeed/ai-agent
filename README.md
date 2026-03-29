# AI Agent

一个基于 Spring Boot + LangChain4j + Next.js 的学习型智能体项目，支持流式对话、知识库上传检索（RAG）、学习计划管理与飞书回调。

## 功能特性

- 流式对话（SSE）
- 历史会话管理
- 文档上传与主题化知识库管理
- 学习计划创建、查询与提醒
- 飞书事件回调与长连接处理（可开关）
- Docker Compose 一键部署（含 HTTPS 脚本）

## 技术栈

- 后端: Java 17, Spring Boot 3.2.6, LangChain4j, MongoDB, MySQL
- 前端: Next.js 16, React 19, TypeScript
- 部署: Docker Compose, Nginx, Certbot

## 项目结构

- `src/main/java`: 后端业务代码
- `src/main/resources`: 配置与 SQL 初始化脚本
- `frontend`: 前端代码
- `deploy`: 部署脚本与 Nginx 配置
- `docker-compose.yml`: 本地数据库模式部署
- `docker-compose.managed.yml`: 托管数据库模式部署

## 快速开始

### 1) 本地开发

后端（项目根目录）:

```bash
mvn compile test-compile
mvn spring-boot:run
```

前端（frontend 目录）:

```bash
npm ci
npm run dev
```

默认访问:

- 前端: <http://localhost:3000>
- 后端: <http://localhost:8080>

### 2) Docker 部署

请先阅读 [deploy/README.md](deploy/README.md)。

```bash
docker compose up -d --build
```

## 主要接口

- 对话: `POST /agent/chat`（SSE）
- 历史会话列表: `GET /agent/history/sessions`
- 历史消息: `GET /agent/history/{memoryId}`
- 知识库主题: `GET /knowledge/topics`
- 文档上传: `POST /knowledge/upload`
- 学习计划创建: `POST /agent/study-plan/weekly`
- 学习任务查询: `GET /agent/study-plan/tasks`

## 配置说明

建议使用环境变量，不要在仓库中保存真实密钥。

关键变量示例:

- `DEEPSEEK_API_KEY`
- `EMBEDDING_API_KEY`
- `PINECONE_API_KEY`
- `MYSQL_PASSWORD`
- `MYSQL_ROOT_PASSWORD`
- `FEISHU_APP_ID`
- `FEISHU_APP_SECRET`

示例文件:

- [.env.example](.env.example)
- [.env.managed.example](.env.managed.example)

## 上传 GitHub 前必做清单

- 轮换全部已暴露的密钥（DeepSeek、Pinecone、飞书、数据库）
- 确认 `.env` 未提交
- 检查是否有明文凭据:

```bash
git grep -nE "(sk-|pcsk_|app-secret|api-key|password\s*:|MYSQL_PASSWORD|PINECONE_API_KEY)"
```

- 如果历史提交中出现过密钥，重写 Git 历史并强制推送（推荐 `git filter-repo`）
- 补充开源基础文件: `LICENSE`, `CONTRIBUTING.md`, `SECURITY.md`

## 参考优秀开源项目的实践

- 文档优先: 快速开始 + 配置 + 常见问题
- 默认安全: 密钥全部环境变量化，禁止明文提交
- 可观测性: 保留部署日志、健康检查和故障排查路径
- 可维护性: 分层清晰，接口与部署文档同步更新

## 部署与运维

- Docker/HTTPS 操作说明见 [deploy/README.md](deploy/README.md)
- 前端说明见 [frontend/README.md](frontend/README.md)

## 发布与变更记录

- 发布说明模板: [.github/release-template.md](.github/release-template.md)
- 变更日志: [CHANGELOG.md](CHANGELOG.md)

## License

本项目使用 [MIT License](LICENSE)。
