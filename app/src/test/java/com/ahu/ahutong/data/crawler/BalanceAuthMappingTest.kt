package com.ahu.ahutong.data.crawler

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.data.crawler.api.adwmh.createAdwmhApi
import com.ahu.ahutong.data.crawler.api.jwxt.createJwxtApi
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Balance business codes must not all look like an expired login. */
class BalanceAuthMappingTest {

    private fun source(server: MockWebServer): CrawlerDataSource {
        val client = OkHttpClient()
        val base = server.url("/").toString()
        return CrawlerDataSource(
            jwxt = createJwxtApi(client, base),
            adwmh = createAdwmhApi(client, base)
        )
    }

    private fun balance(code: Int, msg: String): String =
        """{"code":$code,"msg":"$msg","object":null}"""

    @Test
    fun `only business code 401 becomes unauthorized`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(balance(401, "expired")))
            val result = source(server).getCardMoney()
            assertTrue(result.errorOrNull() is AhuError.Unauthorized)
        }
    }

    @Test
    fun `other business failures stay server errors`() = runBlocking {
        for (code in listOf(500, 503, 10001)) {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setBody(balance(code, "busy")))
                val result = source(server).getCardMoney()
                val error = result.errorOrNull()
                assertTrue(error is AhuError.Server, "code=$code -> $error")
                assertEquals(code, error.code)
            }
        }
    }
}
