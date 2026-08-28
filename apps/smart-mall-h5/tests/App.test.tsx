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
});
