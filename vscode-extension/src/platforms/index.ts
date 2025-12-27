/**
 * 平台适配器模块
 */

import { PlatformType } from '../types';
import { IPlatformAdapter } from './types';
import { NewApiAdapter } from './newapi';
import { PackyAPIAdapter } from './packyapi';
import { PackyCodeMonthlyAdapter } from './packycodeMonthly';

import { CubenceAdapter } from './cubence';

// 创建适配器实例
const adapters: Record<PlatformType, IPlatformAdapter> = {
    newapi: new NewApiAdapter(),
    packyapi: new PackyAPIAdapter(),
    'packycode': new PackyCodeMonthlyAdapter(),
    cubence: new CubenceAdapter(),
};

/**
 * 根据平台类型获取适配器
 */
export function getAdapter(platformType: PlatformType = 'newapi'): IPlatformAdapter {
    return adapters[platformType] || adapters.newapi;
}

/**
 * 获取所有支持的平台类型
 */
export function getSupportedPlatforms(): PlatformType[] {
    return Object.keys(adapters) as PlatformType[];
}

export { IPlatformAdapter } from './types';
export { NewApiAdapter } from './newapi';
export { PackyAPIAdapter } from './packyapi';
export { PackyCodeMonthlyAdapter } from './packycodeMonthly';
export { CubenceAdapter } from './cubence';
