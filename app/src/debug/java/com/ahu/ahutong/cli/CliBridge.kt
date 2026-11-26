package com.ahu.ahutong.cli

import android.content.Context
import android.content.Intent
import com.ahu.ahutong.BuildConfig
import com.ahu.ahutong.MainActivity
import com.ahu.ahutong.ShortcutRoutes
import com.ahu.ahutong.assistant.*
import com.ahu.ahutong.assistant.capabilities.AssistantCapabilities
import com.ahu.ahutong.assistant.model.AssistantModelConfig
import com.ahu.ahutong.assistant.model.AssistantModelConfigStore
import com.ahu.ahutong.assistant.model.AssistantModelLibrary
import com.ahu.ahutong.assistant.safety.AssistantApprovalGate
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.crawler.gmis.PostgraduateScheduleRepository
import com.ahu.ahutong.data.crawler.net.SessionRefreshCoordinator
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.laundry.*
import com.ahu.ahutong.data.mail.StudentMailRepository
import com.ahu.ahutong.data.mail.MailDraft
import com.ahu.ahutong.data.mail.StudentMailDownload
import com.ahu.ahutong.data.notes.NoteSource
import com.ahu.ahutong.data.model.LoginOutcome
import com.ahu.ahutong.data.notes.NoteStore
import com.ahu.ahutong.data.notice.CampusNoticeRepository
import com.ahu.ahutong.data.repository.RepositoryManager
import com.ahu.ahutong.data.schedule.CustomCourse
import com.ahu.ahutong.data.session.DefaultAhuSession
import com.ahu.ahutong.data.session.SavedAccounts
import com.ahu.ahutong.data.session.SessionStore
import com.ahu.ahutong.data.session.SecureCredentialVault
import com.ahu.ahutong.data.wall.CampusWallRepository
import com.ahu.ahutong.data.xuexiaotong.ChaoxingApi
import com.ahu.ahutong.data.xuexiaotong.Store
import com.ahu.ahutong.personalization.action.AppActionCatalog
import com.ahu.ahutong.ui.plugin.PluginRegistry
import com.ahu.ahutong.ui.screen.main.home.HomeWidgetRegistry
import com.ahu.ahutong.ui.state.RepositoryCustomCourseStore
import com.ahu.ahutong.ui.state.RepositoryScheduleWeekConfig
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.collect
import java.io.File
import android.net.Uri

internal class CliBridge(private val context: Context) {
    private val gson = Gson()
    private val approvals = CliApprovals()
    private val weekConfig = RepositoryScheduleWeekConfig()
    private val customCourses = RepositoryCustomCourseStore()
    private val capabilities by lazy {
        AssistantCapabilityRegistry(AssistantCapabilities.all(
            config = weekConfig::localSnapshot, cachedSchedule = AHURepository::getCachedSchedule
        ))
    }
    private val mail by lazy { StudentMailRepository() }
    private var mailOwner: Long? = null
    private val wall = CampusWallRepository()
    private val laundryTable by lazy { LaundryDeviceTable(context) }
    private val laundryToken = LaundryTokenStore()
    private val settings by lazy { CliSettings(context) }
    private val chaoxing by lazy { ChaoxingApi(context) }
    private val longheLaundry = QiekjLaundrySource()

    private data class Operation(
        val name: String, val description: String, val schema: JsonObject,
        val write: Boolean, val secret: Boolean,
        val execute: suspend (JsonObject, String?) -> Any?
    )

    private val operations by lazy { buildOperations().associateBy { it.name } }

    suspend fun dispatch(request: JsonObject): Any {
        if (request.get("version")?.asInt != 1) return failure("protocol_version", "Expected protocol version 1")
        val name = request.get("command")?.asString ?: return failure("invalid_request", "Missing command")
        if (name == "catalog") return success(catalog())
        val args = request.getAsJsonObject("arguments") ?: JsonObject()
        val tool = capabilities.find(name)
        val operation = operations[name]
        if (tool == null && operation == null) return failure("unknown_command", "Use catalog to list commands")
        val schema = tool?.spec?.parameters ?: requireNotNull(operation).schema
        try { CliSchema.validate(schema, args) }
        catch (_: IllegalArgumentException) { return failure("invalid_arguments", "Arguments do not match command schema") }
        val write = tool?.spec?.requiresConfirmation == true || operation?.write == true
        val needsSecret = tool?.spec?.requiresPaymentPassword == true || operation?.secret == true
        val owner = SessionRefreshCoordinator.currentIdentityGeneration()
        val academicGeneration = SessionRefreshCoordinator.currentGeneration()
        val identity = "$name\n$args"
        if (write && request.get("phase")?.asString == "prepare") {
            return success(mapOf(
                "confirmation" to approvals.prepare(identity, owner), "expiresIn" to 120,
                "command" to name, "arguments" to args, "requiresSecret" to needsSecret,
                "description" to (tool?.describe(args) ?: operation?.description ?: tool?.spec?.description)
            ))
        }
        if (write && !approvals.consume(request.get("confirmation")?.asString.orEmpty(), identity, owner)) {
            return failure("confirmation_required", "Prepare and confirm this exact request; confirmations expire and cannot be reused")
        }
        val secret = request.get("secret")?.asString
        if (needsSecret && secret.isNullOrBlank()) return failure("secret_required", "Provide the secret through an interactive prompt or environment variable")
        val response = if (tool != null) {
            when (val outcome = if (write) tool.executeWithSecret(args, secret) else tool.execute(args)) {
                is AssistantOutcome.Success -> success(mapOf("mode" to if (tool.spec.kind == AssistantCapabilityKind.NAVIGATE) "navigation" else "business", "result" to outcome.answer))
                is AssistantOutcome.Failure -> failure(outcome.code, outcome.message)
            }
        } else {
            val op = requireNotNull(operation)
            var result: Any? = null
            if (name in LOCAL_WRITES) {
                // Only local, non-network actions may hold this barrier. Login/HTTP actions
                // need the same coordinator for renewal and must never execute under it.
                var accepted = false
                val committed = SessionRefreshCoordinator.commitIfCurrent(academicGeneration) {
                    if (owner == SessionRefreshCoordinator.currentIdentityGeneration()) {
                        accepted = true
                        result = op.execute(args, secret)
                    }
                }
                if (!committed || !accepted) return failure("account_changed", "Account changed before the local write")
            } else {
                result = op.execute(args, secret)
            }
            success(mapOf("mode" to if (name == "open") "android-ui" else "business", "result" to result))
        }
        if (name !in setOf("login", "logout") && owner != SessionRefreshCoordinator.currentIdentityGeneration()) {
            return failure("account_changed", "Account changed during the request; verify any write before retrying")
        }
        return response
    }

    private fun catalog(): Map<String, Any?> = mapOf(
        "protocol" to 1, "version" to BuildConfig.VERSION_NAME, "transport" to "adb-debug-companion",
        "commands" to capabilities.all.map { capability ->
            mapOf("name" to capability.spec.id, "description" to capability.spec.description,
                "schema" to capability.spec.parameters, "write" to capability.spec.requiresConfirmation,
                "secret" to capability.spec.requiresPaymentPassword, "mode" to capability.spec.kind.name.lowercase())
        } + operations.values.map { op ->
            mapOf("name" to op.name, "description" to op.description, "schema" to op.schema,
                "write" to op.write, "secret" to op.secret, "mode" to if (op.name == "open") "android-ui" else "business")
        },
        "pages" to routes().sorted().map { mapOf("route" to it, "mode" to "android-ui") },
        "widgets" to HomeWidgetRegistry.widgets.map { mapOf("id" to it.id, "title" to it.title, "route" to it.route) },
        "limitations" to listOf("Requires the debug companion and authorized ADB", "Hardware and external-app features use Android UI", "Business commands reuse the active device account", "AI chat cannot approve writes; invoke write commands explicitly")
    )

    private fun routes(): Set<String> = AppActionCatalog.specs.mapNotNull { it.route }
        .map { if (it == "repository/{path}") "repository_dir?path={path}" else it }.toSet() +
        HomeWidgetRegistry.widgets.map { it.route } + setOf("camera", "profile", "widgets", "billing", "billing_stats",
            "canteen_footprint", "campus_circle/hot", "electricity_recent_rooms", "electricity_alert_settings",
            "settings__electricity_alert", "settings__about", "settings__plugins", "settings__service_status",
            "settings__privacy_general", "settings__license", "settings__contributors", "settings__theme_lab",
            "settings__theme_color", "settings__ai_official_trial", "repository_downloads", "repository_settings", "debug", "info",
            "custom_courses", "custom_courses?editId={editId}", "repository_dir", "repository_dir?path={path}")

    private fun open(route: String): Any {
        require(route.length <= 2048 && !route.any(Char::isISOControl))
        require(routes().any { template ->
            val pattern = template.split(Regex("\\{[^}]+}"))
                .joinToString("[A-Za-z0-9_.%-]+") { Regex.escape(it) }
            Regex(pattern).matches(route)
        }) { "Unknown page" }
        require(AHUCache.canOpenRoute(route)) { "Unavailable for this account type" }
        ShortcutRoutes.publishFromCli(route)
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return mapOf("requestedRoute" to route, "status" to "requested", "note" to "Login and OS permission gates still apply")
    }

    private suspend fun mailReady(): StudentMailRepository {
        val generation = SessionRefreshCoordinator.currentIdentityGeneration()
        if (mailOwner != generation) {
            mail.disconnect()
            mailOwner = null
            mail.connect()
            check(generation == SessionRefreshCoordinator.currentIdentityGeneration())
            mailOwner = generation
        }
        return mail
    }

    private fun buildOperations(): List<Operation> = buildList {
        fun op(name: String, description: String, fields: String = "", write: Boolean = false, secret: Boolean = false,
               body: suspend (JsonObject, String?) -> Any?) {
            add(Operation(name, description, CliSchema.of(fields), write, secret, body))
        }
        op("status", "Companion, account and protocol status") { _, _ ->
            mapOf("package" to context.packageName, "version" to BuildConfig.VERSION_NAME,
                "loggedIn" to SessionStore.isLoggedIn(), "user" to SessionStore.currentUser(),
                "session" to DefaultAhuSession.state.value.toString())
        }
        op("open", "Request an Android page; this does not execute the page's business action", "route:string") { a, _ -> open(a.string("route")) }
        op("accounts", "List account names and IDs, never credentials") { _, _ -> SavedAccounts.accounts() }
        op("login", "Sign in using the existing session workflow; complete web challenges on Android", "username:string", write = true, secret = true) { a, secret ->
            check(!SessionStore.isLoggedIn()) { "Sign out before changing account" }
            val result = DefaultAhuSession.signIn(a.string("username"), requireNotNull(secret))
            val outcome = result.value()
            if (outcome is LoginOutcome.Success) {
                try {
                    SecureCredentialVault.saveWisdomPassword(secret)
                    AHUCache.saveCurrentUser(outcome.user)
                } catch (error: Exception) {
                    DefaultAhuSession.signOut()
                    throw error
                }
            }
            mapOf("outcome" to when (outcome) {
                is LoginOutcome.Success -> "authenticated"
                is LoginOutcome.JwxtWebVerificationRequired -> "web_verification_required"
            }, "note" to "Open the app to finish any remaining profile or web verification steps")
        }
        op("logout", "Sign out and clear active service sessions", write = true) { _, _ -> DefaultAhuSession.signOut(); true }
        op("account-forget", "Forget one saved account", "username:string", write = true) { a, _ -> SavedAccounts.forget(a.string("username")); true }
        op("student-info", "Student profile", "refresh?:boolean") { a, _ -> AHURepository.getStudentInfo(a.bool("refresh")).value() }
        op("dormitory", "Dormitory information", "refresh?:boolean") { a, _ -> AHURepository.getDormitoryInfo(a.bool("refresh")).value() }
        op("schedule-raw", "Raw semester courses", "refresh?:boolean") { a, _ -> AHURepository.getSchedule(a.bool("refresh")).value() }
        op("schedule-next", "Next semester courses", "refresh?:boolean") { a, _ -> AHURepository.getNextSchedule(a.bool("refresh")).value() }
        op("schedule-terms", "Available undergraduate semesters") { _, _ -> AHURepository.getScheduleSemesters().value() }
        op("schedule-term", "Courses for an undergraduate semester", "id:integer") { a, _ -> AHURepository.getSemesterSchedule(a.int("id")).value() }
        op("schedule-config", "Resolve teaching week using the same source as the app") { _, _ -> weekConfig.resolveLocalFirst() }
        op("gmis-schedule", "Graduate timetable, optionally refresh", "refresh?:boolean term?:string") { a, _ ->
            val id = requireNotNull(SessionStore.currentUser()?.xh)
            if (a.bool("refresh")) PostgraduateScheduleRepository.instance.refresh(id, a.get("term")?.asString)
            else PostgraduateScheduleRepository.instance.current(id)
        }
        op("custom-courses", "Current semester custom courses") { _, _ -> customCourses.currentCourses() }
        op("custom-courses-replace", "Replace the current semester's local custom courses", "courses:array", write = true) { a, _ ->
            val courses = a.getAsJsonArray("courses").map { gson.fromJson(it, CustomCourse::class.java) }
            require(courses.all { it.normalized() != null } && courses.map { it.id }.distinct().size == courses.size)
            customCourses.saveCurrent(courses); customCourses.currentCourses()
        }
        op("grade-profiles", "Academic student profiles") { _, _ -> AHURepository.getGradeStudentProfiles() }
        op("card-info", "Campus card account information") { _, _ -> AHURepository.getCardInfo().value() }
        op("payment-qr", "Current campus payment QR payload; treat output as sensitive") { _, _ -> AHURepository.getQrcode().value() }
        op("bills-raw", "Paginated campus card transactions", "page?:integer size?:integer from?:string to?:string type?:integer") { a, _ ->
            AHURepository.getBillPage(a.int("page", 1).coerceIn(1, 10000), a.int("size", 20).coerceIn(1, 100),
                a.get("from")?.asString, a.get("to")?.asString, a.get("type")?.asInt).value()
        }
        op("bills-summary", "Income and expense totals", "from:string to:string") { a, _ -> AHURepository.getBillSummary(a.string("from"), a.string("to")).value() }
        op("bathrooms", "Bathroom accounts and facilities") { _, _ -> AHURepository.getBathRooms().value() }
        op("bathroom-account", "Bathroom account lookup", "bathroom:string phone:string") { a, _ -> AHURepository.getBathroomInfo(a.string("bathroom"), a.string("phone")).value() }
        op("calendar-years", "Available school calendars") { _, _ -> AHURepository.getSchoolCalendarYears().value() }
        op("calendar-export", "Export a school calendar for host download", "year:string") { a, _ ->
            val (id, file) = CliFiles.create(context)
            val response = AHURepository.getSchoolCalendar(a.string("year")).value()
            check(response.isSuccessful)
            requireNotNull(response.body()).use { body -> file.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } } }
            CliFiles.info(context, id)
        }
        op("wall-feed", "Read-only campus wall feed", "limit?:integer beforeTs?:integer beforeId?:integer") { a, _ ->
            wall.feed(a.int("limit", 20).coerceIn(1, 50), a.get("beforeTs")?.asLong, a.get("beforeId")?.asLong).value()
        }
        op("wall-hot", "Read-only campus wall hot topics") { _, _ -> wall.hot().value() }
        op("wall-status", "Campus wall service status") { _, _ -> wall.status().value() }
        op("notices-sync", "Request account-scoped notice synchronization") { _, _ -> CampusNoticeRepository.requestSyncIfDue(force = true); mapOf("status" to "requested") }
        op("notices-snapshot", "Current account's cached notices") { _, _ -> CampusNoticeRepository.snapshot.value?.takeIf { it.accountId == SessionStore.currentUser()?.xh } }
        op("notices-read", "Mark a notice as read", "id:string", write = true) { a, _ -> CampusNoticeRepository.markRead(requireNotNull(SessionStore.currentUser()?.xh), a.string("id")); true }
        op("resources", "Browse the learning resource index", "path?:string refresh?:boolean") { a, _ -> RepositoryManager.getContents(a.string("path", ""), a.bool("refresh")) }
        op("resource-document", "Read a Markdown resource", "path:string") { a, _ -> RepositoryManager.getMarkdownDocument(a.string("path")) }
        op("resource-download", "Download to the device's selected resource directory", "path:string", write = true) { a, _ -> requireNotNull(RepositoryManager.downloadFile(a.string("path"), context)) }
        op("resource-downloads", "Device resource download records") { _, _ -> RepositoryManager.getDownloadedFiles(context) }
        op("resource-export", "Export an already downloaded resource to the host", "path:string") { a, _ ->
            val source = requireNotNull(RepositoryManager.getDownloadedFile(a.string("path"), context))
            val (id, file) = CliFiles.create(context)
            val input = if (source.uri != null) requireNotNull(context.contentResolver.openInputStream(Uri.parse(source.uri))) else File(source.localPath).inputStream()
            input.use { stream -> file.outputStream().use { stream.copyTo(it) } }
            CliFiles.info(context, id)
        }
        op("resource-delete", "Delete a downloaded resource from the device", "path:string", write = true) { a, _ -> RepositoryManager.deleteFile(a.string("path"), context) }
        op("resource-sources", "Test resource acceleration sources") { _, _ -> RepositoryManager.testAccelerationSources(force = true) }
        op("laundry-devices", "Qingyuan laundry device table", "refresh?:boolean") { a, _ -> if (a.bool("refresh")) laundryTable.refreshFromNetwork(); laundryTable.load() }
        op("laundry-status", "Qingyuan occupancy for one park and machine kind", "park:integer kind:string") { a, _ ->
            val devices = laundryTable.load().devices.filter { it.parkIndex == a.int("park") && it.kind.wire == a.string("kind") }.take(40)
            require(devices.isNotEmpty())
            LaundryRepository().occupancy(requireNotNull(laundryToken.token()), devices)
        }
        op("laundry-token-set", "Store a laundry platform token in Android Keystore", write = true, secret = true) { _, secret -> laundryToken.save(requireNotNull(secret)); true }
        op("laundry-token-clear", "Remove the saved laundry token", write = true) { _, _ -> laundryToken.clear(); true }
        op("laundry-longhe-points", "Longhe laundry rooms", "kind:string refresh?:boolean") { a, _ -> QiekjLaundrySource().points(LaundryKind.valueOf(a.string("kind")), a.bool("refresh")) }
        op("laundry-longhe-status", "Longhe machine occupancy", "kind:string room?:string") { a, _ ->
            val kind = LaundryKind.valueOf(a.string("kind"))
            val room = a.get("room")?.asString?.let { id -> longheLaundry.points(kind).first { it.orgId == id } }
            longheLaundry.machines(kind, room)
        }
        op("mail-folders", "Mailbox folders") { _, _ -> mailReady().folders() }
        op("mail-messages", "Paginated mailbox messages", "folder?:integer start?:integer unread?:boolean") { a, _ -> mailReady().messages(a.int("folder", 1), a.int("start", 0).coerceAtLeast(0), a.bool("unread")) }
        op("mail-detail", "Read a mail message", "id:string") { a, _ -> mailReady().detail(a.string("id")) }
        op("mail-contacts", "Mailbox contacts") { _, _ -> mailReady().contacts() }
        op("mail-attachments", "Mailbox attachment index") { _, _ -> mailReady().attachments() }
        op("mail-move", "Move a mail message to another folder", "id:string folder:integer", write = true) { a, _ -> mailReady().moveMessage(a.string("id"), a.int("folder")); true }
        op("mail-draft", "Restore a saved mail draft", "id:string") { a, _ -> mailReady().restoreDraft(a.string("id")) }
        op("mail-draft-save", "Create or update a mail draft", "to:string subject:string body:string cc?:string bcc?:string id?:string", write = true) { a, _ ->
            val service = mailReady()
            val draft = a.get("id")?.asString?.let { service.restoreDraft(it) } ?: MailDraft()
            service.saveDraft(draft.copy(to = a.string("to"), cc = a.string("cc", ""), bcc = a.string("bcc", ""),
                subject = a.string("subject"), body = a.string("body")))
        }
        op("mail-draft-attach", "Attach an uploaded host file to a saved mail draft", "id:string file:string name:string mime?:string", write = true) { a, _ ->
            val service = mailReady()
            service.saveDraft(service.uploadAttachment(service.restoreDraft(a.string("id")), CliFiles.uploaded(a.string("file")),
                a.string("name").substringAfterLast('/').substringAfterLast('\\'), a.string("mime", "application/octet-stream")) { _, _ -> })
        }
        op("mail-draft-detach", "Remove an attachment from a saved mail draft", "id:string attachment:string", write = true) { a, _ ->
            val service = mailReady()
            service.saveDraft(service.removeDraftAttachment(service.restoreDraft(a.string("id")), a.string("attachment")))
        }
        op("mail-attachment-export", "Download a mail attachment for host export", "message:string part:string") { a, _ ->
            val service = mailReady()
            val (id, file) = CliFiles.create(context)
            file.outputStream().use { StudentMailDownload(service.webSession()).download(a.string("message"), a.string("part"), it) }
            CliFiles.info(context, id)
        }
        op("mail-draft-send", "Send an existing saved draft including its attachments", "id:string", write = true) { a, _ ->
            val service = mailReady()
            service.send(service.restoreDraft(a.string("id"))); mapOf("status" to "submitted")
        }
        op("chaoxing-login", "Sign in to Learning Pass separately from the campus account", "phone:string", write = true, secret = true) { a, secret ->
            chaoxing.loginByPassword(a.string("phone"), requireNotNull(secret)); mapOf("hasSession" to chaoxing.hasSession())
        }
        op("chaoxing-courses", "Learning Pass course list", "refresh?:boolean") { a, _ -> if (a.bool("refresh")) chaoxing.fetchCourses() else Store.getCourses() }
        op("chaoxing-progress", "Cached Learning Pass task progress") { _, _ -> Store.getCourseProgress() }
        op("notes-subjects", "Local note subjects") { _, _ -> NoteStore.subjects() }
        op("notes-photos", "Local note photo metadata", "subject?:string") { a, _ -> NoteStore.photos(a.get("subject")?.asString) }
        op("notes-subject-create", "Create a note subject", "name:string", write = true) { a, _ -> NoteStore.ensureSubject(a.string("name")) }
        op("notes-subject-rename", "Rename a note subject", "id:string name:string", write = true) { a, _ -> NoteStore.renameSubject(a.string("id"), a.string("name")) }
        op("notes-subject-delete", "Delete a subject, preserving its photos as unfiled", "id:string", write = true) { a, _ -> NoteStore.deleteSubject(a.string("id")); true }
        op("notes-photos-move", "Move note photos to another subject", "ids:array subject:string", write = true) { a, _ -> NoteStore.movePhotos(a.getAsJsonArray("ids").map { it.asString }.toSet(), a.string("subject")) }
        op("notes-photos-delete", "Delete selected local note photos", "ids:array", write = true) { a, _ -> NoteStore.deletePhotos(a.getAsJsonArray("ids").map { it.asString }.toSet()) }
        op("notes-export", "Export selected photos to Android gallery", "ids:array", write = true) { a, _ -> NoteStore.exportToGallery(a.getAsJsonArray("ids").map { it.asString }.toSet()) }
        op("note-photo-export", "Export one note photo to the host", "id:string") { a, _ ->
            val photo = NoteStore.photos().first { it.id == a.string("id") }
            val (id, file) = CliFiles.create(context)
            NoteStore.photoFile(photo).inputStream().use { input -> file.outputStream().use { input.copyTo(it) } }
            CliFiles.info(context, id)
        }
        op("note-photo-import", "Import an uploaded image into a note subject", "file:string subject:string", write = true) { a, _ ->
            requireNotNull(NoteStore.subject(a.string("subject")))
            val uploaded = CliFiles.uploaded(a.string("file"))
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(uploaded.absolutePath, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0)
            val target = File(NoteStore.subjectDir(a.string("subject")), NoteStore.newPhotoFileName())
            target.parentFile?.mkdirs()
            uploaded.copyTo(target)
            NoteStore.addPhoto(target, a.string("subject"), NoteSource.MANUAL)
        }
        op("file-upload", "Allocate a temporary host upload; no business action is executed") { _, _ -> CliFiles.upload(context) }
        op("file-info", "Verify a temporary transfer's size and SHA-256", "id:string") { a, _ -> CliFiles.info(context, a.string("id")) }
        op("file-close", "Discard a temporary CLI transfer", "id:string") { a, _ -> CliFiles.discard(a.string("id")); true }
        op("plugins", "Installed Android plugins and declared capabilities") { _, _ -> PluginRegistry.plugins.value.map { mapOf("id" to it.meta.id, "title" to it.meta.title, "capabilities" to it.capabilities.map { value -> value.name }) } }
        op("settings", "Public preferences; no arbitrary storage or credential access") { _, _ -> settings.read() }
        op("setting-set", "Change one public preference; notifications requiring OS permission remain in Android UI", "name:string value:string", write = true) { a, _ -> settings.set(a.string("name"), a.string("value")); true }
        op("ai-models", "Model metadata, excluding API Keys") { _, _ -> AssistantModelLibrary.snapshot() }
        op("ai-model-select", "Select a saved AI model", "id:string", write = true) { a, _ -> AssistantModelLibrary.setActive(a.string("id")) }
        op("ai-model-delete", "Delete a configured model and its local Key", "id:string", write = true) { a, _ -> AssistantModelLibrary.delete(a.string("id")) }
        op("ai-configure", "Store an OpenAI-compatible model configuration", "baseUrl:string model:string", write = true, secret = true) { a, secret ->
            val config = AssistantModelConfig(baseUrl = a.string("baseUrl"), model = a.string("model"), apiKey = requireNotNull(secret))
            require(config.isSecureEndpoint && config.isConfigured)
            AssistantModelConfigStore.save(config); true
        }
        op("ask", "Ask the configured AI; writes are rejected here and must use explicit commands", "question:string") { a, _ ->
            val gate = AssistantApprovalGate()
            val events = mutableListOf<AssistantServiceEvent>()
            AssistantService(capabilities).stream(a.string("question"), gate = gate).collect { event ->
                if (event is AssistantServiceEvent.ApprovalRequired) gate.reject()
                // Keep a bounded reply; do not collect thousands of token delta objects.
                if (event !is AssistantServiceEvent.AssistantDelta && event !is AssistantServiceEvent.ReasoningDelta) events.add(event)
                check(events.size <= 200)
            }
            events
        }
    }

    private fun <T> AhuResult<T>.value(): T = when (this) {
        is AhuResult.Success -> value
        is AhuResult.Failure -> error("Business request failed: ${error.javaClass.simpleName}")
    }
    private fun JsonObject.string(key: String, fallback: String? = null): String = get(key)?.asString ?: requireNotNull(fallback)
    private fun JsonObject.int(key: String, fallback: Int = 0): Int = get(key)?.asBigDecimal?.intValueExact() ?: fallback
    private fun JsonObject.bool(key: String): Boolean = get(key)?.asBoolean ?: false
    private fun success(data: Map<String, Any?>): Any = mapOf("ok" to true,
        "data" to (data + ("appDemoMode" to AHUCache.getMockData())))
    private fun failure(code: String, message: String): Any = mapOf("ok" to false, "code" to code, "message" to message)

    private companion object {
        val LOCAL_WRITES = setOf("account-forget", "custom-courses-replace", "notes-subject-create",
            "notes-subject-rename", "notes-subject-delete", "notes-photos-move", "notes-photos-delete",
            "notes-export", "note-photo-import", "setting-set", "ai-configure", "ai-model-delete", "ai-model-select")
    }
}
