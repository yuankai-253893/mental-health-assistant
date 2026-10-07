# 心理健康助手 · 后端服务

基于 Spring Boot + MyBatis-Plus + Spring AI 的心理健康管理系统后端，覆盖用户、AI 心理咨询、情绪日记、知识库、文件上传、数据分析六大模块。

---

## 技术栈

| 分类 | 技术 |
|---|---|
| 框架 | Spring Boot 4.1.0、Spring MVC |
| ORM | MyBatis-Plus 3.5.17（分页插件 / 条件构造器 / 原生 `@Select` 聚合） |
| AI | Spring AI 2.0.1（硅基流动 Qwen2.5，流式 SSE 对话） |
| 鉴权 | java-jwt 4.4.0（自定义 JWT 过滤器 + Redis Token 黑名单登出，Redis 故障 fail-open 降级；管理端接口方法级 @PreAuthorize） |
| 安全 | BCrypt 密码加密、登录防暴力破解 + 注册 IP 限流（Redis 计数器）、文件扩展名白名单 + 魔数校验 + UUID 文件名 |
| 数据库 | MySQL 8.0、Redis 7.x（Token 黑名单 / 登录限流 / 注册限流 / 分类树缓存 / 阅读量计数 / 今日活跃） |
| 工具 | Lombok、Hutool、Jakarta Validation |
| 部署 | Docker、Docker Compose（MySQL + Redis + App 一键编排） |
| 构建 | Maven、Java 17+ |

---

## 功能模块与接口

### 1. 用户模块
| 接口 | 方法 | 说明 |
|---|---|---|
| `/api/user/add` | POST | 注册（仅允许普通用户，封闭提权漏洞；同 IP 一天最多 3 个账号，Redis 计数限流） |
| `/api/user/login` | POST | 登录，返回 JWT（Redis 计数防暴力破解：5 次失败锁 15 分钟） |
| `/api/user/current` | GET | 获取当前登录用户 |
| `/api/user/logout` | POST | 登出（Redis Token 黑名单） |

### 2. AI 心理咨询
| 接口 | 方法 | 说明 |
|---|---|---|
| `/api/psychological-chat/session/start` | POST | 创建咨询会话 |
| `/api/psychological-chat/stream` | POST | 流式 SSE 对话（Spring AI） |
| `/api/psychological-chat/sessions` | GET | 分页查询会话 |
| `/api/psychological-chat/sessions/{sessionId}/messages` | GET | 会话消息列表 |
| `/api/psychological-chat/sessions/{sessionId}` | DELETE | 删除会话 |
| `/api/psychological-chat/session/{sessionId}/emotion` | GET | 会话情绪分析结果 |

### 3. 情绪日记
| 接口 | 方法 | 说明 |
|---|---|---|
| `/api/emotion-diary` | POST | 创建/更新（按 `user_id + diary_date` upsert，每天一篇） |
| `/api/emotion-diary/admin/page` | GET | 管理端分页 |
| `/api/emotion-diary/admin/{id}` | DELETE | 删除 |

### 4. 知识库
| 接口 | 方法 | 权限 | 说明 |
|---|---|---|---|
| `/api/knowledge/category/tree` | GET | 公开 | 分类树（一次查全 → 内存挂树，Redis 缓存 TTL 1h） |
| `/api/knowledge/article/page` | GET | 公开 | 文章分页（用户端按阅读量排序，排序字段白名单防注入） |
| `/api/knowledge/article/{id}` | GET | 公开 | 文章详情（未发布仅管理员可见；阅读量 Redis INCR 自增 + 定时刷库） |
| `/api/knowledge/admin/article/page` | GET | 管理员 | 管理端文章分页 |
| `/api/knowledge/article` | POST | 管理员 | 新增文章（雪花 ID 主键） |
| `/api/knowledge/article/{id}` | PUT | 管理员 | 更新 |
| `/api/knowledge/article/{id}/status` | PUT | 管理员 | 发布/下线 |
| `/api/knowledge/article/{id}` | DELETE | 管理员 | 删除 |

### 5. 文件上传
| 接口 | 方法 | 说明 |
|---|---|---|
| `/api/file/upload` | POST | 图片/文档上传（扩展名白名单 + 文件头魔数校验 + UUID 文件名 + 10MB 大小限制） |

### 6. 数据分析
| 接口 | 方法 | 说明 |
|---|---|---|
| `/api/data-analytics/overview` | GET | 管理端仪表盘聚合：系统概览（用户/今日活跃/日记/会话/今日新增，今日活跃用 Redis HyperLogLog）、近 7 日情绪趋势（按天补零）、咨询统计（7 日会话趋势）、用户活跃度（活跃/新增/日记用户/咨询用户，日记+会话 UNION 去重） |

> 需鉴权接口统一用请求头 `token` 传递 JWT；管理端接口统一 `/admin` 前缀 + `@PreAuthorize` 角色校验。

---

## 项目结构

```
src/main/java/com/yuankai/aispringboot
├── controller/     # 接口层（登录鉴权后调用 Service）
├── service/        # 业务层（User/EmotionDiary/KnowledgeCategory/Consultation*/DataAnalytics/ActiveUserRecord 等）
├── AiService/      # Spring AI 集成（PsychologicalSupportService：会话创建 + SSE 流式对话）
├── mapper/         # MyBatis-Plus Mapper + 原生 @Select 聚合 SQL
├── entity/         # 数据库实体
├── DTO/
│   ├── command/    # 入参（带校验注解）
│   ├── query/      # 查询条件
│   └── response/   # 出参（含 DataAnalyticsResponseDTO 分组结构）
├── config/         # WebConfig 静态映射、SecurityConfig 鉴权、MyBatis-Plus 分页插件、JwtAuthenticationFilter
├── common/         # Result / ResultCode 统一返回
├── consts/         # RedisKeyConsts 集中 Redis Key 常量
├── exception/      # BusinessException + 全局异常处理
├── enumclass/      # UserType / UserStatus 枚举
├── annotation/     # 自定义注解（@OperationLog 操作日志）
├── aspect/         # AOP 切面（OperationLogAspect 操作日志切面）
├── task/           # 定时任务（ArticleReadCountSyncTask 阅读量刷库）
└── util/           # JWT 工具、GetUserInfo、RedisCounterUtil、ValidateMagicNumber
```

## 数据库

9 张表：`user`、`consultation_session`、`consultation_message`、`emotion_diary`、`knowledge_category`、`knowledge_article`、`sys_file_info`、`user_favorite`、`ai_analysis_task`（后两张为预留表，当前业务未启用）。建表脚本见根目录 `mental_health_assistant.sql`。

---

## 快速启动

**环境要求**：JDK 17+、MySQL 8、Redis 6.2+ 。

### 方式一：本地启动

1. 导入数据库：`mysql -u root -p < mental_health_assistant.sql`
2. 启动 Redis：

   ```powershell
   docker run -d --name redis7 --restart unless-stopped -p 6379:6379 -v redis7-data:/data redis:7 redis-server --appendonly yes
   ```

3. 配置环境变量：

   ```powershell
   $env:MYSQL_PASSWORD='你的MySQL密码'
   $env:JWT_SECRET='自定义JWT密钥'
   $env:SILICONFLOW_API_KEY='硅基流动API Key'   # AI 对话用，可填占位
   ```

3. 启动：`mvn spring-boot:run`，默认端口 `8080`
4. 本地存储目录：`file.upload-path`（`application.yml`，上传文件落盘根目录）

### 方式二：Docker 一键启动（推荐）

> 需要先安装 [Docker Desktop](https://www.docker.com/products/docker-desktop/)。

1. 打包应用：`mvn package -DskipTests`
2. 设置环境变量后启动：

   ```powershell
   $env:JWT_SECRET='自定义JWT密钥'
   docker compose up -d --build
   ```

3. 首次启动自动导入建表 SQL 并拉起 MySQL / Redis / 应用三个容器，访问 `http://localhost:8080`
4. 停止：`docker compose down`（加 `-v` 同时删除数据卷）

---

## 技术亮点

- **权限三层防线**：JWT 过滤器（登录）→ Controller 方法级 `@PreAuthorize`（管理端 `/admin`）→ Service 存在性/业务校验（防越权拼 id 访问未发布文章）
- **登录防暴力破解 + 注册 IP 限流**：登录按账号维度 Redis 计数，同一账号 5 次失败锁定 15 分钟；注册按 IP 维度限流，同 IP 一天最多注册 3 个账号（成功才计数，失败注册不消耗配额）；Redis 故障时跳过限流检查（fail-open），登录/注册不中断
- **故障容错（fail-open）**：登录限流查询/记录/清除与 JWT 黑名单查询/写入全部包 try-catch——Redis 不可用时限流视为 0 次失败、黑名单视为未拉黑并放行，系统降级为"无 Redis 辅助安全"运行，恢复后自动一致（代价：故障期间登出不立即生效、暴力破解暂不受限，均为可接受短窗口）
- **Redis 缓存**：分类树 Cache Aside 缓存（TTL 1h + 主动失效），缓存失败降级回查库
- **防注入**：排序字段白名单映射，用户输入不直接拼 SQL；MyBatis-Plus 条件构造器防拼接注入
- **并发安全**：文章阅读量走 Redis INCR 原子自增 + 定时任务刷回 MySQL（GETDEL 原子取增量防丢失），Redis 故障自动降级 SQL 原子自增；情绪日记按 `user_id + diary_date` SQL 原子 upsert（`INSERT ... ON DUPLICATE KEY UPDATE` + 唯一索引），同一天并发提交不会重复插入
- **文件上传安全**：扩展名白名单（拒绝 .exe/.jsp/.html）+ 文件头魔数校验（防 .exe 改名 .jpg 绕过）+ UUID 文件名（防并发覆盖/路径穿越）+ 大小限制
- **统一异常处理**：`BusinessException` + 全局处理器，参数校验/业务异常/系统异常分级返回
- **聚合统计**：今日活跃用 Redis HyperLogLog（PFADD 埋点 + PFCOUNT 去重计数，登录/会话/日记三处埋点，按天 key + 3 天 TTL），Redis 故障自动回退 SQL（UNION 去重口径）
- **操作日志（AOP 切面）**：自定义 `@OperationLog` 注解 + `@Around` 切面统一记录"谁、何时、调了什么接口、请求参数、耗时、成功/失败"；密码等敏感字段自动脱敏、MultipartFile 等不可序列化参数过滤、超长截断；日志落库失败仅告警不阻断业务（fail-safe）
- **复用与工程化**：登录限流、注册限流与阅读量共用 INCR/GETDEL/KEYS，今日活跃共用 PFADD/PFCOUNT，均收口于 `RedisCounterUtil` 公共工具类；活跃埋点单独抽 `ActiveUserRecordService`，登录/会话/日记三模块一行调用
- **容器化部署**：Dockerfile + docker-compose 一键编排 MySQL / Redis / 应用，环境一致开箱即用

---

## 默认账号

| 账号 | 密码 | 角色 |
|---|---|---|
| admin | 123456 | 管理员 |
| test | 123456 | 普通用户 |
