# 心理健康助手 · AI 全栈项目

基于 **Spring Boot 4 + Spring AI + Vue 3** 的心理健康管理系统，覆盖用户鉴权、AI 心理咨询（流式 SSE 对话）、情绪日记、知识库、文件上传、数据分析六大模块。

---

## 目录导航

| 目录 | 状态 | 说明 |
|---|---|---|
| [`Backend/`](./Backend) | 已完成 | Spring Boot 后端服务，六大模块接口全部落地，含完整技术文档 |
| [`Frontend/`](./Frontend) | 已完成 | Vue 3 前端工程，用户端 + 管理端全页面 |

后端的详细设计、接口清单、技术亮点与启动方式见 [`Backend/README.md`](./Backend/README.md)。

---

## 技术栈

| 分类 | 技术 |
|---|---|
| 框架 | Spring Boot 4.1.0、Spring MVC |
| ORM | MyBatis-Plus 3.5.17（分页插件 / 条件构造器） |
| AI | Spring AI 2.0.1（硅基流动 Qwen2.5，流式 SSE 对话） |
| 鉴权 | JWT + 自定义过滤器 + Redis Token 黑名单登出，管理端方法级 @PreAuthorize |
| 数据库 | MySQL 8.0、Redis 7.x |
| 前端 | Vue 3、Vite、Element Plus、Pinia、ECharts、wangEditor |
| 部署 | Docker、Docker Compose |

---

## 功能模块

| 模块 | 关键能力 |
|---|---|
| 用户 | 注册 / 登录 / 登出，BCrypt 加密，登录防暴力破解 + 注册 IP 限流 |
| AI 心理咨询 | 多轮会话管理，Spring AI 流式 SSE 输出，会话情绪分析 |
| 情绪日记 | 按 `user_id + diary_date` SQL 原子 upsert，每天一篇 |
| 知识库 | 分类树，文章 CRUD，阅读量 Redis INCR + 定时刷库，读接口开放未登录访问 |
| 文件上传 | 扩展名白名单 + 文件头魔数校验 + UUID 文件名防覆盖 |
| 数据分析 | 管理端仪表盘：系统概览 / 近 7 日情绪趋势 / 咨询统计 / 用户活跃度 |

---

## Redis 的六种用法

项目中 Redis 不只是缓存，按场景分了六类用法：

| 场景 | 数据结构 / 方案 |
|---|---|
| Token 登出黑名单 | String + TTL |
| 登录防暴力破解 | String 计数器，5 次失败锁 15 分钟 |
| 注册 IP 限流 | String 计数器，同 IP 一天最多注册 3 个账号 |
| 分类树缓存 | Cache Aside，TTL 1 小时 |
| 文章阅读量 | INCR 原子自增 + 定时任务 GETDEL 刷回 MySQL |
| 今日活跃用户 | HyperLogLog，PFADD 埋点 + PFCOUNT 去重 |

所有 Redis 操作收口在 `RedisCounterUtil` 公共工具类，且统一做 fail-open 降级——Redis 不可用时系统降级运行而非直接报错。

---

## 快速开始

**后端**（需先启动 MySQL 与 Redis，并配置环境变量 `MYSQL_PASSWORD`、`JWT_SECRET`、`SILICONFLOW_API_KEY`）：

```bash
cd Backend
mvn spring-boot:run    # 默认端口 8080
```

**前端**：

```bash
cd Frontend
npm install
npm run dev            # 默认端口 5173，访问 http://localhost:5173
```
