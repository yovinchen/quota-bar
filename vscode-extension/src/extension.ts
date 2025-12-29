/**
 * API Quota Watcher - VS Code 插件入口
 */

import * as vscode from 'vscode';
import { QuotaService } from './quotaService';
import { StatusBarService } from './statusBar';
import { ConfigService } from './configService';
import { SpeedTestService } from './speedTestService';
import { QuotaSnapshot } from './types';
import { getAdapter } from './platforms';
import { i18n } from './i18n';
import { PlatformType } from './types';

let quotaService: QuotaService | undefined;
let statusBarService: StatusBarService | undefined;
let configService: ConfigService | undefined;
let speedTestService: SpeedTestService | undefined;
let lastPlatformType: PlatformType | undefined;

export async function activate(context: vscode.ExtensionContext) {
    console.log('API Quota Watcher is now active');

    // 初始化服务
    configService = new ConfigService(context);
    speedTestService = new SpeedTestService();

    // 初始化配置存储
    await configService.initialize();

    const config = configService.getConfig();
    const credentials = configService.getCredentials();
    lastPlatformType = config.platformType;

    statusBarService = new StatusBarService(config);
    statusBarService.show();

    // 设置测速结果更新回调
    speedTestService.onResultsUpdate((results) => {
        statusBarService?.updateSpeedTestResults(results);
    });

    // 检查配置
    const { valid, missing } = configService.isConfigValid();
    if (!valid) {
        statusBarService.showConfigMissing(missing);
    } else if (config.enabled) {
        initQuotaService(config, credentials);
        // 如果启用测速且配置了测速地址，自动进行测速
        if (config.speedTestEnabled && config.platform.speedTestUrls.length > 0) {
            speedTestService.runSpeedTest(config.platform.speedTestUrls);
        }
    }

    // 注册命令：刷新配额
    const refreshCommand = vscode.commands.registerCommand(
        'quota-bar.refresh',
        async () => {
            const validation = configService!.isConfigValid();
            if (!validation.valid) {
                vscode.window.showErrorMessage(`请先配置: ${validation.missing.join(', ')}`);
                vscode.commands.executeCommand('quota-bar.configure');
                return;
            }

            if (!quotaService) {
                initQuotaService(configService!.getConfig(), configService!.getCredentials());
            } else {
                statusBarService?.showLoading();
                await quotaService.refresh();
            }
        }
    );

    // 注册命令：打开配置
    const configureCommand = vscode.commands.registerCommand(
        'quota-bar.configure',
        () => {
            vscode.commands.executeCommand(
                'workbench.action.openSettings',
                'quota-bar'
            );
        }
    );

    // 注册命令：测试连接
    const testConnectionCommand = vscode.commands.registerCommand(
        'quota-bar.testConnection',
        async () => {
            const config = configService!.getConfig();
            const credentials = configService!.getCredentials();
            const adapter = getAdapter(config.platformType);

            const validation = adapter.validateCredentials(credentials);
            if (!validation.valid) {
                vscode.window.showErrorMessage(`缺少必填字段: ${validation.missing.join(', ')}`);
                return;
            }

            const platformName = configService!.getPlatformDisplayName(config.platformType);
            const msg = i18n.get();
            vscode.window.withProgress(
                {
                    location: vscode.ProgressLocation.Notification,
                    title: `${platformName}...`,
                    cancellable: false,
                },
                async () => {
                    const result = await adapter.testConnection(credentials);
                    if (result.success) {
                        vscode.window.showInformationMessage(`✅ ${platformName} ${msg.connectionSuccess}`);
                    } else {
                        vscode.window.showErrorMessage(`❌ ${platformName} ${msg.connectionFailed}: ${result.message}`);
                    }
                }
            );
        }
    );

    // 注册命令：设置 Access Token（密码输入框）
    const setTokenCommand = vscode.commands.registerCommand(
        'quota-bar.setToken',
        async () => {
            const platformType = configService!.getPlatformType();
            const platformName = configService!.getPlatformDisplayName(platformType);
            const msg = i18n.get();

            const token = await vscode.window.showInputBox({
                prompt: `${platformName} ${msg.enterToken}`,
                placeHolder: 'sk-xxxxxxxxxxxxxxxx',
                password: true,
                ignoreFocusOut: true,
                validateInput: (value) => {
                    if (!value || value.trim().length === 0) {
                        return msg.tokenEmpty;
                    }
                    return null;
                }
            });

            if (token) {
                await configService!.saveAccessToken(token);
                vscode.window.showInformationMessage(`✅ ${platformName} ${msg.tokenSaved}`);
            }
        }
    );

    // 注册命令：测速
    const speedTestCommand = vscode.commands.registerCommand(
        'quota-bar.speedTest',
        async () => {
            const config = configService!.getConfig();
            const speedTestUrls = config.platform.speedTestUrls;
            const msg = i18n.get();

            if (speedTestUrls.length === 0) {
                const platformName = configService!.getPlatformDisplayName(config.platformType);
                const addUrls = await vscode.window.showQuickPick(['Yes', 'No'], {
                    placeHolder: `${platformName} ${msg.noSpeedTestUrls}`
                });

                if (addUrls === 'Yes') {
                    vscode.commands.executeCommand(
                        'workbench.action.openSettings',
                        `quota-bar.${config.platformType}.speedTestUrls`
                    );
                }
                return;
            }

            vscode.window.withProgress(
                {
                    location: vscode.ProgressLocation.Notification,
                    title: msg.speedTest,
                    cancellable: false,
                },
                async (progress) => {
                    progress.report({ message: i18n.format(msg.testingUrls, speedTestUrls.length) });

                    const results = await speedTestService!.runSpeedTest(speedTestUrls);

                    const successCount = results.filter(r => r.status === 'success').length;
                    const avgLatency = results
                        .filter(r => r.status === 'success')
                        .reduce((sum, r) => sum + r.latency, 0) / successCount || 0;

                    vscode.window.showInformationMessage(
                        `🚀 ${msg.speedTestComplete}: ${successCount}/${results.length} ${msg.success}, ${msg.avgLatency} ${avgLatency.toFixed(0)}ms`
                    );
                }
            );
        }
    );

    // 监听配置变化
    const configWatcher = configService.onConfigChange((newConfig) => {
        console.log('Config changed, platform:', newConfig.platformType);
        const newCredentials = configService!.getCredentials();

        // 检测平台切换
        const platformChanged = lastPlatformType !== newConfig.platformType;
        if (platformChanged) {
            console.log(`Platform switched: ${lastPlatformType} -> ${newConfig.platformType}`);
            // 清除旧平台的测速结果
            speedTestService?.clearResults();
        }
        lastPlatformType = newConfig.platformType;

        statusBarService?.setConfig(newConfig);

        const validation = configService!.isConfigValid();
        if (!validation.valid) {
            quotaService?.stopPolling();
            statusBarService?.showConfigMissing(validation.missing);
            return;
        }

        if (newConfig.enabled) {
            if (!quotaService) {
                initQuotaService(newConfig, newCredentials);
            } else {
                quotaService.setConfig(newConfig, newCredentials);
                // 平台切换时立即刷新配额
                if (platformChanged) {
                    quotaService.refresh();
                }
                quotaService.startPolling(newConfig.pollingInterval);
            }
            statusBarService?.show();

            // 如果启用测速且有测速地址，进行测速
            if (newConfig.speedTestEnabled && newConfig.platform.speedTestUrls.length > 0) {
                speedTestService?.runSpeedTest(newConfig.platform.speedTestUrls);
            }
        } else {
            quotaService?.stopPolling();
            statusBarService?.hide();
        }
    });

    // 注册到 context
    context.subscriptions.push(
        refreshCommand,
        configureCommand,
        testConnectionCommand,
        setTokenCommand,
        speedTestCommand,
        configWatcher,
        { dispose: () => quotaService?.dispose() },
        { dispose: () => statusBarService?.dispose() }
    );
}

function initQuotaService(config: any, credentials: any): void {
    quotaService = new QuotaService(config, credentials);

    quotaService.onQuotaUpdate((snapshot: QuotaSnapshot) => {
        statusBarService?.updateDisplay(snapshot);
    });

    quotaService.onError((error: Error) => {
        console.error('QuotaService error:', error);
        statusBarService?.showError(error.message);
    });

    quotaService.onStatus((status, retryCount) => {
        if (status === 'fetching') {
            statusBarService?.showLoading();
        } else if (status === 'retrying' && retryCount !== undefined) {
            statusBarService?.showRetrying(retryCount, 3);
        }
    });

    statusBarService?.showLoading();
    quotaService.startPolling(config.pollingInterval);
}

export function deactivate() {
    quotaService?.dispose();
    statusBarService?.dispose();
}
