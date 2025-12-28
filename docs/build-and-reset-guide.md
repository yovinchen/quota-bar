# 构建与数据重置指南

## 📋 检查结论

### ✅ 源代码检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| VS Code 源码 (`src/`) | ✅ 无残留 | 无 `displayStyle` 相关代码 |
| IDEA 源码 (`src/main/kotlin/`) | ✅ 无残留 | 无 `displayStyle` 相关代码 |
| `package.json` | ✅ 已更新 | 使用 `widgets.*` 配置 |
| `QuotaSettings.kt` | ✅ 已更新 | 使用 `widgetXxx` 字段 |

### ⚠️ 编译产物目录（可能包含旧代码）

| 目录 | 说明 |
|------|------|
| `vscode-extension/out/` | TypeScript 编译后的 JS 文件 |
| `idea-plugin/bin/` | Kotlin 编译的中间产物 |
| `idea-plugin/build/` | Gradle 构建产物 |

---

## 🧹 完整清理与重新构建

### 方式一：使用构建脚本（推荐）

```bash
cd /Users/yovinchen/Desktop/project/quota-bar

# 清理所有构建产物
./build.sh clean

# 重新编译并打包
./build.sh package
```

### 方式二：手动清理

```bash
cd /Users/yovinchen/Desktop/project/quota-bar

# 清理 VS Code 扩展编译产物
rm -rf vscode-extension/out
rm -f vscode-extension/*.vsix

# 清理 IDEA 插件编译产物
rm -rf idea-plugin/bin
rm -rf idea-plugin/build
rm -rf idea-plugin/.gradle

# 清理项目输出目录
rm -rf out
```

---

## 🔄 重置用户数据/配置

### VS Code 端

#### 方式一：通过设置 UI 重置

1. 打开 VS Code 设置 (`Cmd+,`)
2. 搜索 `quota-bar`
3. 点击每个设置项右侧的"恢复默认值"图标

#### 方式二：编辑 settings.json

1. `Cmd+Shift+P` → "Preferences: Open User Settings (JSON)"
2. 删除所有 `"quota-bar.*"` 相关配置
3. 保存文件

```json
// 删除以下配置（如果存在）：
"quota-bar.displayStyle": "...",  // ← 旧配置，必须删除
"quota-bar.enabled": true,
"quota-bar.platformType": "...",
"quota-bar.widgets.statusIcon": true,
// ... 其他 quota-bar.* 配置
```

#### 方式三：完全重置扩展数据

```bash
# macOS - 删除 VS Code 扩展全局存储（谨慎使用）
rm -rf ~/Library/Application\ Support/Code/User/globalStorage/yovinchen.quota-bar
```

---

### IDEA 端

#### 方式一：通过插件设置重置

1. 打开 `Settings → Tools → Quota Bar`
2. 点击 "恢复默认配置" 按钮

#### 方式二：删除配置文件

```bash
# 查找并删除 IDEA 配置文件
# 路径格式：~/Library/Application Support/JetBrains/<IDE版本>/options/ApiQuotaWatcher.xml

# IntelliJ IDEA 2024.3
rm -f ~/Library/Application\ Support/JetBrains/IntelliJIdea2024.3/options/ApiQuotaWatcher.xml

# IntelliJ IDEA 2025.2
rm -f ~/Library/Application\ Support/JetBrains/IntelliJIdea2025.2/options/ApiQuotaWatcher.xml

# 如果不确定版本，可以搜索
find ~/Library/Application\ Support/JetBrains -name "ApiQuotaWatcher.xml" -type f
```

#### 删除配置后需重启 IDE

---

## 🔧 重新编译步骤

### VS Code 扩展

```bash
cd /Users/yovinchen/Desktop/project/quota-bar/vscode-extension

# 1. 清理旧编译产物
rm -rf out

# 2. 确保依赖已安装
npm install

# 3. 重新编译
npm run compile

# 4. （可选）打包为 .vsix
npx vsce package
```

**开发模式测试**：
- 在 VS Code 中按 `F5` 启动扩展开发宿主
- 或 `Cmd+Shift+P` → "Debug: Start Debugging"

### IDEA 插件

```bash
cd /Users/yovinchen/Desktop/project/quota-bar/idea-plugin

# 1. 清理旧编译产物
./gradlew clean

# 2. 重新编译
./gradlew compileKotlin

# 3. 打包为 .zip
./gradlew buildPlugin

# 输出文件位于 build/distributions/*.zip
```

**重新安装插件**：
1. `Settings → Plugins → ⚙️ → Install Plugin from Disk...`
2. 选择 `build/distributions/*.zip`
3. 重启 IDE

---

## 📁 构建脚本命令一览

```bash
./build.sh help      # 显示帮助
./build.sh all       # 编译所有项目（默认）
./build.sh vscode    # 仅编译 VS Code 扩展
./build.sh idea      # 仅编译 IDEA 插件
./build.sh package   # 编译并打包所有项目
./build.sh clean     # 清理所有构建产物
```

---

## ⚠️ 常见问题

### Q: 重新编译后仍显示旧界面？

**VS Code**：
1. 关闭所有 VS Code 窗口
2. 重新打开项目并按 `F5`

**IDEA**：
1. 确保删除了 `bin/` 和 `build/` 目录
2. 通过 "Install Plugin from Disk" 重新安装
3. 完全重启 IDE（不是仅重载插件）

### Q: 配置无法保存？

检查是否有权限问题：
- VS Code: `~/Library/Application Support/Code/User/settings.json`
- IDEA: `~/Library/Application Support/JetBrains/<版本>/options/`

### Q: 如何确认运行的是新版本？

查看设置界面：
- **旧版**：单个"显示样式"下拉框
- **新版**：五个独立复选框（状态图标、百分比、已使用、总金额、延迟）
