/**
 * API Quota Watcher - 状态栏服务（优化版 + 国际化 + 扩展数据支持）
 */

import * as vscode from 'vscode';
import { QuotaSnapshot, Config, SpeedTestResult, ExtendedQuotaData, BudgetPeriod, PackyCodeProgressMode, CubenceProgressMode } from './types';
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

        // 根据平台类型和进度条模式获取对应的使用数据
        const { used, total, remaining } = this.getDisplayQuota(snapshot);
        const remainPct = total > 0 ? (remaining / total) * 100 : 0;
        const usedPct = 100 - remainPct;

        // 状态图标
        if (widgets.statusIcon) {
            const icon = remainPct > 60 ? '🟢' : remainPct > 20 ? '🟡' : '🔴';
            parts.push(icon);
        }

        // 进度条展示（状态栏上的可视化进度条）
        if (widgets.progressBar) {
            const progressBar = this.buildStatusBarProgressBar(usedPct);
            parts.push(progressBar);
        }

        // 状态比例
        if (widgets.percentage) {
            parts.push(`${usedPct.toFixed(1)}%`);
        }

        // 已使用金额
        if (widgets.used) {
            parts.push(`$${used.toFixed(2)}`);
        }

        // 总金额
        if (widgets.total) {
            parts.push(`$${total.toFixed(2)}`);
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

    /**
     * 构建状态栏进度条（精细版，使用 Unicode 块字符实现平滑过渡）
     * 10 格 x 8 段 = 可精确到 1.25% 的进度显示
     */
    private buildStatusBarProgressBar(percentage: number): string {
        const pct = Math.min(100, Math.max(0, percentage));
        const totalBlocks = 10; // 总格数
        const filledBlocks = (pct / 100) * totalBlocks;

        // Unicode 块字符：从满到空的 8 段
        const blocks = ['█', '▉', '▊', '▋', '▌', '▍', '▎', '▏', ' '];

        let result = '';
        for (let i = 0; i < totalBlocks; i++) {
            const blockValue = filledBlocks - i;
            if (blockValue >= 1) {
                result += blocks[0]; // 完全填充 █
            } else if (blockValue > 0) {
                // 部分填充：根据小数部分选择对应的块字符
                const partialIndex = Math.floor((1 - blockValue) * 8);
                result += blocks[Math.min(partialIndex, 7)];
            } else {
                result += '░'; // 空格用灰色块表示
            }
        }
        return result;
    }

    private getMinLatency(): number | undefined {
        const successes = this.speedTestResults.filter(r => r.status === 'success');
        if (successes.length === 0) return undefined;
        return Math.min(...successes.map(r => r.latency));
    }

    /**
     * 根据平台类型和进度条模式获取要显示的配额数据
     */
    private getDisplayQuota(snapshot: QuotaSnapshot): { used: number; total: number; remaining: number } {
        const platformType = this.config.platformType;
        const extended = snapshot.extended;

        // PackyCode 平台：根据配置选择今日/本周/本月
        if (platformType === 'packycode' && extended) {
            const mode = this.config.packycodeProgressMode;
            const period = this.getPackyCodePeriodByMode(extended, mode);
            if (period) {
                return {
                    used: period.spent,
                    total: period.budget,
                    remaining: period.remaining
                };
            }
        }

        // Cubence 平台：根据配置选择5小时/本周/API Key
        if (platformType === 'cubence' && extended) {
            const mode = this.config.cubenceProgressMode;
            const period = this.getCubencePeriodByMode(extended, mode);
            if (period) {
                return {
                    used: period.spent,
                    total: period.budget,
                    remaining: period.remaining
                };
            }
        }

        // 默认返回快照中的基础数据（NewAPI/PackyAPI 等）
        return {
            used: snapshot.used,
            total: snapshot.total,
            remaining: snapshot.remaining
        };
    }

    /**
     * 根据 PackyCode 进度条模式获取对应的预算周期
     */
    private getPackyCodePeriodByMode(extended: ExtendedQuotaData, mode: PackyCodeProgressMode): BudgetPeriod | undefined {
        switch (mode) {
            case 'daily':
                return extended.daily;
            case 'weekly':
                return extended.weekly;
            case 'monthly':
                return extended.monthly;
            default:
                return extended.daily;
        }
    }

    /**
     * 根据 Cubence 进度条模式获取对应的预算周期
     */
    private getCubencePeriodByMode(extended: ExtendedQuotaData, mode: CubenceProgressMode): BudgetPeriod | undefined {
        switch (mode) {
            case 'fiveHour':
                return extended.fiveHour;
            case 'weekly':
                return extended.weekly;
            case 'apiKey':
                return extended.apiKeyQuota;
            default:
                return extended.fiveHour;
        }
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
        const platformName = this.getPlatformDisplayName();

        let content = `**${platformName}**\n\n`;
        content += `| 周期 | 已用 | 预算 | 进度 |\n`;
        content += `|:-----|-----:|-----:|:-----|\n`;
        content += `| 总额度 | $${snapshot.used.toFixed(2)} | $${snapshot.total.toFixed(2)} | ${this.buildProgressBar(usedPct)} |\n`;

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
            content += `| 项目 | 金额 |\n`;
            content += `|:-----|-----:|\n`;
            content += `| 余额 | $${ext.balanceUsd.toFixed(2)} |\n\n`;
        }

        // 额度使用表格 (带重置时间)
        const quotaRows: { label: string; period: BudgetPeriod }[] = [];
        if (ext.apiKeyQuota) quotaRows.push({ label: 'API Key', period: ext.apiKeyQuota });
        if (ext.fiveHour) quotaRows.push({ label: '5小时', period: ext.fiveHour });
        if (ext.weekly) quotaRows.push({ label: '本周', period: ext.weekly });

        if (quotaRows.length > 0) {
            content += `**额度使用**\n\n`;
            content += `| 周期 | 已用 | 预算 | 进度 | 重置时间 |\n`;
            content += `|:-----|-----:|-----:|:-----|:-----|\n`;
            for (const row of quotaRows) {
                const resetTimeStr = row.period.resetAt
                    ? this.formatResetTime(row.period.resetAt)
                    : '-';
                content += `| ${row.label} | $${row.period.spent.toFixed(2)} | $${row.period.budget.toFixed(2)} | ${this.buildProgressBar(row.period.percentage)} | ${resetTimeStr} |\n`;
            }
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

        // 额度使用表格
        const quotaRows: { label: string; period: BudgetPeriod }[] = [];
        if (ext.monthly) quotaRows.push({ label: '本月', period: ext.monthly });
        if (ext.weekly) quotaRows.push({ label: '本周', period: ext.weekly });
        if (ext.daily) quotaRows.push({ label: '今日', period: ext.daily });

        if (quotaRows.length > 0) {
            content += `\n**额度使用**\n\n`;
            content += `| 周期 | 已用 | 预算 | 进度 |\n`;
            content += `|:-----|-----:|-----:|:-----|\n`;
            for (const row of quotaRows) {
                content += `| ${row.label} | $${row.period.spent.toFixed(2)} | $${row.period.budget.toFixed(2)} | ${this.buildProgressBar(row.period.percentage)} |\n`;
            }
        }

        content += this.buildSpeedTestSection();

        const tooltip = new vscode.MarkdownString(content);
        tooltip.isTrusted = true;
        return tooltip;
    }

    /**
     * 构建进度条（使用 Unicode 字符 + 等宽字体）
     */
    private buildProgressBar(percentage: number): string {
        const pct = Math.min(100, Math.max(0, percentage));
        const filled = Math.round(pct / 10);
        const empty = 10 - filled;
        const bar = '█'.repeat(filled) + '░'.repeat(empty);
        // 使用反引号包裹实现等宽字体效果，与 IDEA 插件保持一致
        return `\`${bar}\` ${pct.toFixed(1)}%`;
    }

    /**
     * 构建测速结果段落
     */
    private buildSpeedTestSection(): string {
        if (this.speedTestResults.length === 0) {
            return '';
        }

        let content = `\n\n**测速**\n\n`;
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
     * 格式化重置时间（用于 Cubence 等平台）
     * 显示相对时间，如 "3小时后" 或 "2天后"
     */
    private formatResetTime(date: Date): string {
        const now = new Date();
        const diff = date.getTime() - now.getTime();

        if (diff <= 0) {
            return '已重置';
        }

        const minutes = Math.floor(diff / (1000 * 60));
        const hours = Math.floor(diff / (1000 * 60 * 60));
        const days = Math.floor(diff / (1000 * 60 * 60 * 24));

        if (days > 0) {
            const remainingHours = hours % 24;
            return remainingHours > 0 ? `${days}天${remainingHours}小时` : `${days}天后`;
        } else if (hours > 0) {
            const remainingMinutes = minutes % 60;
            return remainingMinutes > 0 ? `${hours}小时${remainingMinutes}分` : `${hours}小时后`;
        } else {
            return `${minutes}分钟后`;
        }
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
