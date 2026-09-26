package com.ahu.ahutong.data.network

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class CampusCompatibleDnsTest {
    private val publicAddress = InetAddress.getByAddress(byteArrayOf(1, 2, 3, 4))
    private val privateAddress = InetAddress.getByAddress(byteArrayOf(10, 0, 0, 2))

    @Test
    fun publicDomainsUseDohWithoutConsultingSystemDns() {
        val doh = RecordingDns(listOf(publicAddress))
        val system = RecordingDns(listOf(privateAddress))

        assertEquals(listOf(publicAddress), CampusCompatibleDns(doh, system).lookup("openahu.org"))
        assertEquals(listOf("openahu.org"), doh.lookups)
        assertEquals(emptyList(), system.lookups)
    }

    @Test
    fun literalIpsNeverMakeDnsRequests() {
        val doh = RecordingDns(listOf(publicAddress))
        val system = RecordingDns(listOf(privateAddress))
        val resolver = CampusCompatibleDns(doh, system)

        assertEquals("127.0.0.1", resolver.lookup("127.0.0.1").single().hostAddress)
        assertEquals(InetAddress.getByName("::1"), resolver.lookup("::1").single())
        assertEquals(emptyList(), doh.lookups)
        assertEquals(emptyList(), system.lookups)
    }

    @Test
    fun localNamesStayOnSystemDns() {
        val doh = RecordingDns(listOf(publicAddress))
        val system = RecordingDns(listOf(privateAddress))
        val resolver = CampusCompatibleDns(doh, system)

        listOf("localhost", "printer.local", "gateway", "router.home.arpa").forEach {
            assertEquals(listOf(privateAddress), resolver.lookup(it))
        }
        assertEquals(emptyList(), doh.lookups)
        assertEquals(4, system.lookups.size)
    }

    @Test
    fun campusPrivateAndIpv6UniqueLocalAnswersTakePriority() {
        val uniqueLocal = InetAddress.getByName("fd00::2")
        listOf(privateAddress, uniqueLocal).forEach { localAddress ->
            val doh = RecordingDns(listOf(publicAddress))
            val system = RecordingDns(listOf(localAddress))

            assertEquals(listOf(localAddress), CampusCompatibleDns(doh, system).lookup("JW.AHU.EDU.CN."))
            assertEquals(emptyList(), doh.lookups)
        }
    }

    @Test
    fun publicCampusAnswersStillUseDoh() {
        val doh = RecordingDns(listOf(publicAddress))
        val system = RecordingDns(listOf(InetAddress.getByAddress(byteArrayOf(5, 6, 7, 8))))

        assertEquals(listOf(publicAddress), CampusCompatibleDns(doh, system).lookup("ycard.ahu.edu.cn"))
        assertEquals(1, doh.lookups.size)
        assertEquals(1, system.lookups.size)
    }

    @Test
    fun publicSuffixLookalikesDoNotReceiveCampusSystemLookup() {
        val doh = RecordingDns(listOf(publicAddress))
        val system = RecordingDns(listOf(privateAddress))

        CampusCompatibleDns(doh, system).lookup("ahu.edu.cn.example.com")
        assertEquals(emptyList(), system.lookups)
    }

    @Test
    fun failedOrEmptyDohAnswerFallsBackToSystem() {
        val system = RecordingDns(listOf(publicAddress))
        listOf(RecordingDns(emptyList()), RecordingDns(failure = UnknownHostException("DoH unavailable")))
            .forEach { doh ->
                assertEquals(listOf(publicAddress), CampusCompatibleDns(doh, system).lookup("openahu.org"))
            }
        assertEquals(2, system.lookups.size)
    }

    @Test
    fun bothFailuresRetainDohFailureForDiagnostics() {
        val dohFailure = UnknownHostException("DoH unavailable")
        val systemFailure = UnknownHostException("System DNS unavailable")
        val resolver = CampusCompatibleDns(
            RecordingDns(failure = dohFailure),
            RecordingDns(failure = systemFailure)
        )

        val failure = assertFailsWith<UnknownHostException> { resolver.lookup("openahu.org") }
        assertSame(systemFailure, failure)
        assertSame(dohFailure, failure.suppressed.single())
    }

    @Test
    fun httpDohErrorsFallBackWithoutRepeatingCampusSystemLookup() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            val doh = DnsOverHttps.Builder()
                .client(OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS).build())
                .url(server.url("/dns-query"))
                .includeIPv6(false)
                // Android's public-suffix asset loader is unavailable in this plain JVM test.
                // CampusCompatibleDns's local-name policy is covered above; exercise the real
                // DNS HTTP transport here without invoking the library's Android asset lookup.
                .resolvePrivateAddresses(true)
                .build()
            val system = RecordingDns(listOf(publicAddress))

            assertEquals(listOf(publicAddress), CampusCompatibleDns(doh, system).lookup("ycard.ahu.edu.cn"))
            assertEquals(1, system.lookups.size)
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("application/dns-message", request.getHeader("Accept"))
            assertEquals("/dns-query", request.requestUrl!!.encodedPath)
        }
    }

    private class RecordingDns(
        private val addresses: List<InetAddress> = emptyList(),
        private val failure: UnknownHostException? = null
    ) : Dns {
        val lookups = mutableListOf<String>()

        override fun lookup(hostname: String): List<InetAddress> {
            lookups += hostname
            failure?.let { throw it }
            return addresses
        }
    }
}
