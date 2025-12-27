/**
 * API Quota Watcher - 测速服务
 */

import * as https from 'https';
import * as http from 'http';
import { URL } from 'url';
import { SpeedTestResult } from './types';

export class SpeedTestService {
    private results: Map<string, SpeedTestResult> = new Map();
    private updateCallback?: (results: SpeedTestResult[]) => void;

    /**
     * 设置结果更新回调
     */
    onResultsUpdate(callback: (results: SpeedTestResult[]) => void): void {
        this.updateCallback = callback;
    }

    /**
     * 获取当前测速结果
     */
    getResults(): SpeedTestResult[] {
        return Array.from(this.results.values());
    }

    /**
     * 对多个 URL 进行并发测速
     */
    async runSpeedTest(urls: string[]): Promise<SpeedTestResult[]> {
        if (!urls || urls.length === 0) {
            return [];
        }

        // 初始化所有为 pending 状态
        urls.forEach(url => {
            this.results.set(url, { url, latency: -1, status: 'pending' });
        });

        // 并发测速
        const promises = urls.map(url => this.testUrl(url));
        const results = await Promise.all(promises);

        // 更新结果
        results.forEach(result => {
            this.results.set(result.url, result);
        });

        if (this.updateCallback) {
            this.updateCallback(this.getResults());
        }

        return results;
    }

    /**
     * 测试单个 URL 的延迟
     */
    private async testUrl(url: string): Promise<SpeedTestResult> {
        const startTime = Date.now();

        try {
            await this.pingUrl(url);
            const latency = Date.now() - startTime;
            return { url, latency, status: 'success' };
        } catch (error: any) {
            return {
                url,
                latency: -1,
                status: 'failed',
                error: error.message
            };
        }
    }

    /**
     * 发送 HEAD 请求测试连接
     */
    private pingUrl(url: string): Promise<void> {
        return new Promise((resolve, reject) => {
            try {
                const parsedUrl = new URL(url);
                const isHttps = parsedUrl.protocol === 'https:';
                const client = isHttps ? https : http;

                const options = {
                    hostname: parsedUrl.hostname,
                    port: parsedUrl.port || (isHttps ? 443 : 80),
                    path: '/',
                    method: 'HEAD',
                    timeout: 10000,
                };

                const req = client.request(options, (res) => {
                    resolve();
                });

                req.on('error', (err) => {
                    reject(err);
                });

                req.on('timeout', () => {
                    req.destroy();
                    reject(new Error('超时'));
                });

                req.end();
            } catch (error) {
                reject(error);
            }
        });
    }

    /**
     * 清除结果
     */
    clearResults(): void {
        this.results.clear();
    }
}
