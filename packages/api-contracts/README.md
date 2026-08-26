# API Contracts

`openapi.yaml` 是 HengPick 对外 HTTP API 的唯一手写契约源。P01-S02 仅定义公开 health、标准成功信封和标准错误信封；业务 API、内部 Agent/Tool 协议与 HostBridge 均不在本轮范围内。

## 命令

```bash
npm run contract:validate  # 校验本轮 OpenAPI 3.1 结构与引用
npm run contract:generate  # 从契约覆盖生成 TS/Python 类型
npm run contract:check     # 校验契约、生成物漂移，并编译/加载 TS、Python 类型
```

正常修改流程是先改 `openapi.yaml`，再运行 `npm run contract:generate`。不要手改 `generated/`；根级 `npm run check` 会执行漂移门禁。

生成器刻意只支持 P01-S02 所需的 OpenAPI 子集。后续引入复杂组合 Schema、nullable/discriminator 或完整业务 API 前，应替换或扩展生成器，并先用测试冻结兼容性语义。
