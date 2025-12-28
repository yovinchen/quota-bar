# Quota Bar - IntelliJ IDEA Plugin

IntelliJ IDEA 状态栏 API 配额监控插件。

## 功能

- 状态栏实时显示配额
- 支持 NewAPI、PackyAPI、PackyCode、Cubence
- API 节点测速

## 构建

```bash
./gradlew buildPlugin
```

构建产物：`build/distributions/quota-bar-1.0.0.zip`

## 安装

`Settings → Plugins → ⚙️ → Install Plugin from Disk...`

## 配置

`Settings → Tools → Quota Bar`

- Platform Type: 平台类型
- Base URL: API 地址
- Access Token: 访问令牌

## 调试

```bash
./gradlew runIde
```

## License

MIT
