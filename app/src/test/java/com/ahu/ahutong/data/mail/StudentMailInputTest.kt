package com.ahu.ahutong.data.mail

import com.ahu.ahutong.ui.screen.main.StudentMailWebActivity
import com.ahu.ahutong.ui.screen.main.mailDisplayName
import com.ahu.ahutong.ui.screen.main.mailDisplayDate
import com.ahu.ahutong.ui.screen.main.mailDateGroup
import java.time.LocalDate
import com.ahu.ahutong.ui.state.isMailAddress
import com.ahu.ahutong.ui.state.mailAddressOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudentMailInputTest {
    @Test fun senderLabelsKeepNamesAndDoNotRepeatFullAddresses() {
        assertEquals("学生服务", mailDisplayName("\"学生服务\" <service@example.test>"))
        assertEquals("service", mailDisplayName("service@example.test"))
        assertEquals("学生服务", mailDisplayName("学生服务"))
    }

    @Test fun dateLabelsHandleYearBoundariesAndUnexpectedServerValues() {
        val today = LocalDate.of(2026, 1, 1)
        assertEquals("09:05", mailDisplayDate("2026-01-01 09:05:22", today))
        assertEquals("昨天", mailDateGroup("2025-12-31 23:59:00", today))
        assertEquals("2025/12/30", mailDisplayDate("2025-12-30 12:00:00", today))
        assertEquals("邮件", mailDateGroup("unexpected", today))
    }

    @Test fun repliesUseEmailFromServerDisplayAddress() {
        assertEquals("sender@example.test", mailAddressOf("\"Sender Name\" <sender@example.test>"))
        assertTrue(isMailAddress("Sender Name <sender@example.test>"))
        assertFalse(isMailAddress("sender@example.test\r\nBcc: injected@example.test"))
        assertFalse(isMailAddress("sender@example.test <invalid>"))
    }

    @Test fun webAttachmentRequestsCannotLeaveTrustedMailboxOrigin() {
        assertTrue(StudentMailWebActivity.trustedMailUrl("https://mail.stu.ahu.edu.cn/js6/read/attachment"))
        listOf("http://mail.stu.ahu.edu.cn/", "https://mail.stu.ahu.edu.cn.attacker.test/",
            "https://user@mail.stu.ahu.edu.cn/", "https://mail.stu.ahu.edu.cn:8443/", "file:///data/private")
            .forEach { assertFalse(StudentMailWebActivity.trustedMailUrl(it)) }
    }
}
