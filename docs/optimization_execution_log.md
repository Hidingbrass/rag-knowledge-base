# AI 应用求职项目优化执行记录

> 2026-07-16 起，产品品牌确定为“知途 AI”，定位调整为面向学生、转行者和求职者的
> AI 学习与求职成长平台；底层 RAG、求职工作流和技术标识继续兼容原实现。

> 用途：按真实执行顺序记录项目优化，避免功能、文档、测试和简历材料相互漂移。
>
> 更新规则：每完成一个步骤，立即更新状态、实际改动、验证结果和遗留问题；未验证的能力不得写成“已完成”。

## 1. 本轮目标

本轮先完成影响项目可信度的基础收口，不继续横向堆叠业务功能：

1. 收口 Spring Security/JWT 鉴权，避免业务接口继续默认匿名放行。
2. 为本地演示保留显式、可配置的兼容模式，但生产默认不允许请求参数伪造身份。
3. 同步 README 和核心展示文档中的测试数量、鉴权状态与已完成功能。
4. 增加鉴权回归测试，并运行 Python、Java 全量测试。

## 2. 执行顺序与实时状态

| 顺序 | 工作项 | 状态 | 完成标准 |
| ---: | --- | --- | --- |
| 1 | 建立本执行记录 | 已完成 | 文档进入仓库，后续步骤持续更新 |
| 2 | 盘点 JWT、公开接口与前端登录流程 | 已完成 | 明确允许匿名访问的最小路径集合 |
| 3 | 收口 Spring Security 鉴权 | 已完成 | 业务 API 默认要求 JWT，兼容模式必须显式开启 |
| 4 | 隔离旧版身份 fallback | 已完成 | 严格模式只允许认证请求进入业务 API |
| 5 | 补充鉴权测试 | 已完成 | 覆盖匿名、合法 Token、伪造身份和公开路径 |
| 6 | 同步 README 与核心证据文档 | 已完成 | 测试数字、鉴权描述和项目边界与代码一致 |
| 7 | 运行全量回归 | 已完成 | Python 与 Java 测试全部通过 |
| 8 | 记录结果与下一阶段 | 已完成 | 写明实际结果、风险和后续优先级 |

## 3. 已确认的当前基线

记录时间：2026-07-12（Asia/Shanghai）。

- Python：107 tests passed。
- Java：94 tests passed。
- `app/main.py` 已完成轻量化分层。
- JWT 注册、登录、Token 解析已经存在。
- 当前 Spring Security 只有 `/api/auth/me` 强制认证，其余请求仍为 `permitAll`。
- 未登录请求仍可通过 `userId`、`department` 参数模拟身份。
- README 和多份求职材料仍保留 96/57/79 等旧测试数字。

## 4. 本轮设计原则

- 安全默认：业务 API 默认需要认证。
- 演示兼容必须显式：如果保留匿名演示，只能通过配置项开启，并在文档中标注。
- 身份不可由客户端覆盖：已有 JWT 时，只使用 Token 中的身份。
- 文档以自动化验证结果为准，不手工夸大能力。
- 每个改动先补测试或同步测试，再进入下一步。

## 5. 执行日志

### 2026-07-12：建立执行记录

- 创建本文件。
- 确认本轮优先处理鉴权、事实同步和测试证据。
- 下一步：检查注册/登录接口、静态资源、健康检查和现有前端是否已经携带 Bearer Token。

### 2026-07-12：收口 Spring Security 鉴权

- 确认 Vue3 工作台已经支持注册、登录、Token 本地保存和 `Authorization: Bearer` 请求头。
- 新增 `AuthSecurityProperties`，引入 `AUTH_ALLOW_LEGACY_IDENTITY_PARAMETERS` 显式兼容开关。
- 默认值设为 `false`：除健康检查、注册、登录和静态页面外，所有 `/api/**` 业务接口必须认证。
- 测试环境显式开启 legacy 兼容模式，保留既有 Controller 业务测试；严格模式使用独立测试覆盖。
- 新增严格鉴权测试：公开入口、匿名伪造身份被拒绝、合法 JWT 可访问业务接口。
- 修复知识库列表和单条查询的已登录越权：当时先统一按 owner/department 校验或过滤，2026-07-16 产品转向个人学习后进一步收口为 owner-only。
- 新增 `listAccessible` 集成测试，确保无权知识库不会出现在列表中。
- 修正前端退出登录提示，避免暗示默认可以继续匿名操作。
- 下一步：运行 Java 全量回归；若通过，再同步 README 和核心展示文档。

## 6. 验证记录

| 时间 | 验证命令 | 结果 |
| --- | --- | --- |
| 2026-07-12 修改前 | `.venv/bin/python -m pytest -q` | 107 passed |
| 2026-07-12 修改前 | `mvn -s maven-settings.xml test -q` | 94 passed |
| 2026-07-12 鉴权与权限过滤修改后 | `mvn -s maven-settings.xml test -q` | 98 passed |
| 2026-07-12 本轮终检 | `bash scripts/pre_submit_check.sh` | 全部通过：Markdown 链接、密钥扫描、Compose、Python 107、Java 98 |

## 7. 本轮完成结论

- 业务 API 从默认匿名放行改为默认强制 JWT。
- 注册、登录、健康检查和静态演示入口保持公开。
- legacy 身份参数兼容改为显式配置，默认关闭。
- 知识库列表和详情在该阶段按 owner/department 过滤或校验；2026-07-16 已进一步调整为 owner-only。
- README、演示脚本、简历材料和面试材料统一到 Python 107 / Java 98 的真实回归结果。
- 当前工作台已能携带 Bearer Token，默认安全模式下可继续使用。

### 已知边界

- `debug.html` 仍是旧版参数身份联调页；使用它访问业务接口时，需要显式开启 legacy 演示模式。
- 现有大量 Controller 测试仍在 legacy 测试配置下验证业务行为；严格鉴权由独立测试覆盖。后续可以逐步把 Controller 测试迁移为默认携带 JWT。
- 当前只完成本地和自动化测试验证，尚未执行带真实 MySQL、Redis、Qdrant 和 DashScope 的完整在线演示回归。

### 2026-07-12：Docker 重建时发现旧库迁移兼容问题

- 症状：`rag-backend` 反复重启，8080 无法连接。
- 根因：旧 Hibernate 数据库 baseline 到 Flyway V1 后缺少后期求职表，V4 直接修改不存在的 `job_generated_task` 而失败。
- 修复：V4 在增加状态字段前，先以 `create table if not exists` 补齐三个可能缺失的求职业务表。
- 测试：新增 `LegacySchemaMigrationTests`，模拟非空旧数据库 baseline 到 V1 后继续执行 V2～V4。
- 当前数据库仍需要清除 V4 的失败历史记录并重新启动，执行结果将在完成后补充。
- Docker 恢复时发现 3307/6379 已被另一个项目占用；Compose 的 MySQL、Redis 宿主机端口已改为可配置，当前本地 `.env` 使用 3308/6380，未停止其他项目容器。
- 已删除确认未产生表结构变更的 V4 失败历史，修复后的 V4 在真实 MySQL 8.4 上成功执行。
- Docker 全部服务恢复：后端健康检查 200，匿名业务 API 401。
- 最终提交前检查通过：Python 107 passed，Java 99 passed。

### 2026-07-12：增加前端登录门禁与 401 跳转

- 未登录访问 `index.html` 时只渲染全屏登录/注册界面，不再进入工作台。
- 页面启动时先调用 `/api/auth/me` 验证本地 Token，通过后才加载业务数据。
- 任意非登录接口返回 401 时，立即清理失效 Token、切换登录模式并跳转到 `#login`。
- 主动退出登录也统一返回登录界面。
- 登录或注册成功后清除 `#login` 并进入工作台。
- 静态页面测试与完整提交前检查通过：Python 107 passed，Java 99 passed。
- Docker 镜像已重新构建；Playwright 浏览器组件已安装，但自动浏览器会话最终因当前工具审批额度限制未能启动，真实浏览器 E2E 待人工刷新页面确认。

### 2026-07-15：真实 Hybrid Retrieval 第一阶段

- 项目定位确认：企业知识库 RAG 为主线，求职 Copilot 作为架构扩展案例。
- 用户确认旧 Qdrant 只有一份测试文稿，无需保留或编写旧数据迁移脚本。
- 新 Collection 使用 `rag_chunks_hybrid_v1`，同时保存 `dense` 和 `sparse` 命名向量。
- 新增轻量 Sparse Lexical Encoder：英文技术词、中文字符 bi-gram/tri-gram、稳定哈希维度和 BM25 风格 TF 饱和；Collection 级 IDF 交给 Qdrant。
- Hybrid 查询改为 Qdrant Dense/Sparse 预取和服务端 RRF，删除 Python 扫描最多 1000 个 Chunk 的字符串包含实现。
- Source 分数拆分为 `vector_score`、`sparse_score`、`fusion_score`、`rerank_score`。
- Hybrid 候选的 Rerank 调用失败时重新执行 Dense fallback，避免把 RRF 分数误当向量相似度。
- 新增四路消融入口：Vector、Sparse、Hybrid RRF、Hybrid RRF + Rerank，并记录 Hit@K、P50、P95。
- 新增真实内存 Qdrant 测试，实际创建 Dense/Sparse Collection 并执行 Sparse 与 RRF 查询。
- 自动化回归：Python 115 passed，Java 100 passed。
- 完整提交前检查：`bash scripts/pre_submit_check.sh` 已通过，包括忽略文件、疑似密钥、空白字符、Markdown 链接、Docker Compose 配置以及 Python/Java 全量测试。
- Docker 在线验证：FastAPI 与 Spring Boot 镜像重新构建成功；真实 Qdrant 已创建
  `rag_chunks_hybrid_v1`，确认包含 `dense` 与 `sparse` 命名向量且当前为 0 条数据。
- 容器内联调：FastAPI 与 Spring Boot 健康检查通过，匿名访问业务接口返回 401。
- 宿主机原有 Java 进程占用 8080；未终止该进程，Compose 后端端口改为
  `${BACKEND_HOST_PORT:-8080}` 可配置形式，当前本地使用 8081，所有项目容器均已启动。

### 2026-07-15：企业技术文档评测集

- 主演示场景确定为“企业内部 AI/RAG 平台研发技术文档库”。
- 基于当前代码事实拆分 6 份文档：架构、Hybrid RAG、安全、部署排障、API、质量门禁。
- 保存可维护 Markdown 源文档，并生成 6 份可直接上传 PDF，共 12 页。
- 按 PDF 技能流程完成两轮逐页渲染；第二轮消除孤立尾页，未发现截断、重叠或乱码。
- 新增 26 条独立评测题：20 条可回答题、6 条无答案题，支持跨文档 gold source。
- 消融评测增加按文件名计算的 MRR、mean gold document recall 和 full gold hit at K。
- 新增 PDF 构建脚本、资产一致性测试和批量上传脚本；上传默认间隔 13 秒以遵守 5 次/分钟限流。
- 当前仅完成离线资产与自动化验证；尚未调用 DashScope 上传文档或运行在线消融，不记录虚构指标。
- 本阶段全量回归：Python 122 passed，Java 100 passed。
- 最终 `bash scripts/pre_submit_check.sh` 通过：忽略文件、疑似密钥、空白字符、Markdown 链接、
  Docker Compose 配置以及 Python/Java 全量测试均通过。

### 2026-07-16：真实文档入库与四路在线消融

- 使用专用本地演示账号创建知识库 `228058d9-30ce-4f9c-a84d-975df983f9cb`；账号凭据只在本地使用，未写入仓库。
- DashScope 最初受本机 Tyty 代理路由影响出现 TLS EOF；用户切换全局模式后，宿主机和容器内均恢复连通。
- 六份企业技术 PDF 全部上传成功；MySQL 中 6 条文档为 `AVAILABLE`，Qdrant 中共 6 个文档、40 个 Chunk。
- 使用真实 DashScope 和 Qdrant 运行 `technical_docs` 四路消融，原始逐题结果保存为
  `app/evaluation/evaluation_results/retrieval_ablation_20260716_131556.json`。
- Vector / Sparse / Hybrid / Hybrid + Rerank 的 Hit@6 均为 `1.0`；MRR 分别为
  `0.95 / 0.8792 / 0.95 / 0.95`。
- Hybrid + Rerank 的 mean gold document recall 为 `0.975`，full gold hit@6 为 `0.95`；
  `EVAL-MULTI-020` 暴露 Rerank Top 3 被同一文档多个 Chunk 占用、跨文档 gold 丢失的问题。
- 本轮为小规模受控语料单次实验，不把 `Hit@6=1.0` 表述为生产质量，也不根据一次远程 API 延迟断言某条链路天然更快。
- 最终 `bash scripts/pre_submit_check.sh` 通过：Python 122 passed、Java 100 passed，Markdown 链接、疑似密钥、
  空白字符和 Docker Compose 配置检查全部成功。
- Compose 中 FastAPI、Spring Boot、MySQL、Qdrant、Redis 均在运行；FastAPI `/health` 与
  Spring Boot `/api/health` 实际返回正常。

### 2026-07-16：Rerank 跨阶段文档级 RRF

- 初版“每文档取一个 Chunk”真实 A/B 未恢复 SEC gold，继续定位发现 SEC 在 Hybrid 候选第 2、
  Rerank 第 5；因此问题同时涉及来源重复与精排覆盖召回信号。
- 实现候选排名 + Rerank 排名的文档级 RRF；每个入选文档先返回最高精排 Chunk，文档不足时再补同文档 Chunk。
- 新增 `RAG_RERANK_DOCUMENT_DIVERSITY_ENABLED` 开关和消融 CLI 正反向参数；关闭后可复现原始 Rerank Top K。
- 拒答判断改为检查入选结果中的最高 Rerank 分数，不再假设文档融合后的第一项具有最高精排分。
- 同批真实候选 A/B：原始 Top 3 为 OPS/EVAL/EVAL，优化后为 EVAL/OPS/SEC，目标题 gold recall 从 `0.5` 修复为 `1.0`。
- 最终 26 题在线复测中，Hybrid + Rerank 的 Hit@3=`1.0`、MRR@3=`0.975`、mean gold document recall=`1.0`、full gold hit@3=`1.0`。
- 最终逐题结果保存为 `app/evaluation/evaluation_results/retrieval_ablation_20260716_134348.json`。
- 完整提交前检查通过：Python 128 passed、Java 100 passed，Markdown 链接、疑似密钥、空白字符和 Compose 配置均通过。
- FastAPI Docker 镜像已重新构建，容器健康检查通过；容器内确认 `diversity_enabled=True`、`document_rrf_k=60`。

### 2026-07-16：知途 AI 品牌与定位调整

- 产品名称确定为“知途 AI”，副标题为“你的 AI 学习与求职成长助手”。
- 主叙事从“企业知识库 + 求职 Agent”调整为“学习资料沉淀 → AI 伴学 → 面试准备 → 求职输出”。
- README、登录页、浏览器标题、侧栏、导航、联调页和 Spring Boot 运行时应用名完成首轮统一。
- 面向用户的模块名称调整为“成长主页、学习资料库、AI 伴学、求职工具箱”。
- 保留 Maven artifactId/内部工程名、Java 包名、API 路径和 `aikb` 本地存储键，避免品牌变更破坏兼容性，并保持 Docker 依赖缓存稳定。

### 2026-07-16：Vite + Vue3 消费级前端重构

- 将约 3900 行 CDN 单页拆分为 Vite 工程、Vue Router、共享组件、四个业务 View 和统一设计令牌；原有 API 方法与求职能力完整迁移。
- 新增温暖学习产品风的登录页、成长主页、资料库卡片、三栏伴学空间和四阶段求职工作流，并增加桌面 / 平板 / 手机响应式布局。
- 登录失效与主动退出统一清理 Token 并回到 `/` 登录入口；Spring Boot 新增 `/library`、`/study`、`/career` History 路由回退。
- Dockerfile 调整为 Node 前端构建、Maven 后端打包、JRE 运行的三阶段镜像；全部修改完成后已按约定成功重建最终镜像。
- 新增 Vitest 登录态与路由测试，重写 Spring 静态资源测试，覆盖生产 JS/CSS、核心 API 能力标识和 History 路由。
- 使用 Playwright WebKit 完成 1440×900 与 390×844 真实浏览器回归；覆盖登录、首页、资料库、伴学、求职四阶段，并修复移动端退出按钮被隐藏的问题。
- 完整提交前检查通过：Vue3 4 passed、Python 128 passed、Java 102 passed；GitHub Actions 和本地门禁均已纳入前端依赖安装、测试与构建。
- Docker 在线验收：`/api/health`、`/index.html`、`/library` 和生产 JS 均返回 200；匿名会话接口返回 401，确认新前端与严格鉴权同时生效。

### 2026-07-16：个人知识库权限收口为 owner-only

- 产品定位从企业部门资料共享转向个人学习与求职后，确认原有“同 department 可访问”会造成用户学习资料和聊天记录的隐私越权。
- 在 `KnowledgeBaseService` 集中将详情访问和列表查询收口为 owner-only；文档上传、文档列表、会话创建、消息读取与 RAG 提问继续复用同一权限入口。
- Repository 查询从 `ownerId OR department` 改为只按 `ownerId` 查询，避免他人的知识库元数据出现在列表中。
- 保留数据库、JWT 和接口中的 `department` 字段及必填校验，作为学习方向元数据兼容旧数据，但明确不再参与授权。
- 将服务层、文档 Controller、聊天 Controller 的越权场景调整为“同学习方向非 owner”，并新增双真实 JWT 账号回归，覆盖列表隐藏、详情 403、文档 403 和会话创建 403。
- 针对性回归通过：`KnowledgeBaseServiceTests`、`ChatServiceTests`、`DocumentControllerTests`、`ChatControllerTests`、`StrictAuthenticationTests` 共 26 个测试通过。
- README、后端说明、演示脚本、面试材料、简历材料和旧联调页已统一为 owner-only 口径。
- 完整提交前检查通过：Vue3 4 passed、Python 128 passed、Java 103 passed；Markdown 链接、疑似密钥、空白字符和 Compose 配置检查均通过。
- Backend Docker 镜像已重新构建并替换运行容器；宿主机映射端口为 8081，健康接口与新前端均返回 200，匿名业务请求返回 401。
- 容器在线双账号验收通过：两个账号使用相同学习方向，owner 创建知识库后可正常读取；非 owner 的列表不包含该知识库，访问详情、文档列表和创建会话均返回 403。

### 2026-07-16：FastAPI 内网收口与表单默认值清理

- Docker 模式不再把 FastAPI 的 8000 端口映射到宿主机；Spring Boot 只通过 Compose 内网地址 `http://api:8000` 调用 AI 服务。
- Compose 启动时强制要求 `FASTAPI_API_KEY`；本机密钥只保存在被 Git 忽略的 `.env`，`.env.example` 保持空值并给出随机生成命令。
- FastAPI 继续保留公开 `/health`，其余路由统一校验 `X-API-Key`；密钥比较改为常量时间比较，并在 OpenAPI 中声明 API Key 安全方案。
- Spring `RestClient` 自动附加内部密钥，新增“配置密钥时发送 Header、空值时不发送”的单元测试。
- 登录、注册、资料库、伴学、岗位分析、岗位收藏和简历版本等全部可编辑表单改为空初始值；示例内容只作为 placeholder 展示。
- 主动退出或登录失效时清空前一账号的表单、查询结果、分页与选择状态，避免同一浏览器切换账号后残留内存数据。
- 冒烟脚本改为从 FastAPI 容器内部执行受保护检查，不再依赖宿主机 8000；部署、迁移与验收文档同步为“Spring 单入口 + FastAPI 内网服务”。
- 完整提交前检查通过：Vue3 6 passed、Python 132 passed、Java 105 passed，Markdown 链接、疑似密钥、空白字符和 Compose 配置检查均通过。
- Docker 镜像重新构建并替换成功；`docker compose ps` 中 FastAPI 仅显示 `8000/tcp`，宿主机访问 `127.0.0.1:8000` 连接失败，Spring `/api/health` 与前端均返回 200，匿名业务请求返回 401。
- FastAPI 容器内验收：公开 `/health` 无密钥返回 200，受保护 `/qdrant/health` 无密钥返回 401、使用服务密钥返回 200；FastAPI 与 Spring 容器均确认已注入非空密钥。
- Playwright WebKit 真实页面验收覆盖登录、注册与求职四阶段：所有用户输入字段初始为空，仅保留提示性 placeholder；浏览器控制台 0 error、0 warning。

### 2026-07-16：多格式学习资料与个人空间管理

- 上传链路由仅支持 PDF 扩展为 PDF、Markdown（`.md` / `.markdown`）、Word（`.docx`）和纯文本（`.txt`）；Spring 与 FastAPI 同时校验扩展名，避免前后端支持范围不一致。
- FastAPI 保留 PDF 分页解析，Markdown/TXT 按文本解析并兼容 UTF-8-SIG 与 GB18030，DOCX 直接读取 OOXML 正文和表格内容；不支持旧版二进制 `.doc`，文本类和 DOCX 引用使用逻辑页码 1。
- 新增资料库 `PATCH` 与 `DELETE` 接口，只有 owner 可以修改或删除；删除时依次清理 Qdrant 向量、聊天消息、聊天会话、文档记录和资料库记录，并利用向量删除的幂等性保证失败后可安全重试。
- 个人资料接口改为实时读取数据库，新增昵称和学习方向修改能力；保存成功后签发包含最新资料的新 JWT，登录账号继续作为数据归属标识保持只读。
- Vue3 资料库页面新增卡片级编辑/删除入口、编辑表单回填和不可恢复操作确认；上传区明确展示支持格式，并在浏览器端提前拒绝不支持的文件。
- 顶部用户入口新增个人资料抽屉，可编辑昵称与学习方向；新建资料库和个人信息继续保持空表单或真实账号数据，不写入演示默认数据。
- 完整提交前检查通过：Vue3 8 passed、Python 139 passed、Java 114 passed；Markdown 链接、疑似密钥、空白字符和 Compose 配置检查均通过。
- 真实浏览器验收额外发现并修复“新建资料库学习方向被 JWT 个人方向覆盖”的问题；现在表单值优先，未填写时才回退个人资料，并为两种分支补齐回归测试。
- Docker 镜像已重新构建并替换；在线接口验收覆盖个人资料更新与恢复、资料库创建/修改/删除，FastAPI 容器内无模型费用预览验证 Markdown、GB18030 TXT、DOCX 均返回 200 和正确文档类型。
- Playwright WebKit 真实页面验收完成“登录—打开个人资料—创建资料库—编辑并持久化—确认级联删除”，临时数据已清理；多格式上传提示可见，浏览器控制台 0 error、0 warning。
- 最终容器烟雾检查通过，覆盖 Spring 健康接口、Vue3 工作台、调试页、Qdrant 集合和 Compose 内网 FastAPI；验收过程未调用 DashScope。

### 2026-07-21：修复大文档 Embedding 批次超限

- 复现 `八股文.md` 上传失败：45,477 字节 Markdown 被解析为 62 个 Chunk，原实现一次性调用 DashScope Embedding，超过模型批次上限后返回 HTTP 400，Spring 端只显示“调用 FastAPI 文档入库服务失败”。
- 首轮按通用模型资料尝试每批 20 条，真实接口仍返回 400；进一步读取当前 `text-embedding-v4` OpenAI-compatible 接口错误，确认该接口实际要求单批不超过 10 条，最终以真实接口约束为准。
- 在 `qwen_service` 统一实现每批最多 10 条的 Embedding 调度，文档服务无需感知批次；每批按响应 `index` 重新排序，并校验返回向量数与输入文本数一致，防止乱序或缺失向量污染 Chunk 映射。
- 新增安全的上游服务异常类型：FastAPI 对外返回 502 和可读提示，不暴露 DashScope 原始响应；Spring 客户端只提取 FastAPI JSON 中的安全 `message`，用户可以看到具体失败阶段。
- 新增 5 个 Embedding 单元测试，覆盖 45 条文本分成 `10/10/10/10/5`、10 条边界、空输入、乱序返回、向量数量不一致和上游异常转换；Spring 客户端测试增加 FastAPI 安全错误透传场景。
- FastAPI 与 Spring Boot Docker 镜像已重建，Compose 烟雾检查通过；Spring、Vue3、FastAPI、MySQL、Redis 和 Qdrant 均可用。
- 使用授权的本地演示账号真实上传 `资料库/八股文.md`：62 个 Chunk 按 `10/10/10/10/10/10/2` 共 7 批完成 Embedding，Spring 文档状态为 `AVAILABLE`，Qdrant 按 `document_id` 精确计数为 62。
- 在线验收使用临时资料库；验证完成后已级联删除临时 MySQL 记录和 Qdrant 向量，不保留测试数据。本次只产生少量 Embedding 调用，没有调用聊天生成模型。
- 最终 `bash scripts/pre_submit_check.sh` 通过：Vue3 8 passed、Python 144 passed、Java 115 passed；Markdown 链接、疑似密钥、空白字符、Docker Compose 配置和前端生产构建检查全部通过。

### 2026-07-23：AI 伴学流式问答与求职交互反馈

- 将 AI 伴学从“等待完整 JSON 后一次显示”改为 FastAPI → Spring Boot → Vue3 端到端 NDJSON 流式链路。
- FastAPI 新增流式生成封装和 `/rag/chat/rerank/stream`，按 `retrieving`、`reranking`、`sources`、`generating`、`delta`、`done` 或 `error` 输出真实阶段事件。
- Spring Boot 新增 `/api/chat/sessions/{sessionId}/ask/stream`：在发出响应头前完成 JWT、会话权限、文档归属和限流校验；合法请求立即保存用户消息，逐行转发 FastAPI 事件，完整结束后再保存 AI 回答、引用、检索模式和 Rerank 耗时。
- Vue3 在用户发送问题时立即把问题移入聊天记录并清空输入框，同时创建 AI 占位消息；回答按分片追加，显示检索/重排/生成状态、打字光标、引用来源、自动滚动和停止生成入口。
- 求职工具箱的简历解析、JD 解析、岗位分析、简历优化、面试准备、STAR 回答和求职成品包统一增加运行中、成功、失败、耗时、按钮禁用、旋转图标与结果骨架屏反馈。
- 修复岗位图片/PDF 分析的状态断链：Spring 组合响应现在同时返回分析结果、提取 JD、文件名、来源类型和警告；前端分析完成后自动把提取 JD 回填到表单，供简历优化和后续求职能力复用。
- 简历优化的 JD 改为可选：填写 JD 时执行针对岗位优化，留空时执行通用简历质量优化；两种模式均保留结构化结果和 MySQL 生成历史。
- 修复生成历史类型筛选值与后端枚举不一致的问题，统一为 `STAR_INTERVIEW_ANSWER` 和 `JOB_DELIVERY_PACKAGE`。
- 自动化回归通过：Vue3 10 passed、Python 148 passed、Java 117 passed；前端生产构建通过。
- FastAPI 与 Spring Boot Docker 镜像已重建并替换；8081 健康接口、Vue3 资源、Qdrant 和 Compose 内网 FastAPI 冒烟通过，匿名访问流式业务接口返回 401。
- 容器内使用“检索前参数校验失败”的无费用请求验证 NDJSON 事件顺序，实际收到 `status` 后跟安全 `error`；本轮在线验收未调用 DashScope，不产生模型费用。
- 使用 Docker 生产页面完成浏览器可视化回归：未登录保持登录门禁；登录后成长主页、AI 伴学三栏空间、求职四阶段和“通用简历优化 / 目标 JD 选填”界面均正常渲染；控制台 0 error、0 warning，检查结束后已退出演示账号。

### 2026-07-24：AI 可信性、安全、成本与治理补齐

本阶段只记录经过代码或自动化测试验证的结果，不调用真实付费模型，不把待实现能力提前写入简历材料。

| 顺序 | 工作项 | 状态 | 验收标准 |
| ---: | --- | --- | --- |
| 1 | 盘点现有实现并冻结修改顺序 | 已完成 | 明确已有能力、数据迁移和兼容边界 |
| 2 | 在线输出验证与安全输入处理 | 已完成 | 引用、结构、范围、注入风险和敏感信息处理均有单元测试 |
| 3 | AI 调用成功率、Token 与成本统计 | 已完成 | 日志字段、聚合接口和前端状态面板形成闭环 |
| 4 | 模型超时、重试与熔断 | 已完成 | 只重试瞬时错误，达到阈值后快速失败，错误语义稳定 |
| 5 | 端到端评测与多模型比较 | 已完成 | 数据集、模型、质量、延迟、Token 和成本可复现实验 |
| 6 | 用户本人审核求职输出 | 已完成 | 待审核、通过、驳回、审核意见和 owner 校验完整 |
| 7 | 全量回归与文档同步 | 已完成 | Vue3、Python、Java 和生产构建全部通过 |

设计边界：

- RAG 的确定性校验优先于再次调用模型担任裁判，避免验证环节继续引入幻觉和额外费用。
- 提示词注入命中时按风险分级；高风险输入拒绝调用模型，普通技术讨论不能因出现单个关键词被误伤。
- 联系方式等非任务必要的个人信息在发给模型前脱敏，业务库现有 owner-only 权限继续保留。
- 模型重试只覆盖连接失败、超时、429 和 5xx；参数错误、鉴权错误和已输出 Token 的流式响应不自动重放。
- 求职内容由用户本人审核，不引入默认可查看私人简历的后台审核员。

完成结果：

- RAG 同步与流式回答都会校验引用；缺少引用或引用序号越界时，用安全拒答替换未验证答案，流式链路通过 `verification` / `replace` 事件同步到 Vue3 和 MySQL。
- Pydantic 对求职评分范围和主要文本长度做结构校验；简历、JD、问题和检索片段按不可信数据包裹，高风险提示词注入在调用模型前阻断。
- 手机号、邮箱、身份证号和银行卡号在调用 Chat/Rerank 前脱敏；聊天与求职业务的敏感 TEXT 字段支持 AES-256-GCM 转换，旧明文可继续读取。
- 模型运行层只对超时、连接失败、429 和 5xx 做指数退避重试；达到阈值开启熔断，并将稳定错误语义交给统一异常处理。
- FastAPI 按请求汇总模型名、上游调用数、重试数、输入/输出/总 Token 和估算费用；Spring Boot 通过 Flyway V5 持久化，并提供最近 24 小时成功率、平均/P95 延迟、Token、费用和分业务统计。
- 求职生成任务通过 Flyway V6 增加 `PENDING_REVIEW / APPROVED / REJECTED`、审核意见和审核时间，新内容默认待审核，且只有 owner 可以审核。
- 新增冻结数据集多模型比较工具；指标包含回答通过率、拒答 Precision/Recall/F1、引用有效率、Chunk 支持率、fallback、延迟、Token 和费用。
- 新增 10 条无费用安全回归集，提示词注入、脱敏和引用校验通过率为 `1.0`。
- 全量回归通过：Vue3 `10 passed`、Python `161 passed`、Java `121 passed`；Vite 生产构建通过。本阶段未调用真实 DashScope。
- FastAPI 与 Spring Boot Docker 镜像已重建并替换；真实 MySQL 旧库由 Flyway V4 成功升级到 V6。8081 健康接口和首页返回 200，匿名聊天与指标接口返回 401；FastAPI 受保护接口无服务密钥返回 401，携带内部密钥的 Qdrant 健康检查返回 200。

## 8. 下一阶段候选项

本轮完成后再按投入产出比选择，不在本轮混做：

1. 运行生成与拒答评测，并加入 Token、成本和拒答 F1。
2. 对 Embedding/Rerank 预热后重复多轮，给延迟指标增加均值和置信区间。
3. 异步文档入库状态机、对象存储、重试和补偿。
4. OpenTelemetry、Prometheus、Grafana 和 AI 质量面板。
5. 在现有 Vite 前端上逐步引入 TypeScript，并补充关键表单的组件级交互测试。

## 9. 2026-07-27：面试准备 JSON 与流式收尾故障修复

现象与根因：

- 面试准备调用 DashScope 返回 200，但普通文本生成模式不能保证严格 JSON；结构复杂且原先与普通聊天共用 2048 输出 Token 上限，解析器最终返回“面试准备包不是合法 JSON”。
- AI 伴学已经收到模型完整回答，但 FastAPI 同步生成器由 Starlette 在线程池中分多次迭代。请求级 `ContextVar` Token 在一次 Context 中创建、在另一个 Context 中 reset，最终抛出 `Token was created in a different Context`，导致流式连接在收尾阶段异常。

修复：

- 明确要求 JSON 的系统 Prompt 统一启用 DashScope OpenAI-compatible `response_format={"type":"json_object"}`。
- 结构化任务使用独立的 `QWEN_STRUCTURED_MAX_OUTPUT_TOKENS=4096`，普通聊天继续保持 2048，避免为所有对话无差别提高成本上限。
- JSON 提取器支持代码块、前后说明、多个对象和字符串内换行；如果仍失败，返回安全的错误原因和位置，不记录原始简历或模型全文，也不二次调用模型修复。
- 流式路由改为持有显式 usage 列表，每次 `next()` 只在当前执行 Context 内绑定和重置，同一列表跨迭代累计 Embedding、Rerank 和 Chat 用量。

验证：

- 新增结构化 JSON mode、普通聊天隔离、输出截断、复杂 JSON 提取、跨 Context usage 和 NDJSON 路由回归；Python 全量 `167 passed`。
- FastAPI 镜像已重建。匿名合成简历/JD 的真实面试准备返回 200，七个必需字段完整，总 Token 2039。
- 使用不存在的合成文档 ID 做无资料正文流式验收，收到 `status → status → sources → delta → done`，HTTP 200，最终 usage 为 1 次上游调用、6 Token；日志无 Context 或其他 ERROR。

## 10. 2026-08-06：规则后置轻量意图路由

实现：

- 保留原有整句小聊白名单，并新增明确指向当前资料或企业内部事实的高召回保护规则；两类规则都在轻量分类器之前执行。
- FastAPI 新增 `/intent/classify`，使用可独立配置的 `QWEN_INTENT_MODEL` 返回知识问答、工具调用、澄清或开放域聊天的结构化结果。
- 企业知识标记、低于 `0.80`、未知标签、无效 JSON、模型异常和分类服务异常都保守回到 RAG，不会在 RAG 拒答后切换成无引用回答。
- 高置信非企业意图复用受控 `/chat` 生成；回答不返回引用，并用 `open_domain_chat / tool_call / clarification` 写入消息 `retrievalMode`。
- 分类与普通聊天分别写入 `INTENT_CLASSIFY` 和 `NON_RAG_CHAT` AI 调用日志；企业知识继续保留 RAG 引用、阈值拒答和原有调用审计。
- 当前未实现通用工具执行器；工具意图只返回操作步骤或追问必要参数，明确禁止伪造已执行结果。

验证：

- 针对性 Python 测试覆盖结构化分类、企业标记覆盖、低置信回退、无效输出和工具模式提示约束，共 31 项通过。
- 针对性 Java 测试覆盖规则顺序、企业知识保护、开放域回答、分类失败回退、工具意图持久化和 FastAPI 契约，共 61 项通过。
- `bash scripts/pre_submit_check.sh` 全量通过：Vue 11 项、FastAPI 173 项、Spring Boot 172 项，前端生产构建、Markdown 链接、疑似密钥、空白字符和 Compose 配置检查均通过。
- 尚未调用真实 DashScope，也未完成分类真实样本混淆矩阵、Docker 镜像更新或在线页面验收；这些不能写成已验证能力。

## 11. 2026-08-07：确定性风险门禁与意图路由评测基线

实现：

- Spring Boot 新增确定性策略路由，按“外部写操作 -> 企业知识 -> 实时查询”处理高风险或高时效请求；写操作和无工具实时查询都不再因分类器故障进入 RAG。
- 删除、修改、发送等请求仅返回受控说明，不调用分类器、RAG、普通聊天或删除接口；当前没有新增任何写权限和外部副作用。
- 分类结果扩展为知识范围、操作类型、时效性、工具名、缺失参数和确认要求；Spring Boot 对 `WRITE_TOOL` 和确认要求拥有最终强制阻断权。
- Flyway V7 为聊天消息增加 `routing_decision_json`，保存最终路由、来源、原因、置信度和风险字段。
- 新增独立人工标注意图路由集及评测程序，覆盖企业知识、公开问答、实时读取、写操作和澄清，并输出字段准确率、安全召回率和混淆矩阵。

验证状态：

- Python 与 Java 定向测试已通过；`bash scripts/pre_submit_check.sh` 全量通过：Vue 11 项、FastAPI 179 项、Spring Boot 192 项，前端生产构建、Markdown 链接、疑似密钥、空白字符和 Compose 配置检查均通过。
- 首次全量检查发现旧基线数据库缺少 `chat_message` 时 V7 迁移失败；迁移补上兼容建表后，旧库迁移回归和第二次全量检查均通过。
- 评测数据集和评测程序已经建立，但尚未调用真实 DashScope 跑全集，所以没有真实分类准确率、混淆矩阵或阈值校准结论。
- 当前没有 ToolRegistry、授权工具执行器或写操作二次确认流程；本阶段实现的是安全阻断，不是工具执行。
- 本阶段未重建 Docker 镜像，也未进行在线接口或浏览器验收。
