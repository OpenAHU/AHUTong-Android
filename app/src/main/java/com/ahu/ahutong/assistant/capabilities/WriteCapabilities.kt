package com.ahu.ahutong.assistant.capabilities

import com.ahu.ahutong.assistant.AssistantAnswer
import com.ahu.ahutong.assistant.AssistantCapability
import com.ahu.ahutong.assistant.AssistantCapabilityKind
import com.ahu.ahutong.assistant.AssistantCapabilitySpec
import com.ahu.ahutong.assistant.AssistantCard
import com.ahu.ahutong.assistant.AssistantOutcome
import com.ahu.ahutong.assistant.AssistantSensitivity
import com.ahu.ahutong.assistant.AssistantTrust
import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.adapter.RepositoryElectricityDepositSource
import com.ahu.ahutong.data.adapter.RepositoryNetworkRechargeSource
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundPublishRequest
import com.ahu.ahutong.data.mail.MailDraft
import com.ahu.ahutong.data.mail.StudentMailRepository
import com.ahu.ahutong.data.model.CardRechargeBank
import com.ahu.ahutong.data.model.ElectricityController
import com.ahu.ahutong.data.model.ElectricityDepositHistoryItem
import com.ahu.ahutong.data.model.RoomSelectionInfo
import com.ahu.ahutong.data.recharge.BathroomDepositSource
import com.ahu.ahutong.data.recharge.BathroomPayment
import com.ahu.ahutong.data.recharge.CardRechargeSource
import com.ahu.ahutong.data.recharge.ElectricityDepositSource
import com.ahu.ahutong.data.recharge.ElectricityPayment
import com.ahu.ahutong.data.recharge.NetworkRechargeSource
import com.ahu.ahutong.data.session.SessionStore
import com.ahu.ahutong.ui.state.RepositoryBathroomDepositSource
import com.ahu.ahutong.ui.state.RepositoryCardRechargeSource
import com.google.gson.JsonObject
import java.math.BigDecimal
import kotlinx.coroutines.CancellationException

/**
 * 真实写操作。所有扣款类能力都必须经过确认卡；支付密码只在 executeWithSecret
 * 的本次调用内使用，不进入 arguments、不写历史、不发给模型。
 */
class RechargeCardCapability(
    private val source: CardRechargeSource = RepositoryCardRechargeSource(),
) : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "recharge_card",
        title = "校园卡充值",
        description = "从用户指定的银行卡真实扣款，给校园一卡通充值。只有用户本轮明确给出金额和银行卡时才能调用；" +
            "金额单位为元（0.01-2000），bank 只能是 agricultural（中国农业银行）或 cmb（招商银行）。" +
            "失败后不要自动重试，先查余额确认是否已到账。",
        parameters = schema(
            "amount" to numberProp("充值金额，单位元", minimum = 0.01, maximum = 2000.0),
            "bank" to stringProp(
                "扣款银行卡：agricultural=中国农业银行；cmb=招商银行",
                enumValues = listOf("agricultural", "cmb"),
            ),
        ),
        sensitivity = AssistantSensitivity.FINANCIAL,
        kind = AssistantCapabilityKind.WRITE,
        source = "一卡通系统",
        openRoute = "card_balance_deposit",
        requiresConfirmation = true,
    )

    override suspend fun execute(arguments: JsonObject): AssistantOutcome =
        AssistantOutcome.Failure("payment_password_required", "需要本机确认后才能充值")

    override suspend fun executeWithSecret(arguments: JsonObject, secret: String?): AssistantOutcome {
        val amount = arguments.doubleOrNull("amount")
            ?: return AssistantOutcome.Failure("invalid_arguments", "缺少充值金额")
        if (!amount.isFinite() || amount < 0.01 || amount > 2000.0) {
            return AssistantOutcome.Failure("invalid_arguments", "充值金额需在 0.01 - 2000 元之间")
        }
        val bankKey = arguments.stringArg("bank")?.lowercase()
            ?: return AssistantOutcome.Failure("invalid_arguments", "缺少扣款银行卡")
        val bank = when (bankKey) {
            "agricultural", "agricultural_bank" -> CardRechargeBank.AGRICULTURAL_BANK
            "cmb", "china_merchants_bank" -> CardRechargeBank.CHINA_MERCHANTS_BANK
            "alipay" -> CardRechargeBank.ALIPAY
            else -> return AssistantOutcome.Failure(
                "invalid_arguments",
                "只支持 agricultural（中国农业银行）或 cmb（招商银行）",
            )
        }
        val account = when (val result = source.account()) {
            is AhuResult.Failure -> return result.error.asAssistantFailure()
            is AhuResult.Success -> result.value
        }
        val cardType = account.data.card.firstOrNull()
            ?.accinfo?.firstOrNull()?.type
            ?.takeIf { it.isNotBlank() }
            ?: return AssistantOutcome.Failure("card_unavailable", "没有读到现在可充值的校园卡")
        val orderId = when (val result = source.openOrder(formatAmount(amount), cardType)) {
            is AhuResult.Failure -> return result.error.asAssistantFailure()
            is AhuResult.Success -> result.value
                ?: return AssistantOutcome.Failure("order_failed", "没有开通充值订单，请稍后再试")
        }
        return when (val result = source.pay(orderId, bank)) {
            is AhuResult.Failure -> paymentUncertain("校园卡充值", result.error)
            is AhuResult.Success -> success(
                amount = amount,
                target = bankLabel(bank),
                reference = orderId,
            )
        }
    }

    private fun success(amount: Double, target: String, reference: String): AssistantOutcome =
        AssistantOutcome.Success(
            AssistantAnswer(
                summary = "已提交校园卡充值 ¥${formatAmount(amount)}（$target）",
                route = spec.openRoute,
                card = AssistantCard(
                    kind = "recharge",
                    headline = formatAmount(amount),
                    headlinePrefix = "¥",
                    subtitle = "校园卡充值 · $target",
                    note = "订单号 $reference",
                ),
            )
        )

    private fun bankLabel(bank: CardRechargeBank): String = when (bank) {
        CardRechargeBank.AGRICULTURAL_BANK -> "中国农业银行"
        CardRechargeBank.CHINA_MERCHANTS_BANK -> "招商银行"
        CardRechargeBank.ALIPAY -> "支付宝"
    }
}

class RechargeElectricityCapability(
    private val source: ElectricityDepositSource = RepositoryElectricityDepositSource(),
) : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "recharge_electricity",
        title = "宿舍电费充值",
        description = "从校园卡账户真实扣款，给当前保存的宿舍房间充电费。只有用户本轮明确说出金额时才能调用；" +
            "金额单位为元（0.01-2000）。支付密码由用户在确认卡里输入，模型不要索要或传递密码。",
        parameters = schema("amount" to numberProp("充值金额，单位元", minimum = 0.01, maximum = 2000.0)),
        sensitivity = AssistantSensitivity.FINANCIAL,
        kind = AssistantCapabilityKind.WRITE,
        source = "电费系统",
        openRoute = "electricity_pay",
        requiresConfirmation = true,
        requiresPaymentPassword = true,
    )

    override suspend fun describe(arguments: JsonObject): String? {
        val selection = source.roomSelection() ?: return null
        return "房间：" + listOfNotNull(
            selection.campus?.name,
            selection.building?.name,
            selection.floor?.name,
            selection.room?.name,
        ).joinToString(" ").ifBlank { selection.room?.name.orEmpty() }
    }

    override suspend fun execute(arguments: JsonObject): AssistantOutcome =
        AssistantOutcome.Failure("payment_password_required", "需要本机确认并输入支付密码")

    override suspend fun executeWithSecret(arguments: JsonObject, secret: String?): AssistantOutcome {
        val amount = arguments.doubleOrNull("amount")
            ?: return AssistantOutcome.Failure("invalid_arguments", "缺少充值金额")
        if (!amount.isFinite() || amount < 0.01 || amount > 2000.0) {
            return AssistantOutcome.Failure("invalid_arguments", "充值金额需在 0.01 - 2000 元之间")
        }
        val password = secret?.takeIf { it.length == 6 && it.all(Char::isDigit) }
            ?: return AssistantOutcome.Failure("payment_password_required", "请输入 6 位校园卡支付密码")
        val selection = source.roomSelection()?.takeIf { it.room != null }
            ?: return AssistantOutcome.Failure("room_missing", "还没有选择电费房间，请先在电费页选择一次")
        val controller = selection.controller ?: source.selectedController()
        val room = when (val result = source.room(controller, selection)) {
            is AhuResult.Failure -> return result.error.asAssistantFailure()
            is AhuResult.Success -> result.value
        }
        return when (val result = source.pay(
            ElectricityPayment(controller, formatAmount(amount), password, room.details)
        )) {
            is AhuResult.Failure -> paymentUncertain("电费充值", result.error)
            is AhuResult.Success -> AssistantOutcome.Success(
                AssistantAnswer(
                    summary = "已提交电费充值 ¥${formatAmount(amount)}",
                    route = spec.openRoute,
                    card = AssistantCard(
                        kind = "recharge",
                        headline = formatAmount(amount),
                        headlinePrefix = "¥",
                        subtitle = "宿舍电费 · ${room.details.roomName.orEmpty()}",
                        note = "凭证 ${result.value.reference}",
                    ),
                )
            )
        }
    }
}

class RechargeBathroomCapability(
    private val source: BathroomDepositSource = RepositoryBathroomDepositSource(),
) : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "recharge_bathroom",
        title = "浴室缴费",
        description = "从校园卡账户真实扣款，给浴室账户充值。只有用户本轮明确说出浴室、手机号和金额时才能调用；" +
            "bathroom 只能是「竹园/龙河」或「桔园/蕙园」，phone 是 11 位手机号，金额单位为元（0.01-2000）。" +
            "支付密码由用户在确认卡里输入，模型不要索要或传递密码。",
        parameters = schema(
            "bathroom" to stringProp("浴室：「竹园/龙河」或「桔园/蕙园」"),
            "phone" to stringProp("浴室账户绑定的 11 位手机号"),
            "amount" to numberProp("缴费金额，单位元", minimum = 0.01, maximum = 2000.0),
        ),
        sensitivity = AssistantSensitivity.FINANCIAL,
        kind = AssistantCapabilityKind.WRITE,
        source = "浴室缴费系统",
        openRoute = "bathroom_deposit",
        requiresConfirmation = true,
        requiresPaymentPassword = true,
    )

    override suspend fun describe(arguments: JsonObject): String? {
        val bathroom = arguments.stringArg("bathroom") ?: return null
        val phone = arguments.stringArg("phone") ?: return null
        return "浴室：$bathroom · 手机号：$phone"
    }

    override suspend fun execute(arguments: JsonObject): AssistantOutcome =
        AssistantOutcome.Failure("payment_password_required", "需要本机确认并输入支付密码")

    override suspend fun executeWithSecret(arguments: JsonObject, secret: String?): AssistantOutcome {
        val bathroom = arguments.stringArg("bathroom")
            ?: return AssistantOutcome.Failure("invalid_arguments", "缺少浴室")
        if (bathroom != "竹园/龙河" && bathroom != "桔园/蕙园") {
            return AssistantOutcome.Failure("invalid_arguments", "浴室只能是「竹园/龙河」或「桔园/蕙园」")
        }
        val phone = arguments.stringArg("phone")
            ?: return AssistantOutcome.Failure("invalid_arguments", "缺少手机号")
        if (!phone.matches(Regex("\\d{11}"))) {
            return AssistantOutcome.Failure("invalid_arguments", "请输入 11 位手机号")
        }
        val amount = arguments.doubleOrNull("amount")
            ?: return AssistantOutcome.Failure("invalid_arguments", "缺少缴费金额")
        if (!amount.isFinite() || amount < 0.01 || amount > 2000.0) {
            return AssistantOutcome.Failure("invalid_arguments", "缴费金额需在 0.01 - 2000 元之间")
        }
        val password = secret?.takeIf { it.length == 6 && it.all(Char::isDigit) }
            ?: return AssistantOutcome.Failure("payment_password_required", "请输入 6 位校园卡支付密码")
        val info = when (val result = source.bathroomInfo(bathroom, phone)) {
            is AhuResult.Failure -> return result.error.asAssistantFailure()
            is AhuResult.Success -> result.value
        }
        val account = info.map?.data
            ?: return AssistantOutcome.Failure("account_missing", "未查询到浴室账户，请核对手机号")
        return when (val result = source.pay(
            BathroomPayment(bathroom, formatAmount(amount), password, account)
        )) {
            is AhuResult.Failure -> paymentUncertain("浴室缴费", result.error)
            is AhuResult.Success -> AssistantOutcome.Success(
                AssistantAnswer(
                    summary = "已提交浴室缴费 ¥${formatAmount(amount)}",
                    route = spec.openRoute,
                    card = AssistantCard(
                        kind = "recharge",
                        headline = formatAmount(amount),
                        headlinePrefix = "¥",
                        subtitle = "浴室缴费 · $bathroom",
                        note = "凭证 ${result.value.reference}",
                    ),
                )
            )
        }
    }
}

class RechargeNetworkCapability(
    private val source: NetworkRechargeSource = RepositoryNetworkRechargeSource(),
) : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "recharge_network",
        title = "网费充值",
        description = "从校园卡账户真实扣款，给校园网账户充值。只有用户本轮明确说出金额时才能调用；" +
            "金额单位为元（0.01-2000）。支付密码由用户在确认卡里输入，模型不要索要或传递密码。",
        parameters = schema("amount" to numberProp("充值金额，单位元", minimum = 0.01, maximum = 2000.0)),
        sensitivity = AssistantSensitivity.FINANCIAL,
        kind = AssistantCapabilityKind.WRITE,
        source = "网费系统",
        openRoute = "network_recharge",
        requiresConfirmation = true,
        requiresPaymentPassword = true,
    )

    override suspend fun execute(arguments: JsonObject): AssistantOutcome =
        AssistantOutcome.Failure("payment_password_required", "需要本机确认并输入支付密码")

    override suspend fun executeWithSecret(arguments: JsonObject, secret: String?): AssistantOutcome {
        val amount = arguments.doubleOrNull("amount")
            ?: return AssistantOutcome.Failure("invalid_arguments", "缺少充值金额")
        if (!amount.isFinite() || amount < 0.01 || amount > 2000.0) {
            return AssistantOutcome.Failure("invalid_arguments", "充值金额需在 0.01 - 2000 元之间")
        }
        val password = secret?.takeIf { it.length == 6 && it.all(Char::isDigit) }
            ?: return AssistantOutcome.Failure("payment_password_required", "请输入 6 位校园卡支付密码")
        val snapshot = when (val result = source.load()) {
            is AhuResult.Failure -> return result.error.asAssistantFailure()
            is AhuResult.Success -> result.value
        }
        return when (val result = source.pay(snapshot, formatAmount(amount), password)) {
            is AhuResult.Failure -> paymentUncertain("网费充值", result.error)
            is AhuResult.Success -> AssistantOutcome.Success(
                AssistantAnswer(
                    summary = "已提交网费充值 ¥${formatAmount(amount)}",
                    route = spec.openRoute,
                    card = AssistantCard(
                        kind = "recharge",
                        headline = formatAmount(amount),
                        headlinePrefix = "¥",
                        subtitle = "网费充值",
                        note = "凭证 ${result.value.reference}",
                    ),
                )
            )
        }
    }
}

class SendMailCapability(
    private val repository: () -> StudentMailRepository = { StudentMailRepository() },
) : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "send_mail",
        title = "发送学生邮箱邮件",
        description = "使用学生邮箱发送一封邮件。只有用户明确给出收件人、主题和正文并要求发送时才能调用；发送后不可撤回。",
        parameters = schema(
            "to" to stringProp("收件人邮箱，多个用逗号分隔"),
            "cc" to stringProp("抄送邮箱，可选"),
            "subject" to stringProp("主题"),
            "body" to stringProp("正文"),
        ),
        sensitivity = AssistantSensitivity.PUBLIC,
        kind = AssistantCapabilityKind.WRITE,
        source = "学生邮箱",
        openRoute = "student_mail",
        requiresConfirmation = true,
    )

    override suspend fun execute(arguments: JsonObject): AssistantOutcome =
        AssistantOutcome.Failure("confirmation_required", "需要本机确认后才能发送")

    override suspend fun executeWithSecret(arguments: JsonObject, secret: String?): AssistantOutcome {
        val to = arguments.stringArg("to")
            ?: return AssistantOutcome.Failure("invalid_arguments", "缺少收件人")
        val subject = arguments.stringArg("subject")
            ?: return AssistantOutcome.Failure("invalid_arguments", "缺少主题")
        val body = arguments.stringArg("body")
            ?: return AssistantOutcome.Failure("invalid_arguments", "缺少正文")
        val cc = arguments.stringArg("cc").orEmpty()
        return try {
            val mail = repository()
            mail.connect()
            val draft = mail.beginDraft(MailDraft(to = to, cc = cc, subject = subject, body = body))
            mail.send(draft)
            AssistantOutcome.Success(
                AssistantAnswer(
                    summary = "邮件已发送给 $to",
                    route = spec.openRoute,
                    card = AssistantCard(
                        kind = "mail_sent",
                        headline = "已发送",
                        subtitle = subject,
                        note = "收件人：$to" + if (cc.isNotBlank()) " · 抄送：$cc" else "",
                    ),
                )
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AssistantOutcome.Failure(
                "uncertain",
                "邮件发送结果未知，请先检查已发送，避免重复发送",
                retryable = false,
            )
        }
    }
}

class PublishLostFoundCapability : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "publish_lost_found",
        title = "发布失物招领",
        description = "发布一条失物招领或寻物启事。只有用户明确要求发布时才能调用；需要标题、描述、手机号、类型和校区。",
        parameters = schema(
            "state" to intProp("1=失物招领，2=寻物启事", minimum = 1, maximum = 2),
            "title" to stringProp("标题"),
            "description" to stringProp("物品描述"),
            "phone" to stringProp("联系电话"),
            "type" to stringProp("物品类型名称或 ID"),
            "campus" to stringProp("校区名称或 ID"),
            "linkman" to stringProp("联系人，默认当前账号姓名"),
        ),
        sensitivity = AssistantSensitivity.PUBLIC,
        kind = AssistantCapabilityKind.WRITE,
        source = "失物招领",
        openRoute = "lost_found",
        requiresConfirmation = true,
    )

    override suspend fun execute(arguments: JsonObject): AssistantOutcome =
        AssistantOutcome.Failure("confirmation_required", "需要本机确认后才能发布")

    override suspend fun executeWithSecret(arguments: JsonObject, secret: String?): AssistantOutcome {
        if (!SessionStore.isLoggedIn()) return notLoggedIn(spec)
        val state = (arguments.intArg("state") ?: 1).coerceIn(1, 2)
        val title = arguments.stringArg("title")
            ?: return AssistantOutcome.Failure("invalid_arguments", "缺少标题")
        val description = arguments.stringArg("description")
            ?: return AssistantOutcome.Failure("invalid_arguments", "缺少描述")
        val phone = arguments.stringArg("phone")
            ?: return AssistantOutcome.Failure("invalid_arguments", "缺少联系电话")
        val typeKey = arguments.stringArg("type")
            ?: return AssistantOutcome.Failure("invalid_arguments", "缺少类型")
        val campusKey = arguments.stringArg("campus")
            ?: return AssistantOutcome.Failure("invalid_arguments", "缺少校区")
        val types = when (val result = AHURepository.getAllLostFoundType()) {
            is AhuResult.Failure -> return result.error.asAssistantFailure()
            is AhuResult.Success -> result.value.`object`
        }
        val campuses = when (val result = AHURepository.getAllCampus()) {
            is AhuResult.Failure -> return result.error.asAssistantFailure()
            is AhuResult.Success -> result.value.`object`
        }
        val type = types.firstOrNull { it.typeId == typeKey }
            ?: types.firstOrNull { it.typeName.contains(typeKey, ignoreCase = true) }
        val campus = campuses.firstOrNull { it.id == campusKey }
            ?: campuses.firstOrNull { it.campusName.contains(campusKey, ignoreCase = true) }
        if (type == null || campus == null) {
            return AssistantOutcome.Failure(
                "invalid_arguments",
                "类型或校区无法匹配。类型：${types.joinToString { it.typeName }}；校区：${campuses.joinToString { it.campusName }}",
            )
        }
        val request = LostFoundPublishRequest(
            linkman = arguments.stringArg("linkman")
                ?: SessionStore.currentUser()?.name.orEmpty(),
            phone = phone,
            typeid = type.typeId,
            num1 = description,
            campusid = campus.id,
            title = title,
            state = state.toString(),
        )
        return when (val result = AHURepository.publishLostFound(request)) {
            is AhuResult.Failure -> writeFailure("发布失物招领", result.error)
            is AhuResult.Success -> AssistantOutcome.Success(
                AssistantAnswer(
                    summary = "已发布${if (state == 1) "失物招领" else "寻物启事"}：$title",
                    route = spec.openRoute,
                    card = AssistantCard(
                        kind = "lost_found_published",
                        headline = "已发布",
                        subtitle = title,
                        note = "${type.typeName} · ${campus.campusName}",
                    ),
                )
            )
        }
    }
}

class DeleteLostFoundCapability : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "delete_lost_found",
        title = "删除失物招领",
        description = "删除一条自己发布的失物招领/寻物启事。只有用户明确要求删除并提供 id 时才能调用。",
        parameters = schema("id" to stringProp("信息 ID，可从 get_lost_found 返回的 items[].id 获取")),
        sensitivity = AssistantSensitivity.PUBLIC,
        kind = AssistantCapabilityKind.WRITE,
        source = "失物招领",
        openRoute = "lost_found",
        requiresConfirmation = true,
    )

    override suspend fun execute(arguments: JsonObject): AssistantOutcome =
        AssistantOutcome.Failure("confirmation_required", "需要本机确认后才能删除")

    override suspend fun executeWithSecret(arguments: JsonObject, secret: String?): AssistantOutcome {
        val id = arguments.stringArg("id")
            ?: return AssistantOutcome.Failure("invalid_arguments", "缺少 id")
        return when (val result = AHURepository.deleteLostFound(id)) {
            is AhuResult.Failure -> writeFailure("删除失物招领", result.error)
            is AhuResult.Success -> AssistantOutcome.Success(
                AssistantAnswer(
                    summary = "已删除失物招领",
                    route = spec.openRoute,
                    card = AssistantCard(kind = "lost_found_deleted", headline = "已删除", note = "ID $id"),
                )
            )
        }
    }
}

class SwitchElectricityConditionCapability(
    private val source: ElectricityDepositSource = RepositoryElectricityDepositSource(),
) : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "switch_electricity_condition",
        title = "切换电控条件",
        description = "把当前电费房间切换为最近使用过的某个条件（如照明/空调）。只改变后续查电费和充值使用的房间，不扣款。" +
            "index 来自 get_electricity_conditions；也可用 label 或 room 匹配。",
        parameters = schema(
            "index" to intProp("条件序号，从 0 开始", minimum = 0),
            "label" to stringProp("条件名称，支持模糊匹配"),
            "room" to stringProp("房间名称，支持模糊匹配"),
        ),
        sensitivity = AssistantSensitivity.PUBLIC,
        kind = AssistantCapabilityKind.WRITE,
        source = "电费系统",
        openRoute = "electricity_recent_rooms",
        requiresConfirmation = true,
    )

    override suspend fun execute(arguments: JsonObject): AssistantOutcome =
        AssistantOutcome.Failure("confirmation_required", "需要本机确认后才能切换")

    override suspend fun executeWithSecret(arguments: JsonObject, secret: String?): AssistantOutcome {
        val items = buildList {
            addAll(source.depositHistory())
            source.roomSelection()?.takeIf { it.room != null }?.let {
                add(ElectricityDepositHistoryItem(it, "", 0L))
            }
        }.distinctBy { selectionKey(it.selection) }
        if (items.isEmpty()) {
            return AssistantOutcome.Failure("electricity_condition_empty", "还没有保存过电控条件")
        }
        val index = arguments.intArg("index")
        val label = arguments.stringArg("label")
        val room = arguments.stringArg("room")
        val target = when {
            index != null -> items.getOrNull(index)
            label != null -> items.firstOrNull { it.label.contains(label, ignoreCase = true) }
            room != null -> items.firstOrNull {
                it.selection.room?.name?.contains(room, ignoreCase = true) == true
            }
            else -> null
        } ?: return AssistantOutcome.Failure(
            "electricity_condition_not_found",
            "没有匹配的电控条件，请先查询电控条件",
        )
        val controller = target.selection.controller ?: source.selectedController()
        source.saveController(controller)
        source.saveRoomSelection(target.selection)
        val labelText = target.label.takeIf { it.isNotBlank() } ?: selectionLabel(target.selection)
        return AssistantOutcome.Success(
            AssistantAnswer(
                summary = "已切换为 $labelText",
                route = spec.openRoute,
                card = AssistantCard(
                    kind = "electricity_condition",
                    headline = "已切换",
                    subtitle = labelText,
                ),
            )
        )
    }

    private fun selectionLabel(selection: RoomSelectionInfo): String =
        listOfNotNull(
            selection.controller?.displayName,
            selection.room?.name,
            selection.building?.name,
            selection.floor?.name,
        ).joinToString(" · ")

    private fun selectionKey(selection: RoomSelectionInfo): String = listOf(
        selection.controller?.name,
        selection.campus?.value,
        selection.building?.value,
        selection.floor?.value,
        selection.room?.value,
    ).joinToString("|") { it ?: "" }
}

private fun paymentUncertain(action: String, error: AhuError): AssistantOutcome.Failure =
    AssistantOutcome.Failure(
        "uncertain",
        "$action 结果未知，请先核对余额/账单，确认没有到账前不要重复操作",
        retryable = false,
    )

private fun writeFailure(action: String, error: AhuError): AssistantOutcome.Failure =
    if (error is AhuError.ProtocolChanged || error is AhuError.Unknown) {
        AssistantOutcome.Failure(
            "uncertain",
            "$action 结果未知（${error.userMessage()}），请先核对结果，避免重复提交",
            retryable = false,
        )
    } else {
        error.asAssistantFailure()
    }

private fun AhuError.userMessage(): String = when (this) {
    AhuError.Network -> "网络异常"
    AhuError.Timeout -> "请求超时"
    is AhuError.Unauthorized -> message
    is AhuError.ProtocolChanged -> detail
    is AhuError.Server -> message
    is AhuError.Unknown -> message
}

private fun formatAmount(value: Double): String =
    BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

private fun JsonObject.doubleOrNull(key: String): Double? =
    get(key)?.takeIf { !it.isJsonNull }?.let { runCatching { it.asDouble }.getOrNull() }
