package com.ahu.ahutong.data.notice

import com.ahu.ahutong.data.dao.AHUCache
import java.net.URI

internal enum class CollegeListVariant { A, B, D, E, F, G, H }

internal data class CollegeNoticeConfig(
    val key: String,
    val displayName: String,
    val host: String,
    val columnPath: String,
    val variant: CollegeListVariant,
    val aliases: List<String> = emptyList()
) {
    val listUrl: String get() = "https://$host$columnPath"

    val listPathPattern: Regex
        get() = Regex(
            Regex.escape(columnPath.substringBeforeLast('/') + "/") + "list\\d*\\.htm"
        )
}

/**
 * 用户所在院系 → 学院通知栏目。表来自《学院通知聚合-开发报告》第 4 章的人工核对结果，
 * 不做域名推测，也不按拼音自动发现栏目。
 */
internal object CollegeNoticeRegistry {

    const val SOURCE_ID = "college"

    private val configs = listOf(
        CollegeNoticeConfig(
            "fmse", "材料科学与工程学部", "fmse.ahu.edu.cn", "/21716/list.htm",
            CollegeListVariant.A
        ),
        CollegeNoticeConfig("math", "数学科学学院", "math.ahu.edu.cn", "/10805/list.htm", CollegeListVariant.A),
        CollegeNoticeConfig("wlxy", "物理学院", "wlxy.ahu.edu.cn", "/22533/list.htm", CollegeListVariant.A),
        CollegeNoticeConfig("ose", "光电科学与工程学院", "ose.ahu.edu.cn", "/tzgg/list.htm", CollegeListVariant.A),
        CollegeNoticeConfig("chem", "化学化工学院", "chem.ahu.edu.cn", "/10735/list.htm", CollegeListVariant.A),
        CollegeNoticeConfig("mse", "材料科学与工程学院", "mse.ahu.edu.cn", "/19459/list.htm", CollegeListVariant.A),
        CollegeNoticeConfig("cs", "计算机科学与技术学院", "cs.ahu.edu.cn", "/11157/list.htm", CollegeListVariant.A),
        CollegeNoticeConfig("si", "互联网学院", "si.ahu.edu.cn", "/tzgg/list.htm", CollegeListVariant.A),
        CollegeNoticeConfig("dy", "电子信息工程学院", "dy.ahu.edu.cn", "/11062/list.htm", CollegeListVariant.A),
        CollegeNoticeConfig("dqxy", "电气工程与自动化学院", "dqxy.ahu.edu.cn", "/tzgg/list.htm", CollegeListVariant.B),
        CollegeNoticeConfig("ai", "人工智能学院", "ai.ahu.edu.cn", "/19134/list.htm", CollegeListVariant.A),
        CollegeNoticeConfig("ei", "机器人学院", "ei.ahu.edu.cn", "/tzgg/list.htm", CollegeListVariant.E),
        CollegeNoticeConfig("ic", "集成电路学院", "ic.ahu.edu.cn", "/19168/list.htm", CollegeListVariant.A),
        CollegeNoticeConfig("ds", "大数据与统计学院", "ds.ahu.edu.cn", "/20484/list.htm", CollegeListVariant.A),
        CollegeNoticeConfig(
            "life", "生命科学与医学工程学院", "life.ahu.edu.cn", "/10958/list.htm",
            CollegeListVariant.A, aliases = listOf("生命科学学院")
        ),
        CollegeNoticeConfig("zhxy", "资源与环境工程学院", "zhxy.ahu.edu.cn", "/11422/list.htm", CollegeListVariant.A),
        CollegeNoticeConfig("mkszyxy", "马克思主义学院", "mkszyxy.ahu.edu.cn", "/11268/list.htm", CollegeListVariant.A),
        CollegeNoticeConfig("wxy", "文学院", "wxy.ahu.edu.cn", "/notice/list.htm", CollegeListVariant.A),
        CollegeNoticeConfig(
            "hx", "历史学院", "hx.ahu.edu.cn", "/11126/list.htm",
            CollegeListVariant.D, aliases = listOf("历史系")
        ),
        CollegeNoticeConfig("zxxnew", "哲学学院", "zxxnew.ahu.edu.cn", "/15519/list.htm", CollegeListVariant.A),
        CollegeNoticeConfig("sjc", "新闻传播学院", "sjc.ahu.edu.cn", "/14632/list.htm", CollegeListVariant.E),
        CollegeNoticeConfig("jjxy", "经济学院", "jjxy.ahu.edu.cn", "/9830/list.htm", CollegeListVariant.F),
        CollegeNoticeConfig("sba", "商学院", "sba.ahu.edu.cn", "/13647/list.htm", CollegeListVariant.A),
        CollegeNoticeConfig(
            "sfs", "外语学院", "sfs.ahu.edu.cn", "/notice/list.htm",
            CollegeListVariant.A, aliases = listOf("外国语学院")
        ),
        CollegeNoticeConfig("law", "法学院", "law.ahu.edu.cn", "/4800/list.htm", CollegeListVariant.B),
        CollegeNoticeConfig("man", "管理学院", "man.ahu.edu.cn", "/13740/list.htm", CollegeListVariant.A),
        CollegeNoticeConfig("szxy", "社会与政治学院", "szxy.ahu.edu.cn", "/13679/list.htm", CollegeListVariant.A),
        CollegeNoticeConfig("ysxy", "艺术学院", "ysxy.ahu.edu.cn", "/13760/list.htm", CollegeListVariant.A),
        CollegeNoticeConfig("wendian", "文典学院", "wendian.ahu.edu.cn", "/xwgg/list.htm", CollegeListVariant.B),
        CollegeNoticeConfig(
            "sbiahu", "纽约石溪学院", "sbiahu.ahu.edu.cn", "/22114/list.htm",
            CollegeListVariant.A, aliases = listOf("安徽大学纽约石溪学院")
        ),
        CollegeNoticeConfig("eisf", "中法工程师学院", "eisf.ahu.edu.cn", "/22625/list.htm", CollegeListVariant.A),
        CollegeNoticeConfig(
            "tyjs", "体育军事教学部", "tyjs.ahu.edu.cn", "/14500/list.htm",
            CollegeListVariant.G, aliases = listOf("体育部")
        ),
        CollegeNoticeConfig("dszx", "大学数学教学中心", "dszx.ahu.edu.cn", "/tzgg/list.htm", CollegeListVariant.A),
        CollegeNoticeConfig("dw", "大学外语教学中心", "dw.ahu.edu.cn", "/tzgg/list.htm", CollegeListVariant.A),
        CollegeNoticeConfig("sie", "国际教育学院", "sie.ahu.edu.cn", "/11473/list.htm", CollegeListVariant.H),
        CollegeNoticeConfig("jxjy", "继续教育学院", "jxjy.ahu.edu.cn", "/10609/list.htm", CollegeListVariant.A)
    )

    private val byNormalizedName: Map<String, CollegeNoticeConfig> = buildMap {
        configs.forEach { config ->
            (listOf(config.displayName) + config.aliases).forEach { name ->
                put(normalize(name), config)
            }
        }
    }

    val hosts: Set<String> = configs.mapTo(mutableSetOf()) { it.host }

    fun resolveCurrent(): CollegeNoticeConfig? {
        val fromStudentInfo = AHUCache.getStudentInfo()?.department
        val profiles = AHUCache.getGradeStudentProfiles()
        val fromGradeProfile = (profiles.firstOrNull { it.trainingType == "主修" } ?: profiles.firstOrNull())
            ?.department
        return resolve(fromStudentInfo) ?: resolve(fromGradeProfile)
    }

    fun resolve(department: String?): CollegeNoticeConfig? {
        val normalized = normalize(department ?: return null)
        if (normalized.isEmpty()) return null
        return byNormalizedName[normalized]
    }

    fun configForListUrl(rawUrl: String?): CollegeNoticeConfig? = runCatching {
        val uri = URI(rawUrl)
        if (uri.scheme != "https" || uri.userInfo != null) return@runCatching null
        configs.firstOrNull { config ->
            config.host == uri.host && config.listPathPattern.matches(uri.path)
        }
    }.getOrNull()

    fun isTrustedArticleUrl(rawUrl: String?): Boolean = runCatching {
        val uri = URI(rawUrl)
        val host = uri.host ?: return@runCatching false
        uri.scheme in setOf("https", "http") &&
            (host in hosts || host.endsWith(".ahu.edu.cn") || host == "mp.weixin.qq.com")
    }.getOrDefault(false)

    fun currentDisplayName(): String = resolveCurrent()?.displayName ?: "学院"

    private fun normalize(value: String): String = value
        .replace(Regex("""\s+"""), "")
        .replace("安徽大学", "")
        .replace(Regex("""[（(][^）)]*[）)]"""), "")
        .trim()
}
