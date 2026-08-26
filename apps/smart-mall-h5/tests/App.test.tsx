import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";

import { App } from "../src/App";

describe("App", () => {
  it("renders the minimal H5 page and its honest scope", () => {
    const html = renderToStaticMarkup(<App />);

    expect(html).toContain("AI 购物决策体验已就绪");
    expect(html).toContain("P01-S01");
    expect(html).toContain("模拟数据");
    expect(html).toContain("后续阶段接入");
  });
});
