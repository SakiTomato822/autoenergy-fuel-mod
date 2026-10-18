package com.lynk.dvrprobe

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** Used only after the user enables the online map; never changes system DNS. */
internal class MapTileHttp(directory: File) {
    private val normal = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    private val resolver = DnsOverHttps.Builder()
        .client(normal.newBuilder().cache(okhttp3.Cache(File(directory, "dns"), 1024 * 1024)).build())
        .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
        // Bootstrap only the resolver, not the tile CDN. TLS still verifies its hostname.
        .bootstrapDnsHosts(InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1)),
            InetAddress.getByAddress(byteArrayOf(1, 0, 0, 1)))
        .includeIPv6(false).build()
    private val secure = normal.newBuilder().dns(object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            if (hostname != "tile.openstreetmap.org") throw UnknownHostException("unexpected map host")
            return resolver.lookup(hostname)
        }
    }).build()
    @Volatile private var secureUntilMs = 0L

    fun get(url: String, etag: String? = null, modified: String? = null): Response {
        val parsed = url.toHttpUrl()
        require(parsed.scheme == "https" && parsed.host == "tile.openstreetmap.org")
        val builder = okhttp3.Request.Builder().url(parsed)
            .header("User-Agent", "AutoEnergyFuel/${BuildConfig.VERSION_NAME} (+https://github.com/SakiTomato822/autoenergy-fuel-mod)")
        etag?.let { builder.header("If-None-Match", it) }
        modified?.let { builder.header("If-Modified-Since", it) }
        val request = builder.build()
        if (System.currentTimeMillis() < secureUntilMs) return secure.newCall(request).execute()
        try {
            return normal.newCall(request).execute()
        } catch (error: IOException) {
            AppLog.w("MAP", "map connection failed kind=${error.javaClass.simpleName}; retrying with HTTPS DNS (coordinates omitted)")
            val response = secure.newCall(request).execute()
            secureUntilMs = System.currentTimeMillis() + 5 * 60_000L
            return response
        }
    }
}
