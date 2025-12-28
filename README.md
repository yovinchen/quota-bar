# Quota Bar

<p align="center">
  <img src="assets/logo.svg" width="128" height="128" alt="Quota Bar Logo">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/VS%20Code-1.107+-blue.svg" alt="VS Code">
  <img src="https://img.shields.io/badge/IntelliJ%20IDEA-2025.3+-orange.svg" alt="IDEA">
  <img src="https://img.shields.io/badge/License-MIT-green.svg" alt="License">
  <img src="https://img.shields.io/github/actions/workflow/status/yovinchen/quota-bar/build.yml?branch=main" alt="Build">
</p>

<p align="center">
  跨 IDE 的 API 配额监控插件<br>
  实时显示 AI API 余额与延迟，支持 VS Code 和 IntelliJ IDEA
</p>

---

## 功能特性

| 功能 | 说明 |
|------|------|
| 📊 实时监控 | 状态栏显示 API 配额，悬浮查看详情 |
| 🔌 多平台支持 | NewAPI、PackyAPI、PackyCode、Cubence |
| 🚀 智能测速 | 自动测试多个 API 节点，显示最低延迟 |
| 🎨 灵活显示 | 剩余额度 / 使用百分比 / 已用总额 |
| 🌐 多语言 | 中文、英语、日语界面（VS Code） |

## 支持平台

| 平台 | 类型 | 特性 |
|------|------|------|
| **NewAPI** | One API 兼容 | 基础配额监控 |
| **PackyAPI** | One API 兼容 | 基础配额监控 |
| **PackyCode** | 包月订阅 | 日/周/月预算，套餐信息 |
| **Cubence** | 多周期限制 | API Key 配额，5小时/周限制 |

## 效果预览

### 状态栏显示

```
💳 剩余 $12.50  |  ⚡ 45ms
```

### 悬浮提示

| 平台类型 | 显示内容 |
|----------|----------|
| 基础平台 | 💰 额度明细、📊 使用率、🚀 测速结果 |
| PackyCode | 👤 账户信息、📅 月/周/日预算、🎫 套餐详情 |
| Cubence | 💰 账户余额、🔑 API Key 配额、⏱️ 时间窗口限制 |

## 快速开始

### VS Code

1. 从 [Releases](https://github.com/yovinchen/quota-bar/releases) 下载 `.vsix` 文件
2. VS Code 中执行 `Extensions: Install from VSIX...`
3. 设置中搜索 `quota-bar` 配置平台和 Token

### IntelliJ IDEA

1. 从 [Releases](https://github.com/yovinchen/quota-bar/releases) 下载 `.zip` 文件
2. `Settings → Plugins → ⚙️ → Install Plugin from Disk...`
3. `Settings → Tools → Quota Bar` 配置

## 配置说明

### 基础配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| Platform Type | 平台类型 | newapi |
| Base URL | API 基础地址 | - |
| Access Token | 访问令牌 | - |
| Polling Interval | 刷新间隔（毫秒） | 60000 |

### 显示组件

采用五个独立开关，可自由组合搭配显示内容：

| 组件 | 说明 | 默认 | 示例 |
|------|------|------|------|
| 状态图标 | 根据剩余比例显示颜色图标（🟢\>60% / 🟡\>20% / 🔴≤20%） | ✅ 启用 | 🟢 |
| 状态比例 | 已用百分比 | ✅ 启用 | `45.2%` |
| 已使用金额 | 已使用的具体金额 | ❌ 禁用 | `$12.50` |
| 总金额 | 总额度 | ❌ 禁用 | `$50.00` |
| 测速延迟 | 最低延迟 | ✅ 启用 | `128ms` |

**显示效果示例**：
- 默认配置：`🟢 45.2% 128ms`
- 全部启用：`🟢 45.2% $12.50 $50.00 128ms`

## 构建

### VS Code 插件

```bash
cd vscode-extension
npm install
npm run compile
npx vsce package
```

输出：`quota-bar-*.vsix`

### IDEA 插件

```bash
cd idea-plugin
./gradlew buildPlugin
```

输出：`build/distributions/*.zip`

## 项目结构

```
quota-bar/
├── vscode-extension/    # VS Code 插件（TypeScript）
│   ├── src/
│   │   ├── extension.ts      # 入口
│   │   ├── quotaService.ts   # 配额服务
│   │   ├── statusBar.ts      # 状态栏
│   │   └── platforms/        # 平台适配
│   └── package.json
│
└── idea-plugin/         # IDEA 插件（Kotlin）
    ├── src/main/kotlin/
    │   ├── action/           # 动作
    │   ├── service/          # 服务
    │   ├── settings/         # 设置
    │   └── widget/           # 状态栏组件
    └── build.gradle.kts
```

## 技术栈

| 组件 | 技术 |
|------|------|
| VS Code 插件 | TypeScript + VS Code Extension API |
| IDEA 插件 | Kotlin + IntelliJ Platform SDK |
| HTTP 客户端 | fetch (VS Code) / OkHttp (IDEA) |
| 构建工具 | npm + vsce / Gradle |

## 常见问题

**Q: 状态栏显示 "配额未配置"？**

请在设置中配置对应平台的 Base URL 和 Access Token。

**Q: 配额信息不更新？**

点击状态栏手动刷新，或检查网络连接和 Token 是否有效。

**Q: 如何获取 Access Token？**

登录对应平台的控制台，在 API Keys 或令牌管理页面创建。

## 版本要求

- VS Code 1.107+
- IntelliJ IDEA 2025.3+
- Node.js 18+（构建 VS Code 插件）
- JDK 17+（构建 IDEA 插件）

## License

[MIT](LICENSE)

---

<p align="center">
  Made by <a href="https://github.com/yovinchen">yovinchen</a>
</p>
