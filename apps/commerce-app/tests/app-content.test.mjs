import assert from "node:assert/strict";
import test from "node:test";

import { appContent } from "../src/appContent.js";

test("minimal commerce screen is explicit about its P01-S01 boundary", () => {
  assert.match(appContent.title, /已就绪/);
  assert.match(appContent.description, /P01-S01/);
  assert.match(appContent.badge, /演示数据/);
  assert.match(appContent.badge, /暂无业务功能/);
});
