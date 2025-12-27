package com.yovinchen.apiquotawatcher.service

import java.util.Date

/**
 * 预算周期数据
 */
data class BudgetPeriod(
    val budget: Double,
    val spent: Double,
    val remaining: Double,
    val percentage: Double
)

/**
 * 扩展配额数据（用于 PackyCode 等丰富数据平台）
 */
data class ExtendedQuotaData(
    // 用户信息
    val username: String? = null,
    val email: String? = null,

    // 套餐信息
    val planType: String? = null,
    val planExpiresAt: Date? = null,

    // 余额信息
    val balanceUsd: Double? = null,
    val totalSpentUsd: Double? = null,

    // 各周期预算
    val daily: BudgetPeriod? = null,
    val weekly: BudgetPeriod? = null,
    val monthly: BudgetPeriod? = null,
    val fiveHour: BudgetPeriod? = null,
    val apiKeyQuota: BudgetPeriod? = null,

    // 周统计窗口
    val weeklyWindowStart: Date? = null,
    val weeklyWindowEnd: Date? = null
)

/**
 * 测速结果
 */
data class SpeedTestResult(
    val url: String,
    val latency: Long?,
    val status: SpeedTestStatus,
    val error: String? = null
)

enum class SpeedTestStatus {
    SUCCESS, FAILED, PENDING
}

/**
 * 基础配额信息
 */
data class QuotaInfo(
    val used: Double,
    val total: Double,
    val remaining: Double,
    val percentage: Double,
    val unit: String = "$",
    val extended: ExtendedQuotaData? = null
)

interface QuotaService {
    fun fetchQuota(): QuotaInfo?
    fun getDisplayText(info: QuotaInfo): String
    fun testSpeed(): Long?
    fun testSpeedAll(): List<SpeedTestResult>
}
