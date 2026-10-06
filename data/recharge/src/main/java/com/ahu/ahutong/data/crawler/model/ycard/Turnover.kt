package com.ahu.ahutong.data.crawler.model.ycard

/**
 * 校园卡账单流水（ycard /berserker-search 系列）。
 * 金额一律为「分」，展示层 ÷100 保留两位；时间为北京时间字符串（effectdateStr），勿解析 ISO 字段。
 * 字段以 2026-09-22 实测为准（见 ycard-bill-crawler-spec.md），可空字段均实测可为 null。
 */
data class TurnoverResponse(
    val code: Int,
    val `data`: TurnoverPage?,
    val msg: String? = null,
    val success: Boolean = false
)

/** 分页包装；当月无数据时 total 为 null（H5 依此停止加载）。 */
data class TurnoverPage(
    val records: List<TurnoverRecord>?,
    val total: Int?,
    val pages: Int?
)

data class TurnoverRecord(
    val orderId: String,
    /** 交易金额，分 */
    val tranamt: Long = 0,
    /** 交易后卡余额，分 */
    val cardBalance: Long = 0,
    /** 手续费，分 */
    val feeAmt: Long = 0,
    /** 2=支出（消费），1=收入（充值） */
    val typeFrom: String? = null,
    /** 大类，如「二维码支付」「充值」 */
    val turnoverType: String? = null,
    /** 细类，如「联机扫码支付(被扫)」 */
    val consumeTypeName: String? = null,
    /** 摘要，如「北二区食堂一楼-扫码支付」 */
    val resume: String? = null,
    /** 商户/地点 */
    val toMerchant: String? = null,
    val remark: String? = null,
    /** 交易时间（北京时间字符串） */
    val effectdateStr: String? = null,
    /** 记账时间 */
    val jndatetimeStr: String? = null,
    /** 卡账号 */
    val fromAccount: String? = null,
    /** 退款标记 */
    val isRefund: String? = null,
    /** 终端位置码（POS 机号，如 "77-139"）——窗口级粒度，干饭足迹/必吃榜的数据源。 */
    val locationName: String? = null
) {
    val isExpense: Boolean get() = typeFrom != "1"
}

/** 收支汇总（分）。 */
data class TurnoverCountResponse(
    val code: Int,
    val `data`: TurnoverCount?
)

data class TurnoverCount(
    val expenses: Long?,
    val income: Long?
)
