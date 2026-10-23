package com.ahu.ahutong.error

import com.ahu.ahutong.data.attendance.AttendanceAuthException
import com.ahu.ahutong.data.attendance.AttendanceException
import com.google.gson.JsonSyntaxException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals

class FailurePresentationTest {
    @Test
    fun `network timeout and explicit authentication or business failures remain ordinary notices`() {
        listOf(IOException(), SocketTimeoutException(), UnknownHostException(),
            AttendanceAuthException("请重新登录"), AttendanceException("不能申诉"))
            .forEach { assertEquals(FailurePresentation.Expected, failurePresentation(it)) }
    }

    @Test
    fun `programmer and response parsing failures retain a diagnostic dialog`() {
        listOf(NullPointerException("missing field"), IllegalStateException("bad state"),
            JsonSyntaxException("changed response"))
            .forEach { assertEquals(FailurePresentation.Unexpected, failurePresentation(it)) }
    }

    @Test
    fun `cancellation is silent even when wrapped`() {
        assertEquals(FailurePresentation.Ignore, failurePresentation(CancellationException()))
        assertEquals(FailurePresentation.Ignore,
            failurePresentation(IllegalStateException("cancelled request", CancellationException())))
    }

    @Test
    fun `transport causes remain ordinary when wrapped`() {
        assertEquals(FailurePresentation.Expected,
            failurePresentation(IllegalStateException("request failed", IOException("offline"))))
    }

    @Test
    fun `an io cause does not hide a null pointer bug`() {
        val failure = NullPointerException("unexpected absent response").apply { initCause(IOException("offline")) }
        assertEquals(FailurePresentation.Unexpected, failurePresentation(failure))
    }

    @Test
    fun `an io wrapper does not hide a response parsing failure`() {
        val failure = IOException("response failed", JsonSyntaxException("changed response"))
        assertEquals(FailurePresentation.Unexpected, failurePresentation(failure))
    }
}
