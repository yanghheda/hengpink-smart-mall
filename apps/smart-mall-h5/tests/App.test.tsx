import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";

import { App } from "../src/App";

describe("H5 首页", () => {
  it("展示输入框、快捷场景和醒目的模拟数据声明", () => {
    const html = renderToStaticMarkup(<App initialPath="/standalone" />);

    expect(html).toContain("告诉我为谁买、怎么用");
    expect(html).toContain("描述你的购买目标");
    expect(html).toContain("给父母买手机");
    expect(html).toContain("Standalone Demo");
    expect(html).toContain("项目模拟数据");
  });

  it("商品预览只展示资源标识，不伪造商品事实", () => {
    const html = renderToStaticMarkup(
      <App initialPath="/standalone/products/product-1?skuId=sku-2" />,
    );

    expect(html).toContain("商品预览");
    expect(html).toContain("product-1");
    expect(html).toContain("sku-2");
    expect(html).toContain("未接入商品详情接口");
    expect(html).not.toContain("¥");
  });

  it("开发者 Trace 展示失败定位信息且不展示模型私有推理", () => {
    const html = renderToStaticMarkup(
      <App
        initialPath="/admin/decision-runs/RUN-1/trace"
        initialTrace={{
          runId: "RUN-1",
          sessionId: "SESSION-1",
          runVersion: 3,
          status: "FAILED",
          activeNode: "REVIEW",
          failureCode: "TOOL_TIMEOUT",
          degradationCodes: ["QDRANT_UNAVAILABLE"],
          traceId: "trace-1",
          startedAt: "2026-08-30T11:59:58Z",
          completedAt: "2026-08-30T12:00:00Z",
          versions: { dataset: "dataset-v1", scoring: "score-v2" },
          usage: {
            tokenInput: 1200,
            tokenOutput: 220,
            estimatedCost: "0.0180",
          },
          steps: [
            {
              sequence: 2,
              node: "REVIEW",
              status: "FAILED",
              startedAt: "2026-08-30T11:59:59Z",
              completedAt: "2026-08-30T12:00:00Z",
              durationMs: 1000,
              errorCode: "TOOL_TIMEOUT",
              warningCodes: ["RETRIEVAL_DEGRADED"],
              inputSummary: { toolName: "retrieve_evidence" },
              outputSummary: { evidenceIds: ["EV-1"] },
            },
          ],
        }}
      />,
    );

    expect(html).toContain("开发者 Trace");
    expect(html).toContain("TOOL_TIMEOUT");
    expect(html).toContain("QDRANT_UNAVAILABLE");
    expect(html).toContain("retrieve_evidence");
    expect(html).toContain("EV-1");
    expect(html).toContain("dataset-v1");
    expect(html).not.toContain("Chain-of-Thought");
  });
});
