package com.ahu.ahutong.ui.screen.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException

/** 分类规则按《聚合扫码 · 三平台实现手册》的真机样本钉住。 */
class QrRouterTest {
    @Test
    fun unsupportedAndMalformedQrNeverReachThePlatformLauncher() {
        var launches = 0
        listOf("hello", "javascript://yuketang.cn/test", "https://evil.example@yuketang.cn/x",
            "https://mobilelearn.chaoxing.com.evil.example/widget/sign/e", "https://yuketang.cn/%XX",
            "intent://mobilelearn.chaoxing.com/widget/sign/e", "https://yuketang.cn:8080/x").forEach { raw ->
            assertEquals(QrRouter.Outcome.Unrecognized, QrRouter.dispatch(raw) { _, _ ->
                launches++
                error("The launcher must not see unsupported input")
            })
        }
        assertEquals(0, launches)
    }

    @Test
    fun launcherExceptionsProduceRecoverableFailureAndCancellationStillPropagates() {
        val raw = "https://mobilelearn.chaoxing.com/widget/sign/e?id=1"
        assertEquals(QrRouter.Outcome.Failed("学习通"), QrRouter.dispatch(raw) { _, _ ->
            throw SecurityException("Not exported")
        })
        assertFailsWith<CancellationException> {
            QrRouter.dispatch(raw) { _, _ -> throw CancellationException() }
        }
        assertIs<QrRouter.Outcome.Handed>(QrRouter.dispatch(raw) { _, text ->
            assertEquals(raw, text)
            QrRouter.Outcome.Handed("学习通")
        })
    }

    @Test
    fun chaoxingSignByPrefix() {
        assertEquals(
            QrRouter.Kind.ChaoxingSign,
            QrRouter.classify(
                "https://mobilelearn.chaoxing.com/widget/sign/e?id=1&c=2&enc=ab" +
                    "&DB_STRATEGY=PRIMARY_KEY&STRATEGY_PARA=id"
            )
        )
        // 路径段随签到类型变，前缀匹配要能吃到 pcqrcode
        assertEquals(
            QrRouter.Kind.ChaoxingSign,
            QrRouter.classify("http://mobilelearn.chaoxing.com/widget/sign/pcqrcode?x=1")
        )
    }

    @Test
    fun yuketangBySchoolSubdomain() {
        assertEquals(
            QrRouter.Kind.YuketangSign,
            QrRouter.classify(
                "https://changjiang.yuketang.cn/api/v3/lesson/check-in/dynamic-qr-code?c=1&t=2&s=3&v=2"
            )
        )
        assertEquals(QrRouter.Kind.YuketangSign, QrRouter.classify("https://yuketang.cn/x"))
    }

    @Test
    fun mobikeByUrlShortLinkAndPlate() {
        assertEquals(
            QrRouter.Kind.MeituanBike,
            QrRouter.classify("https://www.mobike.com/download/app.html?b=8661234567_1")
        )
        assertEquals(QrRouter.Kind.MeituanBike, QrRouter.classify("http://t8.pub/abcd"))
        assertEquals(QrRouter.Kind.MeituanBike, QrRouter.classify("http://t9.pub/abcd"))
        assertEquals(QrRouter.Kind.MeituanBike, QrRouter.classify("8701234567"))
    }

    @Test
    fun rejectsTenCharCouponInsteadOfTreatingAsPlate() {
        // 手册里的宽松式 ^[0-9A-Z]{10}$ 会命中它；收紧成前缀白名单后必须不认
        assertEquals(QrRouter.Kind.Unknown, QrRouter.classify("AB12CD34EF"))
        assertEquals(QrRouter.Kind.Unknown, QrRouter.classify("8611234567"))
    }

    @Test
    fun rejectsMobikeHostWithMalformedBikeParam() {
        assertEquals(
            QrRouter.Kind.Unknown,
            QrRouter.classify("https://www.mobike.com/download/app.html?b=oops")
        )
        assertEquals(
            QrRouter.Kind.Unknown,
            QrRouter.classify("https://www.mobike.com/other/path?b=8661234567_1")
        )
    }

    @Test
    fun rejectsUnrelatedText() {
        assertEquals(QrRouter.Kind.Unknown, QrRouter.classify("https://example.com/x"))
        assertEquals(QrRouter.Kind.Unknown, QrRouter.classify(""))
    }
}
