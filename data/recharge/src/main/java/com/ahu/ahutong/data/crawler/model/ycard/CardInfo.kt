package com.ahu.ahutong.data.crawler.model.ycard

data class CardInfo(
    val code: Int,
    val `data`: Data,
    val msg: String,
    val success: Boolean
)

data class Data(
    val account: Any,
    val card: List<Card>,
    val errmsg: Any,
    val retcode: String,
    val sno: Any
)

data class Card(
    val acc_status: Int,
    val accinfo: List<Accinfo>,
    val account: String,
    val acctId: Any,
    val auth_code_background: Any,
    val auth_code_font_color: Any,
    val autotrans_amt: Int,
    val autotrans_flag: Int,
    val autotrans_limite: Int,
    val bankacc: String,
    val barflag: Int,
    val card_background: Any,
    val card_font_color: Any,
    val card_logo: String,
    val card_name: String,
    val card_name_en: String,
    val cardname: String,
    val cardtype: String,
    val cert: String,
    val createdate: String,
    val custId: Any,
    val custMemberId: Any,
    val daycostlimit: Int,
    val db_balance: Int,
    val debitamt: Int,
    val department_name: Any,
    val elec_accamt: Int,
    val expdate: String,
    val flag: String,
    val freezeflag: Int,
    val idflag: Int,
    val lostflag: Int,
    val mscard: Int,
    val name: String,
    val nonpwdlimit: Int,
    val phone: String,
    val scbkbs: Int,
    val schcode: String,
    val singlelimit: Int,
    val sno: String,
    val unsettle_amount: Int,
    val voucher: String,
    val voucherStatus: Int
)

data class Accinfo(
    val autotrans_amt: Any,
    val autotrans_flag: Int,
    val autotrans_limite: Any,
    val balance: Int,
    val daycostamt: Any,
    val daycostlimit: Any,
    val name: String,
    val nonpwdlimit: Any,
    val singlelimit: Any,
    val type: String
)

/**
 * 主账户余额（分），与充值页「账户余额」取的是同一个字段。
 * 首页余额也用它，保证两处看到的是同一份实时数据。
 */
fun CardInfo.primaryAccountBalanceCents(): Int? =
    data.card.firstOrNull()?.accinfo?.firstOrNull()?.balance
