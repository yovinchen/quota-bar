/**
 * PackyCode Monthly (Codex包月) 平台适配器
 * 使用 PackyCode 官方 API 接口
 * 参考: https://github.com/94mashiro/packy-usage-vsce
 */

import { PlatformCredentials, PlatformQuotaResult, BudgetPeriod, ExtendedQuotaData } from '../types';
import { BasePlatformAdapter } from './basePlatform';

/**
 * PackyCode Monthly API 响应格式
 */
interface PackyCodeMonthlyResponse {
    user_id?: string;
    username?: string;
    email?: string;
    user_type?: string;
    balance_usd?: string;
    total_spent_usd?: string;
    api_key?: string;
    created_at?: string;
    plan_type?: string;
    plan_expires_at?: string;
    monthly_budget_usd?: string;
    daily_budget_usd?: string;
    daily_spent_usd?: string;
    monthly_spent_usd?: string;
    weekly_spent_usd?: string;
    weekly_budget_usd?: string;
    weekly_window_start?: string;
    weekly_window_end?: string;
    total_quota?: number;
    used_quota?: number;
    remaining_quota?: number;
    [key: string]: unknown;
}

/**
 * PackyCode Monthly (Codex包月) 平台适配器
 */
export class PackyCodeMonthlyAdapter extends BasePlatformAdapter {
    validateCredentials(credentials: PlatformCredentials): { valid: boolean; missing: string[] } {
        const missing: string[] = [];
        if (!credentials.baseUrl) missing.push('baseUrl');
        if (!credentials.accessToken) missing.push('accessToken');
        // PackyCode Monthly 不需要 userId
        return { valid: missing.length === 0, missing };
    }

    async fetchQuota(credentials: PlatformCredentials): Promise<PlatformQuotaResult> {
        try {
            // 默认使用官方 API 端点
            const baseUrl = credentials.baseUrl || 'https://codex.packycode.com';
            const apiPath = '/api/backend/users/info';

            const response = await this.httpRequest<PackyCodeMonthlyResponse>(
                `${baseUrl}${apiPath}`,
                {
                    method: 'GET',
                    headers: {
                        'Authorization': `Bearer ${credentials.accessToken}`,
                    },
                }
            );

            // 解析数值（API 返回的可能是字符串）
            const parseNumber = (value: string | number | undefined): number => {
                if (value === undefined || value === null) return 0;
                const num = typeof value === 'string' ? parseFloat(value) : value;
                return isNaN(num) ? 0 : num;
            };

            // 解析日期
            const parseDate = (value: string | undefined): Date | undefined => {
                if (!value) return undefined;
                try {
                    return new Date(value);
                } catch {
                    return undefined;
                }
            };

            // 构建预算周期数据
            const buildPeriod = (budget: number, spent: number): BudgetPeriod => ({
                budget,
                spent,
                remaining: budget - spent,
                percentage: budget > 0 ? (spent / budget) * 100 : 0,
            });

            // 解析各项数据
            const dailyBudget = parseNumber(response.daily_budget_usd);
            const dailySpent = parseNumber(response.daily_spent_usd);
            const weeklyBudget = parseNumber(response.weekly_budget_usd);
            const weeklySpent = parseNumber(response.weekly_spent_usd);
            const monthlyBudget = parseNumber(response.monthly_budget_usd);
            const monthlySpent = parseNumber(response.monthly_spent_usd);
            const balanceUsd = parseNumber(response.balance_usd);
            const totalSpentUsd = parseNumber(response.total_spent_usd);

            // 构建扩展数据
            const extended: ExtendedQuotaData = {
                // 用户信息
                username: response.username,
                email: response.email,

                // 套餐信息
                planType: response.plan_type,
                planExpiresAt: parseDate(response.plan_expires_at),

                // 余额信息
                balanceUsd,
                totalSpentUsd,

                // 各周期预算
                daily: buildPeriod(dailyBudget, dailySpent),
                weekly: buildPeriod(weeklyBudget, weeklySpent),
                monthly: buildPeriod(monthlyBudget, monthlySpent),

                // 周统计窗口
                weeklyWindowStart: parseDate(response.weekly_window_start),
                weeklyWindowEnd: parseDate(response.weekly_window_end),

                // 配额信息
                totalQuota: response.total_quota,
                usedQuota: response.used_quota,
                remainingQuota: response.remaining_quota,
            };

            // 获取套餐显示名称
            const planName = this.getPlanDisplayName(response.plan_type);

            // 主要显示今日预算数据
            const remaining = dailyBudget - dailySpent;

            return {
                success: true,
                data: {
                    planName,
                    remaining,
                    used: dailySpent,
                    total: dailyBudget,
                    extended,
                },
            };
        } catch (error: any) {
            // 处理特定错误
            if (error.message.includes('401') || error.message.includes('403')) {
                return { success: false, error: '认证失败，请检查 Token 是否正确' };
            }
            return { success: false, error: error.message };
        }
    }

    /**
     * 获取套餐显示名称
     */
    private getPlanDisplayName(planType?: string): string {
        if (!planType) return 'Codex 包月';

        const planNames: Record<string, string> = {
            'basic': '基础版',
            'pro': '专业版',
            'premium': '高级版',
            'enterprise': '企业版',
        };

        return planNames[planType.toLowerCase()] || planType;
    }
}
