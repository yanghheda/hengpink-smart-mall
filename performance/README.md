# P14-S04 性能与故障演练

基准报告入口：

```bash
python3 scripts/performance_benchmark.py \
  --config performance/benchmark.example.json \
  --output performance/reports/performance.json
```

运行前复制配置并替换 Session、Token 和 URL。`reweight` 只读取报告版本绑定的 Java 权威快照，不调用 LLM；旧报告没有快照时会明确失败，不回读实时数据补齐。

故障演练入口：

```bash
python3 scripts/failure_drill.py \
  --config performance/failure-drill.example.json \
  --scenario qdrant \
  --output performance/reports/qdrant.json \
  --execute
```

演练一次只停一个依赖。脚本要求显式 `--execute`，并在 `finally` 中执行恢复命令；状态码正确但缺少降级码仍判失败。项目基础设施位于 VMware VM 时，应把停启命令改成受控 SSH 参数数组。模型和 Python 的进程管理方式取决于实际 Demo 环境，所以示例不猜测命令。

正式性能报告必须保留 Git、Dataset、Scoring、机器信息、样本分母和 P95；故障报告必须保留降级与恢复两段证据。报告目录默认不预置伪造结果。
