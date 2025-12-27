package com.yovinchen.apiquotawatcher.service

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.yovinchen.apiquotawatcher.settings.QuotaSettings
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

class QuotaServiceImpl : QuotaService {

    private val LOG = Logger.getInstance(QuotaServiceImpl::class.java)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")

    override fun fetchQuota(): QuotaInfo? {
        val settings = QuotaSettings.getInstance().state

        if (!settings.enabled) {
            return null
        }

        return when (settings.platformType) {
            "newapi" -> fetchNewapiQuota(settings)
            "packyapi" -> fetchPackyapiQuota(settings)
            "packycode" -> fetchPackycodeQuota(settings)
            "cubence" -> fetchCubenceQuota(settings)
            else -> null
        }
    }

    private fun fetchNewapiQuota(settings: QuotaSettings.State): QuotaInfo? {
        if (settings.newapiBaseUrl.isBlank() || settings.newapiAccessToken.isBlank()) {
            LOG.warn("NewAPI: baseUrl or accessToken is empty")
            return null
        }

        return try {
            val url = "${settings.newapiBaseUrl.trimEnd('/')}/api/user/self"
            LOG.info("NewAPI: Fetching quota from $url")
            
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${settings.newapiAccessToken}")
                .header("New-Api-User", settings.newapiUserId)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    LOG.warn("NewAPI: Request failed with code ${response.code}")
                    return null
                }

                val body = response.body?.string() ?: return null
                val json = gson.fromJson(body, JsonObject::class.java)
                val data = json.getAsJsonObject("data") ?: return null

                val quota = data.get("quota")?.asDouble ?: 0.0
                val usedQuota = data.get("used_quota")?.asDouble ?: 0.0

                val remaining = quota / 500000.0
                val used = usedQuota / 500000.0
                val total = remaining + used
                val percentage = if (total > 0) (used / total) * 100 else 0.0

                LOG.info("NewAPI: Quota fetched - used=$used, total=$total, remaining=$remaining")
                QuotaInfo(used, total, remaining, percentage)
            }
        } catch (e: Exception) {
            LOG.error("NewAPI: Error fetching quota", e)
            null
        }
    }

    private fun fetchPackyapiQuota(settings: QuotaSettings.State): QuotaInfo? {
        if (settings.packyapiBaseUrl.isBlank() || settings.packyapiAccessToken.isBlank()) {
            LOG.warn("PackyAPI: baseUrl or accessToken is empty")
            return null
        }

        return try {
            val url = "${settings.packyapiBaseUrl.trimEnd('/')}/api/user/self"
            LOG.info("PackyAPI: Fetching quota from $url")
            
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${settings.packyapiAccessToken}")
                .header("New-Api-User", settings.packyapiUserId)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    LOG.warn("PackyAPI: Request failed with code ${response.code}")
                    return null
                }

                val body = response.body?.string() ?: return null
                val json = gson.fromJson(body, JsonObject::class.java)
                val data = json.getAsJsonObject("data") ?: return null

                val quota = data.get("quota")?.asDouble ?: 0.0
                val usedQuota = data.get("used_quota")?.asDouble ?: 0.0

                val remaining = quota / 500000.0
                val used = usedQuota / 500000.0
                val total = remaining + used
                val percentage = if (total > 0) (used / total) * 100 else 0.0

                LOG.info("PackyAPI: Quota fetched - used=$used, total=$total, remaining=$remaining")
                QuotaInfo(used, total, remaining, percentage)
            }
        } catch (e: Exception) {
            LOG.error("PackyAPI: Error fetching quota", e)
            null
        }
    }

    /**
     * PackyCode 使用不同的 API 端点和响应格式
     * API: /api/backend/users/info
     * 响应直接返回 USD 金额，不需要换算
     * 显示每日额度，并包含扩展信息
     */
    private fun fetchPackycodeQuota(settings: QuotaSettings.State): QuotaInfo? {
        if (settings.packycodeBaseUrl.isBlank() || settings.packycodeAccessToken.isBlank()) {
            LOG.warn("PackyCode: baseUrl or accessToken is empty")
            return null
        }

        return try {
            val url = "${settings.packycodeBaseUrl.trimEnd('/')}/api/backend/users/info"
            LOG.info("PackyCode: Fetching quota from $url")
            
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${settings.packycodeAccessToken}")
                .header("Content-Type", "application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    LOG.warn("PackyCode: Request failed with code ${response.code}")
                    return null
                }

                val body = response.body?.string() ?: return null
                LOG.debug("PackyCode: Response body: $body")
                
                val json = gson.fromJson(body, JsonObject::class.java)

                // 解析数值
                fun parseDouble(key: String): Double {
                    return json.get(key)?.asString?.toDoubleOrNull() ?: 0.0
                }

                // 解析日期
                fun parseDate(key: String): Date? {
                    return try {
                        json.get(key)?.asString?.let { dateFormat.parse(it) }
                    } catch (e: Exception) {
                        null
                    }
                }

                // 构建预算周期
                fun buildPeriod(budget: Double, spent: Double): BudgetPeriod {
                    val remaining = budget - spent
                    val percentage = if (budget > 0) (spent / budget) * 100 else 0.0
                    return BudgetPeriod(budget, spent, remaining, percentage)
                }

                // 解析各项数据
                val dailyBudget = parseDouble("daily_budget_usd")
                val dailySpent = parseDouble("daily_spent_usd")
                val weeklyBudget = parseDouble("weekly_budget_usd")
                val weeklySpent = parseDouble("weekly_spent_usd")
                val monthlyBudget = parseDouble("monthly_budget_usd")
                val monthlySpent = parseDouble("monthly_spent_usd")
                val balanceUsd = parseDouble("balance_usd")
                val totalSpentUsd = parseDouble("total_spent_usd")

                // 构建扩展数据
                val extended = ExtendedQuotaData(
                    username = json.get("username")?.asString,
                    email = json.get("email")?.asString,
                    planType = json.get("plan_type")?.asString,
                    planExpiresAt = parseDate("plan_expires_at"),
                    balanceUsd = balanceUsd,
                    totalSpentUsd = totalSpentUsd,
                    daily = buildPeriod(dailyBudget, dailySpent),
                    weekly = buildPeriod(weeklyBudget, weeklySpent),
                    monthly = buildPeriod(monthlyBudget, monthlySpent),
                    weeklyWindowStart = parseDate("weekly_window_start"),
                    weeklyWindowEnd = parseDate("weekly_window_end")
                )

                // 主要显示日度数据
                val total = dailyBudget
                val used = dailySpent
                val remaining = total - used
                val percentage = if (total > 0) (used / total) * 100 else 0.0

                LOG.info("PackyCode: Daily quota fetched - used=$used, total=$total, remaining=$remaining")
                QuotaInfo(used, total, remaining, percentage, extended = extended)
            }
        } catch (e: Exception) {
            LOG.error("PackyCode: Error fetching quota", e)
            null
        }

    }

    private fun fetchCubenceQuota(settings: QuotaSettings.State): QuotaInfo? {
        if (settings.cubenceBaseUrl.isBlank() || settings.cubenceAccessToken.isBlank()) {
            LOG.warn("Cubence: baseUrl or accessToken is empty")
            return null
        }

        return try {
            val url = "${settings.cubenceBaseUrl.trimEnd('/')}/api/v1/user/subscription-info"
            LOG.info("Cubence: Fetching quota from $url")
            
            val request = Request.Builder()
                .url(url)
                .header("Authorization", settings.cubenceAccessToken)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    LOG.warn("Cubence: Request failed with code ${response.code}")
                    return null
                }

                val body = response.body?.string() ?: return null
                val json = gson.fromJson(body, JsonObject::class.java)
                
                // Helper to parse units to dollars
                fun unitsToDollar(units: Long): Double = units / 1_000_000.0

                // 1. 解析余额
                val normalBalance = json.getAsJsonObject("normal_balance")
                val balanceDollar = normalBalance?.get("amount_dollar")?.asDouble ?: 0.0

                // 2. 解析订阅窗口
                val subWindow = json.getAsJsonObject("subscription_window")
                
                // 5小时限制 (作为主要显示)
                val fiveHour = subWindow?.getAsJsonObject("five_hour")
                val fiveHourLimit = unitsToDollar(fiveHour?.get("limit")?.asLong ?: 0)
                val fiveHourRemaining = unitsToDollar(fiveHour?.get("remaining")?.asLong ?: 0)
                val fiveHourUsed = fiveHourLimit - fiveHourRemaining
                val fiveHourPercent = if (fiveHourLimit > 0) (fiveHourUsed / fiveHourLimit) * 100 else 0.0
                val fiveHourPeriod = BudgetPeriod(fiveHourLimit, fiveHourUsed, fiveHourRemaining, fiveHourPercent)

                // 周限制
                val weekly = subWindow?.getAsJsonObject("weekly")
                val weeklyLimit = unitsToDollar(weekly?.get("limit")?.asLong ?: 0)
                val weeklyRemaining = unitsToDollar(weekly?.get("remaining")?.asLong ?: 0)
                val weeklyUsed = weeklyLimit - weeklyRemaining // 注意 weekly.used 在 json 里可能有值，我们优先用计算值保证一致，或者直接取 used
                val weeklyPercent = if (weeklyLimit > 0) (weeklyUsed / weeklyLimit) * 100 else 0.0
                val weeklyPeriod = BudgetPeriod(weeklyLimit, weeklyUsed, weeklyRemaining, weeklyPercent)

                // 3. API Key 配额
                val apiKeyQuotaJson = json.getAsJsonObject("api_key_quota")
                val keyLimit = apiKeyQuotaJson?.get("quota_limit_dollar")?.asDouble ?: 0.0
                val keyUsed = apiKeyQuotaJson?.get("quota_used_dollar")?.asDouble ?: 0.0
                val keyRemaining = apiKeyQuotaJson?.get("remaining_dollar")?.asDouble ?: 0.0
                val keyPercent = if (keyLimit > 0) (keyUsed / keyLimit) * 100 else 0.0
                val apiKeyPeriod = BudgetPeriod(keyLimit, keyUsed, keyRemaining, keyPercent)

                // 构建扩展数据
                val extended = ExtendedQuotaData(
                    balanceUsd = balanceDollar,
                    fiveHour = fiveHourPeriod,
                    weekly = weeklyPeriod,
                    apiKeyQuota = apiKeyPeriod
                )

                // 主状态栏显示 5小时限制
                val total = fiveHourLimit
                val used = fiveHourUsed
                val remaining = fiveHourRemaining
                val percentage = fiveHourPercent

                LOG.info("Cubence: Quota fetched - 5H Limit: used=$used, total=$total")
                QuotaInfo(used, total, remaining, percentage, extended = extended)
            }
        } catch (e: Exception) {
            LOG.error("Cubence: Error fetching quota", e)
            null
        }
    }


    override fun getDisplayText(info: QuotaInfo): String {
        val settings = QuotaSettings.getInstance().state

        return when (settings.displayStyle) {
            "remaining" -> String.format("$%.2f", info.remaining)
            "percentage" -> String.format("%.1f%%", info.percentage)
            "both" -> String.format("$%.2f / $%.2f", info.used, info.total)
            else -> String.format("$%.2f", info.remaining)
        }
    }

    override fun testSpeed(): Long? {
        val results = testSpeedAll()
        return results.filter { it.status == SpeedTestStatus.SUCCESS }
            .minByOrNull { it.latency ?: Long.MAX_VALUE }
            ?.latency
    }

    override fun testSpeedAll(): List<SpeedTestResult> {
        val settings = QuotaSettings.getInstance().state

        if (!settings.speedTestEnabled) {
            return emptyList()
        }

        val speedTestUrls = when (settings.platformType) {
            "newapi" -> settings.newapiSpeedTestUrls
            "packyapi" -> settings.packyapiSpeedTestUrls
            "packycode" -> settings.packycodeSpeedTestUrls
            "cubence" -> settings.cubenceSpeedTestUrls
            else -> return emptyList()
        }

        val urlsToTest = if (speedTestUrls.isEmpty()) {
            // 如果没有配置测速地址，使用基础地址
            val baseUrl = when (settings.platformType) {
                "newapi" -> settings.newapiBaseUrl
                "packyapi" -> settings.packyapiBaseUrl
                "packycode" -> settings.packycodeBaseUrl
                "cubence" -> settings.cubenceBaseUrl
                else -> return emptyList()
            }
            if (baseUrl.isBlank()) return emptyList()
            listOf(baseUrl)
        } else {
            speedTestUrls
        }

        // 并行执行测速
        val futures = urlsToTest.map { url ->
            java.util.concurrent.CompletableFuture.supplyAsync {
                testSingleUrlWithResult(url)
            }
        }

        // 等待所有结果
        return try {
            java.util.concurrent.CompletableFuture.allOf(*futures.toTypedArray()).join()
            futures.map { it.get() }
        } catch (e: Exception) {
            LOG.error("Speed test failed", e)
            emptyList()
        }
    }

    private fun testSingleUrlWithResult(url: String): SpeedTestResult {
        if (url.isBlank()) return SpeedTestResult(url, null, SpeedTestStatus.FAILED, "Empty URL")

        return try {
            // 确保 URL 格式正确，添加 http/https 前缀
            val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else {
                url
            }

            // 强制只测试根路径，忽略配置中的接口路径
            val urlObj = java.net.URL(formattedUrl)
            val portPart = if (urlObj.port != -1) ":${urlObj.port}" else ""
            val testUrl = "${urlObj.protocol}://${urlObj.host}$portPart/"
            
            val request = Request.Builder()
                .url(testUrl)
                .head() // 使用 HEAD 请求，减少数据传输，仅测试连通性
                .build()

            val startTime = System.currentTimeMillis()
            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                // 只要能收到响应，就认为连接成功，无论状态码是多少
                // 测速主要关注网络延迟
                SpeedTestResult(url, latency, SpeedTestStatus.SUCCESS)
            }
        } catch (e: Exception) {
            LOG.debug("Speed test failed for $url: ${e.message}")
            SpeedTestResult(url, null, SpeedTestStatus.FAILED, "Error")
        }
    }

    companion object {
        fun getInstance(): QuotaService {
            return ApplicationManager.getApplication().getService(QuotaService::class.java)
        }
    }
}
