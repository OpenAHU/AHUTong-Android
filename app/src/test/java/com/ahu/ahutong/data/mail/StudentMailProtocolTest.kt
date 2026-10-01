package com.ahu.ahutong.data.mail

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class StudentMailProtocolTest {
    private fun json(text: String) = JsonParser.parseString(text).asJsonObject

    @Test fun accountInfoUsesZeroSuccessCodeWhileContactsUse200() {
        assertEquals("reader@example.test", StudentMailProtocol.parseAccount(json("""
            {"code":0,"success":true,"data":{"defaultSender":{"email":"reader@example.test"}}}
        """)))
        assertThrows(IllegalStateException::class.java) {
            StudentMailProtocol.checkResponse(json("""{"code":0,"success":false}"""))
        }
    }

    @Test fun readsAlreadyDecodedHtmlAndHandlesOptionalFields() {
        val detail = StudentMailProtocol.parseDetail(json("""
            {"code":"S_OK","var":{"subject":"Example","from":["sender@example.test"],
            "to":["reader@example.test"],"html":{"encoding":"base64","content":"<p>Hello &amp; welcome</p>"},
            "text":{"content":""},"attachments":[{"id":"2","filename":"file.txt","estimateSize":20}]}}
        """), "message-id")
        assertEquals("Hello & welcome", detail.text)
        assertEquals("<p>Hello &amp; welcome</p>", detail.html)
        assertEquals(20L, detail.attachments.single().size)
        assertNull(detail.attachments.single().downloadPath)
        assertTrue(detail.cc.isEmpty())
    }

    @Test fun handlesMailboxStringRecipientsAndMissingReadFlag() {
        val page = StudentMailProtocol.parsePage(json("""
            {"code":"S_OK","var":[{"id":"m1","fid":1,"to":"reader@example.test","flags":{},
            "sentDate":"2026-01-01 00:00:00"}],"total":31}
        """))
        assertEquals(31, page.total)
        assertEquals(listOf("reader@example.test"), page.messages.single().to)
        assertFalse(page.messages.single().read)
        assertEquals("2026-01-01 00:00:00", page.messages.single().date)
    }

    @Test fun createsComposeSessionWithoutDeliveringAndEscapesTypedMarkup() {
        val draft = MailDraft(to = "a@example.test；b@example.test\na@example.test", body = "<script>x</script>\n&", id = "compose-id")
        val request = StudentMailProtocol.composeRequest(draft, "sender@example.test", false)
        assertEquals("continue", request["action"].asString)
        assertEquals("compose-id", request["id"].asString)
        assertEquals(2, request.getAsJsonObject("attrs").getAsJsonArray("to").size())
        assertEquals("<div>&lt;script&gt;x&lt;/script&gt;<br>&amp;</div>", request.getAsJsonObject("attrs")["content"].asString)
        assertFalse(request["riskHitIntercept"].asBoolean)
        assertEquals("deliver", StudentMailProtocol.composeRequest(draft, "sender@example.test", true)["action"].asString)
    }

    @Test fun virtualFolderFiltersFollowObservedWireContract() {
        val flagged = StudentMailProtocol.listRequest(-1)
        assertEquals(1, flagged.getAsJsonObject("filter")["label0"].asInt)
        assertFalse(flagged.has("fid"))
        val later = StudentMailProtocol.listRequest(-3)
        assertEquals(":22000101", later.getAsJsonObject("filter")["defer"].asString)
        assertEquals("deferredDate", later["order"].asString)
        assertFalse(later["desc"].asBoolean)
        val unread = StudentMailProtocol.listRequest(1, 30, true)
        assertEquals(30, unread["start"].asInt)
        assertFalse(unread.getAsJsonObject("filter").getAsJsonObject("flags")["read"].asBoolean)
    }

    @Test fun extractsRefreshWithoutExecutingScripts() {
        assertEquals("http://mail.stu.ahu.edu.cn/redirect?sid=fixture&l=fixture", StudentMailProtocol.extractRedirect(
            """<META HTTP-EQUIV=REFRESH CONTENT="0;URL=http://mail.stu.ahu.edu.cn/redirect?sid=fixture&amp;l=fixture">""",
            "https://entryhz.qiye.163.com/entry/door"))
        assertEquals("https://mail.stu.ahu.edu.cn/redirect?sid=fixture", StudentMailProtocol.extractRedirect(
            """<script>window.location.replace("/redirect?sid=fixture");</script>""",
            "https://mail.stu.ahu.edu.cn/entry"))
        assertNull(StudentMailProtocol.extractRedirect("<script>location.replace(generateUrl())</script>", "https://mail.stu.ahu.edu.cn/"))
    }

    @Test fun serverErrorsNeverEchoPrivateResponseFields() {
        val error = runCatching { StudentMailProtocol.checkResponse(json("""
            {"code":"S_SESSION_TIMEOUT","message":"private-secret-token","var":"private-body"}
        """)) }.exceptionOrNull()
        assertNotNull(error)
        assertFalse(error!!.message.orEmpty().contains("private"))
    }

    @Test fun mapsPersonalAndRecentContactsFromDifferentEnvelopes() {
        val personal = StudentMailProtocol.parseContacts(json("""
            {"success":true,"code":200,"data":{"statusCode":0,"personContactVOList":[
            {"cid":"fixture","qiyeAccountName":"Example","email":["one@example.test","two@example.test"]}]}}
        """))
        assertEquals(2, personal.size)
        assertEquals("Example", personal.first().name)
        val recent = StudentMailProtocol.parseRecentContacts(json("""
            {"success":true,"code":200,"data":{"contactList":[{"email":"one@example.test","name":"Example","accountId":null}]}}
        """))
        assertEquals("one@example.test", recent.single().id)
    }
}
