/**
 * API Quota Watcher - 状态栏服务（优化版 + 国际化 + 扩展数据支持）
 */

import * as vscode from 'vscode';
import { QuotaSnapshot, Config, SpeedTestResult, ExtendedQuotaData, BudgetPeriod } from './types';
import { i18n } from './i18n';

export class StatusBarService {
    private statusBarItem: vscode.StatusBarItem;
    private config: Config;
    private currentSnapshot?: QuotaSnapshot;
    private speedTestResults: SpeedTestResult[] = [];

    // 缓存 Tooltip，避免重复构建
    private cachedTooltip?: vscode.MarkdownString;
    private tooltipDirty = true;

    constructor(config: Config) {
        this.config = config;
        this.statusBarItem = vscode.window.createStatusBarItem(
            vscode.StatusBarAlignment.Right,
            100
        );
        this.statusBarItem.command = 'quota-bar.refresh';
    }

    show(): void {
        this.statusBarItem.show();
    }

    hide(): void {
        this.statusBarItem.hide();
    }

    /**
     * 更新显示
     */
    updateDisplay(snapshot: QuotaSnapshot): void {
        this.currentSnapshot = snapshot;
        this.tooltipDirty = true;

        this.refreshStatusBarText();
        this.statusBarItem.color = undefined;
        this.statusBarItem.tooltip = this.getTooltip();
        this.statusBarItem.command = 'quota-bar.refresh';
    }

    /**
     * 更新测速结果
     */
    updateSpeedTestResults(results: SpeedTestResult[]): void {
        this.speedTestResults = results;
        this.tooltipDirty = true;

        if (this.currentSnapshot) {
            this.refreshStatusBarText();
            this.statusBarItem.tooltip = this.getTooltip();
        }
    }

    /**
     * 刷新状态栏文本（包含配额和延迟）
     */
    private refreshStatusBarText(): void {
        if (!this.currentSnapshot) return;

        let text = `$(credit-card) ${this.formatText(this.currentSnapshot)}`;

        // 查找最低延迟
        const minLatency = this.getMinLatency();
        if (minLatency !== undefined) {
            text += `  $(pulse) ${minLatency}ms`;
        }

        this.statusBarItem.text = text;
    }

    private getMinLatency(): number | undefined {
        const successes = this.speedTestResults.filter(r => r.status === 'success');
        if (successes.length === 0) return undefined;
        return Math.min(...successes.map(r => r.latency));
    }

    /**
     * 获取 Tooltip（使用缓存）
     */
    private getTooltip(): vscode.MarkdownString {
        if (this.tooltipDirty || !this.cachedTooltip) {
            this.cachedTooltip = this.buildTooltip();
            this.tooltipDirty = false;
        }
        return this.cachedTooltip;
    }

    /**
     * 显示加载状态
     */
    showLoading(): void {
        const msg = i18n.get();
        this.statusBarItem.text = `$(sync~spin) ${msg.querying}`;
        this.statusBarItem.color = undefined;
        this.statusBarItem.tooltip = msg.querying;
    }

    /**
     * 显示重试状态
     */
    showRetrying(retryCount: number, maxRetry: number): void {
        const msg = i18n.get();
        this.statusBarItem.text = `$(sync~spin) ${msg.retrying} (${retryCount}/${maxRetry})`;
        this.statusBarItem.color = new vscode.ThemeColor('editorWarning.foreground');
        this.statusBarItem.tooltip = msg.retrying;
    }

    /**
     * 显示错误状态
     */
    showError(message: string): void {
        const msg = i18n.get();
        this.statusBarItem.text = `$(error) ${msg.failed}`;
        this.statusBarItem.color = new vscode.ThemeColor('errorForeground');
        this.statusBarItem.tooltip = `${msg.failed}: ${message}`;
        this.statusBarItem.command = 'quota-bar.refresh';
    }

    /**
     * 显示配置缺失
     */
    showConfigMissing(missing: string[]): void {
        const msg = i18n.get();
        this.statusBarItem.text = `$(gear) ${msg.notConfigured}`;
        this.statusBarItem.color = new vscode.ThemeColor('editorWarning.foreground');
        this.statusBarItem.tooltip = `${msg.configMissing}: ${missing.join(', ')}`;
        this.statusBarItem.command = 'quota-bar.configure';
    }

    /**
     * 格式化状态栏文本
     */
    private formatText(snapshot: QuotaSnapshot): string {
        const msg = i18n.get();
        switch (this.config.displayStyle) {
            case 'percentage':
                const pct = snapshot.total > 0 ? (snapshot.used / snapshot.total) * 100 : 0;
                return `${msg.used} ${pct.toFixed(1)}%`;
            case 'both':
                return `$${snapshot.used.toFixed(2)} / $${snapshot.total.toFixed(2)}`;
            default:
                return `${msg.remaining} $${snapshot.remaining.toFixed(2)}`;
        }
    }

    /**
     * 构建 Tooltip
     */
    private buildTooltip(): vscode.MarkdownString {
        const snapshot = this.currentSnapshot;
        const msg = i18n.get();

        if (!snapshot) {
            return new vscode.MarkdownString(msg.notConfigured);
        }

        // 检查是否是 PackyCode 包月平台
        if (this.config.platformType === 'packycode' && snapshot.extended) {
            return this.buildExtendedTooltip(snapshot, snapshot.extended);
        }

        // 检查是否是 Cubence 平台
        if (this.config.platformType === 'cubence' && snapshot.extended) {
            return this.buildCubenceTooltip(snapshot, snapshot.extended);
        }

        return this.buildBasicTooltip(snapshot);
    }

    /**
     * 构建基础 Tooltip（NewAPI / PackyAPI）
     */
    private buildBasicTooltip(snapshot: QuotaSnapshot): vscode.MarkdownString {
        const msg = i18n.get();
        const usedPct = snapshot.total > 0 ? (snapshot.used / snapshot.total) * 100 : 0;
        const remainPct = 100 - usedPct;
        const bar = this.buildProgressBar(usedPct);
        const platformName = this.getPlatformDisplayName();

        let content = `## 📊 ${platformName} ${msg.tooltipTitle}\n\n`;
        content += `**${msg.progress}** ${usedPct.toFixed(1)}%\n\n`;
        content += `\`${bar}\`\n\n`;
        content += `---\n\n`;
        content += `**💰 ${msg.quotaInfo}**\n\n`;
        content += `| | | |\n`;
        content += `|:-----|-----:|-----:|\n`;
        content += `| 🟢 ${msg.remaining} | $${snapshot.remaining.toFixed(2)} | ${remainPct.toFixed(1)}% |\n`;
        content += `| 🔴 ${msg.used} | $${snapshot.used.toFixed(2)} | ${usedPct.toFixed(1)}% |\n`;
        content += `| ⚪ ${msg.total} | $${snapshot.total.toFixed(2)} | 100% |\n`;
        content += `| 📦 ${msg.plan} | ${snapshot.planName} | - |\n`;

        // 测速结果
        content += this.buildSpeedTestSection();

        const tooltip = new vscode.MarkdownString(content);
        tooltip.isTrusted = true;
        return tooltip;
    }

    /**
     * 构建 Cubence Tooltip
     */
    private buildCubenceTooltip(snapshot: QuotaSnapshot, ext: ExtendedQuotaData): vscode.MarkdownString {
        const msg = i18n.get();
        let content = `## 📊 Cubence ${msg.tooltipTitle}\n\n`;

        // 账户余额
        if (ext.balanceUsd !== undefined) {
            content += `**💰 账户余额**\n\n`;
            content += `| | |\n`;
            content += `|:-----|-----:|\n`;
            content += `| 💵 余额 | $${ext.balanceUsd.toFixed(2)} |\n`;
            content += `\n---\n\n`;
        }

        // API Key 配额
        if (ext.apiKeyQuota) {
            content += this.buildPeriodSection('🔑 API Key 配额', ext.apiKeyQuota);
        }

        // 5小时限制
        if (ext.fiveHour) {
            content += this.buildPeriodSection('⏱️ 5小时限制窗口', ext.fiveHour);
        }

        // 周限制
        if (ext.weekly) {
            content += this.buildPeriodSection('📅 本周限制', ext.weekly);
        }

        // 测速结果
        content += this.buildSpeedTestSection();

        const tooltip = new vscode.MarkdownString(content);
        tooltip.isTrusted = true;
        return tooltip;
    }

    /**
     * 构建扩展 Tooltip（PackyCode 包月）
     */
    private buildExtendedTooltip(snapshot: QuotaSnapshot, ext: ExtendedQuotaData): vscode.MarkdownString {
        const msg = i18n.get();

        let content = `## 📊 PackyCode 包月 ${msg.tooltipTitle}\n\n`;

        // 用户和套餐信息
        content += `**👤 账户信息**\n\n`;
        content += `| | |\n`;
        content += `|:-----|:-----|\n`;
        if (ext.username) {
            content += `| 用户名 | ${ext.username} |\n`;
        }
        content += `| 套餐 | ${snapshot.planName} |\n`;
        if (ext.planExpiresAt) {
            const expiresStr = this.formatDate(ext.planExpiresAt);
            const daysLeft = this.getDaysUntil(ext.planExpiresAt);
            content += `| 到期时间 | ${expiresStr} (${daysLeft}天) |\n`;
        }
        if (ext.balanceUsd !== undefined) {
            content += `| 账户余额 | $${ext.balanceUsd.toFixed(2)} |\n`;
        }
        if (ext.totalSpentUsd !== undefined) {
            content += `| 累计消费 | $${ext.totalSpentUsd.toFixed(2)} |\n`;
        }

        content += `\n---\n\n`;

        // 月度预算
        if (ext.monthly) {
            content += this.buildPeriodSection('📅 本月预算', ext.monthly);
        }

        // 周预算
        if (ext.weekly) {
            let weekLabel = '📆 本周预算';
            if (ext.weeklyWindowStart && ext.weeklyWindowEnd) {
                const start = this.formatShortDate(ext.weeklyWindowStart);
                const end = this.formatShortDate(ext.weeklyWindowEnd);
                weekLabel = `📆 本周预算 (${start} - ${end})`;
            }
            content += this.buildPeriodSection(weekLabel, ext.weekly);
        }

        // 日预算
        if (ext.daily) {
            content += this.buildPeriodSection('🌅 今日预算', ext.daily);
        }

        // 配额信息（如果有）
        if (ext.totalQuota && ext.totalQuota > 0) {
            content += `\n---\n\n`;
            content += `**🎫 配额信息**\n\n`;
            content += `| | | |\n`;
            content += `|:-----|-----:|-----:|\n`;
            const quotaUsedPct = ext.totalQuota > 0 ? ((ext.usedQuota || 0) / ext.totalQuota) * 100 : 0;
            content += `| 已用配额 | ${ext.usedQuota?.toLocaleString() || 0} | ${quotaUsedPct.toFixed(1)}% |\n`;
            content += `| 剩余配额 | ${ext.remainingQuota?.toLocaleString() || 0} | ${(100 - quotaUsedPct).toFixed(1)}% |\n`;
            content += `| 总配额 | ${ext.totalQuota.toLocaleString()} | 100% |\n`;
        }

        // 测速结果
        content += this.buildSpeedTestSection();

        const tooltip = new vscode.MarkdownString(content);
        tooltip.isTrusted = true;
        return tooltip;
    }

    /**
     * 构建周期预算段落
     */
    private buildPeriodSection(title: string, period: BudgetPeriod): string {
        const bar = this.buildProgressBar(period.percentage);
        const remaining = period.remaining;
        const remainPct = 100 - period.percentage;

        let content = `**${title}**\n\n`;
        content += `\`${bar}\` ${period.percentage.toFixed(1)}%\n\n`;
        content += `| | | |\n`;
        content += `|:-----|-----:|-----:|\n`;
        content += `| 🟢 剩余 | $${remaining.toFixed(2)} | ${remainPct.toFixed(1)}% |\n`;
        content += `| 🔴 已用 | $${period.spent.toFixed(2)} | ${period.percentage.toFixed(1)}% |\n`;
        content += `| ⚪ 预算 | $${period.budget.toFixed(2)} | 100% |\n`;
        content += `\n`;

        return content;
    }

    /**
     * 构建测速结果段落
     */
    private buildSpeedTestSection(): string {
        if (this.speedTestResults.length === 0) {
            return '';
        }

        const msg = i18n.get();
        let content = `\n---\n\n`;
        content += `**🚀 ${msg.speedTest}**\n\n`;
        content += `| | | |\n`;
        content += `|:-----|-----:|:-----|\n`;

        for (const r of this.speedTestResults) {
            const host = this.shortenUrl(r.url);
            const latency = r.status === 'success' ? `${r.latency}ms` : '-';
            const icon = r.status === 'success' ? '✅' : r.status === 'pending' ? '⏳' : '❌';
            content += `| ${host} | ${latency} | ${icon} |\n`;
        }

        return content;
    }

    /**
     * 格式化日期
     */
    private formatDate(date: Date): string {
        return date.toLocaleDateString('zh-CN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
        });
    }

    /**
     * 格式化短日期
     */
    private formatShortDate(date: Date): string {
        return date.toLocaleDateString('zh-CN', {
            month: '2-digit',
            day: '2-digit',
        });
    }

    /**
     * 计算距离某日期的天数
     */
    private getDaysUntil(date: Date): number {
        const now = new Date();
        const diff = date.getTime() - now.getTime();
        return Math.ceil(diff / (1000 * 60 * 60 * 24));
    }

    /**
     * 缩短 URL
     */
    private shortenUrl(url: string): string {
        try {
            return new URL(url).hostname;
        } catch {
            return url.length > 20 ? url.slice(0, 17) + '...' : url;
        }
    }

    /**
     * 生成进度条
     */
    private buildProgressBar(percentage: number): string {
        const filled = Math.round(percentage / 5);
        return '█'.repeat(Math.min(filled, 20)) + '░'.repeat(Math.max(20 - filled, 0));
    }

    /**
     * 获取平台显示名称
     */
    private getPlatformDisplayName(): string {
        const names: Record<string, string> = {
            newapi: 'NewAPI',
            packyapi: 'PackyAPI',
            'packycode': 'PackyCode',
        };
        return names[this.config.platformType] || this.config.platformType;
    }

    /**
     * 设置配置，检测平台切换并清除旧数据
     */
    setConfig(config: Config): void {
        const platformChanged = this.config.platformType !== config.platformType;
        this.config = config;
        this.tooltipDirty = true;

        // 平台切换时清除旧数据
        if (platformChanged) {
            this.currentSnapshot = undefined;
            this.speedTestResults = [];
            this.cachedTooltip = undefined;
            // 显示加载状态
            this.showLoading();
        }
    }

    /**
     * 清除当前数据（切换平台时调用）
     */
    clearData(): void {
        this.currentSnapshot = undefined;
        this.speedTestResults = [];
        this.cachedTooltip = undefined;
        this.tooltipDirty = true;
    }

    dispose(): void {
        this.statusBarItem.dispose();
    }
}
