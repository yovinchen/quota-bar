/**
 * API Quota Watcher - 核心配额查询服务
 */

import { Config, QuotaSnapshot, PlatformCredentials, PlatformQuotaResult } from './types';
import { getAdapter, IPlatformAdapter } from './platforms';

export class QuotaService {
    private config: Config;
    private credentials: PlatformCredentials;
    private adapter: IPlatformAdapter;

    private pollingInterval?: NodeJS.Timeout;
    private countdownInterval?: NodeJS.Timeout;
    private updateCallback?: (snapshot: QuotaSnapshot) => void;
    private errorCallback?: (error: Error) => void;
    private statusCallback?: (status: 'fetching' | 'retrying', retryCount?: number) => void;
    private countdownCallback?: (timeLeft: string) => void;

    // 重试配置
    private readonly MAX_RETRY_COUNT = 3;
    private readonly RETRY_DELAY_MS = 5000;
    private retryCount = 0;

    // 查询时间记录
    private lastQueryTime?: Date;
    private nextQueryTime?: Date;

    constructor(config: Config, credentials: PlatformCredentials) {
        this.config = config;
        this.credentials = credentials;
        this.adapter = getAdapter(config.platformType);
    }

    setConfig(config: Config, credentials: PlatformCredentials): void {
        const platformChanged = this.config.platformType !== config.platformType;
        this.config = config;
        this.credentials = credentials;

        // 如果平台类型变化，重新获取适配器
        if (platformChanged) {
            this.adapter = getAdapter(config.platformType);
        }
    }

    onQuotaUpdate(callback: (snapshot: QuotaSnapshot) => void): void {
        this.updateCallback = callback;
    }

    onError(callback: (error: Error) => void): void {
        this.errorCallback = callback;
    }

    onStatus(callback: (status: 'fetching' | 'retrying', retryCount?: number) => void): void {
        this.statusCallback = callback;
    }

    onCountdown(callback: (timeLeft: string) => void): void {
        this.countdownCallback = callback;
    }

    /**
     * 开始轮询
     */
    async startPolling(intervalMs: number): Promise<void> {
        console.log(`[QuotaService] Starting polling every ${intervalMs}ms`);
        this.stopPolling();

        if (this.statusCallback) {
            this.statusCallback('fetching');
        }

        await this.fetchQuota();

        this.nextQueryTime = new Date(Date.now() + intervalMs);

        this.pollingInterval = setInterval(() => {
            this.fetchQuota();
            this.nextQueryTime = new Date(Date.now() + intervalMs);
        }, intervalMs);

        this.startCountdown();
    }

    /**
     * 开始倒计时更新
     */
    private startCountdown(): void {
        this.stopCountdown();
        this.countdownInterval = setInterval(() => {
            if (this.nextQueryTime && this.countdownCallback) {
                const timeLeft = this.nextQueryTime.getTime() - Date.now();
                if (timeLeft > 0) {
                    this.countdownCallback(this.formatTimeLeft(timeLeft));
                }
            }
        }, 1000);
    }

    private stopCountdown(): void {
        if (this.countdownInterval) {
            clearInterval(this.countdownInterval);
            this.countdownInterval = undefined;
        }
    }

    private formatTimeLeft(ms: number): string {
        if (ms <= 0) return '即将刷新';

        const seconds = Math.floor(ms / 1000) % 60;
        const minutes = Math.floor(ms / 60000) % 60;
        const hours = Math.floor(ms / 3600000);

        if (hours > 0) return `${hours}时${minutes}分${seconds}秒`;
        if (minutes > 0) return `${minutes}分${seconds}秒`;
        return `${seconds}秒`;
    }

    stopPolling(): void {
        if (this.pollingInterval) {
            clearInterval(this.pollingInterval);
            this.pollingInterval = undefined;
        }
        this.stopCountdown();
    }

    async refresh(): Promise<void> {
        if (this.statusCallback) {
            this.statusCallback('fetching');
        }
        await this.fetchQuota();
    }

    /**
     * 获取配额
     */
    private async fetchQuota(): Promise<void> {
        try {
            const result: PlatformQuotaResult = await this.adapter.fetchQuota(this.credentials);

            if (!result.success || !result.data) {
                throw new Error(result.error || '查询失败');
            }

            const snapshot = this.buildSnapshot(result);
            this.retryCount = 0;

            if (this.updateCallback) {
                this.updateCallback(snapshot);
            }
        } catch (error: any) {
            console.error('Quota fetch failed:', error.message);

            if (this.retryCount < this.MAX_RETRY_COUNT) {
                this.retryCount++;
                console.log(`Retry ${this.retryCount}/${this.MAX_RETRY_COUNT} in ${this.RETRY_DELAY_MS}ms...`);

                if (this.statusCallback) {
                    this.statusCallback('retrying', this.retryCount);
                }

                setTimeout(() => this.fetchQuota(), this.RETRY_DELAY_MS);
                return;
            }

            this.stopPolling();
            if (this.errorCallback) {
                this.errorCallback(error);
            }
        }
    }

    /**
     * 构建配额快照
     */
    private buildSnapshot(result: PlatformQuotaResult): QuotaSnapshot {
        const data = result.data!;

        const now = new Date();
        this.lastQueryTime = now;

        const nextQuery = this.nextQueryTime || new Date(now.getTime() + this.config.pollingInterval);
        const timeUntilNext = nextQuery.getTime() - now.getTime();

        return {
            timestamp: now,
            planName: data.planName,
            remaining: data.remaining,
            used: data.used,
            total: data.total,
            nextQueryTime: nextQuery,
            timeUntilNextQueryFormatted: this.formatTimeLeft(timeUntilNext),
            extended: data.extended,
        };
    }

    getLastQueryTime(): Date | undefined {
        return this.lastQueryTime;
    }

    getNextQueryTime(): Date | undefined {
        return this.nextQueryTime;
    }

    dispose(): void {
        this.stopPolling();
    }
}
