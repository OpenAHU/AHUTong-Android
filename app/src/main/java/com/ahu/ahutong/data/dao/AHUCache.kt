package com.ahu.ahutong.data.dao

import com.ahu.ahutong.AHUApplication
import com.ahu.ahutong.BuildConfig
import com.ahu.ahutong.data.crawler.model.adwnh.CampusItem
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundItem
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundTypeItem
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.ElectricityChargeInfo
import com.ahu.ahutong.data.model.ElectricityController
import com.ahu.ahutong.data.model.ElectricityDepositHistoryItem
import com.ahu.ahutong.data.model.CardRechargeBank
import com.ahu.ahutong.data.model.EvalPreset
import com.ahu.ahutong.data.model.Exam
import com.ahu.ahutong.data.model.GpaRankInfo
import com.ahu.ahutong.data.model.Grade
import com.ahu.ahutong.data.model.GradeStudentProfile
import com.ahu.ahutong.data.model.RoomSelectionInfo
import com.ahu.ahutong.data.model.User
import com.ahu.ahutong.data.security.SecureStorage
import com.ahu.ahutong.ext.fromJson
import com.ahu.ahutong.sdk.RustSDK
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV

enum class HomeWidgetLayoutFamily {
    CLASSIC,
    RADIANT
}

/**
 * @Author SinkDev
 * @Date 2021/7/27-16:49
 * @Email 468766131@qq.com
 */
object AHUCache {

    init {
        MMKV.initialize(AHUApplication.getApp())
    }

    private val kv_init: MMKV = MMKV.mmkvWithID("ahu")

    private val currentUserCacheLock = Any()
    @Volatile
    private var currentUserCacheInitialized = false
    @Volatile
    private var currentUserCache: User? = null

    @Volatile
    private var mockDataCache: Boolean? = null
    @Volatile
    private var mockCurrentTimeCacheInitialized = false
    @Volatile
    private var mockCurrentTimeCache: Long? = null

    private val kv: MMKV
        get() {
            val user = getCurrentUser()
            return  if (user != null && !user.xh.isNullOrEmpty()) {
                MMKV.mmkvWithID("ahu_${user.xh}")
            } else {
                MMKV.mmkvWithID("ahu_guest")
            }
        }

    private const val INIT_BOX = "init"

    private fun sanitizeBoxPart(value: String): String {
        return value.replace(Regex("[^A-Za-z0-9_.-]"), "_")
    }

    private fun userBoxName(userId: String? = getCurrentUser()?.xh): String {
        val stableUserId = userId?.takeIf { it.isNotEmpty() } ?: "guest"
        return "user_${sanitizeBoxPart(stableUserId)}"
    }

    private fun initPutString(key: String, value: String) {
        SecureStorage.putString("$INIT_BOX.$key", value)
        RustSDK.kvRemoveSafe(INIT_BOX, key)
        kv_init.removeValueForKey(key)
    }

    private fun initGetString(key: String): String? {
        SecureStorage.getString("$INIT_BOX.$key")?.let { return it }
        return RustSDK.kvGetStringSafe(INIT_BOX, key)?.also { value ->
            SecureStorage.putString("$INIT_BOX.$key", value)
            RustSDK.kvRemoveSafe(INIT_BOX, key)
        }
    }

    private fun initGetStringOrMigrate(key: String, fallback: () -> String?): String? {
        initGetString(key)?.let { return it }
        return fallback()?.also { value ->
            if (value.isNotEmpty()) initPutString(key, value)
            kv_init.removeValueForKey(key)
        }
    }

    private fun initRemove(key: String) {
        SecureStorage.remove("$INIT_BOX.$key")
        RustSDK.kvRemoveSafe(INIT_BOX, key)
        kv_init.removeValueForKey(key)
    }

    private fun userPutString(key: String, value: String) {
        val boxName = userBoxName()
        SecureStorage.putString("$boxName.$key", value)
        RustSDK.kvRemoveSafe(boxName, key)
        kv.removeValueForKey(key)
    }

    private fun userGetString(key: String): String? {
        val boxName = userBoxName()
        SecureStorage.getString("$boxName.$key")?.let { return it }
        return RustSDK.kvGetStringSafe(boxName, key)?.also { value ->
            SecureStorage.putString("$boxName.$key", value)
            RustSDK.kvRemoveSafe(boxName, key)
        }
    }

    private fun userGetStringOrMigrate(key: String, fallback: () -> String?): String? {
        userGetString(key)?.let { return it }
        return fallback()?.also { value ->
            if (value.isNotEmpty()) userPutString(key, value)
            kv.removeValueForKey(key)
        }
    }

    private fun userRemove(key: String) {
        val boxName = userBoxName()
        SecureStorage.remove("$boxName.$key")
        RustSDK.kvRemoveSafe(boxName, key)
        kv.removeValueForKey(key)
    }

    /**
     * 清除全部数据
     */
    fun clearAll() {
        val boxName = userBoxName()
        val currentKv = kv
        SecureStorage.clearPrefix("$INIT_BOX.")
        SecureStorage.clearPrefix("$boxName.")
        SecureStorage.clearPrefix("user_guest.")
        RustSDK.kvClearBoxSafe(INIT_BOX)
        RustSDK.kvClearBoxSafe(boxName)
        RustSDK.kvClearBoxSafe("user_guest")
        kv_init.clearAll()
        currentKv.clearAll()
        MMKV.mmkvWithID("ahu_guest").clearAll()
        synchronized(currentUserCacheLock) {
            currentUserCache = null
            currentUserCacheInitialized = true
        }
        mockDataCache = null
        mockCurrentTimeCache = null
        mockCurrentTimeCacheInitialized = false
        homeWidgetSlotsCache = null
    }

    /**
     * 保存本地User对象
     * @param user User
     */
    fun saveCurrentUser(user: User) {
        val data = Gson().toJson(user)
        initPutString("current_user", data)
        synchronized(currentUserCacheLock) {
            currentUserCache = user
            currentUserCacheInitialized = true
        }
        homeWidgetSlotsCache = null
    }

    /**
     * 清除本地登陆状态
     */
    fun clearCurrentUser() {
        initRemove("current_user")
        synchronized(currentUserCacheLock) {
            currentUserCache = null
            currentUserCacheInitialized = true
        }
        homeWidgetSlotsCache = null
    }

    /**
     * 获取本地User对象
     * @return User?
     */
    fun getCurrentUser(): User? {
        if (currentUserCacheInitialized) return currentUserCache
        return synchronized(currentUserCacheLock) {
            if (currentUserCacheInitialized) {
                currentUserCache
            } else {
                val data = initGetStringOrMigrate("current_user") {
                    kv_init.decodeString("current_user")
                }.orEmpty()
                data.fromJson(User::class.java).also { user ->
                    currentUserCache = user
                    currentUserCacheInitialized = true
                }
            }
        }
    }

    /**
     * 是否登录
     * @return Boolean
     */
    fun isLogin(): Boolean {
        return getCurrentUser() != null
    }

    /**
     * 保存智慧安大密码
     * @param password String
     */
    fun saveWisdomPassword(password: String) {
        if (password.isEmpty()) initRemove("password_wisdom")
        else initPutString("password_wisdom", password)
    }

    /**
     * 获取智慧安大密码
     * @return String?
     */
    fun getWisdomPassword(): String? {
        return initGetStringOrMigrate("password_wisdom") { kv_init.decodeString("password_wisdom") }
    }

    fun saveEvalToken(token: String) {
        if (token.isEmpty()) userRemove("eval_token")
        else userPutString("eval_token", token)
    }

    fun getEvalToken(): String? {
        return userGetStringOrMigrate("eval_token") { kv.decodeString("eval_token") }
    }

    fun saveEvalPreset(preset: EvalPreset) {
        val data = Gson().toJson(preset)
        userPutString("eval_preset", data)
    }

    fun getEvalPreset(): EvalPreset {
        val data = userGetStringOrMigrate("eval_preset") { kv.decodeString("eval_preset") } ?: ""
        if (data.isBlank()) return EvalPreset()
        return data.fromJson(EvalPreset::class.java) ?: EvalPreset()
    }

    /**
     * 保存课程表
     * @param schoolYear String
     * @param schoolTerm String
     * @param schdule List<Course>
     */
    fun saveSchedule(schoolYear: String, schoolTerm: String, schedule: List<Course>) {
        val data = Gson().toJson(schedule)
        userPutString("$schoolYear-$schoolTerm.schedule", data)
    }

    fun saveSchedule(schoolTerm: String,schedule: List<Course>) {
        val data = Gson().toJson(schedule)
        userPutString("$schoolTerm.schedule", data)
    }

    /**
     * 获取课程表
     * @param schoolYear String
     * @param schoolTerm String
     * @return List<Course>
     */
    fun getSchedule(schoolYear: String, schoolTerm: String): List<Course>? {
        val key = "$schoolYear-$schoolTerm.schedule"
        val data = userGetStringOrMigrate(key) { kv.getString(key, "") } ?: ""
        return data.fromJson(object : TypeToken<List<Course>>() {}.type)
    }

    fun getSchedule(schoolTerm: String): List<Course>? {
        val key = "$schoolTerm.schedule"
        val data = userGetStringOrMigrate(key) { kv.getString(key, "") } ?: ""
        return data.fromJson(object : TypeToken<List<Course>>() {}.type)
    }

    fun saveScheduleFetchedAt(schoolTerm: String, fetchedAt: Long) {
        userPutString("$schoolTerm.scheduleFetchedAt", fetchedAt.toString())
    }

    fun getScheduleFetchedAt(schoolTerm: String): Long? {
        val key = "$schoolTerm.scheduleFetchedAt"
        return userGetStringOrMigrate(key) { kv.getString(key, null) }?.toLongOrNull()
    }

    fun saveNextSchedule(schedule: List<Course>) {
        val data = Gson().toJson(schedule)
        userPutString("next.schedule", data)
    }

    fun getNextSchedule(): List<Course>? {
        val data = userGetStringOrMigrate("next.schedule") {
            kv.getString("next.schedule", "")
        } ?: ""
        return data.fromJson(object : TypeToken<List<Course>>() {}.type)
    }

    /**
     * 保存成绩
     * @param grade Grade
     */
    fun saveGrade(grade: Grade) {
        val data = Gson().toJson(grade)
        userPutString("grade", data)
    }

    /**
     * 获取成绩
     * @return Grade
     */
    fun getGrade(): Grade? {
        val data = userGetStringOrMigrate("grade") { kv.decodeString("grade") } ?: ""
        return data.fromJson(Grade::class.java)
    }

    /**
     * 保存考试信息
     * @param exams List<Exam>
     */
    fun saveExamInfo(exams: List<Exam>) {
        val data = Gson().toJson(exams)
        userPutString("exams", data)
        userPutString("exams_updated_at", System.currentTimeMillis().toString())
    }

    /**
     * 获取考试信息
     * @return List<Exam>?
     */
    fun getExamInfo(): List<Exam>? {
        val data = userGetStringOrMigrate("exams") { kv.decodeString("exams") } ?: ""
        return data.fromJson(object : TypeToken<List<Exam>>() {}.type)
    }

    fun getExamInfoUpdatedAt(): Long {
        return userGetString("exams_updated_at")?.toLongOrNull() ?: 0L
    }

    /**
     * 获取开学时间
     * @param schoolYear String yyyy-yyyy
     * @param schoolTerm String 1 or 2
     * @return String? yyyy-MM-dd
     */
    fun getSchoolTermStartTime(schoolYear: String, schoolTerm: String): String? {
        val key = "startTime-$schoolYear-$schoolTerm"
        return userGetStringOrMigrate(key) { kv.decodeString(key) }
    }

    /**
     * 保存开学时间
     * @param schoolYear String yyyy-yyyy
     * @param schoolTerm String 1 or 2
     * @param startTime String yyyy-MM-dd
     */
    fun saveSchoolTermStartTime(schoolYear: String, schoolTerm: String, startTime: String) {
        userPutString("startTime-$schoolYear-$schoolTerm", startTime)
    }

    fun getSchoolTermInSemester(schoolYear: String, schoolTerm: String): Boolean? {
        val key = "inSemester-$schoolYear-$schoolTerm"
        return userGetStringOrMigrate(key) { kv.decodeString(key) }
            ?.toBooleanStrictOrNull()
    }

    fun getSchoolTermInSemesterObservedOn(schoolYear: String, schoolTerm: String): String? {
        val key = "inSemesterObservedOn-$schoolYear-$schoolTerm"
        return userGetStringOrMigrate(key) { kv.decodeString(key) }
    }

    fun saveSchoolTermInSemester(
        schoolYear: String,
        schoolTerm: String,
        isInSemester: Boolean,
        observedOn: String
    ) {
        val key = "inSemester-$schoolYear-$schoolTerm"
        val value = isInSemester.toString()
        userPutString(key, value)

        val observedOnKey = "inSemesterObservedOn-$schoolYear-$schoolTerm"
        userPutString(observedOnKey, observedOn)
    }

    /**
     * 获取默认的学年
     * @return String?
     */
    fun getSchoolYear(): String? {
        return userGetStringOrMigrate("defaultSchoolYear") {
            kv.decodeString("defaultSchoolYear")
                ?: initGetStringOrMigrate("defaultSchoolYear") { kv_init.decodeString("defaultSchoolYear") }
        }
    }

    /**
     * 保存默认的学年
     * @param schoolYear String
     */
    fun saveSchoolYear(schoolYear: String) {
        userPutString("defaultSchoolYear", schoolYear)
    }

    /**
     * 获取默认学期
     * @return String?
     */
    fun getSchoolTerm(): String? {
        return userGetStringOrMigrate("defaultSchoolTerm") {
            kv.getString(
                "defaultSchoolTerm",
                initGetStringOrMigrate("defaultSchoolTerm") { kv_init.getString("defaultSchoolTerm", null) }
            )
        }
    }

    /**
     * 保存默认的学期
     * @param schoolTerm String
     */
    fun saveSchoolTerm(schoolTerm: String) {
        userPutString("defaultSchoolTerm", schoolTerm)
    }

    /**
     * 是否显示非本周课程
     * @return Boolean
     */
    fun isShowAllCourse(): Boolean {
        userGetString("isShowAllCourse")?.toBooleanStrictOrNull()?.let { return it }
        val value = kv.getBoolean("isShowAllCourse", false)
        if (kv.containsKey("isShowAllCourse")) userPutString("isShowAllCourse", value.toString())
        return value
    }

    /**
     * 保存是否显示非本周课程
     * @param isCourse Boolean
     */
    fun saveIsShowAllCourse(isCourse: Boolean) {
        userPutString("isShowAllCourse", isCourse.toString())
    }

    fun isShowWidgetTip(): Boolean {
        userGetString("is_show_widget_dialog")?.toBooleanStrictOrNull()?.let { return it }
        val value = kv.getBoolean("is_show_widget_dialog", true)
        if (kv.containsKey("is_show_widget_dialog")) userPutString("is_show_widget_dialog", value.toString())
        return value
    }

    fun ignoreWidgetTip() {
        userPutString("is_show_widget_dialog", false.toString())
    }

    // Keep the legacy key as Classic so existing layouts migrate without a copy or downgrade hazard.
    private const val HOME_WIDGET_SLOTS_CLASSIC_KEY = "home_widget_slots"
    private const val HOME_WIDGET_SLOTS_RADIANT_KEY = "home_widget_slots_radiant"
    private const val HOME_WIDGET_SLOT_COUNT_CLASSIC = 8
    private const val HOME_WIDGET_SLOT_COUNT_RADIANT = 7

    private data class HomeWidgetSlotsCache(
        val userId: String?,
        val layoutFamily: HomeWidgetLayoutFamily,
        val slots: List<String?>
    )

    @Volatile
    private var homeWidgetSlotsCache: HomeWidgetSlotsCache? = null

    private fun homeWidgetSlotsKey(layoutFamily: HomeWidgetLayoutFamily): String =
        when (layoutFamily) {
            HomeWidgetLayoutFamily.CLASSIC -> HOME_WIDGET_SLOTS_CLASSIC_KEY
            HomeWidgetLayoutFamily.RADIANT -> HOME_WIDGET_SLOTS_RADIANT_KEY
        }

    private fun homeWidgetSlotCount(layoutFamily: HomeWidgetLayoutFamily): Int =
        when (layoutFamily) {
            HomeWidgetLayoutFamily.CLASSIC -> HOME_WIDGET_SLOT_COUNT_CLASSIC
            HomeWidgetLayoutFamily.RADIANT -> HOME_WIDGET_SLOT_COUNT_RADIANT
        }

    private fun defaultHomeWidgetSlots(layoutFamily: HomeWidgetLayoutFamily): List<String?> =
        when (layoutFamily) {
            HomeWidgetLayoutFamily.CLASSIC ->
                listOf("bathroom", "electricity") + List(HOME_WIDGET_SLOT_COUNT_CLASSIC - 2) { null }
            HomeWidgetLayoutFamily.RADIANT -> listOf(
                "electricity",
                "bathroom",
                "grade",
                "exam",
                "weather",
                "network_recharge",
                "free_classroom"
            )
        }

    private fun normalizeHomeWidgetSlots(
        layoutFamily: HomeWidgetLayoutFamily,
        slots: List<String?>
    ): List<String?> {
        val seen = mutableSetOf<String>()
        return List(homeWidgetSlotCount(layoutFamily)) { index ->
            val id = slots.getOrNull(index)?.takeIf { it.isNotBlank() }
            if (id != null && seen.add(id)) id else null
        }
    }

    fun getHomeWidgetSlots(): List<String?> =
        getHomeWidgetSlots(HomeWidgetLayoutFamily.CLASSIC)

    fun getHomeWidgetSlots(layoutFamily: HomeWidgetLayoutFamily): List<String?> {
        val userId = getCurrentUser()?.xh
        homeWidgetSlotsCache
            ?.takeIf { it.userId == userId && it.layoutFamily == layoutFamily }
            ?.let { return it.slots }
        val storageKey = homeWidgetSlotsKey(layoutFamily)
        val data = userGetStringOrMigrate(storageKey) {
            kv.decodeString(storageKey)
        } ?: ""
        val slots = if (data.isBlank()) {
            defaultHomeWidgetSlots(layoutFamily)
        } else {
            runCatching {
                Gson().fromJson<List<String?>>(
                    data,
                    object : TypeToken<List<String?>>() {}.type
                )
            }.getOrNull()
                ?.let { normalizeHomeWidgetSlots(layoutFamily, it) }
                ?: defaultHomeWidgetSlots(layoutFamily)
        }
        homeWidgetSlotsCache = HomeWidgetSlotsCache(userId, layoutFamily, slots)
        return slots
    }

    /** 用户是否曾自定义主页插槽（true=已保存过布局，false=从未设置）。 */
    fun hasCustomHomeWidgetSlots(): Boolean =
        hasCustomHomeWidgetSlots(HomeWidgetLayoutFamily.CLASSIC)

    fun hasCustomHomeWidgetSlots(layoutFamily: HomeWidgetLayoutFamily): Boolean {
        val storageKey = homeWidgetSlotsKey(layoutFamily)
        val data = userGetStringOrMigrate(storageKey) {
            kv.decodeString(storageKey)
        } ?: ""
        return data.isNotBlank()
    }

    fun saveHomeWidgetSlots(slots: List<String?>) =
        saveHomeWidgetSlots(HomeWidgetLayoutFamily.CLASSIC, slots)

    fun saveHomeWidgetSlots(
        layoutFamily: HomeWidgetLayoutFamily,
        slots: List<String?>
    ) {
        val normalizedSlots = normalizeHomeWidgetSlots(layoutFamily, slots)
        val data = Gson().toJson(normalizedSlots)
        userPutString(homeWidgetSlotsKey(layoutFamily), data)
        homeWidgetSlotsCache = HomeWidgetSlotsCache(
            getCurrentUser()?.xh,
            layoutFamily,
            normalizedSlots
        )
    }

    fun logout() {
        val userId = getCurrentUser()?.xh
        val boxName = userBoxName(userId)
        val currentUserKv = if (userId.isNullOrEmpty()) {
            MMKV.mmkvWithID("ahu_guest")
        } else {
            MMKV.mmkvWithID("ahu_$userId")
        }
        SecureStorage.clearPrefix("$boxName.")
        RustSDK.kvClearBoxSafe(boxName)
        currentUserKv.clearAll()
        saveWisdomPassword("")
        saveRustCookies("")
        clearCurrentUser()
    }


    fun savePhone(phone:String){
        userPutString("phone", phone)
    }

    fun getPhone() : String?{
        return userGetStringOrMigrate("phone") { kv.getString("phone",null) }
    }


    fun setJwxtStudentId(id: String){
        userPutString("jwxt_stu_id", id)
    }

    fun getJwxtStudentId() : String?{
        return userGetStringOrMigrate("jwxt_stu_id") {
            kv.getString(
                "jwxt_stu_id",
                initGetStringOrMigrate("jwxt_stu_id") { kv_init.getString("jwxt_stu_id", null) }
            )
        }
    }

    fun setGradeStudentProfiles(profiles: List<GradeStudentProfile>) {
        val data = Gson().toJson(profiles)
        userPutString("jwxt_student_profiles", data)
    }

    fun getGradeStudentProfiles(): List<GradeStudentProfile> {
        val data = userGetStringOrMigrate("jwxt_student_profiles") {
            kv.decodeString("jwxt_student_profiles")
        } ?: ""
        if (data.isBlank()) return emptyList()
        return data.fromJson(object : TypeToken<List<GradeStudentProfile>>() {}.type) ?: emptyList()
    }

    fun savePerProfileGrades(map: Map<GradeStudentProfile, Grade?>) {
        // Convert to Map<String, Grade?> keyed by id
        val idMap = map.mapKeys { it.key.id }
        val data = Gson().toJson(idMap)
        userPutString("per_profile_grades", data)
    }

    fun getPerProfileGrades(): Map<String, Grade?> {
        val data = userGetStringOrMigrate("per_profile_grades") {
            kv.decodeString("per_profile_grades")
        } ?: ""
        if (data.isBlank()) return emptyMap()
        return Gson().fromJson(data, object : TypeToken<Map<String, Grade?>>() {}.type) ?: emptyMap()
    }


    fun saveString(key: String ,value : String){
        userPutString(key, value)
    }

    fun saveRustCookies(cookiesJson: String) {
        if (cookiesJson.isEmpty()) {
            initRemove("rust_cookies_json")
        } else {
            initPutString("rust_cookies_json", cookiesJson)
        }
    }

    fun getRustCookies(): String {
        return initGetStringOrMigrate("rust_cookies_json") {
            kv_init.getString("rust_cookies_json", "")
        } ?: ""
    }


    fun isAgreementAccepted(): Boolean{
        userGetString("agreementAccepted")?.toBooleanStrictOrNull()?.let { return it }
        val value = kv.getBoolean("agreementAccepted",false)
        if (kv.containsKey("agreementAccepted")) userPutString("agreementAccepted", value.toString())
        return value
    }

    fun setAgreementAccepted(){
        userPutString("agreementAccepted", true.toString())
    }

    fun isPrivacyAccepted(): Boolean{
        userGetString("privacyAccepted")?.toBooleanStrictOrNull()?.let { return it }
        val value = kv.getBoolean("privacyAccepted",false)
        if (kv.containsKey("privacyAccepted")) userPutString("privacyAccepted", value.toString())
        return value
    }

    fun setPrivacyAccepted(){
        userPutString("privacyAccepted", true.toString())
    }

    fun isBusinessAccepted(): Boolean{
        userGetString("businessAccepted")?.toBooleanStrictOrNull()?.let { return it }
        val value = kv.getBoolean("businessAccepted",false)
        if (kv.containsKey("businessAccepted")) userPutString("businessAccepted", value.toString())
        return value
    }

    fun setBusinessAccepted(){
        userPutString("businessAccepted", true.toString())
    }

    fun getCardRechargeBank(): CardRechargeBank? {
        CardRechargeBank.fromStorage(userGetString("card_recharge_bank"))?.let { return it }
        CardRechargeBank.fromStorage(kv.decodeString("card_recharge_bank"))?.let { bank ->
            userPutString("card_recharge_bank", bank.storageValue)
            return bank
        }

        val legacyValue = userGetString("cmb_card_recharge_preferred")
            ?.toBooleanStrictOrNull()
            ?: if (kv.containsKey("cmb_card_recharge_preferred")) {
                kv.getBoolean("cmb_card_recharge_preferred", false)
            } else {
                null
            }
        return legacyValue?.let { preferred ->
            val bank = if (preferred) {
                CardRechargeBank.CHINA_MERCHANTS_BANK
            } else {
                CardRechargeBank.AGRICULTURAL_BANK
            }
            setCardRechargeBank(bank)
            bank
        }
    }

    fun setCardRechargeBank(bank: CardRechargeBank) {
        userPutString("card_recharge_bank", bank.storageValue)
        kv.putString("card_recharge_bank", bank.storageValue)
    }

    fun isCmbCardRechargePreferred(): Boolean =
        getCardRechargeBank() == CardRechargeBank.CHINA_MERCHANTS_BANK

    fun setCmbCardRechargePreferred(preferred: Boolean) {
        setCardRechargeBank(
            if (preferred) {
                CardRechargeBank.CHINA_MERCHANTS_BANK
            } else {
                CardRechargeBank.AGRICULTURAL_BANK
            }
        )
    }

    fun getElectricityController(): ElectricityController {
        val value = userGetStringOrMigrate("electricity_controller") {
            kv.decodeString("electricity_controller")
        }
        return ElectricityController.entries.firstOrNull { it.name == value }
            ?: ElectricityController.C
    }

    fun setElectricityController(controller: ElectricityController) {
        userPutString("electricity_controller", controller.name)
        kv.putString("electricity_controller", controller.name)
    }

    /**
     * 获取房间选择信息
     * @return RoomSelectionInfo?
     */
    fun getRoomSelection(): RoomSelectionInfo? {
        val data = userGetStringOrMigrate("room_selection_info") {
            kv.decodeString("room_selection_info")
        } ?: ""
        return data.fromJson(RoomSelectionInfo::class.java)
    }

    fun saveElectricityDepositHistory(history: List<ElectricityDepositHistoryItem>) {
        val data = Gson().toJson(history)
        userPutString("electricity_room_history", data)
    }

    fun getElectricityDepositHistory(): List<ElectricityDepositHistoryItem> {
        val data = userGetStringOrMigrate("electricity_room_history") {
            kv.decodeString("electricity_room_history")
                ?: initGetStringOrMigrate("electricity_room_history") {
                    kv_init.decodeString("electricity_room_history")
                }
        } ?: ""
        if (data.isEmpty()) return emptyList()
        return data.fromJson(object : TypeToken<List<ElectricityDepositHistoryItem>>() {}.type) ?: emptyList()
    }

    /**
     * 保存电费累计充值信息
     * @param info ElectricityChargeInfo
     */
    fun saveElectricityChargeInfo(info: ElectricityChargeInfo) {
        val data = Gson().toJson(info)
        userPutString("electricity_charge_acl", data)
    }

    /**
     * 获取电费累计充值信息
     * @return ElectricityChargeInfo?
     */
    fun getElectricityChargeInfo(): ElectricityChargeInfo? {
        val data = userGetStringOrMigrate("electricity_charge_acl") {
            kv.decodeString("electricity_charge_acl")
                ?: initGetStringOrMigrate("electricity_charge_acl") {
                    kv_init.decodeString("electricity_charge_acl")
                }
        } ?: ""
        if (data.isEmpty()) {
            return null
        }
        return data.fromJson(ElectricityChargeInfo::class.java)
    }

    /**
     * 清除电费累计充值信息
     */
    fun clearElectricityChargeInfo() {
        userRemove("electricity_charge_acl")
        kv.removeValueForKey("electricity_charge_acl")
    }

    /**
     * 保存房间选择信息
     * @param info RoomSelectionInfo
     */
    fun saveRoomSelection(info: RoomSelectionInfo) {
        val data = Gson().toJson(info)
        userPutString("room_selection_info", data)
    }

    /**
     * 保存一卡通余额
     * @param balance Double
     */
    fun saveCardBalance(balance: Double) {
        userPutString("card_balance", balance.toString())
    }

    /**
     * 获取一卡通余额
     * @return Double?
     */
    fun getCardBalance(): Double? {
        userGetString("card_balance")?.toDoubleOrNull()?.let { return it }
        if (!kv.containsKey("card_balance")) return null
        return kv.decodeDouble("card_balance").also {
            userPutString("card_balance", it.toString())
        }
    }

    fun getMockData(): Boolean {
        if (!BuildConfig.DEBUG) {
            mockDataCache = false
            return false
        }
        mockDataCache?.let { return it }
        val value = initGetString("mock_data")?.toBooleanStrictOrNull()
            ?: if (!kv.containsKey("mock_data")) {
                false
            } else {
                kv.decodeBool("mock_data").also {
                    initPutString("mock_data", it.toString())
                }
            }
        mockDataCache = value
        return value
    }

    fun setMockData(enable: Boolean) {
        if (!BuildConfig.DEBUG) {
            initRemove("mock_data")
            kv.removeValueForKey("mock_data")
            mockDataCache = false
            return
        }
        initPutString("mock_data", enable.toString())
        mockDataCache = enable
    }

    // === 天气 adcode 缓存（用于精准到区级） ===

    fun saveWeatherAdcode(adcode: String) {
        initPutString("weather_adcode", adcode)
    }

    fun getWeatherAdcode(): String? {
        return initGetStringOrMigrate("weather_adcode") {
            kv_init.decodeString("weather_adcode")
        }?.takeIf { it.isNotBlank() }
    }

    fun saveMockCurrentTimeMillis(value: Long) {
        if (!BuildConfig.DEBUG) return
        initPutString("mock_current_time_millis", value.toString())
        mockCurrentTimeCache = value
        mockCurrentTimeCacheInitialized = true
    }

    fun getMockCurrentTimeMillis(): Long? {
        if (!BuildConfig.DEBUG) return null
        if (mockCurrentTimeCacheInitialized) return mockCurrentTimeCache
        val value = initGetString("mock_current_time_millis")?.toLongOrNull()
            ?: if (!kv.containsKey("mock_current_time_millis")) {
                null
            } else {
                kv.decodeLong("mock_current_time_millis").also {
                    initPutString("mock_current_time_millis", it.toString())
                }
            }
        mockCurrentTimeCache = value
        mockCurrentTimeCacheInitialized = true
        return value
    }

    fun clearMockCurrentTimeMillis() {
        initRemove("mock_current_time_millis")
        kv.removeValueForKey("mock_current_time_millis")
        mockCurrentTimeCache = null
        mockCurrentTimeCacheInitialized = true
    }

    fun getGrayOverride(key: String): String? {
        return initGetString("gray_override_$key")?.takeIf { it.isNotBlank() }
    }

    fun setGrayOverride(key: String, value: String) {
        initPutString("gray_override_$key", value)
    }

    fun clearGrayOverride(key: String) {
        initRemove("gray_override_$key")
    }

    /**
     * 保存按 studentId 分组的 GPA 排名信息
     */
    fun saveGpaRankInfo(studentId: String, gpaRankInfo: GpaRankInfo) {
        val map = getGpaRankInfoMap().toMutableMap()
        map[studentId] = gpaRankInfo
        val data = Gson().toJson(map)
        userPutString("gpa_rank_info_map", data)
    }
    /**
     * 获取指定 studentId 的缓存 GPA 排名信息
     */
    fun getGpaRankInfo(studentId: String): GpaRankInfo? {
        return getGpaRankInfoMap()[studentId]
    }
    /**
     * 获取全量 GPA 排名缓存 Map（内部用）
     */
    private fun getGpaRankInfoMap(): Map<String, GpaRankInfo> {
        val data = userGetStringOrMigrate("gpa_rank_info_map") {
            kv.decodeString("gpa_rank_info_map")
        } ?: ""
        if (data.isBlank()) return emptyMap()
        return Gson().fromJson(data, object : TypeToken<Map<String, GpaRankInfo>>() {}.type) ?: emptyMap()
    }
    /**
     * 清除 GPA 排名缓存
     */
    fun clearGpaRankInfo() {
        userRemove("gpa_rank_info_map")
        kv.removeValueForKey("gpa_rank_info_map")
    }
    /**
     * 保存失物招领校区缓存
     */
    fun saveLostFoundCampus(campus: List<CampusItem>) {
        userPutString("lost_found_campus", Gson().toJson(campus))
    }

    /**
     * 获取失物招领校区缓存
     */
    fun getLostFoundCampus(): List<CampusItem> {
        val data = userGetStringOrMigrate("lost_found_campus") {
            kv.decodeString("lost_found_campus")
        } ?: ""

        if (data.isEmpty()) return emptyList()

        return data.fromJson(
            object : TypeToken<List<CampusItem>>() {}.type
        ) ?: emptyList()
    }

    /**
     * 保存失物招领类型缓存
     */
    fun saveLostFoundType(types: List<LostFoundTypeItem>) {
        userPutString("lost_found_type", Gson().toJson(types))
    }

    /**
     * 获取失物招领类型缓存
     */
    fun getLostFoundType(): List<LostFoundTypeItem> {
        val data = userGetStringOrMigrate("lost_found_type") {
            kv.decodeString("lost_found_type")
        } ?: ""

        if (data.isEmpty()) return emptyList()

        return data.fromJson(
            object : TypeToken<List<LostFoundTypeItem>>() {}.type
        ) ?: emptyList()
    }

    /**
     * 保存失物招领帖子缓存（按状态）
     */
    fun saveLostFoundList(
        state: Int,
        items: List<LostFoundItem>
    ) {
        userPutString("lost_found_list_$state", Gson().toJson(items))
    }

    /**
     * 获取失物招领帖子缓存（按状态）
     */
    fun getLostFoundList(
        state: Int
    ): List<LostFoundItem> {
        val key = "lost_found_list_$state"
        val data = userGetStringOrMigrate(key) { kv.decodeString(key) } ?: ""

        if (data.isEmpty()) return emptyList()

        return data.fromJson(
            object : TypeToken<List<LostFoundItem>>() {}.type
        ) ?: emptyList()
    }

    /**
     * 追加失物招领帖子缓存（分页）
     */
    fun appendLostFoundList(
        state: Int,
        newItems: List<LostFoundItem>
    ) {
        val oldList =
            getLostFoundList(state)

        val merged =
            oldList + newItems

        saveLostFoundList(
            state,
            merged
        )
    }

    /**
     * 清除指定状态帖子缓存
     */
    fun clearLostFoundList(
        state: Int
    ) {
        userRemove("lost_found_list_$state")
    }

    /**
     * 清除全部失物招领缓存
     */
    fun clearLostFoundCache() {
        userRemove("lost_found_campus")
        userRemove("lost_found_type")
        userRemove("lost_found_list_1")
        userRemove("lost_found_list_2")
    }

    /**
     * 天气首页显示设置
     */
    private const val WEATHER_SHOW_ON_HOME_KEY = "weather_show_on_home"
    private const val WEATHER_HOME_MODE_KEY = "weather_home_mode"
    private const val WEATHER_HOME_SHOW_TEMP_KEY = "weather_home_show_temp"
    private const val WEATHER_HOME_SHOW_WEATHER_KEY = "weather_home_show_weather"
    private const val WEATHER_HOME_SHOW_AQI_KEY = "weather_home_show_aqi"
    private const val WEATHER_HOME_SHOW_LOCATION_KEY = "weather_home_show_location"

    fun saveWeatherShowOnHome(enabled: Boolean) {
        userPutString(WEATHER_SHOW_ON_HOME_KEY, enabled.toString())
    }

    fun getWeatherShowOnHome(): Boolean {
        userGetString(WEATHER_SHOW_ON_HOME_KEY)?.toBooleanStrictOrNull()?.let { return it }
        val value = kv.decodeBool(WEATHER_SHOW_ON_HOME_KEY, false)
        if (kv.containsKey(WEATHER_SHOW_ON_HOME_KEY)) userPutString(WEATHER_SHOW_ON_HOME_KEY, value.toString())
        return value
    }

    fun saveWeatherHomeMode(mode: String) {
        userPutString(WEATHER_HOME_MODE_KEY, mode)
    }

    fun getWeatherHomeMode(): String {
        return userGetStringOrMigrate(WEATHER_HOME_MODE_KEY) {
            kv.decodeString(WEATHER_HOME_MODE_KEY)
        } ?: "detailed"
    }

    fun saveWeatherHomeShowTemp(enabled: Boolean) {
        userPutString(WEATHER_HOME_SHOW_TEMP_KEY, enabled.toString())
    }

    fun getWeatherHomeShowTemp(): Boolean {
        return getUserBooleanOrMigrate(WEATHER_HOME_SHOW_TEMP_KEY, true)
    }

    fun saveWeatherHomeShowWeather(enabled: Boolean) {
        userPutString(WEATHER_HOME_SHOW_WEATHER_KEY, enabled.toString())
    }

    fun getWeatherHomeShowWeather(): Boolean {
        return getUserBooleanOrMigrate(WEATHER_HOME_SHOW_WEATHER_KEY, true)
    }

    fun saveWeatherHomeShowAqi(enabled: Boolean) {
        userPutString(WEATHER_HOME_SHOW_AQI_KEY, enabled.toString())
    }

    fun getWeatherHomeShowAqi(): Boolean {
        return getUserBooleanOrMigrate(WEATHER_HOME_SHOW_AQI_KEY, true)
    }

    fun saveWeatherHomeShowLocation(enabled: Boolean) {
        userPutString(WEATHER_HOME_SHOW_LOCATION_KEY, enabled.toString())
    }

    fun getWeatherHomeShowLocation(): Boolean {
        return getUserBooleanOrMigrate(WEATHER_HOME_SHOW_LOCATION_KEY, true)
    }

    private fun getUserBooleanOrMigrate(key: String, defaultValue: Boolean): Boolean {
        userGetString(key)?.toBooleanStrictOrNull()?.let { return it }
        val value = kv.decodeBool(key, defaultValue)
        if (kv.containsKey(key)) userPutString(key, value.toString())
        return value
    }
}
