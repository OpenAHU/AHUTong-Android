package com.ahu.ahutong.assistant.safety.content

internal data class ContentRule(
    val id: String,
    val targets: Set<String>,
    val actions: Set<String>,
    val maxGap: Int = 24,
)

/**
 * 规则只做组合共现，不做话题级词表：
 * 违法类看「对象 + 方法/渠道」，政治类看「对象 + 高危动作/形态」。
 * 规则刻意保持少量、高精度，细节不写进文档。
 */
internal data class ContentRulePack(
    val illegal: List<ContentRule>,
    val political: List<ContentRule>,
    val politicalPhrases: List<Regex>,
    val requestVerbs: Set<String>,
    val jailbreak: List<Regex>,
    val encoding: List<Regex>,
    val promptExtraction: List<Regex>,
    val promptExtractionHard: List<Regex>,
    val selfHarm: List<Regex>,
    val selfHarmOutput: List<Regex>,
    val exemptions: Set<String>,
) {

    companion object {

        fun default(): ContentRulePack = ContentRulePack(
            illegal = listOf(
                ContentRule(
                    id = "explosive",
                    targets = setOf("炸弹", "爆炸物", "炸药", "雷管", "火药", "爆炸装置"),
                    actions = setOf("制作", "制造", "合成", "配方", "教程", "步骤", "怎么做", "如何做", "哪里买", "购买", "获取"),
                ),
                ContentRule(
                    id = "narcotics",
                    targets = setOf("冰毒", "海洛因", "摇头丸", "大麻", "毒品", "制毒"),
                    actions = setOf("制作", "制造", "合成", "提炼", "配方", "购买", "渠道", "哪里买", "获取"),
                ),
                ContentRule(
                    id = "firearm",
                    targets = setOf("枪支", "手枪", "步枪", "弹药", "枪械"),
                    actions = setOf("制造", "改装", "购买", "渠道", "图纸", "获取"),
                ),
                ContentRule(
                    id = "fraud",
                    targets = setOf("诈骗", "洗钱", "赌博", "赌球", "博彩"),
                    actions = setOf("教程", "方法", "平台", "怎么做", "带你", "操作", "话术"),
                ),
                ContentRule(
                    id = "forgery",
                    targets = setOf("假证", "假身份证", "伪造身份证", "代考", "代写", "论文代写", "包过"),
                    actions = setOf("办理", "制作", "代做", "多少钱", "渠道", "包过", "接单"),
                ),
                ContentRule(
                    id = "intrusion",
                    targets = setOf("木马", "钓鱼网站", "撞库", "盗号", "黑产"),
                    actions = setOf("教程", "工具", "搭建", "制作", "购买", "获取"),
                ),
                ContentRule(
                    id = "circumvention",
                    targets = setOf("翻墙", "梯子", "科学上网", "vpn"),
                    actions = setOf("教程", "搭建", "购买", "推荐", "节点", "工具"),
                ),
                ContentRule(
                    id = "doxxing",
                    targets = setOf("开盒", "人肉", "户籍", "身份证号"),
                    actions = setOf("查", "查询", "找人", "获取", "买到", "查到"),
                ),
            ),
            political = listOf(
                ContentRule(
                    id = "subversion",
                    targets = setOf("政权", "国家政权", "体制", "共产党", "社会主义制度", "国家领导人", "领导人"),
                    actions = setOf("推翻", "颠覆", "煽动", "分裂", "独立", "恐怖"),
                    maxGap = 18,
                ),
                ContentRule(
                    id = "insult",
                    targets = setOf("国家领导人", "领导人", "共产党", "社会主义制度"),
                    actions = setOf("侮辱", "辱骂", "丑化", "抹黑", "攻击"),
                    maxGap = 18,
                ),
                ContentRule(
                    id = "rumor",
                    targets = setOf("政府", "官方", "领导人"),
                    actions = setOf("造谣", "编造谣言", "散布谣言", "抹黑"),
                    maxGap = 18,
                ),
            ),
            politicalPhrases = listOf(
                Regex("(号召|组织|煽动|鼓动|串联).{0,12}(游行|抗议|示威|罢课|罢工)"),
                Regex("(推翻|颠覆).{0,6}(政府|政权|体制)"),
                Regex("(分裂|独立).{0,4}(国家|领土|民族)"),
                Regex("(侮辱|辱骂|丑化|抹黑).{0,6}(领导人|共产党|社会主义)"),
            ),
            requestVerbs = setOf(
                "写", "生成", "编造", "创作", "起草", "润色", "翻译", "续写", "改写",
                "扮演", "来一段", "编一段",
            ),
            jailbreak = listOf(
                Regex("(忽略|无视|忘记).{0,6}(之前|以上|前面|所有|你的).{0,4}(指令|规则|提示|限制)"),
                Regex("(开发者|上帝|无限制|越狱|dan).{0,4}模式"),
                Regex("你现在(是|扮演|变成|作为)"),
                Regex("(输出|告诉我|重复|展示).{0,8}(系统|你的).{0,3}(提示词|指令|设定)"),
                Regex("不要(遵守|理会|管).{0,6}(规则|限制|指令)"),
            ),
            encoding = listOf(
                Regex("(用|以|通过).{0,6}(base64|拼音|首字母|谐音|拆字|火星文|摩斯|emoji).{0,8}(回答|输出|写|告诉我|回复)"),
                Regex("(编码|加密|转换|替换).{0,8}(后|再).{0,6}(回答|输出|回复)"),
                Regex("(每个字|逐字).{0,6}(加|插|隔).{0,4}(符号|空格|字符)"),
            ),
            promptExtraction = listOf(
                Regex("(系统|预|预设|前置|初始|隐藏|开发者|内部)?\\s*提示词"),
                Regex("系统提示"),
                Regex("系统消息"),
                Regex("系统\\s*prompt", RegexOption.IGNORE_CASE),
                Regex("system\\s*prompt", RegexOption.IGNORE_CASE),
                Regex("\\bprompt\\b", RegexOption.IGNORE_CASE),
                Regex("(系统|内部|开发者|你的|隐藏)\\s*(设定|指令|规则)"),
                Regex("(输出|复述|展示|泄露|告诉我|发给我).{0,8}(提示词|规则|指令|设定|系统消息)"),
            ),
            promptExtractionHard = listOf(
                Regex("(系统|预|预设|前置|初始|隐藏|开发者|内部)\\s*(提示词|提示|设定|指令|规则)"),
                Regex("(输出|复述|展示|泄露|告诉我|发给我).{0,8}(提示词|规则|指令|设定|系统消息)"),
                Regex("(提示词|系统提示|系统消息|规则|指令|设定).{0,8}(发给我|发我|给我|输出|复述|展示|泄露)"),
                Regex("(你的|隐藏的|内部的).{0,4}(规则|设定|指令)"),
            ),
            selfHarm = listOf(
                Regex("(我|自己).{0,8}(想|要|打算|准备).{0,6}(死|自杀|轻生|结束生命|自残)"),
                Regex("(不想活|活不下去|活着没意思|想消失|撑不下去)"),
                Regex("(怎么|如何|教我).{0,6}(自杀|自残|结束生命)"),
            ),
            selfHarmOutput = listOf(
                Regex("(自杀|自残|轻生).{0,10}(方法|步骤|方式|工具|药物|剂量|怎么做)"),
                Regex("(割腕|上吊|跳楼|服毒).{0,8}(方法|步骤|怎么|如何)"),
            ),
            exemptions = setOf(
                "反", "防", "预防", "抵制", "打击", "举报", "识别", "拒绝", "远离",
                "禁毒", "安全教育", "案例", "宣传", "治理", "干预", "救助", "帮助", "保护",
            ),
        )
    }
}
