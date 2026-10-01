package com.ahu.ahutong.data.mail

import com.google.gson.JsonParser
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class StudentMailDraftProtocolTest {
    private fun json(value: String) = JsonParser.parseString(value).asJsonObject

    @Test fun restoredDraftSeparatesPermanentMessageAndNewComposeIds() {
        val draft = StudentMailProtocol.parseDraft(json("""
            {"code":"S_OK","var":{"id":"new-compose","subject":"Example","to":["a@example.test"],
            "cc":["b@example.test"],"bcc":["c@example.test"],"content":"<p>Hello &amp; welcome</p>","isHtml":true,
            "inlineResources":false,"attachments":[{"id":2,"name":"sample.txt","size":17,"type":"internal","contentType":"text/plain"}]}}
        """), "persistent-mid", "active-session")
        assertEquals("new-compose", draft.id)
        assertEquals("persistent-mid", draft.draftId)
        assertEquals("active-session", draft.sessionKey)
        assertEquals("Hello & welcome", draft.body)
        assertEquals("<p>Hello &amp; welcome</p>", draft.originalHtml)
        assertEquals("a@example.test", draft.to)
        assertEquals("b@example.test", draft.cc)
        assertEquals("c@example.test", draft.bcc)
        assertEquals("2", draft.attachments.single().id)
        assertFalse(draft.unsupportedResources)
        assertEquals("persistent-mid", StudentMailProtocol.restoreDraftRequest("persistent-mid")["id"].asString)
    }

    @Test fun savingIncludesAttachmentIdsButNeverUsesDraftMidAsComposeId() {
        val draft = MailDraft(id = "editing-compose", draftId = "persisted-mail", subject = "Sample",
            attachments = listOf(MailDraftAttachment("2", "sample.txt", 17)))
        val root = StudentMailProtocol.composeRequest(draft, "sender@example.test", "save")
        assertEquals("save", root["action"].asString)
        assertEquals("editing-compose", root["id"].asString)
        assertFalse(root.has("draftId"))
        val attachment = root.getAsJsonObject("attrs").getAsJsonArray("attachments").single().asJsonObject
        assertEquals(2L, attachment["id"].asLong)
        assertEquals("sample.txt", attachment["name"].asString)
        assertFalse(attachment["inlined"].asBoolean)
        assertFalse(attachment["deleted"].asBoolean)
        assertEquals("server-mid", StudentMailProtocol.parseSavedDraftId(json("""{"code":"S_OK","draftId":"server-mid"}""")))
        assertThrows(IllegalStateException::class.java) {
            StudentMailProtocol.parseSavedDraftId(json("""{"code":"S_OK","var":{"id":"editing-compose"}}"""))
        }
    }

    @Test fun unchangedRestoredHtmlIsPreservedAndEditedBodyIsEscaped() {
        val draft = MailDraft(body = "Hello", originalHtml = "<p><b>Hello</b> &amp; world</p>")
        assertEquals(draft.originalHtml, StudentMailProtocol.composeRequest(draft, "a@example.test", "save")
            .getAsJsonObject("attrs")["content"].asString)
        val edited = draft.copy(body = "<img src=x>\r\n&", originalHtml = null)
        assertEquals("<div>&lt;img src=x&gt;<br>&amp;</div>", StudentMailProtocol.composeRequest(edited, "a@example.test", true)
            .getAsJsonObject("attrs")["content"].asString)
    }

    @Test fun unsupportedDraftResourcesCannotSilentlyDisappearWhenSubmitted() {
        val root = json("""{"code":"S_OK","var":{"id":"editing","content":"<p>Example</p>","isHtml":true,
            "inlineResources":true,"attachments":[]}}""")
        val draft = StudentMailProtocol.parseDraft(root)
        assertTrue(draft.unsupportedResources)
        assertThrows(IllegalStateException::class.java) {
            StudentMailProtocol.composeRequest(draft, "a@example.test", true)
        }
        root.getAsJsonObject("var").addProperty("inlineResources", false)
        root.getAsJsonObject("var").add("cloudattachments", JsonParser.parseString("[{\"id\":\"cloud-resource\"}]"))
        assertTrue(StudentMailProtocol.parseDraft(root).unsupportedResources)
    }

    @Test fun temporaryAttachmentIdsCannotCrossMailboxSessions() {
        val draft = MailDraft(id = "editing", sessionKey = "old-session",
            attachments = listOf(MailDraftAttachment("2", "sample.txt", 17)))
        assertThrows(MailServiceFailure::class.java) { requireReusableDraft(draft, "new-session") }
        assertThrows(MailServiceFailure::class.java) {
            requireReusableDraft(MailDraft(draftId = "stored-mid", sessionKey = "old-session"), "new-session")
        }
        requireReusableDraft(draft, "old-session")
        requireReusableDraft(MailDraft(body = "Local text", sessionKey = "old-session"), "new-session")
    }

    @Test fun attachmentMetadataUsesDecodedSizeAndBindsMessagePart() {
        val detail = StudentMailProtocol.parseDetail(json("""{"code":"S_OK","var":{"attachments":[
            {"id":"2","filename":"sample.bin","estimateSize":17,"contentLength":24}]}}"""), "message-mid")
        assertEquals(17L, detail.attachments.single().size)
        assertEquals("message-mid", detail.attachments.single().messageId)
        assertEquals("2", detail.attachments.single().partId)
        val library = StudentMailProtocol.parseAttachmentList(json("""{"code":"S_OK","var":[
            {"id":"message-mid","partId":"2","attn":"sample.bin","attsize":17}]}"""))
        assertEquals("message-mid", library.single().messageId)
        assertEquals("2", library.single().partId)
    }

    @Test fun inlineImageMetadataRemainsBoundToItsMailPart() {
        val detail = StudentMailProtocol.parseDetail(json("""{"code":"S_OK","var":{"attachments":[
            {"id":"2.1","filename":"sample.png","estimateSize":17,"contentId":"sample-image",
            "contentLocation":"images/sample.png","contentType":"image/png","inlined":true}]}}"""), "message-mid")
        val image = detail.attachments.single()
        assertEquals("message-mid", image.messageId)
        assertEquals("2.1", image.partId)
        assertEquals("sample-image", image.contentId)
        assertEquals("images/sample.png", image.contentLocation)
        assertEquals("image/png", image.contentType)
        assertTrue(image.inlined)
    }

    @Test fun attachmentSyncPreservesNumericIdentifiersAndRejectsMalformedIds() {
        val draft = MailDraft(id = "editing", attachments = listOf(MailDraftAttachment("2", "sample.txt", 17)))
        val request = StudentMailProtocol.syncAttachmentsRequest(draft, "a@example.test")
        assertEquals("continue", request["action"].asString)
        assertEquals(2L, request.getAsJsonObject("attrs").getAsJsonArray("attachments").single().asJsonObject["id"].asLong)
        assertThrows(IllegalArgumentException::class.java) {
            StudentMailProtocol.composeRequest(draft.copy(attachments = listOf(MailDraftAttachment("bad-id", "x", 1))), "a@example.test", true)
        }
    }

    @Test fun removalUsesAnExplicitDeletionDeltaFromOfficialClient() {
        val draft = MailDraft(id = "editing", attachments = listOf(MailDraftAttachment("2", "sample.txt", 17)))
        val root = StudentMailProtocol.removeAttachmentRequest(draft, "a@example.test", "2")
        val delta = root.getAsJsonObject("attrs").getAsJsonArray("attachments").single().asJsonObject
        assertEquals(2L, delta["id"].asLong)
        assertTrue(delta["deleted"].asBoolean)
        assertFalse(delta.has("name"))
        assertFalse(delta.has("inlined"))
        assertThrows(IllegalArgumentException::class.java) {
            StudentMailProtocol.removeAttachmentRequest(draft, "a@example.test", "3")
        }
    }

    @Test fun deletedAttachmentDescriptorsAreExcludedFromRestoredAndSyncedDrafts() {
        val draft = StudentMailProtocol.parseDraft(json("""{"code":"S_OK","var":{"id":"editing",
            "attachments":[{"id":2,"name":"removed.txt","deleted":true,"type":"internal","inlined":true},
            {"id":3,"name":"kept.txt","size":17,"deleted":false,"type":"upload"}]}}"""))
        assertEquals(listOf("3"), draft.attachments.map { it.id })
        assertFalse(draft.unsupportedResources)
        val encoded = StudentMailProtocol.syncAttachmentsRequest(draft, "a@example.test")
            .getAsJsonObject("attrs").getAsJsonArray("attachments")
        assertEquals(3L, encoded.single().asJsonObject["id"].asLong)
    }

    @Test fun savingIsNotAutomaticallyReplayedAfterRetryableServerResponse() = runBlocking {
        val server = MockWebServer().apply { start() }
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false).readTimeout(2, TimeUnit.SECONDS).build()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
            server.enqueue(MockResponse().setBody("""{"code":"S_OK","draftId":"duplicate-mid"}"""))
            val api = StudentMailApi(client, "synthetic-session", server.url("/"))
            assertNotNull(runCatching {
                api.rpc("mbox:compose", StudentMailProtocol.composeRequest(MailDraft(id = "editing"), "a@example.test", "save"),
                    mapOf("l" to "compose", "action" to "save"))
            }.exceptionOrNull())
            assertEquals(1, server.requestCount)
            assertEquals("save", server.takeRequest().requestUrl!!.queryParameter("action"))
        } finally {
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdownNow()
            server.shutdown()
        }
    }

    @Test fun cancellingSaveClosesSocketAfterAcknowledgementHeadersArrive() = runBlocking {
        val server = MockWebServer().apply { start() }
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false).readTimeout(30, TimeUnit.SECONDS).build()
        try {
            server.enqueue(MockResponse().setBody("""{"code":"S_OK","draftId":"stored-mid"}""")
                .setBodyDelay(3, TimeUnit.SECONDS))
            val api = StudentMailApi(client, "synthetic-session", server.url("/"))
            val pending = async {
                api.rpc("mbox:compose", StudentMailProtocol.composeRequest(MailDraft(id = "editing"), "a@example.test", "save"),
                    mapOf("l" to "compose", "action" to "save"))
            }
            withTimeout(2_000) {
                while (server.requestCount == 0) delay(10)
                delay(150)
            }
            withTimeout(1_500) { pending.cancelAndJoin() }
            assertTrue(pending.isCancelled)
            assertEquals(1, server.requestCount)
        } finally {
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdownNow()
            server.shutdown()
        }
    }
}
