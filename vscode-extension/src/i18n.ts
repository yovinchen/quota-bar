/**
 * API Quota Watcher - 国际化模块 (默认中文)
 */

import * as vscode from 'vscode';

interface Messages {
    // 状态栏
    querying: string;
    retrying: string;
    failed: string;
    notConfigured: string;

    // Tooltip
    tooltipTitle: string;
    progress: string;
    quotaInfo: string;
    remaining: string;
    used: string;
    total: string;
    plan: string;
    speedTest: string;
    clickToRefresh: string;

    // 消息
    connectionSuccess: string;
    connectionFailed: string;
    tokenSaved: string;
    speedTestComplete: string;
    noSpeedTestUrls: string;
    configMissing: string;

    // 输入框
    enterToken: string;
    tokenEmpty: string;
    addSpeedTestUrls: string;
    testingUrls: string;
    success: string;
    avgLatency: string;
}

const messages: Messages = {
    querying: '查询中...',
    retrying: '重试中',
    failed: '查询失败',
    notConfigured: '未配置',

    tooltipTitle: '配额详情',
    progress: '使用进度',
    quotaInfo: '额度信息',
    remaining: '剩余',
    used: '已用',
    total: '总额',
    plan: '套餐',
    speedTest: '测速结果',
    clickToRefresh: '点击刷新',

    connectionSuccess: '连接成功！',
    connectionFailed: '连接失败',
    tokenSaved: 'Access Token 已保存',
    speedTestComplete: '测速完成',
    noSpeedTestUrls: '未配置测速地址，是否现在添加？',
    configMissing: '请先配置',

    enterToken: '请输入 Access Token',
    tokenEmpty: 'Access Token 不能为空',
    addSpeedTestUrls: '是否添加？',
    testingUrls: '测试 {0} 个地址',
    success: '成功',
    avgLatency: '平均延迟',
};

/**
 * 获取本地化消息 (固定为中文)
 */
export function getMessages(): Messages {
    return messages;
}

/**
 * 格式化消息（支持占位符）
 */
export function format(message: string, ...args: (string | number)[]): string {
    return message.replace(/\{(\d+)\}/g, (_, index) => String(args[index] ?? ''));
}

export const i18n = {
    get: getMessages,
    format,
};

