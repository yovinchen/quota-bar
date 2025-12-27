/**
 * API Quota Watcher - 配置服务
 * 
 * 使用独立配置字段方案，每个平台的配置完全隔离，切换平台不会丢失数据
 */

import * as vscode from 'vscode';
import { Config, PlatformCredentials, PlatformType, PlatformConfig } from './types';

export class ConfigService {
    private static readonly CONFIG_SECTION = 'quota-bar';

    /**
     * 获取完整配置
     */
    getConfig(): Config {
        const config = vscode.workspace.getConfiguration(ConfigService.CONFIG_SECTION);
        const platformType = config.get('platformType', 'newapi') as PlatformType;

        return {
            enabled: config.get('enabled', true),
            speedTestEnabled: config.get('speedTestEnabled', true),
            platformType,
            pollingInterval: config.get('pollingInterval', 60000),
            displayStyle: config.get('displayStyle', 'remaining') as Config['displayStyle'],
            platform: this.getPlatformConfig(platformType),
        };
    }

    /**
     * 获取指定平台的配置（直接从独立字段读取）
     */
    getPlatformConfig(platformType: PlatformType): PlatformConfig {
        const config = vscode.workspace.getConfiguration(ConfigService.CONFIG_SECTION);
        return {
            baseUrl: config.get(`${platformType}.baseUrl`, ''),
            accessToken: config.get(`${platformType}.accessToken`, ''),
            userId: config.get(`${platformType}.userId`, ''),
            speedTestUrls: config.get(`${platformType}.speedTestUrls`, []) as string[],
        };
    }

    /**
     * 获取当前平台的凭证
     */
    getCredentials(): PlatformCredentials {
        const config = this.getConfig();
        return {
            baseUrl: config.platform.baseUrl,
            accessToken: config.platform.accessToken,
            userId: config.platform.userId,
        };
    }

    /**
     * 获取当前平台类型
     */
    getPlatformType(): PlatformType {
        const config = vscode.workspace.getConfiguration(ConfigService.CONFIG_SECTION);
        return config.get('platformType', 'newapi') as PlatformType;
    }

    /**
     * 获取当前平台的测速地址
     */
    getSpeedTestUrls(): string[] {
        const config = this.getConfig();
        return config.platform.speedTestUrls;
    }

    /**
     * 监听配置变化
     */
    onConfigChange(callback: (config: Config) => void): vscode.Disposable {
        return vscode.workspace.onDidChangeConfiguration((e) => {
            if (e.affectsConfiguration(ConfigService.CONFIG_SECTION)) {
                callback(this.getConfig());
            }
        });
    }

    /**
     * 检查当前平台配置是否完整
     */
    isConfigValid(): { valid: boolean; missing: string[] } {
        const config = this.getConfig();
        const platform = config.platform;
        const platformType = config.platformType;
        const missing: string[] = [];

        if (!platform.baseUrl) {
            missing.push('baseUrl (API 基础地址)');
        }
        if (!platform.accessToken) {
            missing.push('accessToken (Access Token)');
        }
        // PackyCode Monthly 和 Cubence 不需要 userId
        if (platformType !== 'packycode' && platformType !== 'cubence' && !platform.userId) {
            missing.push('userId (用户 ID)');
        }

        return { valid: missing.length === 0, missing };
    }

    /**
     * 保存 Access Token 到当前平台
     */
    async saveAccessToken(token: string): Promise<void> {
        const platformType = this.getPlatformType();
        await vscode.workspace.getConfiguration(ConfigService.CONFIG_SECTION)
            .update(`${platformType}.accessToken`, token, vscode.ConfigurationTarget.Global);
    }

    /**
     * 获取平台显示名称
     */
    getPlatformDisplayName(platformType: PlatformType): string {
        const names: Record<PlatformType, string> = {
            newapi: 'NewAPI',
            packyapi: 'PackyAPI',
            'packycode': 'PackyCode',
            'cubence': 'Cubence',
        };
        return names[platformType] || platformType;
    }

    /**
     * 初始化（保持接口兼容）
     */
    async initialize(): Promise<void> {
        // 独立配置字段方案不需要特殊初始化
    }
}
