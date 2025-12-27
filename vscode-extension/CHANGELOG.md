# 更新日志

所有重要的更改都会记录在此文件中。

## [1.0.0] - 2024-12-27

### 🎉 首次发布

#### 功能

- ✨ 支持 NewAPI 和 PackyCode 平台配额查询
- ✨ 状态栏实时显示剩余/已用额度
- ✨ 自动轮询配额信息（可配置间隔）
- ✨ 多种显示样式：剩余额度、使用百分比、已用/总额
- ✨ 多语言支持（简体中文、英语、日语）
- ✨ 速度测试功能（支持多节点）
- ✨ Tooltip 详细信息显示

#### 命令

- `API Quota: 刷新配额` - 立即刷新配额信息
- `API Quota: 打开配置` - 打开插件配置页面
- `API Quota: 测试连接` - 测试 API 连接
- `API Quota: 设置令牌` - 设置访问令牌
- `API Quota: 速度测试` - 执行速度测试

#### 配置项

##### 基础设置

- `enabled` - 启用/禁用插件
- `speedTestEnabled` - 启用速度测试
- `platformType` - 平台类型 (newapi / packycode)
- `pollingInterval` - 轮询间隔
- `displayStyle` - 显示样式

##### NewAPI 平台

- `newapi.baseUrl` - API 基础地址
- `newapi.accessToken` - 访问令牌
- `newapi.userId` - 用户 ID
- `newapi.speedTestUrls` - 速度测试 URL 列表

##### PackyCode 平台

- `packycode.baseUrl` - API 基础地址
- `packycode.accessToken` - 访问令牌
- `packycode.userId` - 用户 ID
- `packycode.speedTestUrls` - 速度测试 URL 列表
