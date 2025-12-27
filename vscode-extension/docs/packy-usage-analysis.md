# Packy Usage VSCE 项目分析

> 分析项目：https://github.com/94mashiro/packy-usage-vsce

## 项目概述

Packy Usage 是一个 VS Code 插件，用于监控 PackyCode 平台的 API 用量信息。

## 项目结构

```
src/
├── extension.ts              # 插件入口
├── controllers/              # 控制器层
│   └── extension.manager.ts  # 扩展管理器
├── models/                   # 数据模型
│   └── budget.model.ts       # 预算数据模型
├── providers/                # VS Code TreeView 提供者
├── services/                 # 服务层
│   ├── api.service.ts        # API 调用服务 ⭐
│   ├── config.service.ts     # 配置服务
│   ├── data.service.ts       # 数据管理服务
│   ├── polling.service.ts    # 轮询服务
│   ├── secret.service.ts     # 密钥管理服务
│   └── status-bar.service.ts # 状态栏服务
└── utils/                    # 工具类
```

## 用量获取核心逻辑

### 1. API 端点

```
GET https://www.packycode.com/api/backend/users/info
```

这是 PackyCode 平台的用户信息接口，默认配置在 `package.json` 中：

```json
{
  "packy-usage.apiEndpoint": {
    "type": "string",
    "default": "https://www.packycode.com/api/backend/users/info"
  }
}
```

### 2. 认证方式

使用 **Bearer Token** 认证：

```typescript
headers: {
  Authorization: `Bearer ${token}`,
  "Content-Type": "application/json"
}
```

Token 通过 VS Code 的 `SecretStorage` API 安全存储。

### 3. API 响应结构

```typescript
interface ApiResponse {
  daily_budget_usd?: number    // 每日预算(USD)
  daily_spent_usd?: number     // 每日已用(USD)
  monthly_budget_usd?: number  // 每月预算(USD)
  monthly_spent_usd?: number   // 每月已用(USD)
}
```

### 4. 数据转换

API 服务将原始响应转换为内部使用的 `BudgetData` 结构：

```typescript
interface BudgetData {
  daily: {
    percentage: number  // 使用百分比
    total: number       // 总预算
    used: number        // 已用金额
  }
  monthly: {
    percentage: number
    total: number
    used: number
  }
}
```

**百分比计算公式：**

```typescript
const dailyPercentage = dailyBudget > 0 ? (dailySpent / dailyBudget) * 100 : 0
```

## 核心代码分析 (api.service.ts)

```typescript
async fetchBudgetData(): Promise<BudgetData | null> {
  // 1. 获取认证信息
  const token = await this.secretService.getToken()
  const endpoint = this.config.get<string>("apiEndpoint")
  
  if (!token || !endpoint) {
    return null
  }

  // 2. 配置请求（支持代理）
  const proxyUrl = this.configService.getProxyUrl()
  const fetchOptions = {
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json"
    },
    method: "GET",
    signal: controller.signal
  }
  
  // 如果配置了代理
  if (proxyUrl) {
    fetchOptions.dispatcher = new ProxyAgent(proxyUrl)
  }

  // 3. 发起请求
  const response = await fetch(endpoint, fetchOptions)
  
  // 4. 处理响应
  const data = await response.json() as ApiResponse
  return this.transformToBudgetData(data)
}
```

## 关键特性

### 1. 网络库

使用 **undici** 作为 HTTP 客户端，而不是 Node.js 原生 fetch 或 axios：

```json
"dependencies": {
  "undici": "^6.21.3"
}
```

**优势：**
- 更好的性能
- 原生支持代理（ProxyAgent）
- 更细粒度的请求控制

### 2. 代理支持

支持通过配置项设置代理：

```json
{
  "packy-usage.proxy": "http://127.0.0.1:7890"
}
```

### 3. 请求超时

设置了 10 秒超时限制：

```typescript
private readonly REQUEST_TIMEOUT = 10000

const controller = new AbortController()
const timeoutId = setTimeout(() => controller.abort(), this.REQUEST_TIMEOUT)
```

### 4. 错误处理

- **401/403 错误**：认证失败，清除 Token
- **网络超时**：AbortError 处理
- **网络连接失败**：fetch 错误处理

### 5. 密钥安全存储

使用 VS Code 的 `SecretStorage` API 安全存储 Token：

```typescript
export class SecretService {
  private secretStorage: vscode.SecretStorage
  
  async getToken(): Promise<string | undefined> {
    return await this.secretStorage.get("apiToken")
  }
  
  async setToken(token: string): Promise<void> {
    await this.secretStorage.store("apiToken", token)
  }
}
```

## 与 NewAPI 的对比

| 特性 | Packy Usage | NewAPI |
|------|-------------|--------|
| API 端点 | `/api/backend/users/info` | `/api/user/self` |
| 认证方式 | Bearer Token | Bearer Token + User-ID Header |
| 数据结构 | 直接返回 USD 金额 | 返回原始 quota 值（需换算） |
| 用量维度 | 每日 + 每月 | 仅总量 |
| 换算公式 | 无需换算 | `quota / 500000 = USD` |

## 建议

如果要为 `api-quota-watcher` 项目添加 PackyCode 平台支持，可以参考以下实现方式：

```typescript
// platforms/packycode.ts
export class PackyCodePlatform extends BasePlatform {
  async fetchQuota(): Promise<QuotaInfo> {
    const response = await fetch(
      `${this.config.baseUrl}/api/backend/users/info`,
      {
        headers: {
          Authorization: `Bearer ${this.config.accessToken}`,
          "Content-Type": "application/json"
        }
      }
    )
    
    const data = await response.json()
    
    return {
      group: "PackyCode",
      remaining: data.monthly_budget_usd - data.monthly_spent_usd,
      used: data.monthly_spent_usd,
      total: data.monthly_budget_usd
    }
  }
}
```
