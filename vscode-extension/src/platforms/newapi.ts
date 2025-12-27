/**
 * NewAPI 平台适配器
 */

import { PlatformCredentials, PlatformQuotaResult } from '../types';
import { BasePlatformAdapter } from './basePlatform';

/**
 * NewAPI 原始响应格式
 */
interface NewApiResponse {
    success: boolean;
    data?: {
        group: string;
        quota: number;
        used_quota: number;
    };
    message?: string;
}

/**
 * NewAPI 平台适配器
 */
export class NewApiAdapter extends BasePlatformAdapter {
    validateCredentials(credentials: PlatformCredentials): { valid: boolean; missing: string[] } {
        const missing: string[] = [];
        if (!credentials.baseUrl) missing.push('baseUrl');
        if (!credentials.accessToken) missing.push('accessToken');
        if (!credentials.userId) missing.push('userId');
        return { valid: missing.length === 0, missing };
    }

    async fetchQuota(credentials: PlatformCredentials): Promise<PlatformQuotaResult> {
        try {
            const response = await this.httpRequest<NewApiResponse>(
                `${credentials.baseUrl}/api/user/self`,
                {
                    method: 'GET',
                    headers: {
                        'Authorization': `Bearer ${credentials.accessToken}`,
                        'New-Api-User': credentials.userId,
                    },
                }
            );

            if (!response.success || !response.data) {
                return { success: false, error: response.message || '查询失败' };
            }

            const { group, quota, used_quota } = response.data;
            // NewAPI 配额单位为 1/500000 USD
            const remaining = quota / 500000;
            const used = used_quota / 500000;
            const total = remaining + used;

            return {
                success: true,
                data: {
                    planName: group || '默认套餐',
                    remaining,
                    used,
                    total,
                },
            };
        } catch (error: any) {
            return { success: false, error: error.message };
        }
    }
}
