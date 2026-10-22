package com.ahu.ahutong.data.adapter

import android.util.Log
import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.crawler.api.ycard.YcardApi
import com.ahu.ahutong.data.crawler.model.ycard.ElectricityAccountPayInfoResponse
import com.ahu.ahutong.data.crawler.model.ycard.ElectricityCampusApiResponse
import com.ahu.ahutong.data.crawler.model.ycard.ElectricityFinalPayResponse
import com.ahu.ahutong.data.crawler.model.ycard.ElectricityOrderData
import com.ahu.ahutong.data.crawler.model.ycard.ElectricityOrderResponse
import com.ahu.ahutong.data.crawler.model.ycard.ElectricityPaymentData
import com.ahu.ahutong.data.crawler.model.ycard.ElectricityRoomInfoApiResponse
import com.ahu.ahutong.data.crawler.model.ycard.buildSignedPaymentFormBody
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.CampusDataItem
import com.ahu.ahutong.data.model.ElectricityChargeInfo
import com.ahu.ahutong.data.model.ElectricityController
import com.ahu.ahutong.data.model.ElectricityDepositHistoryItem
import com.ahu.ahutong.data.model.RoomSelectionInfo
import com.ahu.ahutong.data.recharge.ElectricityDepositSource
import com.ahu.ahutong.data.recharge.ElectricityOptionLevel
import com.ahu.ahutong.data.recharge.ElectricityOptionQuery
import com.ahu.ahutong.data.recharge.ElectricityPayment
import com.ahu.ahutong.data.recharge.ElectricityRoom
import com.ahu.ahutong.data.recharge.ElectricityRoomDetails
import com.ahu.ahutong.data.recharge.RechargeReceipt
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.ResponseBody
import retrofit2.Response

/** Keeps ycard forms, wire DTOs and the three-step payment behind the feature seam. */
@Singleton
class RepositoryElectricityDepositSource @Inject constructor() : ElectricityDepositSource {

    override suspend fun options(
        query: ElectricityOptionQuery
    ): AhuResult<List<CampusDataItem>> = withContext(Dispatchers.IO) {
        val form = when (val result = optionForm(query)) {
            is AhuResult.Failure -> return@withContext result
            is AhuResult.Success -> result.value
        }
        callAndParse(
            label = query.level.logLabel,
            call = { getFeeItemThirdData(form) }
        ) { body ->
            val response = GSON.fromJson(body, ElectricityCampusApiResponse::class.java)
            response.map?.data?.let { AhuResult.Success(it) }
                ?: AhuResult.Failure(AhuError.ProtocolChanged(query.level.missingItemsMessage))
        }
    }

    override suspend fun room(
        controller: ElectricityController,
        selection: RoomSelectionInfo
    ): AhuResult<ElectricityRoom> = withContext(Dispatchers.IO) {
        val room = selection.room?.value
            ?: return@withContext missing("selectedRoomValue内容为空")
        val floor = selection.floor?.value
            ?: return@withContext missing("selectedFloorValue内容为空")
        val building = selection.building?.value
            ?: return@withContext missing("selectedBuildingValue内容为空")
        val campus = selection.campus?.value
        if (controller.requiresCampus && campus == null) {
            return@withContext missing("selectedCampusValue内容为空")
        }

        val builder = FormBody.Builder()
            .add("feeitemid", controller.feeItemId)
            .add("type", "IEC")
            .add("level", controller.roomInfoLevel)
        if (campus != null) builder.add("campus", campus)
        val form = builder
            .add("building", building)
            .add("floor", floor)
            .add("room", room)
            .build()

        callAndParse("getRoomInfo", { getFeeItemThirdData(form) }) { body ->
            val response = GSON.fromJson(body, ElectricityRoomInfoApiResponse::class.java)
            val map = response.map
            map?.data?.let { details ->
                AhuResult.Success(ElectricityRoom(map.showData?.info, details))
            } ?: AhuResult.Failure(
                AhuError.ProtocolChanged("解析数据失败，未找到房间信息")
            )
        }
    }

    override suspend fun pay(
        payment: ElectricityPayment
    ): AhuResult<RechargeReceipt> = withContext(Dispatchers.IO) {
        try {
            val order = when (val result = createOrder(payment)) {
                is AhuResult.Failure -> return@withContext result.withFallback("创建订单失败")
                is AhuResult.Success -> result.value
            }
            val passwordMapping = when (val result = accountPayInfo(order.orderId)) {
                is AhuResult.Failure -> return@withContext result.withFallback("获取支付信息失败")
                is AhuResult.Success -> result.value
            }
            val plainDigits = "0123456789"
            val keyMap = passwordMapping.second.mapIndexed { index, cipherDigit ->
                cipherDigit.toString() to plainDigits[index].toString()
            }.toMap()
            val cipherText = payment.password.map { digit ->
                keyMap[digit.toString()] ?: digit.toString()
            }.joinToString("")
            val form = buildSignedPaymentFormBody(
                linkedMapOf(
                    "orderid" to order.orderId,
                    "paystep" to "2",
                    "paytype" to "ACCOUNTTSM",
                    "paytypeid" to "64",
                    "userAgent" to "h5",
                    "ccctype" to "000",
                    "password" to cipherText,
                    "uuid" to passwordMapping.first,
                    "isWX" to "0"
                )
            )

            val response = request { pay(form) }
            if (!response.isSuccessful) {
                return@withContext AhuResult.Failure(electricityHttpError(response.code))
            }
            val result = GSON.fromJson(response.body, ElectricityFinalPayResponse::class.java)
            if (result.code == 200 && result.success) {
                AhuResult.Success(RechargeReceipt(order.orderId))
            } else {
                AhuResult.Failure(
                    AhuError.Server(-1, result.msg ?: "支付失败，未知错误")
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AhuResult.Failure(AhuError.Unknown("支付请求异常: ${error.message}"))
        }
    }

    override fun selectedController(): ElectricityController = AHUCache.getElectricityController()

    override fun saveController(controller: ElectricityController) =
        AHUCache.setElectricityController(controller)

    override fun roomSelection(): RoomSelectionInfo? = AHUCache.getRoomSelection()

    override fun saveRoomSelection(selection: RoomSelectionInfo) =
        AHUCache.saveRoomSelection(selection)

    override fun depositHistory(): List<ElectricityDepositHistoryItem> =
        AHUCache.getElectricityDepositHistory()

    override fun saveDepositHistory(history: List<ElectricityDepositHistoryItem>) =
        AHUCache.saveElectricityDepositHistory(history)

    override fun chargeInfo(): ElectricityChargeInfo? = AHUCache.getElectricityChargeInfo()

    override fun saveChargeInfo(info: ElectricityChargeInfo) =
        AHUCache.saveElectricityChargeInfo(info)

    private fun optionForm(query: ElectricityOptionQuery): AhuResult<FormBody> {
        val selection = query.selection
        val builder = FormBody.Builder()
            .add("feeitemid", query.controller.feeItemId)
            .add("type", "select")
        when (query.level) {
            ElectricityOptionLevel.Initial -> builder.add("level", "0")
            ElectricityOptionLevel.Buildings -> {
                val campus = selection.campus?.value
                    ?: return missing("selectedCampusValue内容为空")
                builder.add("level", "1").add("campus", campus)
            }
            ElectricityOptionLevel.Floors -> {
                val campus = selection.campus?.value
                if (query.controller.requiresCampus && campus == null) {
                    return missing("selectedCampusValue内容为空")
                }
                val building = selection.building?.value
                    ?: return missing("selectedBuildingValue内容为空")
                builder.add("level", query.controller.floorLevel)
                if (campus != null) builder.add("campus", campus)
                builder.add("building", building)
            }
            ElectricityOptionLevel.Rooms -> {
                val floor = selection.floor?.value
                    ?: return missing("selectedFloorValue内容为空")
                val campus = selection.campus?.value
                if (query.controller.requiresCampus && campus == null) {
                    return missing("_selectedCampus内容为空")
                }
                val building = selection.building?.value
                    ?: return missing("selectedBuildingValue内容为空")
                builder.add("level", query.controller.roomLevel)
                if (campus != null) builder.add("campus", campus)
                builder.add("building", building).add("floor", floor)
            }
        }
        return AhuResult.Success(builder.build())
    }

    private suspend fun createOrder(payment: ElectricityPayment): AhuResult<ElectricityOrderData> {
        val room = payment.room
        val payload = ElectricityPaymentData(
            area = room.area.orEmpty(),
            buildingName = room.buildingName.orEmpty(),
            areaName = room.areaName.orEmpty(),
            floorName = room.floorName.orEmpty(),
            floor = room.floor.orEmpty(),
            aid = room.aid.orEmpty(),
            account = room.account.orEmpty(),
            building = room.building.orEmpty(),
            room = room.room.orEmpty(),
            roomName = room.roomName.orEmpty(),
            customInfo = "房间：${room.areaName} ${room.buildingName} ${room.floorName} ${room.roomName}"
        )
        val form = buildSignedPaymentFormBody(
            linkedMapOf(
                "feeitemid" to payment.controller.feeItemId,
                "tranamt" to payment.amount,
                "flag" to "choose",
                "source" to "app",
                "paystep" to "0",
                "abstracts" to "",
                "redirect_url" to "https://ycard.ahu.edu.cn/plat",
                "third_party" to GSON.toJson(payload)
            )
        )
        return callAndParse("getPaymentOrder", { pay(form) }) { body ->
            val response = GSON.fromJson(body, ElectricityOrderResponse::class.java)
            val data = response.data
            if (response.code == 200 && data != null) {
                AhuResult.Success(data)
            } else {
                AhuResult.Failure(AhuError.Server(-1, response.msg.orEmpty()))
            }
        }
    }

    private suspend fun accountPayInfo(orderId: String): AhuResult<Pair<String, String>> {
        val form = buildSignedPaymentFormBody(
            linkedMapOf(
                "paytypeid" to "64",
                "paytype" to "ACCOUNTTSM",
                "paystep" to "2",
                "orderid" to orderId
            )
        )
        return callAndParse("getAccountPayInfo", { pay(form) }) { body ->
            val response = GSON.fromJson(body, ElectricityAccountPayInfoResponse::class.java)
            val map = response.data?.passwordMap
            if (response.code == 200 && !map.isNullOrEmpty()) {
                val entry = map.entries.first()
                AhuResult.Success(entry.key to entry.value)
            } else {
                AhuResult.Failure(AhuError.Server(-1, response.msg.orEmpty()))
            }
        }
    }

    private suspend fun <T> callAndParse(
        label: String,
        call: suspend YcardApi.() -> Response<ResponseBody>,
        parse: (String) -> AhuResult<T>
    ): AhuResult<T> = try {
        val response = request(call)
        Log.d(TAG, "${label}响应码: ${response.code}")
        if (!response.isSuccessful) {
            AhuResult.Failure(electricityHttpError(response.code))
        } else if (response.body.isEmpty()) {
            AhuResult.Failure(AhuError.ProtocolChanged("服务器返回内容为空"))
        } else {
            parse(response.body)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        AhuResult.Failure(AhuError.Unknown("发生未知错误: ${error.message}"))
    }

    private suspend fun request(
        call: suspend YcardApi.() -> Response<ResponseBody>
    ): HttpPayload {
        val response = YcardApi.authorizedCall(request = call)
        return HttpPayload(
            code = response.code(),
            body = if (response.isSuccessful) {
                response.body()?.string().orEmpty()
            } else {
                response.errorBody()?.string().orEmpty()
            }
        )
    }

    private fun missing(message: String): AhuResult.Failure =
        AhuResult.Failure(AhuError.ProtocolChanged(message))

    private fun AhuResult.Failure.withFallback(message: String): AhuResult.Failure = when (val cause = error) {
        is AhuError.Server -> if (cause.message.isBlank()) {
            AhuResult.Failure(AhuError.Server(cause.code, message))
        } else {
            this
        }
        is AhuError.ProtocolChanged -> if (cause.detail.isBlank()) {
            AhuResult.Failure(AhuError.ProtocolChanged(message))
        } else {
            this
        }
        is AhuError.Unknown -> if (cause.message.isBlank()) {
            AhuResult.Failure(AhuError.Unknown(message))
        } else {
            this
        }
        else -> this
    }

    private companion object {
        const val TAG = "ElectricityDepositViewModel"
        val GSON = Gson()
    }
}

private val ElectricityOptionLevel.logLabel: String
    get() = when (this) {
        ElectricityOptionLevel.Initial -> "getInitialOptions"
        ElectricityOptionLevel.Buildings -> "getBuildings"
        ElectricityOptionLevel.Floors -> "getFloor"
        ElectricityOptionLevel.Rooms -> "getRoom"
    }

private val ElectricityOptionLevel.missingItemsMessage: String
    get() = when (this) {
        ElectricityOptionLevel.Initial -> "解析数据失败，未找到电控选项"
        ElectricityOptionLevel.Buildings -> "解析数据失败，未找到楼栋列表"
        ElectricityOptionLevel.Floors -> "解析数据失败，未找到楼层列表"
        ElectricityOptionLevel.Rooms -> "解析数据失败，未找到房间列表"
    }

internal fun electricityHttpError(code: Int): AhuError = when (code) {
    401, 403 -> AhuError.Unauthorized("校园卡登录暂不可用，请稍后重试")
    else -> AhuError.Server(code, "电控服务暂不可用，请稍后重试（$code）")
}

private data class HttpPayload(val code: Int, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299
}
