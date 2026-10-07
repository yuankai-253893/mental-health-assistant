# 心理健康助手 · 前端工程

基于 **Vue 3 + Vite + Element Plus** 的心理健康管理系统前端，与 [`Backend/`](../Backend) 配套，覆盖用户端与管理端全部页面。

---

## 技术栈

| 分类 | 技术 |
|---|---|
| 框架 | Vue 3.5、Vite 8 |
| UI | Element Plus 2.14 + 图标库 |
| 状态/路由 | Pinia、vue-router 4 |
| 请求 | axios（统一拦截器）、@microsoft/fetch-event-source（SSE 流式对话） |
| 富文本/图表 | wangEditor 5（文章编辑器）、ECharts 6（仪表盘图表） |
| 样式 | SCSS |

---

## 页面清单

### 认证
| 路由 | 页面 | 说明 |
|---|---|---|
| `/auth/login` | Login | 登录 |
| `/auth/register` | Register | 注册 |

### 用户端
| 路由 | 页面 | 说明 |
|---|---|---|
| `/home` | Home | 首页 |
| `/consultation` | Consultation | AI 心理咨询（SSE 流式对话、会话管理、情绪花园） |
| `/emotion-diary` | EmotionDiary | 情绪日志（今日记录回显、评分/情绪/生活指标） |
| `/knowledge` | Knowledge | 知识库文章列表 |
| `/knowledge/article/:id` | ArticleDetail | 文章详情 |

### 管理端（`/back/*`，仅管理员角色可访问）
| 路由 | 页面 | 说明 |
|---|---|---|
| `/back/dashboard` | Dashboard | 数据分析仪表盘（概览卡片/情绪趋势/咨询统计/用户活跃度） |
| `/back/knowledge` | Knowledge | 知识库管理（文章 CRUD、分类树、富文本编辑器） |
| `/back/consultations` | Consultations | 咨询会话管理（消息查看） |
| `/back/emotional` | Emotional | 情绪日记管理 |

---

## 目录结构

```
Frontend/src
├── api/            # 接口封装（admin.js / user.js / auth.js）
├── utils/
│   ├── request.js  # axios 实例 + 拦截器（token 注入、业务码处理）
│   └── format.js   # 时间格式化等
├── router/         # 路由 + 守卫（按 userType 控制 /back 与 /auth 访问）
├── stores/         # Pinia 状态
├── config/         # 全局配置（文件访问基础地址）
├── components/     # 公共组件（后台布局/表格、前端 MarkdownRenderer 等）
├── views/
│   ├── auth/       # 登录、注册
│   ├── frontend/   # 用户端页面
│   └── backend/    # 管理端页面
├── assets/         # 静态资源
└── main.js
```

---

## 快速启动

**环境要求**：Node.js 18+；需先启动后端（见 [`Backend/README.md`](../Backend/README.md)）。

```bash
cd Frontend
npm install        # 安装依赖
npm run dev        # 启动开发服务器，默认 http://localhost:5173
```

### 代理配置

`vite.config.js` 已配置两个代理，全部转发到本机后端 `http://127.0.0.1:8080`：

| 前缀 | 用途 |
|---|---|
| `/api` | 业务接口 |
| `/files` | 上传文件静态资源（图片地址走相对路径，换环境无需改代码） |

生产部署时由网关/nginx 将 `/api`、`/files` 转发到后端即可。

---

## 与后端对接约定

- **鉴权**：登录后 token 存 `localStorage`，axios 拦截器自动注入请求头 `token`；接口返回业务码 `200` 为成功、`-1` 为未登录（自动清除登录态并跳转登录页）。
- **SSE 流式对话**：走 `fetch-event-source`（不受 axios 5s 超时限制），请求头同样携带 `token`，逐段渲染 AI 回复。
- **路由守卫**：`userType === 2`（管理员）只能访问 `/back/*`；`userType === 1`（普通用户）禁止访问 `/back` 与 `/auth`；未登录可浏览知识库等公开页。前端守卫只是体验层，真正的权限控制由后端 `@PreAuthorize` 兜底。

---

## 安全设计

- **XSS 防护**：用户/AI 消息默认纯文本插值渲染（`{{ }}`）保留换行，不解析 HTML；AI 消息的 Markdown 渲染器先转义 `<`/`>` 再处理语法，链接仅允许 `http/https/mailto` 及相对地址（防 `javascript:` 伪协议）。
- **越权防护**：管理端页面通过路由守卫 + 后端角色校验双重控制；水平越权由后端按会话归属校验兜底。
- **输入约束**：表单统一走 Element Plus 校验 + 后端 DTO 参数校验（`@Valid`），消息长度限制 500 字、日记字段长度限制等。

---

## 构建

```bash
npm run build      # 产物输出到 dist/
npm run preview    # 本地预览构建产物
```
