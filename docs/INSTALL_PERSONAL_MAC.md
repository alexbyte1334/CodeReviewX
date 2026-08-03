# CodeReviewX Personal Edition for macOS

目标平台：Apple Silicon arm64。第一版不支持 Intel，不需要 Docker Desktop。

## 安装

1. 从 GitHub Release 下载 `CodeReviewX-arm64.dmg`。
2. 打开 DMG，将 `CodeReviewX.app` 拖入 Applications。
3. 第一次启动时允许 macOS 打开本地应用。
4. 按向导先填写 GitHub PAT 和模型 API，并通过连接测试。
5. Embedding/Rerank 属于可选增强；可以跳过，先使用基础 Review 和本地 Preview。
6. 只有 Evidence 可用时，向导和 Review 页面才会开放 GitHub 评论发布。

所有密钥由 macOS Keychain 保存，不写入浏览器 localStorage、Review Snapshot、SSE 或日志。

## GitHub PAT

个人 Review 至少需要：

- Metadata: Read
- Contents: Read
- Pull requests: Read

如果要在人工批准后发布评论，还需要 Issues / Pull request comments: Write。

## 模型

模型必须支持 OpenAI-compatible `POST /chat/completions`。向导提供 OpenAI、DeepSeek、Qwen、Moonshot、智谱和自定义端点预设。

结构化评审的各阶段默认使用同一个 Base URL、模型和 API Key。

## RAG 与降级模式

Embedding 和 Rerank 使用外部服务，不随 DMG 打包模型权重。配置完成后，Review 会先完成 commit-scoped 索引和检索；没有 Evidence 的 Finding 不会进入 Preview 或发布流程。

如果跳过 RAG，应用会进入 `DEGRADED` 模式：

- 可以读取 PR、执行基础 Review 和查看本地 Preview；
- `Evidence` 显示为不可用；
- 所有模型 Finding 的 GitHub 评论发布都会被后端拒绝；
- 后续可以在“重新配置”中补充 Embedding/Rerank，再重新运行 PR。

## 数据目录

```text
~/Library/Application Support/CodeReviewX/
├── config/
├── data/
├── postgres/
├── rag-work/
└── logs/
```

## 真实 PR 验收

1. 输入一个真实 GitHub 仓库和 PR。
2. 等待索引状态为 READY。
3. 确认 Review 有 PR head SHA、changed files 和 Evidence。
4. 确认未批准前没有 GitHub 写入。
5. 选择 Preview 并点击人工批准发布。
6. 再次发布，确认不会生成重复评论。
7. 重启应用，刷新 Review 页面，确认状态和 Evidence 可以恢复。

## 故障排查

- 启动失败：查看 `logs/backend.log` 和 `logs/postgres.log`。
- 如果提示 `JRE_INVALID`、`POSTGRES_RUNTIME_INVALID`、`BACKEND_START_FAILED` 或 `HEALTH_TIMEOUT`，请保留对应日志并重新下载完整 DMG。
- 模型失败：检查 Base URL 是否需要 `/v1`，以及模型名称和余额。
- Evidence 为空：检查 Embedding/Rerank endpoint、Key 和 1024 维模型契约。
- GitHub 失败：检查 PAT 权限、仓库可见性和 PR 编号。

## 从源码构建 DMG

仅在 Apple Silicon 发布机执行：

```bash
cd /Users/liyi/projects/CodeReviewX
desktop/prepare-postgresql-arm64.sh
desktop/build-arm64.sh
```

`prepare-postgresql-arm64.sh` 默认使用与当前 Homebrew pgvector 匹配的
arm64 PostgreSQL 17；也可以通过 `POSTGRES_FORMULA`、`POSTGRES_PREFIX`
和 `PGVECTOR_PREFIX` 指定其他匹配版本。它们会被复制进 DMG，用户端不
需要安装 Docker 或 Homebrew。
构建产物位于 `desktop/dist/CodeReviewX-*.dmg`。发布前请对 DMG 生成
SHA256，并在干净的 Apple Silicon Mac 上按上面的安装与真实 PR 清单验收。
当前源码构建默认不包含 Developer ID 签名和 Apple notarization；公开分发前必须另行完成签名、公证和 Gatekeeper 验证。
