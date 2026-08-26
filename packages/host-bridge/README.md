# HostBridge Contract

`schemas/bridge-message.schema.json` 是 Bridge 协议的唯一手写契约源。P01-S03 只定义通用 Envelope、`bridge.ready`、`bridge.bootstrap` 与 `openProduct`；不包含 WebView、Ticket 兑换、页面导航或其他业务 Action。

```bash
npm run bridge:validate
npm run bridge:generate
npm run bridge:check
```

修改 Schema 后运行 `bridge:generate`。RN 和 H5 的薄入口都复用同一生成运行时，因此类型生成不能替代的边界校验不会在两端分叉。生成物不要手改。

运行时接入时还必须在通道边界限制原始消息为 32KB、嵌套深度为 8，并校验时间戳与本机相差不超过 5 分钟；这些依赖原始消息和本机时钟的检查不属于本轮纯对象 Schema。
