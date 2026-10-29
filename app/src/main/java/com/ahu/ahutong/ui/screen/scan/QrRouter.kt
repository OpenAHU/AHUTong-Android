package com.ahu.ahutong.ui.screen.scan

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLDecoder
import java.net.URI
import kotlinx.coroutines.CancellationException

/**
 * 聚合扫码的**路由**：只判定平台 + 用显式 Intent 免选择框直启目标 App，把二维码**原文**递进去。
 * 签到/开锁等动作 100% 由目标 App 自己完成——这里不碰协议、不伪造 UA、不存凭据、不打日志。
 *
 * 依据：《聚合扫码 · 三平台实现手册》（2026-10-13 真机实测）。
 *
 * 分类用**纯字符串解析**而不是 `android.net.Uri`：JVM 单测里 `Uri` 是 stub（会抛），
 * 用 Uri 就等于这段关键逻辑只能靠真机试；纯解析则可在 `QrRouterTest` 里逐条钉住。
 * （构造 Intent 仍用 `Uri.Builder`——那部分本来只在真机上跑。）
 */
object QrRouter {

    const val MEITUAN_PKG = "com.sankuai.meituan"
    const val CHAOXING_PKG = "com.chaoxing.mobile"
    const val WEIXIN_PKG = "com.tencent.mm"

    // 微信注册给 Launcher 的「扫一扫」动态快捷方式（冷/热启动都能拉起微信扫一扫）
    private const val WEIXIN_SHORTCUT_ACTION = "com.tencent.mm.ui.ShortCutDispatchAction"
    private const val WEIXIN_SHORTCUT_ACTIVITY = "com.tencent.mm.ui.LauncherUI"
    private const val WEIXIN_SHORTCUT_EXTRA = "LauncherUI.Shortcut.LaunchType"
    private const val WEIXIN_SCAN_TYPE = "launch_type_scan_qrcode"

    /**
     * 美团单车「纯车牌」分支。手册里的宽松式 `^[0-9A-Z]{10}$` 会命中任意 10 位券码/序列号，
     * 而我们现在是**认到就直启**，误判代价太大——按手册的建议加前缀白名单收紧。
     */
    private val MOBIKE_PLATE = Regex("^(866|867|868|870)\\d{7}$")

    /** 正式形态里 `b` 参数的值：10 位车牌 + `_1`。 */
    private val MOBIKE_BIKE_PARAM = Regex("^[0-9A-Z]{10}_1$")

    private val HTTP_URL =
        Regex("^(https?)://([^/?#]+)([^?#]*)(?:\\?([^#]*))?(?:#.*)?$", RegexOption.IGNORE_CASE)

    sealed interface Kind {
        data object MeituanBike : Kind
        data object ChaoxingSign : Kind
        data object YuketangSign : Kind
        data object Unknown : Kind
    }

    /** 路由结果。展示文案留给 UI 层。 */
    sealed interface Outcome {
        /** 已把原文交给对方；[note] 是需要补一句的话（可空）。 */
        data class Handed(val targetApp: String, val note: String? = null) : Outcome

        /** 认得这个平台，但目标 App 没装 / 没响应。 */
        data class TargetMissing(val targetApp: String) : Outcome

        /** 目标存在但无法打开；不暴露外部异常及二维码内容。 */
        data class Failed(val targetApp: String) : Outcome

        /** 不是这三种码，不做跳转。 */
        data object Unrecognized : Outcome
    }

    fun classify(raw: String): Kind {
        val text = raw.trim()
        if (text.isEmpty() || text.length > 8192 || text.any { it.isISOControl() }) return Kind.Unknown
        if (MOBIKE_PLATE.matches(text)) return Kind.MeituanBike
        val uri = try { URI(text) } catch (_: Exception) { return Kind.Unknown }
        if (!uri.scheme.orEmpty().equals("http", true) && !uri.scheme.orEmpty().equals("https", true)) return Kind.Unknown
        if (uri.rawUserInfo != null || uri.port !in listOf(-1, 80, 443)) return Kind.Unknown
        val host = uri.host?.lowercase() ?: return Kind.Unknown
        val path = uri.rawPath.orEmpty()
        return when {
            host == "mobilelearn.chaoxing.com" && path.startsWith("/widget/sign/") &&
                path.length > "/widget/sign/".length -> Kind.ChaoxingSign
            host == "yuketang.cn" || host.endsWith(".yuketang.cn") -> Kind.YuketangSign
            (host == "t8.pub" || host == "t9.pub") && path.length > 1 -> Kind.MeituanBike
            isMobikeUrl(text) -> Kind.MeituanBike
            else -> Kind.Unknown
        }
    }

    fun route(context: Context, raw: String): Outcome = dispatch(raw) { kind, text ->
        when (kind) {
            Kind.MeituanBike -> openMeituanBike(context, text)
            Kind.ChaoxingSign -> openChaoxingSign(context, text)
            Kind.YuketangSign -> openWechatScanner(context)
            Kind.Unknown -> Outcome.Unrecognized
        }
    }

    /** The unsupported branch never reaches platform URI construction or the supplied launcher. */
    internal fun dispatch(raw: String, launch: (Kind, String) -> Outcome): Outcome {
        val kind = classify(raw)
        if (kind == Kind.Unknown) return Outcome.Unrecognized
        return try {
            launch(kind, raw.trim())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Outcome.Failed(targetName(kind))
        }
    }

    internal fun targetName(kind: Kind): String = when (kind) {
        Kind.MeituanBike -> "美团"
        Kind.ChaoxingSign -> "学习通"
        Kind.YuketangSign -> "微信"
        Kind.Unknown -> "应用"
    }

    /**
     * 校验美团那张码的正式形态：`http(s)://www.mobike.com/download/app.html?b=<10位>_1`。
     * 校验通过后**整条原文**递给美团（后续分类、预检、开锁面板全走美团自己的逻辑）。
     */
    private fun isMobikeUrl(text: String): Boolean {
        val match = HTTP_URL.find(text) ?: return false
        val scheme = match.groupValues[1].lowercase()
        if (scheme != "http" && scheme != "https") return false
        val host = match.groupValues[2]
            .substringAfterLast('@')
            .substringBefore(':')
            .lowercase()
        if (host != "www.mobike.com") return false
        if (match.groupValues[3] != "/download/app.html") return false
        val raw = match.groupValues[4]
            .split('&')
            .firstOrNull { it.substringBefore('=') == "b" }
            ?.substringAfter('=', "")
            .orEmpty()
        val bike = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        return MOBIKE_BIKE_PARAM.matches(bike)
    }

    private fun openMeituanBike(context: Context, raw: String): Outcome {
        val uri = Uri.Builder()
            .scheme("imeituan")
            .authority("www.meituan.com")
            .path("/bike/scan")
            // 接收端有 60 秒时效窗口：time 必须是构造 Intent 当下的毫秒时间戳
            .appendQueryParameter("time", System.currentTimeMillis().toString())
            .appendQueryParameter("url", raw)
            .build()
        return launchOutcome(tryLaunch(context, uri, MEITUAN_PKG), "美团")
    }

    private fun openChaoxingSign(context: Context, raw: String): Outcome {
        // sharebacktype=1：在学习通内置 WebView 打开该 H5（共享登录态）→ 落在签到确认页
        val shareback = Uri.Builder()
            .scheme("chaoxingshareback")
            .authority("xuexitong.com")
            .path("/")
            .appendQueryParameter("sharebacktype", "1")
            .appendQueryParameter("title", "签到")
            // 与美团相反：这里**整条透传**，id/c/enc/DB_STRATEGY/STRATEGY_PARA 一个都不能少
            .appendQueryParameter("url", raw)
            .build()
        val primary = tryLaunch(context, shareback, CHAOXING_PKG)
        if (primary != LaunchAttempt.Missing) return launchOutcome(primary, "学习通")
        // A missing shareback handler can still expose the documented sign-page App Link.
        return launchOutcome(tryLaunch(context, Uri.parse(raw), CHAOXING_PKG), "学习通")
    }

    /**
     * 雨课堂没有可注入的入口（App 未声明 http 过滤器；微信那条规则发生在「微信自己扫码」那一刻）。
     * 退一步：拉起微信扫一扫，用户对着码再扫一次。
     */
    private fun openWechatScanner(context: Context): Outcome {
        val intent = Intent(WEIXIN_SHORTCUT_ACTION).apply {
            component = ComponentName(WEIXIN_PKG, WEIXIN_SHORTCUT_ACTIVITY)
            putExtra(WEIXIN_SHORTCUT_EXTRA, WEIXIN_SCAN_TYPE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            Outcome.Handed(
                targetApp = "微信扫一扫",
                note = "请在微信中重新扫码"
            )
        } catch (_: ActivityNotFoundException) {
            Outcome.TargetMissing("微信")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Outcome.Failed("微信")
        }
    }

    private enum class LaunchAttempt { Started, Missing, Failed }

    private fun launchOutcome(attempt: LaunchAttempt, target: String): Outcome = when (attempt) {
        LaunchAttempt.Started -> Outcome.Handed(target)
        LaunchAttempt.Missing -> Outcome.TargetMissing(target)
        LaunchAttempt.Failed -> Outcome.Failed(target)
    }

    private fun tryLaunch(context: Context, uri: Uri, pkg: String): LaunchAttempt = try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        LaunchAttempt.Started
    } catch (_: ActivityNotFoundException) {
        LaunchAttempt.Missing
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        LaunchAttempt.Failed
    }
}
