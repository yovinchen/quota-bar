/**
 * API Quota Watcher - 配置服务
 * 
 * 使用独立配置字段方案，每个平台的配置完全隔离，切换平台不会丢失数据
 */

import * as vscode from 'vscode';
import { Config, PlatformCredentials, PlatformType, PlatformConfig, WidgetConfig } from './types';

export class ConfigService {
    private static readonly CONFIG_SECTION = 'quota-bar';
    private static readonly SECRET_PREFIX = 'quota-bar.token.';
    private tokenCache: Partial<Record<PlatformType, string>> = {};

    constructor(private readonly context: vscode.ExtensionContext) {}

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
            widgets: {
                statusIcon: config.get('widgets.statusIcon', true),
                percentage: config.get('widgets.percentage', true),
                used: config.get('widgets.used', false),
                total: config.get('widgets.total', false),
                latency: config.get('widgets.latency', true),
            },
            platform: this.getPlatformConfig(platformType),
        };
    }

    /**
     * 获取指定平台的配置（直接从独立字段读取）
     */
    getPlatformConfig(platformType: PlatformType): PlatformConfig {
        const config = vscode.workspace.getConfiguration(ConfigService.CONFIG_SECTION);
        const accessToken = this.tokenCache[platformType] ?? '';
        return {
            baseUrl: config.get(`${platformType}.baseUrl`, ''),
            accessToken,
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
        this.tokenCache[platformType] = token;
        await this.context.secrets.store(`${ConfigService.SECRET_PREFIX}${platformType}`, token);
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
        const config = vscode.workspace.getConfiguration(ConfigService.CONFIG_SECTION);
        const platforms: PlatformType[] = ['newapi', 'packyapi', 'packycode', 'cubence'];

        for (const platform of platforms) {
            const secretKey = `${ConfigService.SECRET_PREFIX}${platform}`;
            // 优先读取 Secret Storage
            const secretToken = await this.context.secrets.get(secretKey);
            if (secretToken) {
                this.tokenCache[platform] = secretToken;
                continue;
            }

            // 兼容旧配置（settings.json），迁移后写入 Secret Storage
            const legacyToken = config.get<string>(`${platform}.accessToken`, '');
            if (legacyToken) {
                this.tokenCache[platform] = legacyToken;
                await this.context.secrets.store(secretKey, legacyToken);
                await config.update(`${platform}.accessToken`, '', vscode.ConfigurationTarget.Global);
            } else {
                this.tokenCache[platform] = '';
            }
        }
    }
}
