# AIKB 企业技术文档演示集

这套语料把当前项目模拟成企业内部的 AI/RAG 平台研发文档库。文档中的架构、配置、接口和
故障处理均来自当前仓库实现，不包含虚构的线上指标、SLA 或合规认证。

## 目录

- `source/`：可审查、可维护的 Markdown 原文。
- `pdfs/`：由脚本生成并可直接上传到知识库的 PDF。
- `manifest.json`：稳定的文档编号、源文件和 PDF 文件映射。
- `evaluation_cases.json`：带 gold document 的检索评测题。

## 使用顺序

1. 运行 `python scripts/build_technical_docs_pdfs.py` 生成 PDF。
2. 登录工作台，新建“AIKB 平台技术文档”知识库。
3. 上传 `pdfs/` 下六份 PDF，等待状态变为 `AVAILABLE`。
4. 运行 `python -m app.evaluation.evaluate_retrieval_ablation --dataset technical_docs`。
5. 把生成的 JSON 结果写入实验报告，不要手工编造指标。

如果使用 Docker 且宿主机 8080 被占用，请按 `.env` 中的 `BACKEND_HOST_PORT` 访问工作台。
