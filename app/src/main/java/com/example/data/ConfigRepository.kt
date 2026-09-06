package com.example.data

import com.example.model.DnsResolver
import com.example.model.GameServer
import com.example.model.GlobalThresholds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ConfigRepository {

    var apiBaseUrl: String = "http://10.26.8.122:3000"
        private set

    var pingTarget: String = "8.8.8.8"
        private set

    var thresholds = GlobalThresholds()
        private set

    var gameServers: List<GameServer> = defaultGameServers()
        private set

    var dnsResolvers: List<DnsResolver> = defaultDnsResolvers()
        private set

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    fun updateApiBaseUrl(newUrl: String) {
        apiBaseUrl = newUrl.trim().removeSuffix("/")
    }

    suspend fun fetchRemoteConfig(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$apiBaseUrl/api/config")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        parseConfigJson(body)
                        return@withContext true
                    }
                }
            }
        } catch (_: Exception) {
            // fallback to default config
        }
        return@withContext false
    }

    private fun parseConfigJson(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            if (json.has("pingTarget")) {
                pingTarget = json.getString("pingTarget")
            }
            if (json.has("globalThresholds")) {
                val gt = json.getJSONObject("globalThresholds")
                val ping = gt.optJSONObject("ping")
                val jitter = gt.optJSONObject("jitter")
                val packetLoss = gt.optJSONObject("packetLoss")
                val dbm = gt.optJSONObject("dbm")
                val dns = gt.optJSONObject("dns")

                thresholds = GlobalThresholds(
                    pingGoodMax = ping?.optDouble("goodMax", 50.0) ?: 50.0,
                    pingFairMax = ping?.optDouble("fairMax", 100.0) ?: 100.0,
                    jitterGoodMax = jitter?.optDouble("goodMax", 10.0) ?: 10.0,
                    jitterFairMax = jitter?.optDouble("fairMax", 30.0) ?: 30.0,
                    packetLossGoodMax = packetLoss?.optDouble("goodMax", 1.0) ?: 1.0,
                    packetLossFairMax = packetLoss?.optDouble("fairMax", 5.0) ?: 5.0,
                    dbmGoodMin = dbm?.optInt("goodMin", -65) ?: -65,
                    dbmFairMin = dbm?.optInt("fairMin", -85) ?: -85,
                    dnsSmoothMax = dns?.optDouble("smoothMax", 80.0) ?: 80.0,
                    dnsPlayableMax = dns?.optDouble("playableMax", 200.0) ?: 200.0
                )
            }
        } catch (_: Exception) { }
    }

    companion object {
        fun defaultGameServers(): List<GameServer> = listOf(
            GameServer(
                id = "pubg",
                name = "PUBG Mobile Asia",
                hosts = listOf("asia.pubg.com", "pubgmobile.com", "1.1.1.1")
            ),
            GameServer(
                id = "freefire",
                name = "Free Fire SG",
                hosts = listOf("sg.freefiremobile.com", "freefire.com", "8.8.8.8")
            ),
            GameServer(
                id = "valorant",
                name = "Valorant Mumbai",
                hosts = listOf("ap-south-1.amazonaws.com", "mumbai.valve.net", "1.0.0.1")
            ),
            GameServer(
                id = "mlbb",
                name = "Mobile Legends SEA",
                hosts = listOf("sea.mobilelegends.com", "8.8.4.4", "1.1.1.1")
            ),
            GameServer(
                id = "genshin",
                name = "Genshin Impact Asia",
                hosts = listOf("genshin.hoyoverse.com", "1.0.0.1", "8.8.8.8")
            ),
            GameServer(
                id = "codm",
                name = "Call of Duty Mobile",
                hosts = listOf("codm.activision.com", "208.67.222.222", "8.8.8.8")
            )
        )

        fun defaultDnsResolvers(): List<DnsResolver> = listOf(
            DnsResolver("google", "Google DNS", "https://dns.google/resolve"),
            DnsResolver("cloudflare", "Cloudflare DNS", "https://cloudflare-dns.com/dns-query"),
            DnsResolver("quad9", "Quad9 DNS", "https://dns.quad9.net/dns-query"),
            DnsResolver("adguard", "AdGuard DNS", "https://dns.adguard-dns.com/dns-query")
        )

        val TEST_DOMAINS = listOf("google.com", "cloudflare.com", "wikipedia.org", "github.com")
    }
}
