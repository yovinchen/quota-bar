/**
 * API Quota Watcher - 类型定义
 */

/**
 * 支持的平台类型
 */
export type PlatformType = 'newapi' | 'packyapi' | 'packycode' | 'cubence';

/**
 * 平台凭证配置
 */
export interface PlatformCredentials {
    baseUrl: string;
    accessToken: string;
    userId: string;
}

/**
 * 预算周期数据
 */
export interface BudgetPeriod {
    budget: number;
    spent: number;
    remaining: number;
    percentage: number;
}

/**
 * 扩展配额数据（用于 PackyCode 包月等丰富数据平台）
 */
export interface ExtendedQuotaData {
    // 用户信息
    username?: string;
    email?: string;

    // 套餐信息
    planType?: string;
    planExpiresAt?: Date;

    // 余额信息
    balanceUsd?: number;
    totalSpentUsd?: number;

    // 各周期预算
    daily?: BudgetPeriod;
    weekly?: BudgetPeriod;
    monthly?: BudgetPeriod;
    fiveHour?: BudgetPeriod;
    apiKeyQuota?: BudgetPeriod;

    // 周统计窗口
    weeklyWindowStart?: Date;
    weeklyWindowEnd?: Date;

    // 配额信息
    totalQuota?: number;
    usedQuota?: number;
    remainingQuota?: number;
}

/**
 * 平台配额查询结果
 */
export interface PlatformQuotaResult {
    success: boolean;
    data?: {
        planName: string;
        remaining: number;
        used: number;
        total: number;
        // 扩展数据（可选）
        extended?: ExtendedQuotaData;
    };
    error?: string;
}

/**
 * 配额快照
 */
export interface QuotaSnapshot {
    timestamp: Date;
    planName: string;
    remaining: number;
    used: number;
    total: number;
    nextQueryTime: Date;
    timeUntilNextQueryFormatted: string;
    // 扩展数据（可选）
    extended?: ExtendedQuotaData;
}

/**
 * 测速结果
 */
export interface SpeedTestResult {
    url: string;
    latency: number;
    status: 'success' | 'failed' | 'pending';
    error?: string;
}

/**
 * 平台独立配置
 */
export interface PlatformConfig {
    baseUrl: string;
    accessToken: string;
    userId: string;
    speedTestUrls: string[];
}

/**
 * 小组件配置
 */
export interface WidgetConfig {
    statusIcon: boolean;   // 状态图标
    percentage: boolean;   // 状态比例
    used: boolean;         // 已使用金额
    total: boolean;        // 总金额
    latency: boolean;      // 测速延迟
}

/**
 * 用户配置
 */
export interface Config {
    enabled: boolean;
    speedTestEnabled: boolean;
    platformType: PlatformType;
    pollingInterval: number;
    widgets: WidgetConfig;
    // 当前平台的配置
    platform: PlatformConfig;
}

export interface QuotaConfig extends PlatformCredentials { }

export interface CredentialValidationResult {
    valid: boolean;
    missing: string[];
}

export interface PlatformAdapter {
    id: PlatformType;
    name: string;
    fetchQuota(config: QuotaConfig): Promise<{
        planName: string;
        remaining: number;
        used: number;
        total: number;
        extended?: ExtendedQuotaData;
    }>;
    validateCredentials(config: QuotaConfig): CredentialValidationResult;
    testConnection(config: QuotaConfig): Promise<{ success: boolean; message?: string }>;
}
