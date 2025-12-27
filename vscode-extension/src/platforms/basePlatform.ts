/**
 * API Quota Watcher - 平台适配器基类
 */

import * as https from 'https';
import * as http from 'http';
import { URL } from 'url';
import { PlatformCredentials, PlatformQuotaResult } from '../types';
import { IPlatformAdapter } from './types';

/**
 * 平台适配器基类
 */
export abstract class BasePlatformAdapter implements IPlatformAdapter {
    protected readonly timeout: number = 10000;

    abstract validateCredentials(credentials: PlatformCredentials): { valid: boolean; missing: string[] };
    abstract fetchQuota(credentials: PlatformCredentials): Promise<PlatformQuotaResult>;

    /**
     * 测试连接
     */
    async testConnection(credentials: PlatformCredentials): Promise<{ success: boolean; message: string }> {
        const validation = this.validateCredentials(credentials);
        if (!validation.valid) {
            return { success: false, message: `缺少必填字段: ${validation.missing.join(', ')}` };
        }

        try {
            const result = await this.fetchQuota(credentials);
            if (result.success) {
                return { success: true, message: '连接成功' };
            } else {
                return { success: false, message: result.error || '未知错误' };
            }
        } catch (error: any) {
            return { success: false, message: error.message || '连接失败' };
        }
    }

    /**
     * 通用 HTTP 请求方法
     */
    protected async httpRequest<T>(
        url: string,
        options: {
            method?: 'GET' | 'POST';
            headers?: Record<string, string>;
            body?: any;
        } = {}
    ): Promise<T> {
        const parsedUrl = new URL(url);
        const isHttps = parsedUrl.protocol === 'https:';

        const requestOptions: https.RequestOptions = {
            hostname: parsedUrl.hostname,
            port: parsedUrl.port || (isHttps ? 443 : 80),
            path: parsedUrl.pathname + parsedUrl.search,
            method: options.method || 'GET',
            headers: {
                'Content-Type': 'application/json',
                ...options.headers,
            },
            timeout: this.timeout,
        };

        return new Promise((resolve, reject) => {
            const client = isHttps ? https : http;
            const req = client.request(requestOptions, (res) => {
                let data = '';
                res.on('data', (chunk) => { data += chunk; });
                res.on('end', () => {
                    if (res.statusCode !== 200) {
                        reject(new Error(`HTTP ${res.statusCode}: ${data}`));
                        return;
                    }
                    try {
                        resolve(JSON.parse(data));
                    } catch (e) {
                        reject(new Error(`解析响应失败: ${data}`));
                    }
                });
            });

            req.on('error', reject);
            req.on('timeout', () => {
                req.destroy();
                reject(new Error('请求超时'));
            });

            if (options.body) {
                req.write(JSON.stringify(options.body));
            }
            req.end();
        });
    }
}
