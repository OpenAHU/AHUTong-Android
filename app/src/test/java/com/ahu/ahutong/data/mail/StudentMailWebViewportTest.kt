package com.ahu.ahutong.data.mail

import com.ahu.ahutong.ui.screen.main.StudentMailWebViewport
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class StudentMailWebViewportTest {
    @Test fun phoneViewportFitsTheDesktopMinimumAndPermitsZoom() {
        val content = StudentMailWebViewport.viewportContent(400f)
        assertTrue(content.contains("width=1280"))
        assertTrue(content.contains("initial-scale=0.312500"))
        assertTrue(content.contains("user-scalable=yes"))
        assertTrue(content.contains("maximum-scale=5"))
    }

    @Test fun widerDisplaysKeepTheirFullLayoutWidth() {
        assertTrue(StudentMailWebViewport.viewportContent(1600f).startsWith("width=1600,initial-scale=1.000000"))
    }

    @Test fun scriptNumbersAreLocaleIndependentAndContainOnlyViewportWork() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val script = StudentMailWebViewport.script(400f)
            assertTrue(script.contains("initial-scale=0.312500"))
            assertTrue(script.contains("window.top!==window"))
            assertTrue(script.contains("location.hostname!=='mail.stu.ahu.edu.cn'"))
            assertFalse(script.contains("cookie", true))
            assertFalse(script.contains("localStorage"))
            assertFalse(script.contains("iframe"))
        } finally { Locale.setDefault(previous) }
    }

    @Test fun invalidViewSizesCannotGenerateViewportScripts() {
        for (width in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { StudentMailWebViewport.script(width) }
        }
    }
}
