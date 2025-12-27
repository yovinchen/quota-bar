# Quota Bar - VS Code Extension

VS Code 状态栏 API 配额监控插件。

## 功能

- 状态栏实时显示配额
- 支持 NewAPI、PackyAPI、PackyCode、Cubence
- API 节点测速
- 多语言支持（中/英/日）

## 构建

```bash
npm install
npm run compile
npx vsce package
```

## 安装

在 VS Code 中：`Extensions: Install from VSIX...`

## 配置

设置中搜索 `quota-bar`，配置：

- `platformType`: 平台类型
- `[platform].baseUrl`: API 地址
- `[platform].accessToken`: 访问令牌

## 命令

- `刷新配额` - 立即刷新
- `测速` - API 节点测速
- `测试连接` - 测试 API 连接

## License

MIT
