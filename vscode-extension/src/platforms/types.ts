/**
 * API Quota Watcher - 平台适配器接口
 */

import { PlatformCredentials, PlatformQuotaResult } from '../types';

/**
 * 平台适配器接口
 */
export interface IPlatformAdapter {
    validateCredentials(credentials: PlatformCredentials): { valid: boolean; missing: string[] };
    fetchQuota(credentials: PlatformCredentials): Promise<PlatformQuotaResult>;
    testConnection(credentials: PlatformCredentials): Promise<{ success: boolean; message: string }>;
}
