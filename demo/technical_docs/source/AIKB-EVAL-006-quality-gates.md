# AIKB RAG 评测与发布质量门禁

- 文档编号：AIKB-EVAL-006
- 评测原则：结果可复现、失败可解释、指标不编造
- 主评测集：AIKB 企业技术文档演示集

## 1. 为什么要分层评测

一次回答正确可能来自偶然召回或模型常识，不能证明知识库链路可靠。AIKB 把评测拆成候选检索、融合、
Rerank、拒答、生成和引用六个层次。检索问题先定位 gold document，生成问题再检查答案关键词、拒答短语、
引用编号合法性和被引用 Chunk 是否真正支持结论。

## 2. 四路检索消融

检索消融固定比较 vector、sparse、hybrid、hybrid_rerank 四种模式。vector 只使用 Dense Embedding；
sparse 只使用词法稀疏向量；hybrid 使用 Qdrant RRF；hybrid_rerank 在 RRF 候选后调用 Qwen Rerank。

同一轮实验应使用相同文档、相同问题、相同 candidate_k、sparse_limit 和 rerank_top_k。报告至少记录
Hit@K、MRR、gold document recall、P50 延迟和 P95 延迟，并保留逐题来源文件与分数，避免只展示平均值。

## 3. 评测题分布

技术文档评测集应包含语义改写题、精确配置名题、错误码题、跨文档题和无答案题。精确术语题用于观察
Sparse 分支是否补回 Dense 容易遗漏的配置名；跨文档题检查 top-k 是否覆盖多个 gold document；无答案题
主要用于后续 Rerank 阈值和生成拒答评测，检索阶段本身不应被错误解释为“必须零召回”。

## 4. 发布前自动门禁

脚本 scripts/pre_submit_check.sh 依次检查本地敏感文件忽略规则、疑似密钥、空白字符、Markdown 链接、
Docker Compose 配置、Python 全量测试和 Java 全量测试。任何一步失败都不应更新项目展示材料中的测试数量
或完成状态。

Docker 健康检查通过后，还要完成真实 Collection schema、匿名 401 和技术文档 points_count 验证。调用
真实 DashScope 的消融属于在线实验，会产生网络请求和模型费用，应单独执行并把原始 JSON 结果保存下来。

## 5. 结果记录规则

自动化测试通过可以写成“代码回归通过”，但不能替代真实模型指标。新 Collection 尚未上传文档时，只能
记录 points_count=0 和 schema 验证成功；完成六份 PDF 上传并运行消融后，才能填写新的 Hit@K、延迟和
典型失败案例。

如果某种检索模式效果更差，应保留结果并分析问题类型，例如精确配置名、中文切词、跨文档覆盖或 Rerank
阈值，而不是删除失败样本。可信的失败分析比没有原始证据的“准确率显著提升”更适合工程面试展示。

## 6. 当前边界与后续方向

当前评测集规模适合本地演示和回归，不代表生产流量分布。后续应从真实匿名查询扩充 hard cases，并增加
拒答 F1、端到端延迟、Token 成本、模型错误率和权限隔离测试。指标达到门槛后，再考虑自动化发布阻断。
