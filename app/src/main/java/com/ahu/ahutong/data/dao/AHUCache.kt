package com.ahu.ahutong.data.dao

import com.ahu.ahutong.core.common.AppEnvironmentHolder
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
import com.ahu.ahutong.data.security.SecureBoxStore

import com.ahu.ahutong.data.session.SessionStore
import com.ahu.ahutong.data.session.SecureCredentialVault
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
        MMKV.initialize(AppEnvironmentHolder.context())
    }

    private val kv_init: MMKV = MMKV.mmkvWithID("ahu")


    private val academicTypeFlow = kotlinx.coroutines.flow.MutableStateFlow<
        com.ahu.ahutong.data.model.AcademicAccountType?
    >(null)
    private val gmisCacheLock = Any()

    private val postgraduateWeekRevision = kotlinx.coroutines.flow.MutableStateFlow(0L)
    fun postgraduateWeekUpdates(): kotlinx.coroutines.flow.StateFlow<Long> = postgraduateWeekRevision

    fun academicTypeUpdates(): kotlinx.coroutines.flow.StateFlow<com.ahu.ahutong.data.model.AcademicAccountType?> {
        getCurrentUser()
        return academicTypeFlow
    }

    fun getCurrentUser(): User? = SessionStore.currentUser().also {
        academicTypeFlow.value = it?.academicAccountType
    }

    fun isLogin(): Boolean = getCurrentUser() != null

    fun canUseUndergraduateAcademics(): Boolean =
        getMockData() || getCurrentUser()?.academicAccountType !=
            com.ahu.ahutong.data.model.AcademicAccountType.POSTGRADUATE

    fun canOpenRoute(route: String?): Boolean =
        getMockData() || com.ahu.ahutong.data.model.AcademicFeatureAccess.allowsRoute(
            getCurrentUser()?.academicAccountType, route
        )

    @Volatile
    private var mockDataCache: Boolean? = null
    @Volatile
    private var mockCurrentTimeCacheInitialized = false
    @Volatile
    private var mockCurrentTimeCache: Long? = null

    private val kv: MMKV
        get() {
            val user = SessionStore.currentUser()
            return  if (user != null && !user.xh.isNullOrEmpty()) {
                MMKV.mmkvWithID("ahu_${user.xh}")
            } else {
                MMKV.mmkvWithID("ahu_guest")
            }
        }

    private const val INIT_BOX = "init"

    /**
     * 用户分箱名。命名规则与迁移链统一收口在 [SecureBoxStore]，
     * 避免"缓存层用一份 sanitize、安全层用另一份"这种隐性错位。
     */
    private fun userBoxName(userId: String? = SessionStore.currentUser()?.xh): String =
        SecureBoxStore.userBox(userId)

    private fun initPutString(key: String, value: String) {
        SecureBoxStore.put(key, value)
    }

    private fun initGetString(key: String): String? {
        return SecureBoxStore.get(key)
    }

    private fun initGetStringOrMigrate(key: String, fallback: () -> String?): String? {
        return SecureBoxStore.getOrMigrate(key, fallback)
    }

    private fun initRemove(key: String) {
        SecureBoxStore.remove(key)
    }

    private fun userPutString(key: String, value: String) {
        SecureBoxStore.put(userBoxName(), key, value)
    }

    private fun userGetString(key: String): String? =
        SecureBoxStore.get(userBoxName(), key)

    private fun userGetStringOrMigrate(key: String, fallback: () -> String?): String? =
        SecureBoxStore.getOrMigrate(userBoxName(), key, fallback)

    private fun userRemove(key: String) {
        SecureBoxStore.remove(userBoxName(), key)
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
        SessionStore.clearPersistedCurrentUser()
        academicTypeFlow.value = null
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
        SessionStore.persistCurrentUser(user)
        academicTypeFlow.value = user.academicAccountType
        homeWidgetSlotsCache = null
    }

    /**
     * 清除本地登陆状态
     */
    fun clearCurrentUser() {
        SessionStore.clearPersistedCurrentUser()
        academicTypeFlow.value = null
        homeWidgetSlotsCache = null
    }

    fun getPostgraduateWeekStart(termCode: String): String? =
        userGetString(com.ahu.ahutong.data.schedule.PostgraduateTeachingWeek.storageKey(termCode))

    fun savePostgraduateWeekStart(termCode: String, firstMonday: String) {
        require(com.ahu.ahutong.data.schedule.PostgraduateTeachingWeek.parseStored(firstMonday) != null)
        userPutString(
            com.ahu.ahutong.data.schedule.PostgraduateTeachingWeek.storageKey(termCode),
            firstMonday
        )
        postgraduateWeekRevision.value += 1
    }

    fun getGmisScheduleCache(accountId: String, part: String): String? = synchronized(gmisCacheLock) {
        if (getCurrentUser()?.xh != accountId) null else userGetString("gmis.schedule.v1.$part")
    }

    fun saveGmisScheduleCache(accountId: String, part: String, value: String) {
        synchronized(gmisCacheLock) {
            check(getCurrentUser()?.xh == accountId) { "Graduate timetable account changed" }
            userPutString("gmis.schedule.v1.$part", value)
        }
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
        val userId = SessionStore.currentUser()?.xh
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

    /** 该布局族是否有用户实际存过的槽位配置（区别于默认兜底）。用于布局族收敛时的一次性迁移。 */
    fun hasStoredHomeWidgetSlots(layoutFamily: HomeWidgetLayoutFamily): Boolean {
        val key = homeWidgetSlotsKey(layoutFamily)
        val data = userGetStringOrMigrate(key) { kv.decodeString(key) }
        return !data.isNullOrBlank()
    }

    fun saveHomeWidgetSlots(
        layoutFamily: HomeWidgetLayoutFamily,
        slots: List<String?>
    ) {
        val normalizedSlots = normalizeHomeWidgetSlots(layoutFamily, slots)
        val data = Gson().toJson(normalizedSlots)
        userPutString(homeWidgetSlotsKey(layoutFamily), data)
        homeWidgetSlotsCache = HomeWidgetSlotsCache(
            SessionStore.currentUser()?.xh,
            layoutFamily,
            normalizedSlots
        )
    }

    // ---- 主页网格 v2（统一布局族：全主题一份配置） ----

    private const val HOME_GRID_V2_ICONS_KEY = "home_grid_v2_icons"
    private const val HOME_GRID_V2_CAMPUS_SPAN_KEY = "home_grid_v2_campus_span"

    /**
     * 读取网格 v2 功能槽（7 槽）。首次读取时从旧布局族一次性迁移
     * （优先 RADIANT 配置，其次 CLASSIC；旧 key 保留不删，可回退）。
     */
    fun getHomeGridIconsV2(): List<String?> {
        val existing = userGetStringOrMigrate(HOME_GRID_V2_ICONS_KEY) {
            kv.decodeString(HOME_GRID_V2_ICONS_KEY)
        }
        if (!existing.isNullOrBlank()) {
            return runCatching {
                Gson().fromJson<List<String?>>(
                    existing,
                    object : TypeToken<List<String?>>() {}.type
                )
            }.getOrNull().orEmpty()
        }
        val migrated = getHomeWidgetSlots(HomeWidgetLayoutFamily.RADIANT)
            .ifEmpty { getHomeWidgetSlots(HomeWidgetLayoutFamily.CLASSIC) }
            .filterNotNull()
            .take(7)
        val migratedPadded = migrated + List(7 - migrated.size) { null }
        saveHomeGridIconsV2(migratedPadded)
        return migratedPadded
    }

    fun saveHomeGridIconsV2(icons: List<String?>) {
        userPutString(HOME_GRID_V2_ICONS_KEY, Gson().toJson(icons))
    }

    /** 校园卡形态（"2x2"/"1x4"，存取字符串，解析在 ui 层 CampusSpan）。 */
    fun getCampusCardSpanV2(): String =
        userGetStringOrMigrate(HOME_GRID_V2_CAMPUS_SPAN_KEY) {
            kv.decodeString(HOME_GRID_V2_CAMPUS_SPAN_KEY)
        } ?: "2x2"

    fun saveCampusCardSpanV2(span: String) {
        userPutString(HOME_GRID_V2_CAMPUS_SPAN_KEY, span)
    }

    fun logout() {
        val userId = SessionStore.currentUser()?.xh
        val boxName = userBoxName(userId)
        val currentUserKv = if (userId.isNullOrEmpty()) {
            MMKV.mmkvWithID("ahu_guest")
        } else {
            MMKV.mmkvWithID("ahu_$userId")
        }
        SecureStorage.clearPrefix("$boxName.")
        RustSDK.kvClearBoxSafe(boxName)
        currentUserKv.clearAll()
        SecureCredentialVault.clearWisdomPassword()
        SessionStore.saveRustCookies("")
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

    /** 法律同意是设备级决策（合规语义「本机已同意」），存全局箱——
     *  用户分箱会导致登录前同意、登录后换箱读不到而重复弹窗。 */
    fun isAgreementAccepted(): Boolean{
        initGetString("agreementAccepted")?.toBooleanStrictOrNull()?.let { return it }
        // 迁移兜底：旧版本存在用户箱 / MMKV 里的记录
        userGetString("agreementAccepted")?.toBooleanStrictOrNull()?.let {
            initPutString("agreementAccepted", it.toString())
            return it
        }
        val value = kv.getBoolean("agreementAccepted",false)
        if (kv.containsKey("agreementAccepted")) initPutString("agreementAccepted", value.toString())
        return value
    }

    fun setAgreementAccepted(){
        initPutString("agreementAccepted", true.toString())
    }

    /** 法律同意是设备级决策（合规语义「本机已同意」），存全局箱——
     *  用户分箱会导致登录前同意、登录后换箱读不到而重复弹窗。 */
    fun isPrivacyAccepted(): Boolean{
        initGetString("privacyAccepted")?.toBooleanStrictOrNull()?.let { return it }
        // 迁移兜底：旧版本存在用户箱 / MMKV 里的记录
        userGetString("privacyAccepted")?.toBooleanStrictOrNull()?.let {
            initPutString("privacyAccepted", it.toString())
            return it
        }
        val value = kv.getBoolean("privacyAccepted",false)
        if (kv.containsKey("privacyAccepted")) initPutString("privacyAccepted", value.toString())
        return value
    }

    fun setPrivacyAccepted(){
        initPutString("privacyAccepted", true.toString())
    }

    /** 隐私政策版本：政策更新后递增 CURRENT_PRIVACY_POLICY_VERSION 触发重新征得同意。 */
    fun privacyPolicyVersion(): Int =
        initGetString("privacyPolicyVersion")?.toIntOrNull() ?: 0

    fun savePrivacyPolicyVersion(version: Int) {
        initPutString("privacyPolicyVersion", version.toString())
    }

    /** 法律同意是设备级决策（合规语义「本机已同意」），存全局箱——
     *  用户分箱会导致登录前同意、登录后换箱读不到而重复弹窗。 */
    fun isBusinessAccepted(): Boolean{
        initGetString("businessAccepted")?.toBooleanStrictOrNull()?.let { return it }
        // 迁移兜底：旧版本存在用户箱 / MMKV 里的记录
        userGetString("businessAccepted")?.toBooleanStrictOrNull()?.let {
            initPutString("businessAccepted", it.toString())
            return it
        }
        val value = kv.getBoolean("businessAccepted",false)
        if (kv.containsKey("businessAccepted")) initPutString("businessAccepted", value.toString())
        return value
    }

    fun setBusinessAccepted(){
        initPutString("businessAccepted", true.toString())
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
