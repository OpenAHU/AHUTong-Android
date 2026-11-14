package com.ahu.ahutong.assistant.model

import android.content.Context
import com.ahu.ahutong.assistant.AssistantPreferences
import com.ahu.ahutong.core.common.AppEnvironmentHolder
import com.ahu.ahutong.data.security.SecureBoxStore
import com.google.gson.Gson
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 一条模型配置；API Key 不在这里，按 id 存在 Android Keystore 加密箱里。 */
data class AssistantModelProfile(
    val id: String = "",
    val name: String = "",
    val baseUrl: String = "",
    val model: String = "",
    val providerLabel: String = "自定义",
    val supportsVision: Boolean = false,
    val supportsThinking: Boolean = true,
    val addedAt: Long = 0L,
) {
    val displayName: String get() = name.ifBlank { model }
}

data class AssistantModelLibrarySnapshot(
    val activeId: String? = null,
    val profiles: List<AssistantModelProfile> = emptyList(),
) {
    val active: AssistantModelProfile?
        get() = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
}

/**
 * 本机模型库：多个 OpenAI 兼容配置，首条添加的为默认；切换是全局的。
 * Key 逐条落在 SecureBoxStore；列表本身只存非敏感元数据。
 */
object AssistantModelLibrary {

    private const val PREFS = "assistant_model_library"
    private const val KEY_SNAPSHOT = "snapshot_v1"
    private const val BOX = "assistant"
    private const val LEGACY_KEY = "model_config_v1"
    private const val KEY_PREFIX = "model_key_"

    private val gson = Gson()

    private val prefs by lazy {
        AppEnvironmentHolder.context().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private val _state by lazy { MutableStateFlow(readOrMigrate()) }
    val state: StateFlow<AssistantModelLibrarySnapshot> = _state.asStateFlow()

    fun snapshot(): AssistantModelLibrarySnapshot = state.value

    fun profiles(): List<AssistantModelProfile> = state.value.profiles

    fun activeProfile(): AssistantModelProfile? = state.value.active

    fun isConfigured(): Boolean = activeConfig() != null

    fun activeConfig(): AssistantModelConfig? {
        val profile = activeProfile() ?: return null
        val key = apiKey(profile.id) ?: return null
        return if (profile.id == OfficialTrial.PROFILE_ID) OfficialTrial.profile.toConfig(key) else profile.toConfig(key)
    }

    fun apiKey(id: String): String? =
        if (id == OfficialTrial.PROFILE_ID) OfficialTrial.apiKey() else
            runCatching { SecureBoxStore.get(BOX, KEY_PREFIX + id) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    fun hasApiKey(id: String): Boolean = apiKey(id) != null

    fun selectOfficialTrial(): Boolean {
        if (OfficialTrial.apiKey() == null) return false
        val current = state.value
        val profiles = current.profiles.filterNot { it.id == OfficialTrial.PROFILE_ID } + OfficialTrial.profile
        val next = current.copy(activeId = OfficialTrial.PROFILE_ID, profiles = profiles)
        persist(next)
        refreshSearchCapability(next)
        return true
    }

    fun add(profile: AssistantModelProfile, apiKey: String): String? {
        if (profile.id == OfficialTrial.PROFILE_ID) return null
        if (profile.baseUrl.isBlank() || profile.model.isBlank() || apiKey.isBlank()) return null
        // 先确定 id 再写 Key：首次新增时 profile.id 为空，否则 Key 会落到空 id 上。
        val id = profile.id.ifBlank { UUID.randomUUID().toString() }
        val persistedKey = runCatching {
            SecureBoxStore.put(BOX, KEY_PREFIX + id, apiKey)
        }
        if (persistedKey.isFailure) return null
        val now = System.currentTimeMillis()
        val entry = profile.copy(
            id = id,
            name = profile.name.ifBlank { profile.model },
            addedAt = if (profile.addedAt > 0) profile.addedAt else now,
        )
        val current = state.value
        val next = AssistantModelLibrarySnapshot(
            activeId = current.activeId ?: id,
            profiles = current.profiles + entry,
        )
        persist(next)
        refreshSearchCapability(next)
        return id
    }

    fun update(profile: AssistantModelProfile, apiKey: String?): Boolean {
        if (profile.id == OfficialTrial.PROFILE_ID) return false
        val current = state.value
        val index = current.profiles.indexOfFirst { it.id == profile.id }
        if (index < 0) return false
        if (profile.baseUrl.isBlank() || profile.model.isBlank()) return false
        if (!apiKey.isNullOrBlank()) {
            if (runCatching { SecureBoxStore.put(BOX, KEY_PREFIX + profile.id, apiKey) }.isFailure) {
                return false
            }
        }
        val existing = current.profiles[index]
        val updated = profile.copy(
            name = profile.name.ifBlank { profile.model },
            addedAt = existing.addedAt,
        )
        val next = current.copy(profiles = current.profiles.toMutableList().also { it[index] = updated })
        persist(next)
        refreshSearchCapability(next)
        return true
    }

    fun delete(id: String): Boolean {
        val current = state.value
        if (current.profiles.none { it.id == id }) return false
        runCatching { SecureBoxStore.remove(BOX, KEY_PREFIX + id) }
        val next = nextSnapshotAfterDelete(current, id)
        persist(next)
        refreshSearchCapability(next)
        return true
    }

    fun setActive(id: String): Boolean {
        if (id == OfficialTrial.PROFILE_ID) return selectOfficialTrial()
        val current = state.value
        if (current.profiles.none { it.id == id }) return false
        if (current.activeId == id) return true
        val next = current.copy(activeId = id)
        persist(next)
        refreshSearchCapability(next)
        return true
    }

    fun clear() {
        val current = state.value
        current.profiles.forEach { profile ->
            runCatching { SecureBoxStore.remove(BOX, KEY_PREFIX + profile.id) }
        }
        runCatching { prefs.edit().remove(KEY_SNAPSHOT).apply() }
        _state.value = AssistantModelLibrarySnapshot()
        AssistantPreferences.setSearchEnabled(false)
    }

    /** 兼容旧调用：当前模型存在则更新，否则新增一条。 */
    fun saveActive(config: AssistantModelConfig) {
        val existing = activeProfile()
        if (existing == null || existing.id == OfficialTrial.PROFILE_ID) {
            add(
                AssistantModelProfile(
                    name = config.model,
                    baseUrl = config.baseUrl,
                    model = config.model,
                    providerLabel = providerLabelFor(config.baseUrl, config.model),
                    supportsVision = config.supportsVision,
                    supportsThinking = config.supportsThinking,
                    addedAt = System.currentTimeMillis(),
                ),
                config.apiKey,
            )
        } else {
            update(
                existing.copy(
                    baseUrl = config.baseUrl,
                    model = config.model,
                    supportsVision = config.supportsVision,
                    supportsThinking = config.supportsThinking,
                ),
                config.apiKey,
            )
        }
    }

    private fun persist(snapshot: AssistantModelLibrarySnapshot) {
        runCatching {
            prefs.edit().putString(KEY_SNAPSHOT, gson.toJson(snapshot)).apply()
        }
        _state.value = snapshot
    }

    private fun refreshSearchCapability(snapshot: AssistantModelLibrarySnapshot) {
        val active = snapshot.active ?: run {
            AssistantPreferences.setSearchEnabled(false)
            return
        }
        val supports = active.baseUrl.contains("deepseek.com", ignoreCase = true)
        if (!supports) AssistantPreferences.setSearchEnabled(false)
    }

    private fun readOrMigrate(): AssistantModelLibrarySnapshot {
        val raw = runCatching { prefs.getString(KEY_SNAPSHOT, null) }.getOrNull()
        if (!raw.isNullOrBlank()) {
            val parsed = runCatching {
                gson.fromJson(raw, AssistantModelLibrarySnapshot::class.java)
            }.getOrNull()
            if (parsed != null) return normalizeSnapshot(parsed)
        }
        return migrateLegacy()
    }

    private fun migrateLegacy(): AssistantModelLibrarySnapshot {
        val legacy = runCatching { SecureBoxStore.get(BOX, LEGACY_KEY) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return AssistantModelLibrarySnapshot()
        val config = runCatching { gson.fromJson(legacy, AssistantModelConfig::class.java) }
            .getOrNull()
            ?.takeIf { it.baseUrl.isNotBlank() && it.model.isNotBlank() && it.apiKey.isNotBlank() }
            ?: return AssistantModelLibrarySnapshot()
        val id = "legacy-" + UUID.randomUUID().toString().take(8)
        runCatching { SecureBoxStore.put(BOX, KEY_PREFIX + id, config.apiKey) }
        val profile = AssistantModelProfile(
            id = id,
            name = config.model,
            baseUrl = config.baseUrl,
            model = config.model,
            providerLabel = providerLabelFor(config.baseUrl, config.model),
            supportsVision = guessSupportsVision(config.baseUrl, config.model),
            supportsThinking = guessSupportsThinking(config.baseUrl, config.model),
            addedAt = System.currentTimeMillis(),
        )
        val snapshot = AssistantModelLibrarySnapshot(activeId = id, profiles = listOf(profile))
        runCatching {
            prefs.edit().putString(KEY_SNAPSHOT, gson.toJson(snapshot)).apply()
        }
        return snapshot
    }
}

internal fun normalizeSnapshot(snapshot: AssistantModelLibrarySnapshot): AssistantModelLibrarySnapshot {
    val profiles = snapshot.profiles
        .filter { it.id.isNotBlank() && it.baseUrl.isNotBlank() && it.model.isNotBlank() }
        .sortedBy { it.addedAt }
    if (profiles.isEmpty()) return AssistantModelLibrarySnapshot()
    val activeId = profiles.firstOrNull { it.id == snapshot.activeId }?.id ?: profiles.first().id
    return AssistantModelLibrarySnapshot(activeId = activeId, profiles = profiles)
}

/** 删除后：若删的是当前项，回退到列表顺位的第一条。 */
internal fun nextSnapshotAfterDelete(
    snapshot: AssistantModelLibrarySnapshot,
    id: String,
): AssistantModelLibrarySnapshot {
    val remaining = snapshot.profiles.filterNot { it.id == id }
        .sortedBy { it.addedAt }
    if (remaining.isEmpty()) return AssistantModelLibrarySnapshot()
    val activeId = snapshot.activeId
        ?.takeIf { candidate -> remaining.any { it.id == candidate } }
        ?: remaining.first().id
    return AssistantModelLibrarySnapshot(activeId = activeId, profiles = remaining)
}

internal fun AssistantModelProfile.toConfig(apiKey: String): AssistantModelConfig =
    AssistantModelConfig(
        baseUrl = baseUrl,
        model = model,
        apiKey = apiKey,
        supportsVision = supportsVision,
        supportsThinking = supportsThinking,
        providerLabel = providerLabel,
    )
