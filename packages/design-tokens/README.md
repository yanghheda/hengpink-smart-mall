# Design Tokens

`tokens.json` 是 P01-S03 的唯一手写视觉原语源。构建脚本分别生成 RN TypeScript 常量与 H5 CSS Variables；共享 Token，不共享跨平台 UI 组件。

```bash
npm run tokens:validate
npm run tokens:generate
npm run tokens:check
```

生成物不要手改。本轮只冻结技术方案列出的颜色、圆角、页面间距与字号，不提前建设主题系统、组件库或设计稿像素规范。
