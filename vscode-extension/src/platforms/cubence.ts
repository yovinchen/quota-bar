/**
 * Cubence 平台适配器
 */

import { PlatformCredentials, PlatformQuotaResult } from '../types';
import { BasePlatformAdapter } from './basePlatform';

interface CubenceResponse {
    api_key_quota: {
        quota_limit_dollar: number;
        quota_used_dollar: number;
        remaining_dollar: number;
        // 新增 units 字段
        quota_limit_units: number;
        quota_used_units: number;
        remaining_units: number;
    };
    normal_balance: {
        amount_dollar: number;
        amount_units: number;
    };
    subscription_window: {
        five_hour: CubenceWindow;
        weekly: CubenceWindow;
    };
    timestamp: number;
}

interface CubenceWindow {
    limit: number;
    remaining: number;
    used: number;
    reset_at: number | null;
}

export class CubenceAdapter extends BasePlatformAdapter {
    id: any = 'cubence';
    name = 'Cubence';

    validateCredentials(credentials: PlatformCredentials): { valid: boolean; missing: string[] } {
        const missing: string[] = [];
        if (!credentials.baseUrl) missing.push('baseUrl');
        if (!credentials.accessToken) missing.push('accessToken');

        return {
            valid: missing.length === 0,
            missing
        };
    }

    async fetchQuota(credentials: PlatformCredentials): Promise<PlatformQuotaResult> {
        try {
            const url = `${credentials.baseUrl.replace(/\/+$/, '')}/api/v1/user/subscription-info`;
            const data = await this.httpRequest<CubenceResponse>(
                url,
                {
                    headers: {
                        'Authorization': credentials.accessToken,
                    }
                }
            );

            // 辅助函数：Units 转 Dollar (1,000,000 units = 1 dollar)
            const unitsToDollar = (units: number) => units / 1_000_000.0;

            // 辅助函数：时间戳转 Date
            const timestampToDate = (ts: number | null): Date | undefined => {
                return ts ? new Date(ts * 1000) : undefined;
            };

            // 1. 解析主要显示数据 (使用 5小时限制)
            const fiveHour = data.subscription_window?.five_hour;
            const fiveHourLimit = unitsToDollar(fiveHour?.limit || 0);
            const fiveHourRemaining = unitsToDollar(fiveHour?.remaining || 0);
            const fiveHourUsed = fiveHourLimit - fiveHourRemaining;
            const fiveHourResetAt = timestampToDate(fiveHour?.reset_at);

            const total = fiveHourLimit;
            const remaining = fiveHourRemaining;
            const used = fiveHourUsed;

            // 2. 解析 Extended 数据
            // 周限制
            const weekly = data.subscription_window?.weekly;
            const weeklyLimit = unitsToDollar(weekly?.limit || 0);
            const weeklyRemaining = unitsToDollar(weekly?.remaining || 0);
            const weeklyUsed = weeklyLimit - weeklyRemaining;
            const weeklyResetAt = timestampToDate(weekly?.reset_at);

            // API Key 限制
            const apiKey = data.api_key_quota;

            return {
                success: true,
                data: {
                    planName: 'Cubence',
                    remaining,
                    used,
                    total,
                    extended: {
                        balanceUsd: data.normal_balance?.amount_dollar || 0,
                        fiveHour: {
                            budget: fiveHourLimit,
                            spent: fiveHourUsed,
                            remaining: fiveHourRemaining,
                            percentage: fiveHourLimit > 0 ? (fiveHourUsed / fiveHourLimit) * 100 : 0,
                            resetAt: fiveHourResetAt
                        },
                        weekly: {
                            budget: weeklyLimit,
                            spent: weeklyUsed,
                            remaining: weeklyRemaining,
                            percentage: weeklyLimit > 0 ? (weeklyUsed / weeklyLimit) * 100 : 0,
                            resetAt: weeklyResetAt
                        },
                        apiKeyQuota: {
                            budget: apiKey?.quota_limit_dollar || 0,
                            spent: apiKey?.quota_used_dollar || 0,
                            remaining: apiKey?.remaining_dollar || 0,
                            percentage: apiKey?.quota_limit_dollar > 0 ? (apiKey.quota_used_dollar / apiKey.quota_limit_dollar) * 100 : 0
                        }
                    }
                }
            };
        } catch (error: any) {
            return { success: false, error: error.message };
        }
    }

    async testConnection(config: PlatformCredentials): Promise<{ success: boolean; message: string }> {
        try {
            await this.fetchQuota(config);
            return { success: true, message: '连接成功' };
        } catch (error: any) {
            return { success: false, message: error.message || '连接失败' };
        }
    }
}
