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

        const parts: string[] = [];
        const snapshot = this.currentSnapshot;
        const widgets = this.config.widgets;
        const remainPct = snapshot.total > 0 ? (snapshot.remaining / snapshot.total) * 100 : 0;
        const usedPct = 100 - remainPct;

        // 状态图标
        if (widgets.statusIcon) {
            const icon = remainPct > 60 ? '🟢' : remainPct > 20 ? '🟡' : '🔴';
            parts.push(icon);
        }

        // 状态比例
        if (widgets.percentage) {
            parts.push(`${usedPct.toFixed(1)}%`);
        }

        // 已使用金额
        if (widgets.used) {
            parts.push(`$${snapshot.used.toFixed(2)}`);
        }

        // 总金额
        if (widgets.total) {
            parts.push(`$${snapshot.total.toFixed(2)}`);
        }

        // 测速延迟
        if (widgets.latency) {
            const minLatency = this.getMinLatency();
            if (minLatency !== undefined) {
                parts.push(`${minLatency}ms`);
            }
        }

        this.statusBarItem.text = parts.length > 0 ? parts.join(' ') : '$(credit-card) --';
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
        const platformName = this.getPlatformDisplayName();

        let content = `**${platformName}**\n\n`;
        content += `| 项目 | 金额 | 比例 |\n`;
        content += `|:-----|-----:|-----:|\n`;
        content += `| 剩余 | $${snapshot.remaining.toFixed(2)} | ${remainPct.toFixed(1)}% |\n`;
        content += `| 已用 | $${snapshot.used.toFixed(2)} | ${usedPct.toFixed(1)}% |\n`;
        content += `| 总额 | $${snapshot.total.toFixed(2)} | - |\n`;

        content += this.buildSpeedTestSection();

        const tooltip = new vscode.MarkdownString(content);
        tooltip.isTrusted = true;
        return tooltip;
    }

    /**
     * 构建 Cubence Tooltip
     */
    private buildCubenceTooltip(snapshot: QuotaSnapshot, ext: ExtendedQuotaData): vscode.MarkdownString {
        let content = `**Cubence**\n\n`;

        if (ext.balanceUsd !== undefined) {
            content += `余额: $${ext.balanceUsd.toFixed(2)}\n\n`;
        }

        if (ext.apiKeyQuota) {
            content += this.buildPeriodSection('API Key 配额', ext.apiKeyQuota);
        }
        if (ext.fiveHour) {
            content += this.buildPeriodSection('5小时窗口', ext.fiveHour);
        }
        if (ext.weekly) {
            content += this.buildPeriodSection('本周限制', ext.weekly);
        }

        content += this.buildSpeedTestSection();

        const tooltip = new vscode.MarkdownString(content);
        tooltip.isTrusted = true;
        return tooltip;
    }

    /**
     * 构建扩展 Tooltip（PackyCode 包月）
     */
    private buildExtendedTooltip(snapshot: QuotaSnapshot, ext: ExtendedQuotaData): vscode.MarkdownString {
        let content = `**PackyCode**\n\n`;

        // 账户信息
        content += `| 项目 | 值 |\n`;
        content += `|:-----|:-----|\n`;
        if (ext.username) {
            content += `| 用户 | ${ext.username} |\n`;
        }
        content += `| 套餐 | ${snapshot.planName} |\n`;
        if (ext.planExpiresAt) {
            const daysLeft = this.getDaysUntil(ext.planExpiresAt);
            content += `| 到期 | ${daysLeft}天后 |\n`;
        }
        if (ext.balanceUsd !== undefined) {
            content += `| 余额 | $${ext.balanceUsd.toFixed(2)} |\n`;
        }

        // 预算信息
        if (ext.monthly) {
            content += `\n` + this.buildPeriodSection('本月', ext.monthly);
        }
        if (ext.weekly) {
            content += this.buildPeriodSection('本周', ext.weekly);
        }
        if (ext.daily) {
            content += this.buildPeriodSection('今日', ext.daily);
        }

        content += this.buildSpeedTestSection();

        const tooltip = new vscode.MarkdownString(content);
        tooltip.isTrusted = true;
        return tooltip;
    }

    /**
     * 构建周期预算段落
     */
    private buildPeriodSection(title: string, period: BudgetPeriod): string {
        const remainPct = 100 - period.percentage;

        let content = `**${title}** (已用 ${period.percentage.toFixed(1)}%)\n\n`;
        content += `| 项目 | 金额 |\n`;
        content += `|:-----|-----:|\n`;
        content += `| 剩余 | $${period.remaining.toFixed(2)} |\n`;
        content += `| 已用 | $${period.spent.toFixed(2)} |\n`;
        content += `| 预算 | $${period.budget.toFixed(2)} |\n`;
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

        let content = `\n---\n\n**测速**\n\n`;
        content += `| 节点 | 延迟 |\n`;
        content += `|:-----|-----:|\n`;

        for (const r of this.speedTestResults) {
            const host = this.shortenUrl(r.url);
            const latency = r.status === 'success' ? `${r.latency}ms` : '-';
            content += `| ${host} | ${latency} |\n`;
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
