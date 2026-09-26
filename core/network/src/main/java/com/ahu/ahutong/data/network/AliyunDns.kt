package com.ahu.ahutong.data.network

import okhttp3.Cache
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.File
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** Shared resolver for app-owned HTTP clients; the bootstrap client never uses this resolver. */
object AliyunDns : Dns {
    @Volatile
    private var cacheDirectory: File? = null

    /** Called before constructing clients. HTTP caching honors the DoH response's TTL headers. */
    fun initializeCache(directory: File) {
        cacheDirectory = directory
    }

    private val resolver by lazy {
        val bootstrapClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .apply { cacheDirectory?.let { cache(Cache(it, 2L * 1024 * 1024)) } }
            .build()
        val doh = DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url("https://dns.alidns.com/dns-query".toHttpUrl())
            // The HTTPS hostname and certificate are still verified against dns.alidns.com.
            .bootstrapDnsHosts(
                InetAddress.getByAddress(byteArrayOf(223.toByte(), 5, 5, 5)),
                InetAddress.getByAddress(byteArrayOf(223.toByte(), 6, 6, 6))
            )
            .includeIPv6(true)
            .build()
        CampusCompatibleDns(doh, Dns.SYSTEM)
    }

    override fun lookup(hostname: String): List<InetAddress> = resolver.lookup(hostname)
}

/** Public names prefer DoH; local names and campus split-horizon records retain system DNS. */
internal class CampusCompatibleDns(
    private val doh: Dns,
    private val system: Dns
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val host = hostname.lowercase().trimEnd('.')
        if (isIpLiteral(host)) return listOf(InetAddress.getByName(host))
        if (isLocalName(host)) return system.lookup(hostname)

        // On campus the same public hostname can have an intranet-only answer. Preserve it
        // before consulting a public resolver, which cannot see the campus DNS view.
        var campusAddresses: List<InetAddress>? = null
        if (host == "ahu.edu.cn" || host.endsWith(".ahu.edu.cn")) {
            campusAddresses = try {
                system.lookup(hostname).takeIf { it.isNotEmpty() }
            } catch (_: UnknownHostException) {
                null
            }
            if (campusAddresses?.any(::isLocalAddress) == true) return campusAddresses
        }

        try {
            return doh.lookup(hostname).takeIf { it.isNotEmpty() }
                ?: throw UnknownHostException("DoH returned no addresses for $hostname")
        } catch (dohFailure: UnknownHostException) {
            campusAddresses?.let { return it }
            return try {
                system.lookup(hostname).takeIf { it.isNotEmpty() }
                    ?: throw UnknownHostException("System DNS returned no addresses for $hostname")
            } catch (systemFailure: UnknownHostException) {
                systemFailure.addSuppressed(dohFailure)
                throw systemFailure
            }
        }
    }

    private fun isLocalName(host: String): Boolean = !host.contains('.') ||
        LOCAL_SUFFIXES.any { host == it || host.endsWith(".$it") }

    private fun isIpLiteral(host: String): Boolean = host.contains(':') ||
        host.split('.').let { parts ->
            parts.size == 4 && parts.all { part ->
                part.isNotEmpty() && part.all(Char::isDigit) && part.toIntOrNull() in 0..255
            }
        }

    private fun isLocalAddress(address: InetAddress): Boolean =
        address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            (address.address.size == 16 && (address.address[0].toInt() and 0xfe) == 0xfc)

    private companion object {
        val LOCAL_SUFFIXES = listOf("localhost", "local", "lan", "internal", "home", "home.arpa")
    }
}
