package com.ahu.ahutong.personalization.runtime

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.ahu.ahutong.BuildConfig
import com.ahu.ahutong.personalization.action.ActionFamily
import com.ahu.ahutong.personalization.action.ActionSource
import com.ahu.ahutong.personalization.action.AppActionCatalog
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.action.SideEffect
import com.ahu.ahutong.personalization.action.OrganicLabelPolicy
import com.ahu.ahutong.personalization.context.BalanceBucket
import com.ahu.ahutong.personalization.context.ContextSnapshot
import com.ahu.ahutong.personalization.context.ContextSnapshotCodec
import com.ahu.ahutong.personalization.context.DayType
import com.ahu.ahutong.personalization.context.ExamDistanceBucket
import com.ahu.ahutong.personalization.context.FeatureExtractor
import com.ahu.ahutong.personalization.context.PredictionInput
import com.ahu.ahutong.personalization.journey.JourneyPredictionEngine
import com.ahu.ahutong.personalization.preset.PresetCandidate
import com.ahu.ahutong.personalization.preset.AppliedPreset
import com.ahu.ahutong.personalization.preset.PresetInteractionToken
import com.ahu.ahutong.personalization.preset.PresetRankingEngine
import com.ahu.ahutong.personalization.preset.PresetSubmission
import com.ahu.ahutong.personalization.semantic.CommittedMutation
import com.ahu.ahutong.personalization.semantic.ContentContext
import com.ahu.ahutong.personalization.semantic.ContentStateBucket
import com.ahu.ahutong.personalization.semantic.ErrorTypeBucket
import com.ahu.ahutong.personalization.semantic.MutationId
import com.ahu.ahutong.personalization.semantic.NormalizedSemanticEvent
import com.ahu.ahutong.personalization.semantic.ResultCountBucket
import com.ahu.ahutong.personalization.semantic.ProductCandidateResolver
import com.ahu.ahutong.personalization.semantic.ProductCandidateScope
import com.ahu.ahutong.personalization.semantic.SemanticContext
import com.ahu.ahutong.personalization.semantic.SemanticDomain
import com.ahu.ahutong.personalization.semantic.SemanticEventFamily
import com.ahu.ahutong.personalization.semantic.SemanticEventRecorder
import com.ahu.ahutong.data.schedule.CurrentWeekResolver
import com.ahu.ahutong.personalization.evaluation.ShadowModelEvaluator
import com.ahu.ahutong.personalization.inference.DecayedFrequencyPredictor
import com.ahu.ahutong.personalization.inference.NextActionProbabilityVector
import com.ahu.ahutong.personalization.inference.RecentActionBaselinePredictor
import com.ahu.ahutong.personalization.inference.TimeBucketFrequencyBaselinePredictor
import com.ahu.ahutong.personalization.inference.TinyMlpPredictor
import com.ahu.ahutong.personalization.model.ModelStateStore
import com.ahu.ahutong.personalization.profile.ProfileKeyManager
import com.ahu.ahutong.personalization.prefetch.PaymentQrOpenCommandStore
import com.ahu.ahutong.personalization.prefetch.PrefetchCoordinator
import com.ahu.ahutong.personalization.promotion.LocalPromotionManager
import com.ahu.ahutong.personalization.promotion.PromotionSnapshot
import com.ahu.ahutong.personalization.storage.BehaviorDao
import com.ahu.ahutong.personalization.storage.BehaviorDatabase
import com.ahu.ahutong.personalization.storage.BehaviorDatabaseCompatibilityStore
import com.ahu.ahutong.personalization.storage.BehaviorEventEntity
import com.ahu.ahutong.personalization.storage.BinaryCodec
import com.ahu.ahutong.personalization.storage.LearningStateEntity
import com.ahu.ahutong.personalization.storage.PendingPredictionEntity
import com.ahu.ahutong.personalization.storage.ProductExecutionLeaseEntity
import com.ahu.ahutong.personalization.storage.SemanticChangeSetEntity
import com.ahu.ahutong.personalization.storage.SemanticEventEntity
import com.ahu.ahutong.personalization.storage.transaction
import com.ahu.ahutong.personalization.training.OnDeviceTrainer
import com.ahu.ahutong.personalization.training.OrganicTrainingSample
import com.ahu.ahutong.personalization.training.TrainingFeedbackPolicy
import com.ahu.ahutong.personalization.training.TrainingSliceResult
import com.ahu.ahutong.personalization.telemetry.ModelQualityTelemetryManager
import com.ahu.ahutong.personalization.bootstrap.BootstrapContributionStatus
import com.ahu.ahutong.personalization.bootstrap.BootstrapTrainingDataManager
import com.ahu.ahutong.personalization.telemetry.TelemetryAggregateStore
import com.ahu.ahutong.personalization.telemetry.TelemetryDeliveryEvent
import com.ahu.ahutong.personalization.ui.SuggestionPolicy
import com.ahu.ahutong.personalization.ui.ActionPredictionProposal
import com.ahu.ahutong.personalization.ui.ArbitratedPrediction
import com.ahu.ahutong.personalization.ui.PredictionArbiter
import com.ahu.ahutong.personalization.ui.PredictionTask
import com.ahu.ahutong.personalization.ui.PendingSuggestionOffer
import com.ahu.ahutong.personalization.ui.SuggestionDeliveryBlockReason
import com.ahu.ahutong.personalization.ui.SuggestionDeliveryLane
import com.ahu.ahutong.data.dao.PreferencesManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Mac
import javax.inject.Inject
import javax.inject.Singleton
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class OpportunityTrigger(val labelWindowMillis: Long) {
    STABLE_FOREGROUND(120_000L),
    ACTION_INTENT_ACCEPTED(60_000L),
    BUSINESS_CONTEXT_CHANGED(60_000L),
    SEMANTIC_MUTATION_COMMITTED(60_000L)
}

enum class ConfidenceBucket { LOW, MEDIUM, HIGH }

sealed interface PredictionUiState {
    data object Hidden : PredictionUiState
    data class Suggestion(
        val executionId: String,
        val decisionId: String,
        val contextGeneration: Long,
        val deliveryLane: SuggestionDeliveryLane,
        val action: AppActionId,
        val title: String,
        val reason: String,
        val confidenceBucket: ConfidenceBucket,
        val exposureConfirmed: Boolean,
        val shownAtElapsedMs: Long,
        val expiresAtElapsedMs: Long,
        val visibilityPaused: Boolean = false
    ) : PredictionUiState
}

data class RuntimeDiagnosticsState(
    val profileActive: Boolean = false,
    val foreground: Boolean = false,
    val sessionId: String? = null,
    val decisionId: String? = null,
    val preparationState: String = "IDLE",
    val previousAction: String? = null,
    val deadlineElapsedMs: Long? = null,
    val stage: String = "SHADOW",
    val tier: String = "STAT_ONLY",
    val lambda: Float = 0f,
    val activeCheckpoint: String? = null,
    val statProbabilities: Map<String, Float> = emptyMap(),
    val tinyProbabilities: Map<String, Float> = emptyMap(),
    val effectiveProbabilities: Map<String, Float> = emptyMap(),
    val candidateScope: String = "ORDINARY",
    val targetedActions: Set<String> = emptySet(),
    val suggestionDeliveryLane: String = "NONE",
    val contextGeneration: Long = 0L,
    val suggestionIntervalRemainingMs: Long = 0L,
    val suggestionRetryAtElapsedMs: Long? = null,
    val candidateRejectionReason: String? = null,
    val ordinaryCandidateProbability: Float? = null,
    val ordinaryCompetitorAction: String? = null,
    val ordinaryCompetitorProbability: Float? = null,
    val ordinaryProbabilityMargin: Float? = null,
    val lastResolution: String? = null,
    val lastFailure: String? = null,
    val lastTraining: TrainingSliceResult? = null
)

data class SanitizedDiagnosticsSnapshot(
    val trainingSamples: Int = 0,
    val organicNonNoneSamples: Int = 0,
    val suggestionAcceptedSamples: Int = 0,
    val actionFamilies: Int = 0,
    val statLearningStartedDay: Long? = null,
    val tinyTrainingStartedDay: Long? = null,
    val trainingRevision: Long = 0,
    val candidateCheckpoint: String? = null,
    val activeChecksum: String? = null,
    val modelSizeBytes: Long = 0,
    val promotionWindows: List<String> = emptyList(),
    val recentTimeline: List<String> = emptyList(),
    val pendingTelemetryReports: Int = 0,
    val telemetryProtocolVersion: Int = 2,
    val telemetryV3Aggregates: List<String> = emptyList(),
    val recentSemanticEvents: List<String> = emptyList(),
    val recentSemanticChangeSets: List<String> = emptyList(),
    val pendingJourney: String? = null,
    val journeyProbabilities: List<String> = emptyList(),
    val presetCandidates: List<String> = emptyList(),
    val journeyTrainingSamples: Int = 0,
    val presetTrainingSamples: Int = 0,
    val presetFeedbackDiagnostics: List<String> = emptyList(),
    val taskStates: List<String> = emptyList()
)

@Singleton
class BehaviorPredictionRuntime @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: BehaviorDatabase,
    private val databaseCompatibilityStore: BehaviorDatabaseCompatibilityStore,
    private val dao: BehaviorDao,
    private val profileKeyManager: ProfileKeyManager,
    private val statPredictor: DecayedFrequencyPredictor,
    private val tinyPredictor: TinyMlpPredictor,
    private val recentBaseline: RecentActionBaselinePredictor,
    private val timeBaseline: TimeBucketFrequencyBaselinePredictor,
    private val evaluator: ShadowModelEvaluator,
    private val trainer: OnDeviceTrainer,
    private val promotionManager: LocalPromotionManager,
    private val modelStateStore: ModelStateStore,
    private val preferencesManager: PreferencesManager,
    private val prefetchCoordinator: PrefetchCoordinator,
    private val paymentQrCommands: PaymentQrOpenCommandStore,
    private val bootstrapTrainingDataManager: BootstrapTrainingDataManager,
    private val telemetryManager: ModelQualityTelemetryManager,
    private val telemetryAggregateStore: TelemetryAggregateStore,
    private val semanticEventRecorder: SemanticEventRecorder,
    private val journeyEngine: JourneyPredictionEngine,
    private val presetRankingEngine: PresetRankingEngine
) {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            if (error !is CancellationException) {
                Log.e(TAG, "Background prediction task failed", error)
                _diagnostics.value = _diagnostics.value.copy(
                    lastFailure = "BACKGROUND_TASK_FAILED_${error::class.java.simpleName}"
                )
            }
        }
    )
    private val processInstanceId = UUID.randomUUID().toString()
    private val profileLifecycleMutex = Mutex()
    private val profileLocks = ConcurrentHashMap<String, Mutex>()
    private val deadlineJobs = ConcurrentHashMap<String, Job>()
    private val sequence = AtomicLong(0)
    private val executionEpoch = AtomicLong(0)
    private val suggestionVisibilityGeneration = AtomicLong(0)
    private val suggestionRetryGeneration = AtomicLong(0)
    private val contextGeneration = AtomicLong(0)
    private val settingSubmissionSequence = AtomicLong(0)
    private val latestAppliedSettingSubmission = AtomicLong(0)
    private val profileGeneration = AtomicLong(0)
    private val loginGeneration = AtomicLong(0)
    private val _diagnostics = MutableStateFlow(RuntimeDiagnosticsState())
    private val _uiState = MutableStateFlow<PredictionUiState>(PredictionUiState.Hidden)
    private val _telemetryConsentEnabled = MutableStateFlow(false)
    private val _sensitiveUiVisible = MutableStateFlow(false)
    private val _suggestionOverlayBlocked = MutableStateFlow(false)
    private val recentActions = ArrayDeque<AppActionId>()
    private val recentActionSources = ArrayDeque<ActionSource>()
    private val organicActionHistory = ArrayDeque<AppActionId>()
    @Volatile private var suggestionExpiryJob: Job? = null
    @Volatile private var suggestionRetryJob: Job? = null
    @Volatile private var pendingSuggestionOffer: PendingSuggestionOffer? = null
    @Volatile private var activeTargetedContext: ActiveTargetedContext? = null
    @Volatile private var externalSuggestionHostBlocked = false
    @Volatile private var lastTargetedSuggestionElapsedMs = 0L
    @Volatile private var lastOrdinarySuggestionElapsedMs = 0L
    @Volatile private var diagnosticsObservationActive = false
    private val exposedTargetedChangeSets = ConcurrentHashMap.newKeySet<String>()

    val diagnostics: StateFlow<RuntimeDiagnosticsState> = _diagnostics.asStateFlow()
    val uiState: StateFlow<PredictionUiState> = _uiState.asStateFlow()
    val telemetryConsentEnabled: StateFlow<Boolean> = _telemetryConsentEnabled.asStateFlow()
    val bootstrapContributionStatus: StateFlow<BootstrapContributionStatus> =
        bootstrapTrainingDataManager.status
    val sensitiveUiVisible: StateFlow<Boolean> = _sensitiveUiVisible.asStateFlow()
    val suggestionOverlayBlocked: StateFlow<Boolean> = _suggestionOverlayBlocked.asStateFlow()

    @Volatile private var profileKey: String? = null
    @Volatile private var profileReady = false
    @Volatile private var activeAccountIdentifier: String? = null
    @Volatile private var sessionId: String? = null
    @Volatile private var foreground = false
    @Volatile private var interactive = false
    @Volatile private var lastRoute: String? = null
    @Volatile private var lastAction: AppActionId? = null
    @Volatile private var lastActionSequenceNo = 0L
    @Volatile private var balanceBucket = BalanceBucket.UNKNOWN
    @Volatile private var balanceFresh = false
    @Volatile private var examBucket = ExamDistanceBucket.UNKNOWN
    @Volatile private var sessionStartedElapsedMs = 0L
    @Volatile private var lastBackgroundElapsedMs: Long? = null
    @Volatile private var foregroundGapBucket: Int? = null
    @Volatile private var routeChangedElapsedMs = 0L
    @Volatile private var lastContextOpportunityElapsedMs = 0L
    @Volatile private var taintedChain = false
    @Volatile private var suppressedRoute: String? = null
    @Volatile private var nextNavigationSource: ActionSource? = null
    @Volatile private var latestSemanticContext: SemanticContext? = null
    @Volatile private var latestSemanticOccurredElapsedMs: Long = 0L
    @Volatile private var latestContentContext: ContentContext? = null
    @Volatile private var latestAffectedCandidateCount: Int = 0

    suspend fun startProfile(accountIdentifier: String) = profileLifecycleMutex.withLock {
        startProfileLocked(accountIdentifier)
    }

    private suspend fun startProfileLocked(accountIdentifier: String) {
        val nextProfile = profileKeyManager.profileKey(accountIdentifier)
        if (profileReady && profileKey == nextProfile && sessionId != null) {
            val refreshedLoginGeneration = loginGeneration.incrementAndGet()
            paymentQrCommands.activate(profileGeneration.get(), refreshedLoginGeneration)
            return
        }
        stopSessionLocked(censorReason = "PROFILE_SWITCHED", clearProfile = false)
        profileReady = false
        profileKey = nextProfile
        activeAccountIdentifier = accountIdentifier
        sessionId = UUID.randomUUID().toString()
        sessionStartedElapsedMs = SystemClock.elapsedRealtime()
        contextGeneration.set(0L)
        latestAppliedSettingSubmission.set(0L)
        lastTargetedSuggestionElapsedMs = 0L
        lastOrdinarySuggestionElapsedMs = 0L
        pendingSuggestionOffer = null
        activeTargetedContext = null
        exposedTargetedChangeSets.clear()
        val persistedMaxSequence = dao.maxEventSequence(nextProfile)
        sequence.updateAndGet { current -> maxOf(current, persistedMaxSequence) }
        recentActions.clear()
        recentActionSources.clear()
        organicActionHistory.clear()
        latestSemanticContext = null
        latestSemanticOccurredElapsedMs = 0L
        latestContentContext = null
        latestAffectedCandidateCount = 0
        dao.recentEvents(nextProfile, 64).asReversed()
            .filter { it.eventType == "ACTION_INTENT_ACCEPTED" }
            .forEach { stored ->
                val action = AppActionId.fromStableId(stored.actionId) ?: return@forEach
                val source = runCatching { ActionSource.valueOf(stored.source) }.getOrDefault(ActionSource.SYSTEM)
                recentActions.addLast(action)
                recentActionSources.addLast(source)
                while (recentActions.size > 8) recentActions.removeFirst()
                while (recentActionSources.size > 8) recentActionSources.removeFirst()
                if (source == ActionSource.ORGANIC) organicActionHistory.addLast(action)
            }
        lastAction = null
        lastActionSequenceNo = 0L
        taintedChain = false
        dao.censorPendingWithExistingResolutionEvent(nextProfile)
        dao.censorStaleProcessPending(nextProfile, processInstanceId)
        dao.recoverPreparedSemanticChangeSets(nextProfile)
        journeyEngine.recoverProfile(nextProfile, processInstanceId)
        modelStateStore.loadOrCreate(nextProfile)
        val promotion = promotionManager.snapshot(nextProfile)
        trainer.resumeProfile(nextProfile)
        presetRankingEngine.resumeProfile(nextProfile)
        val onboardingChoice = preferencesManager.modelQualityTelemetryOnboardingChoice.first()
        val profileTelemetryEnabled = telemetryManager.isConsentEnabled(nextProfile)
        when {
            onboardingChoice == true && !profileTelemetryEnabled ->
                telemetryManager.setConsent(
                    nextProfile,
                    enabled = true,
                    localModelGenerationVersion = promotion.modelGeneration
                )
            onboardingChoice != true && profileTelemetryEnabled ->
                telemetryManager.setConsent(nextProfile, enabled = false)
        }
        telemetryManager.reconcileProfile(nextProfile, promotion.modelGeneration)
        _telemetryConsentEnabled.value = onboardingChoice == true && telemetryManager.isConsentEnabled(nextProfile)
        val bootstrapChoice = preferencesManager.bootstrapTrainingOnboardingChoice.first()
        if (bootstrapChoice == true) {
            preferencesManager.claimBootstrapTrainingOnboardingForProfile(nextProfile)
        }
        bootstrapTrainingDataManager.reconcileProfile(
            nextProfile,
            enabled = preferencesManager.bootstrapTrainingEnabled(nextProfile).first(),
            includeHistorical = preferencesManager.bootstrapTrainingIncludeHistorical.first()
        )
        val activeProfileGeneration = profileGeneration.incrementAndGet()
        val activeLoginGeneration = loginGeneration.incrementAndGet()
        paymentQrCommands.activate(activeProfileGeneration, activeLoginGeneration)
        prefetchCoordinator.beginSession()
        profileReady = true
        insertLifecycleEvent("SESSION_STARTED", ActionSource.SYSTEM)
        _diagnostics.value = _diagnostics.value.copy(
            profileActive = true,
            sessionId = sessionId,
            foreground = foreground,
            lastFailure = null
        )
        if (foreground && interactive) createOpportunity(OpportunityTrigger.STABLE_FOREGROUND, null)
    }

    fun setForeground(value: Boolean, isInteractive: Boolean = value) {
        val elapsed = SystemClock.elapsedRealtime()
        if (value && !foreground) {
            foregroundGapBucket = lastBackgroundElapsedMs?.let { gapBucket(elapsed - it) }
        } else if (!value && foreground) {
            lastBackgroundElapsedMs = elapsed
        }
        foreground = value
        interactive = isInteractive
        _diagnostics.value = _diagnostics.value.copy(foreground = value)
        if (!value || !isInteractive) {
            cancelSuggestionDeliveryState("BACKGROUND_OR_NON_INTERACTIVE")
            scope.launch { censorActive("CENSORED_BACKGROUND") }
            profileKey?.let { activeProfile -> scope.launch { journeyEngine.censorProfile(activeProfile, "CENSORED_BACKGROUND") } }
            scope.launch { prefetchCoordinator.cancelAll() }
            profileKey?.let { activeProfile -> scope.launch { trainer.cancelProfile(activeProfile) } }
        } else {
            scope.launch {
                val activeProfile = profileKey ?: return@launch
                trainer.resumeProfile(activeProfile)
                val pending = dao.latestPending(activeProfile)
                if (pending == null) createOpportunity(OpportunityTrigger.STABLE_FOREGROUND, lastAction)
                else registerDeadline(pending)
                delay(TRAINING_IDLE_GRACE_MS)
                runIdleTrainingSlice()
            }
        }
    }

    fun onRouteChanged(route: String?, source: ActionSource = ActionSource.ORGANIC) {
        if (route == null) return
        if (source == ActionSource.DEBUG) {
            setDiagnosticsObservationActive(true)
            return
        }
        setDiagnosticsObservationActive(false)
        val externallyMarkedSource = nextNavigationSource.also { nextNavigationSource = null }
        if (route == lastRoute) return
        cancelSuggestionDeliveryState("ROUTE_CHANGED")
        lastRoute = route
        routeChangedElapsedMs = SystemClock.elapsedRealtime()
        if (suppressedRoute == route) {
            suppressedRoute = null
            return
        }
        val action = AppActionCatalog.actionForRoute(route) ?: run {
            scope.launch { censorActive("CENSORED_UNTRACKED_OR_DEBUG_ROUTE") }
            return
        }
        if (externallyMarkedSource != null && !AppActionCatalog.spec(action).predictable) {
            nextNavigationSource = externallyMarkedSource
        }
        val actualSource = externallyMarkedSource ?: source
        scope.launch { recordActionIntent(action, actualSource, route) }
    }

    fun suppressNextRoute(route: String) {
        suppressedRoute = route.takeUnless { it == lastRoute }
    }

    fun markNextNavigationSource(source: ActionSource) { nextNavigationSource = source }

    fun recordActionIntentAsync(action: AppActionId, source: ActionSource = ActionSource.ORGANIC) {
        scope.launch { recordActionIntent(action, source, AppActionCatalog.spec(action).route) }
    }

    suspend fun recordActionIntent(
        action: AppActionId,
        source: ActionSource = ActionSource.ORGANIC,
        route: String? = AppActionCatalog.spec(action).route,
        deferNextOpportunity: Boolean = false
    ) {
        if (!profileReady) return
        val activeProfile = profileKey ?: return
        val activeSession = sessionId ?: return
        val spec = AppActionCatalog.spec(action)
        val lock = profileLocks.getOrPut(activeProfile) { Mutex() }
        var deferredOpportunity: RecordedActionOpportunity? = null
        var journeyObservation: JourneyActionObservation? = null
        lock.withLock {
            if (!profileReady || profileKey != activeProfile || sessionId != activeSession) return@withLock
            val elapsed = SystemClock.elapsedRealtime()
            val eventId = UUID.randomUUID().toString()
            dao.censorPendingWithExistingResolutionEvent(activeProfile)
            val currentPending = dao.latestPending(activeProfile)
            val cleanOrganic = OrganicLabelPolicy.isEligible(action, source, taintedChain = taintedChain)
            val sequenceNo = sequence.incrementAndGet()
            dao.insertEvent(
                event(
                    eventId,
                    UUID.randomUUID().toString(),
                    activeProfile,
                    activeSession,
                    sequenceNo,
                    "ACTION_INTENT_ACCEPTED",
                    action,
                    source,
                    elapsed,
                    currentPending?.decisionId
                )
            )
            journeyObservation = JourneyActionObservation(activeProfile, activeSession, action, source, eventId, elapsed)
            if (currentPending != null) {
                when {
                    currentPending.preparationState == "PREPARING" ->
                        dao.censorPendingCas(
                            currentPending.decisionId,
                            activeProfile,
                            "CENSORED_PREPARATION_SUPERSEDED",
                            eventId
                        )
                    cleanOrganic && elapsed <= currentPending.labelDeadlineElapsedMs ->
                        resolvePending(currentPending, action.stableId, eventId, "ORGANIC_ACTION", spec.family)
                    else -> {
                        dao.invalidatePendingForObservedSourceCas(
                            currentPending.decisionId,
                            activeProfile,
                            if (source == ActionSource.ORGANIC) currentPending.interventionState else "TAINTED_${source.name}",
                            if (source == ActionSource.ORGANIC) "CENSORED_TAINTED_CHAIN" else "INVALIDATED_PRODUCT_INTERVENTION",
                            eventId
                        )
                        deadlineJobs.remove(currentPending.decisionId)?.cancel()
                    }
                }
            }
            if (source == ActionSource.ORGANIC) {
                if (taintedChain) {
                    recentActions.clear()
                    recentActionSources.clear()
                }
                taintedChain = false
            } else {
                taintedChain = true
            }
            lastAction = action
            lastActionSequenceNo = sequenceNo
            if (prefetchCoordinator.onActionOpened(action)) {
                dao.insertEvent(
                    event(
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        activeProfile,
                        activeSession,
                        sequence.incrementAndGet(),
                        "PREFETCH_CONSUMED",
                        action,
                        ActionSource.SYSTEM,
                        elapsed,
                        null
                    )
                )
            }
            when (action) {
                AppActionId.MANUAL_REFRESH_SCHEDULE -> prefetchCoordinator.invalidate(AppActionId.VIEW_SCHEDULE)
                AppActionId.MANUAL_REFRESH_EXAM, AppActionId.RETRY_EXAM -> prefetchCoordinator.invalidate(AppActionId.VIEW_EXAM_ROOM)
                AppActionId.MANUAL_REFRESH_GRADE, AppActionId.RETRY_GRADE -> prefetchCoordinator.invalidate(AppActionId.VIEW_GRADES)
                AppActionId.REFRESH_PAYMENT_QR -> prefetchCoordinator.invalidate(AppActionId.OPEN_PAYMENT_QR)
                else -> Unit
            }
            recentActions.addLast(action)
            recentActionSources.addLast(source)
            while (recentActions.size > 8) recentActions.removeFirst()
            while (recentActionSources.size > 8) recentActionSources.removeFirst()
            if (source == ActionSource.ORGANIC) {
                organicActionHistory.addLast(action)
                while (organicActionHistory.size > 64) organicActionHistory.removeFirst()
            }
            cancelSuggestionDeliveryState("ACTION_OBSERVED")
            // Semantic/content scopes are single-opportunity product constraints. The immutable
            // pending rows keep their full context for labels and journey evaluation.
            latestSemanticContext = null
            latestSemanticOccurredElapsedMs = 0L
            latestContentContext = null
            latestAffectedCandidateCount = 0
            // Product-directed/deep-link/restore actions may continue product prediction, but the
            // opportunity is TAINTED_CHAIN and can never train, evaluate, or promote a model.
            // The next independent organic action starts a clean opportunity after acting only as an anchor.
            if (spec.predictable && foreground && interactive) {
                if (deferNextOpportunity) {
                    deferredOpportunity = RecordedActionOpportunity(
                        activeProfile,
                        activeSession,
                        action,
                        eventId,
                        sequenceNo
                    )
                } else {
                    createOpportunityLocked(OpportunityTrigger.ACTION_INTENT_ACCEPTED, action, eventId, sequenceNo)
                }
            }
        }
        journeyObservation?.let { observation ->
            journeyEngine.onAction(
                observation.profileKey,
                observation.sessionId,
                observation.action,
                observation.source,
                observation.eventId,
                observation.elapsedMs
            )
        }
        deferredOpportunity?.let(::scheduleRecordedActionOpportunity)
    }

    private fun scheduleRecordedActionOpportunity(value: RecordedActionOpportunity) {
        scope.launch {
            val lock = profileLocks.getOrPut(value.profileKey) { Mutex() }
            lock.withLock {
                if (profileKey != value.profileKey || sessionId != value.sessionId ||
                    lastActionSequenceNo != value.sequenceNo || lastAction != value.action
                ) return@withLock
                createOpportunityLocked(
                    OpportunityTrigger.ACTION_INTENT_ACCEPTED,
                    value.action,
                    value.eventId,
                    value.sequenceNo
                )
            }
        }
    }

    fun onBusinessContextChanged(
        newBalanceBucket: BalanceBucket = balanceBucket,
        newBalanceFresh: Boolean = balanceFresh,
        newExamBucket: ExamDistanceBucket = examBucket
    ) {
        val changed = newBalanceBucket != balanceBucket || newBalanceFresh != balanceFresh || newExamBucket != examBucket
        balanceBucket = newBalanceBucket
        balanceFresh = newBalanceFresh
        examBucket = newExamBucket
        if (!changed || !foreground || !interactive || taintedChain) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastContextOpportunityElapsedMs < CONTEXT_DEBOUNCE_MS) return
        lastContextOpportunityElapsedMs = now
        scope.launch {
            censorActive("CENSORED_CONTEXT_CHANGED")
            createOpportunity(OpportunityTrigger.BUSINESS_CONTEXT_CHANGED, lastAction)
        }
    }

    suspend fun recordCommittedMutation(
        mutationId: MutationId,
        oldValue: Any?,
        newValue: Any?,
        source: ActionSource = ActionSource.ORGANIC,
        coarseValueBucket: String? = null,
        domainOverride: SemanticDomain? = null,
        familyOverride: SemanticEventFamily? = null,
        settingSubmissionOrder: Long = settingSubmissionSequence.incrementAndGet()
    ): Boolean {
        if (!profileReady) return false
        val activeProfile = profileKey ?: return false
        val activeSession = sessionId ?: return false
        val nowElapsed = SystemClock.elapsedRealtime()
        val normalized = semanticEventRecorder.normalize(
            CommittedMutation(
                mutationId = mutationId,
                oldValue = oldValue,
                newValue = newValue,
                source = source,
                route = lastRoute,
                domainOverride = domainOverride,
                coarseValueBucket = coarseValueBucket,
                familyOverride = familyOverride,
                committedAtEpochDay = LocalDate.now(ZoneOffset.UTC).toEpochDay(),
                occurredAtElapsedMs = nowElapsed,
                tainted = taintedChain
            )
        ) ?: return false
        val semanticContext = SemanticContext(
            normalized.eventFamily,
            normalized.domain,
            normalized.semanticId,
            normalized.changeKind,
            ageBucket = 0,
            changeSetSize = 1,
            stable = true,
            affectedCandidateSetVersion = normalized.affectedCandidateSetVersion,
            coarseValueBucket = normalized.coarseValueBucket
        )
        val targetedActions = (ProductCandidateResolver.resolve(
            semantic = semanticContext,
            content = null,
            route = normalized.route
        ) as? ProductCandidateScope.Targeted)?.actions.orEmpty().takeIf { actions ->
            actions.isNotEmpty() && actions.all { action ->
                val spec = AppActionCatalog.spec(action)
                spec.suggestible && spec.sideEffect != SideEffect.TRANSACTION
            }
        }.orEmpty()
        val isPassiveContentEvent = normalized.eventFamily == SemanticEventFamily.CONTENT_STATE_CHANGED
        val isExplicitSettingEvent = normalized.eventFamily == SemanticEventFamily.SETTING_CHANGED
        var journeyStart: JourneyStartRequest? = null
        var committedChangeSetId: String? = null
        var targetedOffer: PendingSuggestionOffer? = null
        val lock = profileLocks.getOrPut(activeProfile) { Mutex() }
        lock.withLock {
            if (!profileReady || profileKey != activeProfile || sessionId != activeSession) return false
            if (isExplicitSettingEvent &&
                settingSubmissionOrder < latestAppliedSettingSubmission.get()
            ) return false
            if (isExplicitSettingEvent) latestAppliedSettingSubmission.set(settingSubmissionOrder)
            val existingSet = dao.mergeableSemanticChangeSet(
                activeProfile,
                activeSession,
                normalized.route,
                nowElapsed - SEMANTIC_CHANGE_SET_WINDOW_MS
            )
            val changeSet = mergeChangeSet(activeProfile, activeSession, normalized, existingSet)
            committedChangeSetId = changeSet.changeSetId
            val sequenceNo = sequence.incrementAndGet()
            val preserveActiveTargetedContext = isPassiveContentEvent &&
                hasActiveTargetedContext(nowElapsed)
            val opportunityGeneration = if (isExplicitSettingEvent) {
                contextGeneration.incrementAndGet().also {
                    cancelSuggestionDeliveryState(
                        reason = "SEMANTIC_CONTEXT_CHANGED"
                    )
                }
            } else {
                contextGeneration.get()
            }
            database.transaction {
                if (!preserveActiveTargetedContext) {
                    dao.latestPending(activeProfile)?.let { pending ->
                        dao.censorPendingCas(
                            pending.decisionId,
                            activeProfile,
                            "CENSORED_SEMANTIC_CONTEXT_CHANGED",
                            normalized.eventId
                        )
                        deadlineJobs.remove(pending.decisionId)?.cancel()
                    }
                }
                dao.insertSemanticEvent(
                    normalized.toEntity(
                        activeProfile,
                        activeSession,
                        sequenceNo,
                        changeSet.changeSetId,
                        changeSet.mutationBatchId
                    )
                )
                dao.upsertSemanticChangeSet(changeSet.copy(state = "PREPARED"))
            }
            if (!isPassiveContentEvent) {
                latestSemanticContext = semanticContext.copy(changeSetSize = changeSet.mutationCount)
                latestSemanticOccurredElapsedMs = normalized.occurredAtElapsedMs
                latestAffectedCandidateCount = targetedActions.size
            } else if (!preserveActiveTargetedContext) {
                latestSemanticContext = null
                latestSemanticOccurredElapsedMs = 0L
                latestAffectedCandidateCount = 0
            }
            if (preserveActiveTargetedContext) return@withLock
            val journeyDecisionId = UUID.randomUUID().toString()
            val journeySnapshot = snapshot(System.currentTimeMillis(), lastAction).copy(journeyPosition = 0)
            val journeyInput = FeatureExtractor.build(
                activeProfile,
                journeyDecisionId,
                journeySnapshot,
                AppActionCatalog.businessAvailability(journeySnapshot.route)
            )
            val promotion = promotionManager.snapshot(activeProfile)
            journeyStart = JourneyStartRequest(
                activeProfile,
                activeSession,
                sequenceNo,
                normalized,
                changeSet.changeSetId,
                journeyInput,
                normalized.tainted,
                bucket(promotion.holdoutSeed, journeyDecisionId, "journey") < JOURNEY_HOLDOUT_PERCENT
            )
            val deliveryLane = if (isExplicitSettingEvent && targetedActions.isNotEmpty()) {
                SuggestionDeliveryLane.TARGETED
            } else {
                SuggestionDeliveryLane.ORDINARY_NEXT_ACTION
            }
            val pending = createOpportunityLocked(
                OpportunityTrigger.SEMANTIC_MUTATION_COMMITTED,
                lastAction,
                normalized.eventId,
                sequenceNo,
                deliveryLane,
                opportunityGeneration
            )
            if (pending != null && deliveryLane == SuggestionDeliveryLane.TARGETED) {
                val activeContext = ActiveTargetedContext(
                    decisionId = pending.decisionId,
                    contextGeneration = opportunityGeneration,
                    deadlineElapsedMs = pending.labelDeadlineElapsedMs,
                    changeSetId = changeSet.changeSetId,
                    targetActions = targetedActions
                )
                activeTargetedContext = activeContext
                when {
                    pending.isPromotionHoldout -> updateDeliveryDiagnostics(
                        lane = SuggestionDeliveryLane.TARGETED,
                        offer = null,
                        rejectionReason = SuggestionDeliveryBlockReason.HOLDOUT.name
                    )
                    changeSet.changeSetId in exposedTargetedChangeSets -> updateDeliveryDiagnostics(
                        lane = SuggestionDeliveryLane.TARGETED,
                        offer = null,
                        rejectionReason = "CHANGE_SET_ALREADY_EXPOSED"
                    )
                    !normalized.tainted -> targetedOffer = PendingSuggestionOffer(
                        decisionId = pending.decisionId,
                        contextGeneration = opportunityGeneration,
                        lane = SuggestionDeliveryLane.TARGETED,
                        targetActions = targetedActions,
                        earliestDisplayElapsedMs =
                            nowElapsed + SuggestionPolicy.TARGETED_CHANGE_DEBOUNCE_MS,
                        deadlineElapsedMs = pending.labelDeadlineElapsedMs
                    )
                }
            }
        }
        targetedOffer?.let(::queueTargetedSuggestionOffer)
        journeyStart?.let { request ->
            explicitMilestoneTarget(request.event)?.let { target ->
                journeyEngine.onExplicitMilestone(activeProfile, target, request.event.eventId)
            }
            journeyEngine.start(
                request.profileKey,
                request.sessionId,
                processInstanceId,
                request.sequenceNo,
                request.event.eventId,
                request.input,
                request.tainted,
                request.holdout
            )
        }
        committedChangeSetId?.let { dao.updateSemanticChangeSetState(it, "COMMITTED") }
        return true
    }

    fun recordCommittedMutationAsync(
        mutationId: MutationId,
        oldValue: Any?,
        newValue: Any?,
        source: ActionSource = ActionSource.ORGANIC,
        coarseValueBucket: String? = null,
        domainOverride: SemanticDomain? = null,
        familyOverride: SemanticEventFamily? = null
    ) {
        val settingSubmissionOrder = settingSubmissionSequence.incrementAndGet()
        scope.launch {
            recordCommittedMutation(
                mutationId,
                oldValue,
                newValue,
                source,
                coarseValueBucket,
                domainOverride,
                familyOverride,
                settingSubmissionOrder
            )
        }
    }

    fun onContentStateChanged(
        domain: SemanticDomain,
        state: ContentStateBucket,
        freshnessBucket: Int,
        resultCount: ResultCountBucket,
        errorType: ErrorTypeBucket = ErrorTypeBucket.NONE
    ) {
        val previous = latestContentContext
        val next = ContentContext(domain, state, freshnessBucket.coerceIn(0, 7), resultCount, errorType)
        if (previous == next) return
        latestContentContext = next
        recordCommittedMutationAsync(
            MutationId.CONTENT_STATE_CHANGED,
            previous?.state,
            next.state,
            coarseValueBucket = next.state.name,
            domainOverride = domain,
            familyOverride = SemanticEventFamily.CONTENT_STATE_CHANGED
        )
    }

    suspend fun rankLocalPresets(domain: SemanticDomain): List<PresetCandidate> {
        val activeProfile = profileKey ?: return emptyList()
        if (!profileReady) return emptyList()
        val promotion = promotionManager.snapshot(activeProfile)
        val decisionId = UUID.randomUUID().toString()
        val holdout = bucket(promotion.holdoutSeed, decisionId, "preset") < PRESET_HOLDOUT_PERCENT
        return presetRankingEngine.rank(activeProfile, domain, snapshot(System.currentTimeMillis(), lastAction), holdout)
    }

    suspend fun markPresetRecommendationExposed(candidate: PresetCandidate): PresetInteractionToken? {
        val activeProfile = profileKey ?: return null
        if (!profileReady) return null
        val token = presetRankingEngine.markRecommendationExposed(activeProfile, candidate) ?: return null
        hideSuggestion()
        journeyEngine.censorProfile(activeProfile, "CENSORED_PRESET_RECOMMENDATION_EXPOSURE")
        return token
    }

    suspend fun recordNaturalPresetSubmission(
        submission: PresetSubmission,
        interactionToken: PresetInteractionToken?,
        candidatesAtOpportunity: List<PresetCandidate>
    ): String? {
        val activeProfile = profileKey ?: return null
        if (!profileReady) return null
        val presetId = presetRankingEngine.recordNaturalSubmission(
            activeProfile,
            submission,
            snapshot(System.currentTimeMillis(), lastAction),
            interactionToken,
            candidatesAtOpportunity
        )
        recordCommittedMutation(
            when (submission.domain) {
                SemanticDomain.FREE_CLASSROOM -> MutationId.FREE_CLASSROOM_QUERY_COMMITTED
                SemanticDomain.GRADE -> MutationId.GRADE_FILTER_COMMITTED
                SemanticDomain.LOST_FOUND -> MutationId.LOST_FOUND_FILTER_COMMITTED
                SemanticDomain.ELECTRICITY -> MutationId.ELECTRICITY_PRESET_COMMITTED
                else -> MutationId.FLOW_STEP_COMPLETED
            },
            oldValue = null,
            newValue = "COMMITTED",
            coarseValueBucket = "COMMITTED",
            domainOverride = submission.domain,
            familyOverride = SemanticEventFamily.QUERY_FILTER_COMMITTED
        )
        return presetId
    }

    suspend fun applyLocalPreset(candidate: PresetCandidate): AppliedPreset? {
        val activeProfile = profileKey ?: return null
        val applied = presetRankingEngine.applyRecommendation(activeProfile, candidate) ?: return null
        recordCommittedMutation(
            MutationId.LOCAL_PRESET_APPLIED,
            oldValue = null,
            newValue = "APPLIED",
            source = ActionSource.SUGGESTION,
            coarseValueBucket = "APPLIED",
            domainOverride = candidate.domain,
            familyOverride = SemanticEventFamily.LOCAL_PRESET_APPLIED
        )
        return applied
    }

    fun expirePresetInteractionAsync(token: PresetInteractionToken?) {
        val activeProfile = profileKey ?: return
        scope.launch { presetRankingEngine.expireInteraction(activeProfile, token) }
    }

    fun recordRemovedPresetAsync(token: PresetInteractionToken) {
        val activeProfile = profileKey ?: return
        scope.launch {
            presetRankingEngine.recordRemovedRecommendation(
                activeProfile,
                token,
                snapshot(System.currentTimeMillis(), lastAction)
            )
        }
    }

    suspend fun prepareVisibleIntervention(
        decisionId: String,
        action: AppActionId,
        type: String,
        source: ActionSource,
        route: String?,
        ttlMillis: Long = 10_000L,
        allowHoldoutInvalidation: Boolean = false,
        requestedExecutionId: String? = null,
        expectedContextGeneration: Long? = null,
        deliveryLane: SuggestionDeliveryLane? = null
    ): ProductExecutionLeaseEntity? {
        val activeProfile = profileKey ?: return null
        if (expectedContextGeneration != null && expectedContextGeneration != contextGeneration.get()) return null
        val lock = profileLocks.getOrPut(activeProfile) { Mutex() }
        return lock.withLock {
            if (expectedContextGeneration != null && expectedContextGeneration != contextGeneration.get()) {
                return@withLock null
            }
            val pending = dao.pending(decisionId) ?: return@withLock null
            if (pending.profileKey != activeProfile || pending.resolutionStatus != "PENDING" ||
                (pending.isPromotionHoldout && !allowHoldoutInvalidation)
            ) return@withLock null
            val elapsed = SystemClock.elapsedRealtime()
            if (elapsed >= pending.labelDeadlineElapsedMs) return@withLock null
            val epoch = executionEpoch.incrementAndGet()
            val executionId = requestedExecutionId ?: UUID.randomUUID().toString()
            val lease = ProductExecutionLeaseEntity(
                executionId,
                decisionId,
                activeProfile,
                requireNotNull(sessionId),
                processInstanceId,
                action.stableId,
                type,
                source.name,
                route,
                profileGeneration.get(),
                loginGeneration.get(),
                sequence.get(),
                epoch,
                elapsed,
                elapsed + ttlMillis,
                "PREPARED"
            )
            if (!dao.prepareProductExecution(
                    decisionId,
                    activeProfile,
                    "PREPARED_$type",
                    lease,
                    allowPreparing = deliveryLane == SuggestionDeliveryLane.TARGETED
                )
            ) {
                return@withLock null
            }
            deadlineJobs.remove(decisionId)?.cancel()
            lease
        }
    }

    suspend fun consumeIntervention(executionId: String): ProductExecutionLeaseEntity? {
        val lease = dao.lease(executionId) ?: return null
        if (!foreground || !interactive || lease.profileKey != profileKey ||
            lease.processInstanceId != processInstanceId ||
            lease.profileGeneration != profileGeneration.get() ||
            lease.loginGeneration != loginGeneration.get()
        ) return null
        val now = SystemClock.elapsedRealtime()
        if (lease.executionEpoch != executionEpoch.get() || dao.consumeLease(executionId, now) != 1) return null
        return lease.copy(state = "CONSUMED")
    }

    private suspend fun showSuggestion(
        offer: PendingSuggestionOffer,
        action: AppActionId,
        probability: Float
    ): Boolean {
        if (!preferencesManager.personalizationEnabled.first()) return false
        val spec = AppActionCatalog.spec(action)
        if (!spec.suggestible || spec.sideEffect == SideEffect.TRANSACTION || !foreground || !interactive ||
            _sensitiveUiVisible.value || _suggestionOverlayBlocked.value || externalSuggestionHostBlocked ||
            !isSuggestionSurfaceAllowed(lastRoute) || _uiState.value != PredictionUiState.Hidden
        ) return false
        val activeProfile = profileKey ?: return false
        if (offer.contextGeneration != contextGeneration.get() ||
            pendingSuggestionOffer != offer || action !in offer.targetActions
        ) return false
        val pending = dao.pending(offer.decisionId) ?: return false
        if (pending.profileKey != activeProfile || pending.resolutionStatus != "PENDING" ||
            pending.isPromotionHoldout || SystemClock.elapsedRealtime() >= pending.labelDeadlineElapsedMs
        ) return false
        if (!isActionAvailable(action, pending)) return false
        val executionId = UUID.randomUUID().toString()
        _uiState.value = PredictionUiState.Suggestion(
            executionId,
            offer.decisionId,
            offer.contextGeneration,
            offer.lane,
            action,
            spec.title,
            spec.reasonLabel,
            when {
                probability >= 0.75f -> ConfidenceBucket.HIGH
                probability >= 0.50f -> ConfidenceBucket.MEDIUM
                else -> ConfidenceBucket.LOW
            },
            exposureConfirmed = false,
            shownAtElapsedMs = 0L,
            expiresAtElapsedMs = 0L,
            visibilityPaused = false
        )
        return true
    }

    suspend fun confirmSuggestionVisible(executionId: String): Boolean {
        val activeProfile = profileKey ?: return false
        val offered = _uiState.value as? PredictionUiState.Suggestion ?: return false
        if (offered.executionId != executionId) return false
        if (offered.exposureConfirmed) return true
        val activeOffer = pendingSuggestionOffer
        if (!SuggestionPolicy.canConfirmExposure(
                offer = activeOffer,
                decisionId = offered.decisionId,
                contextGeneration = offered.contextGeneration,
                currentGeneration = contextGeneration.get(),
                enteredVisiblePopup = true
            ) || !foreground || !interactive || _suggestionOverlayBlocked.value ||
            externalSuggestionHostBlocked ||
            !isSuggestionSurfaceAllowed(lastRoute)
        ) {
            clearPendingOfferIfCurrent(activeOffer)
            hideSuggestionIfCurrent(executionId)
            return false
        }
        val spec = AppActionCatalog.spec(offered.action)
        val lease = prepareVisibleIntervention(
            offered.decisionId,
            offered.action,
            "SUGGESTION",
            ActionSource.SUGGESTION,
            spec.route,
            requestedExecutionId = executionId,
            expectedContextGeneration = offered.contextGeneration,
            deliveryLane = offered.deliveryLane
        ) ?: run {
            clearPendingOfferIfCurrent(activeOffer)
            hideSuggestionIfCurrent(executionId)
            return false
        }
        if (consumeIntervention(lease.executionId) == null) {
            clearPendingOfferIfCurrent(activeOffer)
            hideSuggestionIfCurrent(executionId)
            return false
        }
        profileKey?.let { journeyEngine.censorProfile(it, "CENSORED_RECOMMENDATION_EXPOSURE") }
        val shownAtElapsedMs = SystemClock.elapsedRealtime()
        val current = _uiState.value as? PredictionUiState.Suggestion
        if (current?.executionId != executionId) return false
        _uiState.value = current.copy(
            exposureConfirmed = true,
            shownAtElapsedMs = shownAtElapsedMs,
            expiresAtElapsedMs = shownAtElapsedMs + SUGGESTION_VISIBLE_TTL_MS
        )
        clearPendingOfferIfCurrent(activeOffer)
        if (current.deliveryLane == SuggestionDeliveryLane.TARGETED) {
            lastTargetedSuggestionElapsedMs = shownAtElapsedMs
            activeTargetedContext
                ?.takeIf { it.decisionId == current.decisionId }
                ?.changeSetId
                ?.let(exposedTargetedChangeSets::add)
        }
        lastOrdinarySuggestionElapsedMs = shownAtElapsedMs
        telemetryAggregateStore.recordDelivery(
            profileKey = activeProfile,
            lane = current.deliveryLane,
            event = TelemetryDeliveryEvent.ENTERED_VISIBLE_SURFACE,
            latencyMs = dao.pending(current.decisionId)?.let { shownAtElapsedMs - it.createdAtElapsedMs }
        )
        updateDeliveryDiagnostics(
            lane = current.deliveryLane,
            offer = null,
            rejectionReason = null
        )
        prefetchCoordinator.prefetchSuggestedAction(
            action = current.action,
            holdout = false,
            foreground = foreground
        )
        scheduleSuggestionExpiry(executionId)
        return true
    }

    fun pauseSuggestionVisibility(executionId: String) {
        val current = _uiState.value as? PredictionUiState.Suggestion ?: return
        if (current.executionId != executionId || !current.exposureConfirmed) return
        suggestionVisibilityGeneration.incrementAndGet()
        suggestionExpiryJob?.cancel()
        suggestionExpiryJob = null
        _uiState.value = current.copy(visibilityPaused = true)
    }

    fun restartSuggestionVisibility(executionId: String) {
        val current = _uiState.value as? PredictionUiState.Suggestion ?: return
        if (current.executionId != executionId || !current.exposureConfirmed) return
        val restartedAtElapsedMs = SystemClock.elapsedRealtime()
        _uiState.value = current.copy(
            shownAtElapsedMs = restartedAtElapsedMs,
            expiresAtElapsedMs = restartedAtElapsedMs + SUGGESTION_VISIBLE_TTL_MS,
            visibilityPaused = false
        )
        scheduleSuggestionExpiry(executionId)
    }

    private fun setDiagnosticsObservationActive(active: Boolean) {
        if (diagnosticsObservationActive == active) return
        diagnosticsObservationActive = active
        val current = _uiState.value as? PredictionUiState.Suggestion ?: return
        if (!current.exposureConfirmed) return
        if (active) {
            pauseSuggestionVisibility(current.executionId)
        } else if (current.visibilityPaused) {
            restartSuggestionVisibility(current.executionId)
        }
    }

    fun hideSuggestion() {
        suggestionVisibilityGeneration.incrementAndGet()
        suggestionExpiryJob?.cancel()
        suggestionExpiryJob = null
        _uiState.value = PredictionUiState.Hidden
    }

    private fun hideSuggestionIfCurrent(executionId: String) {
        val current = _uiState.value as? PredictionUiState.Suggestion ?: return
        if (current.executionId == executionId) hideSuggestion()
    }

    fun setSensitiveUiVisible(visible: Boolean) {
        _sensitiveUiVisible.value = visible
        _suggestionOverlayBlocked.value = visible
        if (visible) {
            cancelSuggestionDeliveryState("SENSITIVE_UI")
        }
    }

    fun setInlineSensitiveUiVisible(visible: Boolean) {
        _sensitiveUiVisible.value = visible
        if (visible && _uiState.value == PredictionUiState.Hidden) {
            cancelSuggestionRetry("INLINE_SENSITIVE_UI")
        }
    }

    fun setSuggestionHostBlocked(blocked: Boolean) {
        if (externalSuggestionHostBlocked == blocked) return
        externalSuggestionHostBlocked = blocked
        if (blocked) {
            cancelSuggestionDeliveryState("HOST_SAFETY_GATE")
        }
    }

    fun dismissSuggestionByUser() {
        val current = _uiState.value as? PredictionUiState.Suggestion
        val activeProfile = profileKey
        if (current != null && current.exposureConfirmed && activeProfile != null) {
            scope.launch {
                telemetryAggregateStore.recordDelivery(
                    activeProfile,
                    current.deliveryLane,
                    TelemetryDeliveryEvent.DISMISSED
                )
            }
        }
        cancelSuggestionDeliveryState("DISMISSED_BY_USER")
    }

    suspend fun acceptSuggestion(executionId: String): AppActionId? {
        val state = _uiState.value as? PredictionUiState.Suggestion ?: return null
        if (state.executionId != executionId || !state.exposureConfirmed) return null
        val rewardedPending = dao.pending(state.decisionId)?.takeIf {
            it.profileKey == profileKey &&
                !it.isPromotionHoldout &&
                it.resolutionStatus == "INVALIDATED_INTERVENTION_PREPARED" &&
                it.interventionState == "PREPARED_SUGGESTION"
        }
        profileKey?.let { activeProfile ->
            telemetryAggregateStore.recordDelivery(
                activeProfile,
                state.deliveryLane,
                TelemetryDeliveryEvent.CLICKED
            )
        }
        hideSuggestion()
        AppActionCatalog.spec(state.action).route?.let(::suppressNextRoute)
        recordActionIntent(
            state.action,
            ActionSource.SUGGESTION,
            AppActionCatalog.spec(state.action).route,
            deferNextOpportunity = true
        )
        rewardedPending?.let { pending ->
            scope.launch { recordAcceptedSuggestionReward(pending, state.action, state.deliveryLane) }
        }
        return state.action
    }

    private fun scheduleSuggestionExpiry(executionId: String) {
        val generation = suggestionVisibilityGeneration.incrementAndGet()
        suggestionExpiryJob?.cancel()
        suggestionExpiryJob = scope.launch {
            delay(SUGGESTION_VISIBLE_TTL_MS)
            val current = _uiState.value as? PredictionUiState.Suggestion
            if (suggestionVisibilityGeneration.get() == generation &&
                current?.executionId == executionId && !current.visibilityPaused
            ) {
                _uiState.value = PredictionUiState.Hidden
                suggestionExpiryJob = null
                profileKey?.let { activeProfile ->
                    telemetryAggregateStore.recordDelivery(
                        activeProfile,
                        current.deliveryLane,
                        TelemetryDeliveryEvent.TIMED_OUT
                    )
                }
                updateDeliveryDiagnostics(current.deliveryLane, null, null)
            }
        }
    }

    suspend fun authorizeUserPreferencePaymentQr(): Boolean {
        val activeProfile = profileKey ?: return false
        val pending = dao.latestPending(activeProfile)
        if (pending == null) {
            recordActionIntent(AppActionId.OPEN_PAYMENT_QR, ActionSource.USER_PREFERENCE, null)
            return true
        }
        val lease = prepareVisibleIntervention(
            pending.decisionId,
            AppActionId.OPEN_PAYMENT_QR,
            "PAYMENT_QR_USER_PREFERENCE",
            ActionSource.USER_PREFERENCE,
            null,
            allowHoldoutInvalidation = true
        ) ?: return false
        if (consumeIntervention(lease.executionId) == null) return false
        recordActionIntent(AppActionId.OPEN_PAYMENT_QR, ActionSource.USER_PREFERENCE, null)
        return true
    }

    suspend fun clearLearningRecord() {
        val activeProfile = profileKey
        if (activeProfile == null) {
            databaseCompatibilityStore.clearLegacyLearningDatabase()
            return
        }
        val account = activeAccountIdentifier
        bootstrapTrainingDataManager.revoke(activeProfile)
        telemetryManager.revoke(activeProfile, deleteRemote = true)
        stopSession("CLEARED_BY_USER", clearProfile = true)
        databaseCompatibilityStore.clearLegacyLearningDatabase()
        profileKey = null
        if (account != null) startProfile(account)
    }

    suspend fun logoutAndClear() {
        val activeProfile = profileKey
        if (activeProfile == null) {
            databaseCompatibilityStore.clearLegacyLearningDatabase()
            return
        }
        bootstrapTrainingDataManager.revoke(activeProfile)
        preferencesManager.setBootstrapTrainingEnabled(activeProfile, false)
        telemetryManager.setConsent(activeProfile, enabled = false)
        _telemetryConsentEnabled.value = false
        stopSession("LOGOUT", clearProfile = true)
        databaseCompatibilityStore.clearLegacyLearningDatabase()
        profileKey = null
        activeAccountIdentifier = null
    }

    suspend fun setTelemetryConsent(enabled: Boolean) {
        val activeProfile = profileKey ?: return
        val generation = promotionManager.snapshot(activeProfile).modelGeneration
        telemetryManager.setConsent(activeProfile, enabled, generation)
        _telemetryConsentEnabled.value = enabled
    }

    suspend fun cancelPredictivePrefetch() {
        prefetchCoordinator.cancelAll()
    }

    suspend fun stopSession(censorReason: String = "SESSION_ENDED", clearProfile: Boolean = false) =
        profileLifecycleMutex.withLock {
            stopSessionLocked(censorReason, clearProfile)
        }

    private suspend fun stopSessionLocked(censorReason: String, clearProfile: Boolean) {
        profileReady = false
        val activeProfile = profileKey ?: return
        censorActive(censorReason)
        deadlineJobs.values.forEach(Job::cancel)
        deadlineJobs.clear()
        trainer.cancelProfile(activeProfile)
        journeyEngine.censorProfile(activeProfile, censorReason)
        dao.cancelProfileLeases(activeProfile)
        paymentQrCommands.clear()
        insertLifecycleEvent("SESSION_ENDED", ActionSource.SYSTEM)
        if (clearProfile) {
            journeyEngine.clearProfile(activeProfile)
            presetRankingEngine.clearProfile(activeProfile)
            dao.deleteProfileLearningState(activeProfile)
            modelStateStore.reset(activeProfile)
        }
        profileGeneration.incrementAndGet()
        loginGeneration.incrementAndGet()
        sessionId = null
        recentActions.clear()
        recentActionSources.clear()
        organicActionHistory.clear()
        latestSemanticContext = null
        latestSemanticOccurredElapsedMs = 0L
        latestContentContext = null
        latestAffectedCandidateCount = 0
        contextGeneration.set(0L)
        latestAppliedSettingSubmission.set(0L)
        lastTargetedSuggestionElapsedMs = 0L
        lastOrdinarySuggestionElapsedMs = 0L
        pendingSuggestionOffer = null
        activeTargetedContext = null
        exposedTargetedChangeSets.clear()
        suggestionRetryGeneration.incrementAndGet()
        suggestionRetryJob?.cancel()
        suggestionRetryJob = null
        externalSuggestionHostBlocked = false
        lastAction = null
        lastRoute = null
        routeChangedElapsedMs = 0L
        foregroundGapBucket = null
        hideSuggestion()
        _telemetryConsentEnabled.value = false
        _sensitiveUiVisible.value = false
        _suggestionOverlayBlocked.value = false
        _diagnostics.value = RuntimeDiagnosticsState()
    }

    suspend fun runIdleTrainingSlice(): TrainingSliceResult {
        if (!foreground || !interactive) return TrainingSliceResult(false, profileKey, 0, 0, null, null, 0, "NOT_FOREGROUND_IDLE")
        val battery = context.getSystemService(BatteryManager::class.java)
        if (battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.let { it in 0..14 } == true) {
            return TrainingSliceResult(false, profileKey, 0, 0, null, null, 0, "LOW_BATTERY")
        }
        val power = context.getSystemService(PowerManager::class.java)
        if (power?.isPowerSaveMode == true) {
            return TrainingSliceResult(false, profileKey, 0, 0, null, null, 0, "POWER_SAVER")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            power?.currentThermalStatus?.let { it >= PowerManager.THERMAL_STATUS_SEVERE } == true
        ) {
            return TrainingSliceResult(false, profileKey, 0, 0, null, null, 0, "THERMAL_LIMIT")
        }
        val result = trainer.runIdleSlice(25)
        journeyEngine.runIdleTrainingSlice(15)
        presetRankingEngine.runIdleTrainingSlice(10)
        _diagnostics.value = _diagnostics.value.copy(lastTraining = result)
        return result
    }

    suspend fun sanitizedDiagnosticsSnapshot(): SanitizedDiagnosticsSnapshot {
        val activeProfile = profileKey ?: return SanitizedDiagnosticsSnapshot()
        val learning = dao.learningState(activeProfile)
        val model = modelStateStore.state(activeProfile)
        val pendingJourney = dao.latestPendingJourney(activeProfile)
        val presetSamples = dao.recentPresetTrainingSamples(activeProfile, 512)
        val naturalMass = presetSamples.filter { it.naturalHoldoutEligible }.sumOf { it.sampleWeight.toDouble() }
        val assistedPositiveMass = presetSamples.filter { !it.naturalHoldoutEligible && it.label }.sumOf { it.sampleWeight.toDouble() }
        val assistedNegativeMass = presetSamples.filter { !it.naturalHoldoutEligible && !it.label }.sumOf { it.sampleWeight.toDouble() }
        val weakRatio = if (presetSamples.isEmpty()) 0.0 else presetSamples.count { !it.naturalHoldoutEligible }.toDouble() / presetSamples.size
        return SanitizedDiagnosticsSnapshot(
            trainingSamples = dao.naturalTrainingSampleCount(activeProfile),
            organicNonNoneSamples = dao.organicNonNoneTrainingSampleCount(activeProfile),
            suggestionAcceptedSamples = dao.suggestionAcceptedTrainingSampleCount(activeProfile),
            actionFamilies = dao.trainingActionFamilyCount(activeProfile),
            statLearningStartedDay = learning?.statLearningStartedEpochDay,
            tinyTrainingStartedDay = learning?.tinyTrainingStartedEpochDay,
            trainingRevision = model.training.trainingRevision,
            candidateCheckpoint = model.candidate?.checkpointId?.take(8),
            activeChecksum = model.active.checksum.take(8),
            modelSizeBytes = modelStateStore.modelSizeBytes(activeProfile),
            promotionWindows = dao.promotionWindows(activeProfile, 5).map {
                "${it.stage} ${it.startEpochDay}..${it.endEpochDay} n=${it.pairedSampleCount} ECE=${"%.3f".format(it.ece)} ${if (it.qualified) "PASS" else "FAIL"}"
            },
            recentTimeline = dao.recentEvents(activeProfile, 20).map {
                "#${it.sequenceNo} ${it.eventType} ${it.actionId ?: "--"} ${it.source}"
            },
            pendingTelemetryReports = dao.pendingTelemetryReportCount(activeProfile),
            telemetryProtocolVersion = com.ahu.ahutong.personalization.telemetry.TELEMETRY_SERVER_SCHEMA_VERSION,
            telemetryV3Aggregates = dao.recentTelemetryV3AggregateWindows(activeProfile, 10).map {
                "${it.task} ${it.state} n=${it.sampleCount} holdout=${it.naturalHoldoutSampleCount} " +
                    "days=${it.windowStartEpochDay}..${it.windowEndEpochDay}"
            },
            recentSemanticEvents = dao.recentSemanticEvents(activeProfile, 20).map {
                "#${it.sequenceNo} ${it.eventFamily}/${it.domainId} ${it.semanticId} ${it.changeKind}${if (it.tainted) " TAINTED" else ""}"
            },
            recentSemanticChangeSets = dao.recentSemanticChangeSets(activeProfile, 10).map {
                "${it.changeSetId.take(8)} n=${it.mutationCount} ${it.state} " +
                    "candidates=${it.affectedActionIdsCsv.ifBlank { "NONE" }}"
            },
            pendingJourney = pendingJourney?.let {
                "${it.journeyId.take(8)} steps=${it.observedActionCount}/${it.maximumActions} remaining=${(it.deadlineElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0)}ms ${it.interventionState}"
            },
            journeyProbabilities = pendingJourney?.let(::sanitizedJourneyProbabilities).orEmpty(),
            presetCandidates = presetRankingEngine.sanitizedDiagnostics(activeProfile),
            journeyTrainingSamples = dao.journeyTrainingSampleCount(activeProfile),
            presetTrainingSamples = dao.presetTrainingSampleCount(activeProfile),
            presetFeedbackDiagnostics = listOf(
                "mass natural=${"%.2f".format(naturalMass)} assisted+=${"%.2f".format(assistedPositiveMass)} assisted-=${"%.2f".format(assistedNegativeMass)}",
                "weakPoolRatio=${"%.3f".format(weakRatio)} evaluationSource=ORGANIC_NATURAL_HOLDOUT"
            ) + dao.recentPresetInteractions(activeProfile, 8).map {
                "${it.domainId} ${it.candidateId.take(8)} state=${it.state} weight=${it.feedbackWeight ?: "--"}"
            },
            taskStates = dao.taskModelStates(activeProfile).map {
                "${it.modelTask} ${it.stage} lambda=${it.mixedLambda} n=${it.validSampleCount} " +
                    "eval=${it.lastEvaluationSeq} health=${it.healthState}"
            }
        )
    }

    private fun sanitizedJourneyProbabilities(
        pending: com.ahu.ahutong.personalization.storage.PendingJourneyEntity
    ): List<String> {
        val stat = BinaryCodec.floats(pending.statProbabilities)
        val tiny = pending.tinyProbabilities?.let(BinaryCodec::floats)
        return com.ahu.ahutong.personalization.journey.JourneyGoalCatalog.outputIds.indices
            .map { index ->
                val effective = (1f - pending.mixedLambda) * stat[index] +
                    pending.mixedLambda * (tiny?.get(index) ?: stat[index])
                com.ahu.ahutong.personalization.journey.JourneyGoalCatalog.outputIds[index] to
                    Triple(stat[index], tiny?.get(index), effective)
            }
            .sortedByDescending { it.second.third }
            .take(5)
            .map { (outputId, values) ->
                "$outputId stat=${"%.3f".format(values.first)} " +
                    "tiny=${values.second?.let { "%.3f".format(it) } ?: "--"} " +
                    "effective=${"%.3f".format(values.third)}"
            }
    }

    private suspend fun createOpportunity(trigger: OpportunityTrigger, previousAction: AppActionId?) {
        if (!profileReady) return
        val activeProfile = profileKey ?: return
        val lock = profileLocks.getOrPut(activeProfile) { Mutex() }
        lock.withLock {
            val triggerEventId = UUID.randomUUID().toString()
            val seq = sequence.incrementAndGet()
            createOpportunityLocked(trigger, previousAction, triggerEventId, seq)
        }
    }

    private suspend fun createOpportunityLocked(
        trigger: OpportunityTrigger,
        previousAction: AppActionId?,
        triggerEventId: String,
        sequenceNo: Long,
        deliveryLane: SuggestionDeliveryLane = SuggestionDeliveryLane.ORDINARY_NEXT_ACTION,
        opportunityContextGeneration: Long = contextGeneration.get()
    ): PendingPredictionEntity? {
        val activeProfile = profileKey ?: return null
        val activeSession = sessionId ?: return null
        if (!profileReady || !foreground || !interactive) return null
        if (deliveryLane != SuggestionDeliveryLane.TARGETED && hasActiveTargetedContext()) {
            updateDeliveryDiagnostics(
                lane = deliveryLane,
                offer = pendingSuggestionOffer,
                rejectionReason = "TARGETED_CONTEXT_HAS_PRIORITY"
            )
            return null
        }
        dao.latestPending(activeProfile)?.let { existing ->
            dao.censorPendingCas(existing.decisionId, activeProfile, "CENSORED_SUPERSEDED")
            deadlineJobs.remove(existing.decisionId)?.cancel()
        }
        val nowEpoch = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val decisionId = UUID.randomUUID().toString()
        val contextSnapshot = snapshot(nowEpoch, previousAction)
        val input = FeatureExtractor.build(
            activeProfile,
            decisionId,
            contextSnapshot,
            AppActionCatalog.businessAvailability(contextSnapshot.route)
        )
        val promotion = promotionManager.snapshot(activeProfile)
        val model = modelStateStore.state(activeProfile)
        val learningEligible = !taintedChain
        val holdout = learningEligible && bucket(promotion.holdoutSeed, decisionId, "promotion") < HOLDOUT_PERCENT
        val candidateHoldout = deliveryLane != SuggestionDeliveryLane.TARGETED &&
            learningEligible && model.candidate != null &&
            bucket(promotion.holdoutSeed, decisionId, "candidate") < CANDIDATE_HOLDOUT_PERCENT
        val preparing = PendingPredictionEntity(
            decisionId,
            activeProfile,
            activeSession,
            processInstanceId,
            sequenceNo,
            triggerEventId,
            previousAction?.stableId,
            nowEpoch,
            elapsed,
            elapsed + trigger.labelWindowMillis,
            LABEL_WINDOW_POLICY_VERSION,
            input.featureSchemaVersion,
            input.outputSchemaVersion,
            input.actionCatalogVersion,
            input.features.toBytes(),
            input.businessAvailability.toBytes(),
            input.inputDigest,
            ContextSnapshotCodec.encode(input.snapshot),
            "PREPARING",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            model.active.checkpointId,
            model.active.checksum,
            model.candidate?.checkpointId,
            model.candidate?.checksum,
            null,
            null,
            null,
            null,
            promotion.stage.name,
            promotion.tier.name,
            promotion.tier.lambda,
            holdout || candidateHoldout,
            if (learningEligible) "NONE" else "TAINTED_CHAIN",
            "PENDING",
            null,
            null
        )
        dao.insertPending(preparing)
        _diagnostics.value = _diagnostics.value.copy(
            decisionId = decisionId,
            preparationState = "PREPARING",
            previousAction = previousAction?.stableId,
            deadlineElapsedMs = preparing.labelDeadlineElapsedMs,
            stage = promotion.stage.name,
            tier = promotion.tier.name,
            lambda = promotion.tier.lambda,
            activeCheckpoint = model.active.checkpointId
        )
        scope.launch {
            preparePrediction(
                preparing,
                input,
                promotion,
                candidateHoldout,
                opportunityContextGeneration
            )
        }
        return preparing
    }

    suspend fun setBootstrapTrainingConsent(enabled: Boolean, includeHistorical: Boolean = false) {
        val activeProfile = profileKey ?: return
        preferencesManager.setBootstrapTrainingEnabled(activeProfile, enabled)
        bootstrapTrainingDataManager.setConsent(activeProfile, enabled, includeHistorical)
    }

    private suspend fun preparePrediction(
        preparing: PendingPredictionEntity,
        input: PredictionInput,
        promotion: PromotionSnapshot,
        candidateHoldout: Boolean,
        opportunityContextGeneration: Long
    ) {
        try {
            val statDeferred = scope.async { statPredictor.predict(input) }
            val tinyDeferred = scope.async { tinyPredictor.predict(input) }
            val recentDeferred = scope.async { recentBaseline.predict(input) }
            val timeDeferred = scope.async { timeBaseline.predict(input) }
            val stat = statDeferred.await()
            val tiny = runCatching { tinyDeferred.await() }.getOrNull()
            val recent = recentDeferred.await()
            val time = timeDeferred.await()
            val candidate = if (candidateHoldout) runCatching { tinyPredictor.predictCandidate(input) }.getOrNull() else null
            val lock = profileLocks.getOrPut(preparing.profileKey) { Mutex() }
            lock.withLock {
                if (profileKey != preparing.profileKey || sessionId != preparing.sessionId) return@withLock
                val current = dao.pending(preparing.decisionId) ?: return@withLock
                val now = SystemClock.elapsedRealtime()
                val model = modelStateStore.state(preparing.profileKey)
                if (current.preparationState != "PREPARING" || current.resolutionStatus != "PENDING" ||
                    current.processInstanceId != processInstanceId || current.inputDigest != input.inputDigest ||
                    current.activeCheckpointId != model.active.checkpointId || now >= current.labelDeadlineElapsedMs
                    || opportunityContextGeneration != contextGeneration.get()
                ) {
                    if (current.resolutionStatus == "PENDING") {
                        dao.censorPendingCas(current.decisionId, current.profileKey, "CENSORED_PREPARATION_STALE")
                    }
                    return@withLock
                }
                val generationReset = promotionManager.recordInferenceAttempt(
                    preparing.profileKey,
                    preparing.activeCheckpointId,
                    tiny != null,
                    if (tiny == null) "TINY_FORWARD_FAILED" else null
                )
                if (generationReset) {
                    telemetryManager.reconcileProfile(
                        preparing.profileKey,
                        promotionManager.snapshot(preparing.profileKey).modelGeneration
                    )
                }
                val boundCandidate = candidate.takeIf {
                    current.candidateCheckpointId == model.candidate?.checkpointId &&
                        current.candidateCheckpointChecksum == model.candidate?.checksum
                }
                val effective = composeDecision(stat, tiny, input, preparing.profileKey, promotion)
                val activated = current.copy(
                    preparationState = "PENDING",
                    statProbabilities = BinaryCodec.floats(stat.probabilities),
                    tinyProbabilities = tiny?.let { BinaryCodec.floats(it.probabilities) },
                    recentBaselineProbabilities = BinaryCodec.floats(recent.probabilities),
                    timeBaselineProbabilities = BinaryCodec.floats(time.probabilities),
                    statModelVersion = stat.modelVersion,
                    tinyModelVersion = tiny?.modelVersion,
                    candidateProbabilities = boundCandidate?.let { BinaryCodec.floats(it.probabilities) },
                    candidateInferenceNanos = boundCandidate?.inferenceNanos,
                    statInferenceNanos = stat.inferenceNanos,
                    tinyInferenceNanos = tiny?.inferenceNanos,
                    preparationFailure = if (tiny == null) "TINY_FORWARD_FAILED" else null,
                    effectiveProbabilities = BinaryCodec.floats(effective.probabilities)
                )
                dao.updatePending(activated)
                registerDeadline(activated)
                val productScope = ProductCandidateResolver.resolve(
                    input.snapshot.semanticContext,
                    input.snapshot.contentContext,
                    input.snapshot.route
                )
                _diagnostics.value = _diagnostics.value.copy(
                    preparationState = "PENDING",
                    statProbabilities = stat.asMap(),
                    tinyProbabilities = tiny?.asMap().orEmpty(),
                    effectiveProbabilities = effective.asMap(),
                    lastFailure = activated.preparationFailure,
                    candidateScope = when (productScope) {
                        ProductCandidateScope.Ordinary -> "ORDINARY"
                        ProductCandidateScope.Suppress -> "SUPPRESS"
                        is ProductCandidateScope.Targeted -> "TARGETED"
                    },
                    targetedActions = (productScope as? ProductCandidateScope.Targeted)
                        ?.actions
                        ?.map(AppActionId::stableId)
                        ?.toSet()
                        .orEmpty(),
                    candidateRejectionReason = if (productScope == ProductCandidateScope.Suppress) {
                        "SEMANTIC_OR_CONTENT_SCOPE_SUPPRESSED"
                    } else null
                )
                val productProbabilities = productScopedProbabilities(effective, input.snapshot, preparing.profileKey)
                scope.launch { prefetchCoordinator.consider(productProbabilities, activated.isPromotionHoldout, foreground) }
                scope.launch {
                    maybeOfferSuggestion(activated, effective, opportunityContextGeneration)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val current = dao.pending(preparing.decisionId)
            if (current?.resolutionStatus == "PENDING") {
                dao.censorPendingCas(current.decisionId, current.profileKey, "CENSORED_PREPARATION_FAILED")
            }
            _diagnostics.value = _diagnostics.value.copy(lastFailure = error.javaClass.simpleName)
        }
    }

    private suspend fun resolvePending(
        pending: PendingPredictionEntity,
        targetOutputId: String,
        resolvedByEventId: String,
        labelSource: String,
        family: ActionFamily
    ) {
        if (pending.resolutionStatus != "PENDING" || pending.preparationState != "PENDING" || pending.interventionState != "NONE") return
        val targetIndex = AppActionCatalog.outputIndex[targetOutputId] ?: return
        val availability = BinaryCodec.booleans(pending.availabilityMask)
        if (!availability.getOrElse(targetIndex) { false }) {
            dao.resolvePendingCas(
                pending.decisionId,
                pending.profileKey,
                "INVALIDATED_AVAILABILITY_MISMATCH",
                null,
                resolvedByEventId
            )
            deadlineJobs.remove(pending.decisionId)?.cancel()
            _diagnostics.value = _diagnostics.value.copy(lastFailure = "AVAILABILITY_MISMATCH")
            return
        }
        val input = try {
            restoreInput(pending)
        } catch (error: RuntimeException) {
            dao.resolvePendingCas(
                pending.decisionId,
                pending.profileKey,
                "CENSORED_CONTEXT_DECODE_FAILED",
                null,
                resolvedByEventId
            )
            deadlineJobs.remove(pending.decisionId)?.cancel()
            _diagnostics.value = _diagnostics.value.copy(lastFailure = "CONTEXT_SNAPSHOT_DECODE_FAILED")
            return
        }
        database.transaction {
            check(
                dao.resolvePendingCas(
                    pending.decisionId,
                    pending.profileKey,
                    "RESOLVED",
                    targetOutputId,
                    resolvedByEventId
                ) == 1
            ) { "prediction opportunity was already resolved or intervened" }
            evaluator.resolve(pending, targetOutputId)?.let { evaluation ->
                telemetryAggregateStore.contribute(evaluation)
            }
            statPredictor.update(input, targetOutputId)
            trainer.enqueue(
                OrganicTrainingSample(
                    input = input,
                    targetOutputId = targetOutputId,
                    actionFamily = family,
                    labelSource = labelSource,
                    availabilityMask = pending.availabilityMask,
                    deliveryLane = SuggestionDeliveryLane.ORDINARY_NEXT_ACTION.name,
                    naturalHoldoutEligible = true
                )
            )
            val learning = dao.learningState(pending.profileKey)
            dao.upsertLearningState(
                LearningStateEntity(
                    pending.profileKey,
                    learning?.statLearningStartedEpochDay ?: LocalDate.now(ZoneOffset.UTC).toEpochDay(),
                    learning?.tinyTrainingStartedEpochDay,
                    learning?.lastCommittedBatchId,
                    learning?.lastTrainingNanos ?: 0L,
                    learning?.lastTrainingLoss,
                    learning?.lastGradientNorm
                )
            )
        }
        deadlineJobs.remove(pending.decisionId)?.cancel()
        _diagnostics.value = _diagnostics.value.copy(lastResolution = targetOutputId)
        promotionManager.evaluate(pending.profileKey)
        telemetryManager.reconcileProfile(
            pending.profileKey,
            promotionManager.snapshot(pending.profileKey).modelGeneration
        )
        telemetryManager.onNewEvaluation(pending.profileKey)
        val retentionDays = preferencesManager.behaviorRetentionDays.first().coerceIn(7, 30)
        dao.deleteExpiredEvents(
            pending.profileKey,
            System.currentTimeMillis() - retentionDays * 86_400_000L
        )
        dao.trimEvents(pending.profileKey, MAX_BEHAVIOR_EVENTS)
        dao.trimShadowEvaluations(pending.profileKey, MAX_SHADOW_EVALUATIONS)
        dao.trimPromotionWindows(pending.profileKey, MAX_PROMOTION_WINDOWS)
        dao.trimPromotionTransitionJournals(pending.profileKey, MAX_PROMOTION_JOURNALS)
        scope.launch {
            delay(TRAINING_IDLE_GRACE_MS)
            runIdleTrainingSlice()
        }
    }

    private fun registerDeadline(pending: PendingPredictionEntity) {
        deadlineJobs.remove(pending.decisionId)?.cancel()
        deadlineJobs[pending.decisionId] = scope.launch {
            val remaining = (pending.labelDeadlineElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            delay(remaining + DEADLINE_RACE_GRACE_MS)
            val lock = profileLocks.getOrPut(pending.profileKey) { Mutex() }
            lock.withLock {
                val current = dao.pending(pending.decisionId) ?: return@withLock
                if (current.processInstanceId != processInstanceId) return@withLock
                if (!OrganicLabelPolicy.isTimeoutEligible(
                        foreground && interactive,
                        current.interventionState,
                        current.preparationState,
                        current.resolutionStatus
                    )
                ) {
                    if (current.resolutionStatus == "PENDING" && current.interventionState == "TAINTED_CHAIN") {
                        dao.censorPendingCas(
                            current.decisionId,
                            current.profileKey,
                            "CENSORED_TAINTED_CHAIN_TIMEOUT"
                        )
                    }
                    return@withLock
                }
                resolvePending(
                    current,
                    AppActionCatalog.NONE_OUTPUT_ID,
                    UUID.randomUUID().toString(),
                    "INTERVENTION_FREE_TIMEOUT",
                    ActionFamily.TECHNICAL
                )
            }
        }
    }

    private suspend fun censorActive(reason: String) {
        val activeProfile = profileKey ?: return
        cancelSuggestionDeliveryState(reason)
        val lock = profileLocks.getOrPut(activeProfile) { Mutex() }
        lock.withLock {
            dao.latestPending(activeProfile)?.let { pending ->
                dao.censorPendingCas(pending.decisionId, activeProfile, reason)
                deadlineJobs.remove(pending.decisionId)?.cancel()
            }
            executionEpoch.incrementAndGet()
        dao.cancelProfileLeases(activeProfile)
        prefetchCoordinator.cancelAll()
        }
    }

    private suspend fun maybeOfferSuggestion(
        pending: PendingPredictionEntity,
        effective: NextActionProbabilityVector,
        opportunityContextGeneration: Long
    ) {
        if (pending.isPromotionHoldout) {
            val holdoutLane = activeTargetedContext
                ?.takeIf { it.decisionId == pending.decisionId }
                ?.let { SuggestionDeliveryLane.TARGETED }
                ?: SuggestionDeliveryLane.ORDINARY_NEXT_ACTION
            telemetryAggregateStore.recordDelivery(
                pending.profileKey,
                holdoutLane,
                TelemetryDeliveryEvent.OPPORTUNITY
            )
            telemetryAggregateStore.recordDelivery(
                pending.profileKey,
                holdoutLane,
                TelemetryDeliveryEvent.BLOCKED,
                SuggestionDeliveryBlockReason.HOLDOUT.name
            )
            updateDeliveryDiagnostics(
                lane = activeTargetedContext
                    ?.takeIf { it.decisionId == pending.decisionId }
                    ?.let { SuggestionDeliveryLane.TARGETED },
                offer = null,
                rejectionReason = SuggestionDeliveryBlockReason.HOLDOUT.name
            )
            return
        }
        val targetedContext = activeTargetedContext?.takeIf {
            it.decisionId == pending.decisionId &&
                it.contextGeneration == opportunityContextGeneration &&
                it.contextGeneration == contextGeneration.get()
        }
        if (targetedContext != null) {
            pendingSuggestionOffer?.takeIf {
                it.decisionId == pending.decisionId &&
                    it.contextGeneration == opportunityContextGeneration &&
                    it.lane == SuggestionDeliveryLane.TARGETED
            }?.let { attemptTargetedSuggestionOffer(it) }
            return
        }
        val snapshot = ContextSnapshotCodec.decode(pending.contextSnapshotJson)
        val candidateScope = ProductCandidateResolver.resolve(
            semantic = snapshot.semanticContext,
            content = snapshot.contentContext,
            route = snapshot.route
        )
        if (candidateScope == ProductCandidateScope.Suppress) return
        val targetedActions = (candidateScope as? ProductCandidateScope.Targeted)?.actions
        val organicActionIds = dao.organicNonNoneTrainingActionIds(pending.profileKey).toSet()
        val ordinaryAssessment = if (candidateScope == ProductCandidateScope.Ordinary) {
            SuggestionPolicy.assessOrdinaryNextAction(effective, organicActionIds)
        } else {
            null
        }
        if (ordinaryAssessment != null) {
            _diagnostics.value = _diagnostics.value.copy(
                ordinaryCandidateProbability = ordinaryAssessment.candidateProbability,
                ordinaryCompetitorAction = ordinaryAssessment.strongestCompetitorId,
                ordinaryCompetitorProbability = ordinaryAssessment.strongestCompetitorProbability,
                ordinaryProbabilityMargin = ordinaryAssessment.probabilityMargin,
                candidateRejectionReason = ordinaryAssessment.rejectionReason?.name
            )
            ordinaryAssessment.rejectionReason?.let { reason ->
                telemetryAggregateStore.recordDelivery(
                    pending.profileKey,
                    SuggestionDeliveryLane.ORDINARY_NEXT_ACTION,
                    TelemetryDeliveryEvent.OPPORTUNITY
                )
                telemetryAggregateStore.recordDelivery(
                    pending.profileKey,
                    SuggestionDeliveryLane.ORDINARY_NEXT_ACTION,
                    TelemetryDeliveryEvent.BLOCKED,
                    reason.name
                )
            }
        }
        val candidates = if (ordinaryAssessment != null) {
            listOfNotNull(ordinaryAssessment.candidate)
        } else {
            SuggestionPolicy.rankedCandidates(
                effective,
                organicActionIds,
                requireOrganicHistory = false
            ).filter { targetedActions == null || it.action in targetedActions }
        }
        val next = candidates.firstOrNull()?.let {
            ActionPredictionProposal(PredictionTask.NEXT_ACTION, it.action, it.probability, pending.decisionId)
        }
        val journey = journeyEngine.currentRecommendation(pending.profileKey)?.takeIf {
            targetedActions == null || it.action in targetedActions
        }?.let {
            ActionPredictionProposal(PredictionTask.JOURNEY_GOAL, it.action, it.probability, pending.decisionId)
        }
        val proposal = (PredictionArbiter.choose(0, journey, next) as? ArbitratedPrediction.Action)?.proposal
        if (proposal == null) {
            _diagnostics.value = _diagnostics.value.copy(
                candidateRejectionReason = ordinaryAssessment?.rejectionReason?.name ?: if (candidateScope is ProductCandidateScope.Targeted) {
                    "TARGETED_ACTION_UNAVAILABLE_OR_UNSAFE"
                } else {
                    "NO_ORGANICALLY_ELIGIBLE_ACTION"
                }
            )
            return
        }
        val lane = when (proposal.task) {
            PredictionTask.JOURNEY_GOAL -> SuggestionDeliveryLane.ORDINARY_JOURNEY
            PredictionTask.NEXT_ACTION -> SuggestionDeliveryLane.ORDINARY_NEXT_ACTION
            PredictionTask.PRESET_RANKING -> return
        }
        telemetryAggregateStore.recordDelivery(
            pending.profileKey,
            lane,
            TelemetryDeliveryEvent.MODEL_GATE_PASSED
        )
        val offer = PendingSuggestionOffer(
            decisionId = proposal.decisionId,
            contextGeneration = opportunityContextGeneration,
            lane = lane,
            targetActions = setOf(proposal.action),
            earliestDisplayElapsedMs = SystemClock.elapsedRealtime(),
            deadlineElapsedMs = pending.labelDeadlineElapsedMs
        )
        if (!registerPendingOffer(offer)) {
            updateDeliveryDiagnostics(
                lane = lane,
                offer = offer,
                rejectionReason = "HIGHER_PRIORITY_OFFER_ACTIVE"
            )
            return
        }
        attemptSuggestionOffer(offer, pending, proposal.action, proposal.probability, allowRetry = false)
    }

    private suspend fun recordAcceptedSuggestionReward(
        pending: PendingPredictionEntity,
        action: AppActionId,
        deliveryLane: SuggestionDeliveryLane
    ) {
        val targetIndex = AppActionCatalog.outputIndex[action.stableId] ?: return
        if (!BinaryCodec.booleans(pending.availabilityMask).getOrElse(targetIndex) { false }) return
        val input = runCatching { restoreInput(pending) }.getOrElse {
            _diagnostics.value = _diagnostics.value.copy(lastFailure = "SUGGESTION_REWARD_CONTEXT_DECODE_FAILED")
            return
        }
        val weight = TrainingFeedbackPolicy.SUGGESTION_POSITIVE_WEIGHT
        statPredictor.update(input, action.stableId, weight.toDouble())
        trainer.enqueue(
            OrganicTrainingSample(
                input = input,
                targetOutputId = action.stableId,
                actionFamily = AppActionCatalog.spec(action).family,
                labelSource = TrainingFeedbackPolicy.SUGGESTION_ACCEPTED,
                availabilityMask = pending.availabilityMask,
                deliveryLane = deliveryLane.name,
                naturalHoldoutEligible = false
            )
        )
        telemetryAggregateStore.recordDelivery(
            pending.profileKey,
            deliveryLane,
            TelemetryDeliveryEvent.COMPLETED
        )
        telemetryAggregateStore.recordDelivery(
            pending.profileKey,
            deliveryLane,
            TelemetryDeliveryEvent.ASSISTED_REWARD,
            assistedRewardWeight = weight.toDouble()
        )
    }

    @Synchronized
    private fun queueTargetedSuggestionOffer(offer: PendingSuggestionOffer) {
        if (!foreground || !interactive || _sensitiveUiVisible.value ||
            _suggestionOverlayBlocked.value || externalSuggestionHostBlocked ||
            !isSuggestionSurfaceAllowed(lastRoute)
        ) {
            profileKey?.let { activeProfile ->
                scope.launch {
                    telemetryAggregateStore.recordDelivery(
                        activeProfile,
                        SuggestionDeliveryLane.TARGETED,
                        TelemetryDeliveryEvent.OPPORTUNITY
                    )
                    telemetryAggregateStore.recordDelivery(
                        activeProfile,
                        SuggestionDeliveryLane.TARGETED,
                        TelemetryDeliveryEvent.BLOCKED,
                        SuggestionDeliveryBlockReason.SAFETY_GATE.name
                    )
                }
            }
            updateDeliveryDiagnostics(
                SuggestionDeliveryLane.TARGETED,
                null,
                SuggestionDeliveryBlockReason.SAFETY_GATE.name
            )
            return
        }
        profileKey?.let { activeProfile ->
            scope.launch {
                telemetryAggregateStore.recordDelivery(
                    activeProfile,
                    SuggestionDeliveryLane.TARGETED,
                    TelemetryDeliveryEvent.MODEL_GATE_PASSED
                )
            }
        }
        if (!registerPendingOffer(offer)) return
        scheduleTargetedRetry(offer, offer.earliestDisplayElapsedMs, "TARGETED_DEBOUNCE")
    }

    @Synchronized
    private fun registerPendingOffer(offer: PendingSuggestionOffer): Boolean {
        if (offer.contextGeneration != contextGeneration.get()) return false
        val existing = pendingSuggestionOffer
        if (existing != null && existing != offer &&
            existing.contextGeneration == offer.contextGeneration &&
            existing.lane.priority > offer.lane.priority
        ) return false
        pendingSuggestionOffer = offer
        profileKey?.let { activeProfile ->
            scope.launch {
                telemetryAggregateStore.recordDelivery(
                    activeProfile,
                    offer.lane,
                    TelemetryDeliveryEvent.OPPORTUNITY
                )
            }
        }
        updateDeliveryDiagnostics(offer.lane, offer, null)
        return true
    }

    @Synchronized
    private fun clearPendingOfferIfCurrent(offer: PendingSuggestionOffer?) {
        if (offer == null || pendingSuggestionOffer != offer) return
        pendingSuggestionOffer = null
        suggestionRetryGeneration.incrementAndGet()
        suggestionRetryJob?.cancel()
        suggestionRetryJob = null
    }

    @Synchronized
    private fun cancelSuggestionRetry(reason: String) {
        val offer = pendingSuggestionOffer
        suggestionRetryGeneration.incrementAndGet()
        suggestionRetryJob?.cancel()
        suggestionRetryJob = null
        if (_uiState.value == PredictionUiState.Hidden) {
            pendingSuggestionOffer = null
        }
        updateDeliveryDiagnostics(offer?.lane, null, reason)
    }

    @Synchronized
    private fun cancelSuggestionDeliveryState(reason: String) {
        suggestionRetryGeneration.incrementAndGet()
        suggestionRetryJob?.cancel()
        suggestionRetryJob = null
        pendingSuggestionOffer = null
        activeTargetedContext = null
        hideSuggestion()
        updateDeliveryDiagnostics(null, null, reason)
    }

    @Synchronized
    private fun hasActiveTargetedContext(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Boolean {
        val active = activeTargetedContext ?: return false
        val valid = active.contextGeneration == contextGeneration.get() &&
            nowElapsedMs < active.deadlineElapsedMs
        if (!valid) {
            activeTargetedContext = null
            pendingSuggestionOffer?.takeIf { it.decisionId == active.decisionId }
                ?.let(::clearPendingOfferIfCurrent)
        }
        return valid
    }

    @Synchronized
    private fun scheduleTargetedRetry(
        offer: PendingSuggestionOffer,
        retryAtElapsedMs: Long,
        reason: String
    ) {
        if (offer.lane != SuggestionDeliveryLane.TARGETED ||
            offer.contextGeneration != contextGeneration.get()
        ) return
        val now = SystemClock.elapsedRealtime()
        if (retryAtElapsedMs >= offer.deadlineElapsedMs || now >= offer.deadlineElapsedMs) {
            clearPendingOfferIfCurrent(offer)
            profileKey?.let { activeProfile ->
                scope.launch {
                    telemetryAggregateStore.recordDelivery(
                        activeProfile,
                        SuggestionDeliveryLane.TARGETED,
                        TelemetryDeliveryEvent.BLOCKED,
                        SuggestionDeliveryBlockReason.EXPIRED.name
                    )
                }
            }
            updateDeliveryDiagnostics(
                SuggestionDeliveryLane.TARGETED,
                null,
                SuggestionDeliveryBlockReason.EXPIRED.name
            )
            return
        }
        val scheduled = offer.copy(earliestDisplayElapsedMs = maxOf(offer.earliestDisplayElapsedMs, retryAtElapsedMs))
        pendingSuggestionOffer = scheduled
        val retryGeneration = suggestionRetryGeneration.incrementAndGet()
        suggestionRetryJob?.cancel()
        suggestionRetryJob = scope.launch {
            delay((scheduled.earliestDisplayElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
            if (retryGeneration != suggestionRetryGeneration.get() ||
                pendingSuggestionOffer != scheduled ||
                scheduled.contextGeneration != contextGeneration.get()
            ) return@launch
            attemptTargetedSuggestionOffer(scheduled)
        }
        updateDeliveryDiagnostics(
            SuggestionDeliveryLane.TARGETED,
            scheduled,
            reason.takeIf { scheduled.earliestDisplayElapsedMs > now }
        )
    }

    private suspend fun attemptTargetedSuggestionOffer(offer: PendingSuggestionOffer) {
        if (pendingSuggestionOffer != offer || offer.contextGeneration != contextGeneration.get()) return
        val pending = dao.pending(offer.decisionId) ?: run {
            clearPendingOfferIfCurrent(offer)
            return
        }
        if (pending.resolutionStatus != "PENDING") {
            clearPendingOfferIfCurrent(offer)
            return
        }
        val availableTargets = offer.targetActions.filterTo(linkedSetOf()) { isActionAvailable(it, pending) }
        if (availableTargets.isEmpty()) {
            clearPendingOfferIfCurrent(offer)
            telemetryAggregateStore.recordDelivery(
                pending.profileKey,
                SuggestionDeliveryLane.TARGETED,
                TelemetryDeliveryEvent.BLOCKED,
                "TARGETED_ACTION_UNAVAILABLE_OR_UNSAFE"
            )
            updateDeliveryDiagnostics(
                SuggestionDeliveryLane.TARGETED,
                null,
                "TARGETED_ACTION_UNAVAILABLE_OR_UNSAFE"
            )
            return
        }
        val selected = if (availableTargets.size == 1) {
            availableTargets.single() to 1f
        } else {
            selectModelRankedTarget(pending, availableTargets)
        }
        if (selected == null) {
            val retryAt = SystemClock.elapsedRealtime() + SuggestionPolicy.OCCUPIED_RETRY_DELAY_MS
            scheduleTargetedRetry(offer, retryAt, "WAITING_FOR_MODEL_RANKING")
            return
        }
        attemptSuggestionOffer(
            offer = offer,
            pending = pending,
            action = selected.first,
            probability = selected.second,
            allowRetry = true
        )
    }

    private suspend fun attemptSuggestionOffer(
        offer: PendingSuggestionOffer,
        pending: PendingPredictionEntity,
        action: AppActionId,
        probability: Float,
        allowRetry: Boolean
    ) {
        if (pendingSuggestionOffer != offer ||
            offer.contextGeneration != contextGeneration.get() ||
            pending.decisionId != offer.decisionId
        ) return
        val currentSuggestion = _uiState.value as? PredictionUiState.Suggestion
        if (currentSuggestion?.decisionId == offer.decisionId &&
            currentSuggestion.contextGeneration == offer.contextGeneration
        ) return
        val now = SystemClock.elapsedRealtime()
        val safetyAllowed = preferencesManager.personalizationEnabled.first() &&
            foreground && interactive && !_sensitiveUiVisible.value &&
            !_suggestionOverlayBlocked.value && !externalSuggestionHostBlocked &&
            isSuggestionSurfaceAllowed(lastRoute)
        val assessment = SuggestionPolicy.assessDelivery(
            offer = offer,
            currentGeneration = contextGeneration.get(),
            nowElapsedMs = now,
            lastTargetedShownElapsedMs = lastTargetedSuggestionElapsedMs,
            lastOrdinaryShownElapsedMs = lastOrdinarySuggestionElapsedMs,
            currentLane = currentSuggestion?.deliveryLane,
            holdout = pending.isPromotionHoldout,
            safetyAllowed = safetyAllowed,
            entryAvailable = isActionAvailable(action, pending)
        )
        if (!assessment.canDisplay) {
            if (allowRetry && assessment.retryAtElapsedMs != null) {
                scheduleTargetedRetry(
                    offer,
                    assessment.retryAtElapsedMs,
                    assessment.blockReason?.name ?: SuggestionDeliveryBlockReason.INTERVAL.name
                )
            } else {
                clearPendingOfferIfCurrent(offer)
                telemetryAggregateStore.recordDelivery(
                    pending.profileKey,
                    offer.lane,
                    TelemetryDeliveryEvent.BLOCKED,
                    assessment.blockReason?.name
                )
                updateDeliveryDiagnostics(offer.lane, null, assessment.blockReason?.name)
            }
            return
        }
        if (currentSuggestion != null && offer.lane.priority > currentSuggestion.deliveryLane.priority) {
            hideSuggestion()
        }
        if (showSuggestion(offer, action, probability)) {
            updateDeliveryDiagnostics(offer.lane, offer, null)
        } else if (allowRetry) {
            scheduleTargetedRetry(
                offer,
                SystemClock.elapsedRealtime() + SuggestionPolicy.OCCUPIED_RETRY_DELAY_MS,
                "SURFACE_TEMPORARILY_OCCUPIED"
            )
        } else {
            clearPendingOfferIfCurrent(offer)
            telemetryAggregateStore.recordDelivery(
                pending.profileKey,
                offer.lane,
                TelemetryDeliveryEvent.BLOCKED,
                "SURFACE_OR_SAFETY_GATE_REJECTED"
            )
            updateDeliveryDiagnostics(offer.lane, null, "SURFACE_OR_SAFETY_GATE_REJECTED")
        }
    }

    private fun selectModelRankedTarget(
        pending: PendingPredictionEntity,
        allowedTargets: Set<AppActionId>
    ): Pair<AppActionId, Float>? {
        val stat = pending.statProbabilities?.let(BinaryCodec::floats) ?: return null
        val tiny = pending.tinyProbabilities?.let(BinaryCodec::floats)
        return allowedTargets.mapNotNull { action ->
            val index = AppActionCatalog.outputIndex[action.stableId] ?: return@mapNotNull null
            val probability = (1f - pending.mixedLambda) * stat.getOrElse(index) { 0f } +
                pending.mixedLambda * (tiny?.getOrElse(index) { 0f } ?: stat.getOrElse(index) { 0f })
            action to probability
        }.maxWithOrNull(compareBy<Pair<AppActionId, Float>> { it.second }.thenBy { it.first.stableId })
    }

    private fun isActionAvailable(action: AppActionId, pending: PendingPredictionEntity): Boolean {
        val spec = AppActionCatalog.spec(action)
        if (!spec.suggestible || spec.sideEffect == SideEffect.TRANSACTION) return false
        val index = AppActionCatalog.outputIndex[action.stableId] ?: return false
        val pendingAvailability = BinaryCodec.booleans(pending.availabilityMask)
        val currentAvailability = AppActionCatalog.businessAvailability(lastRoute)
        return pendingAvailability.getOrElse(index) { false } &&
            currentAvailability.getOrElse(index) { false }
    }

    private fun updateDeliveryDiagnostics(
        lane: SuggestionDeliveryLane?,
        offer: PendingSuggestionOffer?,
        rejectionReason: String?
    ) {
        val now = SystemClock.elapsedRealtime()
        val activeTargets = offer?.targetActions ?: activeTargetedContext?.targetActions.orEmpty()
        val remaining = offer?.let { (it.earliestDisplayElapsedMs - now).coerceAtLeast(0L) }
            ?: lane?.let {
                val earliest = SuggestionPolicy.earliestDisplayElapsedMs(
                    lane = it,
                    nowElapsedMs = now,
                    lastTargetedShownElapsedMs = lastTargetedSuggestionElapsedMs,
                    lastOrdinaryShownElapsedMs = lastOrdinarySuggestionElapsedMs
                )
                (earliest - now).coerceAtLeast(0L)
            }
            ?: 0L
        _diagnostics.value = _diagnostics.value.copy(
            suggestionDeliveryLane = lane?.name ?: "NONE",
            contextGeneration = contextGeneration.get(),
            suggestionIntervalRemainingMs = remaining,
            suggestionRetryAtElapsedMs = offer?.earliestDisplayElapsedMs?.takeIf { it > now },
            candidateScope = if (lane == SuggestionDeliveryLane.TARGETED) "TARGETED" else _diagnostics.value.candidateScope,
            targetedActions = if (lane == SuggestionDeliveryLane.TARGETED || activeTargets.isNotEmpty()) {
                activeTargets.map(AppActionId::stableId).toSet()
            } else {
                _diagnostics.value.targetedActions
            },
            candidateRejectionReason = rejectionReason
        )
    }

    private suspend fun productScopedProbabilities(
        effective: NextActionProbabilityVector,
        snapshot: ContextSnapshot,
        profileKey: String
    ): Map<String, Float> {
        val scope = ProductCandidateResolver.resolve(
            semantic = snapshot.semanticContext,
            content = snapshot.contentContext,
            route = snapshot.route
        )
        val allowed = when (scope) {
            ProductCandidateScope.Suppress -> emptySet()
            is ProductCandidateScope.Targeted -> scope.actions
            ProductCandidateScope.Ordinary -> dao.organicNonNoneTrainingActionIds(profileKey)
                .mapNotNull(AppActionId::fromStableId)
                .toSet()
        }
        return effective.outputIds.indices.associate { index ->
            val action = AppActionId.fromStableId(effective.outputIds[index])
            effective.outputIds[index] to if (action in allowed) effective.probabilities[index] else 0f
        }
    }

    private suspend fun composeDecision(
        stat: NextActionProbabilityVector,
        tiny: NextActionProbabilityVector?,
        input: PredictionInput,
        profileKey: String,
        promotion: PromotionSnapshot
    ): NextActionProbabilityVector {
        val lambdas = if (tiny == null) emptyMap() else promotionManager.actionLambdas(profileKey)
        val raw = FloatArray(stat.probabilities.size) { index ->
            val lambda = lambdas[stat.outputIds[index]] ?: 0f
            (1f - lambda) * stat.probabilities[index] + lambda * (tiny?.probabilities?.get(index) ?: stat.probabilities[index])
        }
        val masked = FloatArray(raw.size) { if (input.businessAvailability[it]) raw[it] else 0f }
        val sum = masked.sum()
        val probabilities = if (sum > 0f) FloatArray(masked.size) { masked[it] / sum } else stat.probabilities.copyOf()
        return NextActionProbabilityVector(stat.outputIds, probabilities, modelVersion = 1)
    }

    private fun restoreInput(pending: PendingPredictionEntity): PredictionInput {
        val snapshot = ContextSnapshotCodec.decode(pending.contextSnapshotJson)
        return PredictionInput(
            pending.profileKey,
            pending.decisionId,
            pending.featureSchemaVersion,
            pending.outputSchemaVersion,
            pending.actionCatalogVersion,
            com.ahu.ahutong.personalization.context.ImmutableFloatVector(BinaryCodec.floats(pending.features)),
            com.ahu.ahutong.personalization.context.ImmutableBooleanVector(BinaryCodec.booleans(pending.availabilityMask)),
            pending.inputDigest,
            snapshot
        )
    }

    private fun snapshot(epochMs: Long, previousAction: AppActionId?): ContextSnapshot {
        val time = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDateTime()
        val personal = personalFamilySignals()
        val nowElapsed = SystemClock.elapsedRealtime()
        val semantic = latestSemanticContext?.copy(
            ageBucket = latestSemanticOccurredElapsedMs.takeIf { it > 0 }?.let { gapBucket(nowElapsed - it) } ?: 7
        )
        return ContextSnapshot(
            epochDay = Instant.ofEpochMilli(epochMs).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay(),
            minuteOfDay = time.hour * 60 + time.minute,
            dayType = if (time.dayOfWeek.value >= 6) DayType.WEEKEND else DayType.WEEKDAY,
            route = lastRoute,
            previousAction = previousAction,
            recentActions = recentActions.toList(),
            balanceBucket = balanceBucket,
            balanceFresh = balanceFresh,
            examDistanceBucket = examBucket,
            sessionDurationBucket = ((SystemClock.elapsedRealtime() - sessionStartedElapsedMs) / 60_000L).toInt().coerceIn(0, 7),
            semesterWeek = CurrentWeekResolver.resolveLocalConfig(time.toLocalDate())?.config?.week?.coerceIn(1, 24),
            foregroundGapBucket = foregroundGapBucket,
            sessionDepth = recentActions.size,
            pageDwellBucket = routeChangedElapsedMs.takeIf { it > 0 }?.let { gapBucket(nowElapsed - it) },
            recentActionSources = recentActionSources.toList(),
            personalFamilyFrequencies = personal.first,
            personalFamilyRecencies = personal.second,
            semanticContext = semantic,
            contentContext = latestContentContext,
            candidateSetSize = latestAffectedCandidateCount,
            journeyPosition = recentActions.takeLast(5).count {
                it !in com.ahu.ahutong.personalization.journey.JourneyGoalCatalog.shellActions
            }
        )
    }

    private fun personalFamilySignals(): Pair<List<Float>, List<Float>> {
        val frequencies = FloatArray(ActionFamily.entries.size)
        val recencies = FloatArray(ActionFamily.entries.size)
        var total = 0f
        organicActionHistory.reversed().forEachIndexed { index, action ->
            val familyIndex = AppActionCatalog.spec(action).family.ordinal
            val weight = 1f / (1f + index / 8f)
            frequencies[familyIndex] += weight
            total += weight
            if (recencies[familyIndex] == 0f) recencies[familyIndex] = 1f / (index + 1f)
        }
        if (total > 0f) frequencies.indices.forEach { frequencies[it] /= total }
        return frequencies.toList() to recencies.toList()
    }

    private fun gapBucket(durationMs: Long): Int = when {
        durationMs < 5_000L -> 0
        durationMs < 30_000L -> 1
        durationMs < 2 * 60_000L -> 2
        durationMs < 5 * 60_000L -> 3
        durationMs < 15 * 60_000L -> 4
        durationMs < 60 * 60_000L -> 5
        durationMs < 6 * 60 * 60_000L -> 6
        else -> 7
    }

    private fun isSuggestionSurfaceAllowed(route: String?): Boolean = route != null &&
        route != "login" && route != "setup" && route != "splash" && route != "debug" &&
        route != "electricity_pay" && !route.contains("deposit") && !route.contains("recharge")

    private fun event(
        eventId: String,
        actionInstanceId: String,
        profile: String,
        session: String,
        sequenceNo: Long,
        eventType: String,
        action: AppActionId?,
        source: ActionSource,
        elapsed: Long,
        resolvedDecisionId: String?
    ): BehaviorEventEntity {
        val now = System.currentTimeMillis()
        val snapshot = snapshot(now, lastAction)
        return BehaviorEventEntity(
            eventId,
            actionInstanceId,
            profile,
            session,
            processInstanceId,
            sequenceNo,
            eventType,
            action?.stableId,
            source.name,
            now,
            elapsed,
            elapsed - sessionStartedElapsedMs,
            null,
            resolvedDecisionId,
            snapshot.minuteOfDay / 60,
            snapshot.dayType.name,
            snapshot.balanceBucket.name,
            snapshot.examDistanceBucket.name,
            FeatureExtractor.FEATURE_SCHEMA_VERSION
        )
    }

    private fun mergeChangeSet(
        profile: String,
        session: String,
        event: NormalizedSemanticEvent,
        existing: SemanticChangeSetEntity?
    ): SemanticChangeSetEntity {
        val semanticIds = existing?.semanticIdsCsv?.split(',')?.filter(String::isNotBlank).orEmpty()
            .plus(event.semanticId).distinct().sorted()
        val affected = existing?.affectedActionIdsCsv?.split(',')?.filter(String::isNotBlank).orEmpty()
            .plus(event.affectedActionIds.map(AppActionId::stableId)).distinct().sorted()
        return SemanticChangeSetEntity(
            changeSetId = existing?.changeSetId ?: UUID.randomUUID().toString(),
            profileKey = profile,
            sessionId = session,
            route = event.route,
            mutationBatchId = existing?.mutationBatchId ?: event.mutationBatchId,
            firstOccurredAtElapsedMs = existing?.firstOccurredAtElapsedMs ?: event.occurredAtElapsedMs,
            lastOccurredAtElapsedMs = event.occurredAtElapsedMs,
            mutationCount = (existing?.mutationCount ?: 0) + 1,
            semanticIdsCsv = semanticIds.joinToString(","),
            affectedActionIdsCsv = affected.joinToString(","),
            affectedCandidateSetVersion = maxOf(existing?.affectedCandidateSetVersion ?: 0, event.affectedCandidateSetVersion),
            state = "PREPARED"
        )
    }

    private fun NormalizedSemanticEvent.toEntity(
        profile: String,
        session: String,
        sequenceNo: Long,
        changeSetId: String,
        mergedMutationBatchId: String
    ): SemanticEventEntity = SemanticEventEntity(
        eventId,
        profile,
        session,
        sequenceNo,
        eventFamily.name,
        domain.name,
        semanticId,
        changeKind.name,
        coarseValueBucket,
        route,
        affectedCandidateSetVersion,
        source.name,
        committedAtEpochDay,
        occurredAtElapsedMs,
        semanticSchemaVersion,
        tainted,
        mergedMutationBatchId,
        changeSetId
    )

    private fun explicitMilestoneTarget(event: NormalizedSemanticEvent): AppActionId? {
        if (event.eventFamily != SemanticEventFamily.QUERY_FILTER_COMMITTED &&
            event.eventFamily != SemanticEventFamily.FLOW_STEP_COMPLETED
        ) return null
        return event.affectedActionIds.firstOrNull { action ->
            com.ahu.ahutong.personalization.journey.JourneyGoalCatalog.isSafeTerminal(action)
        }
    }

    private suspend fun insertLifecycleEvent(type: String, source: ActionSource) {
        val activeProfile = profileKey ?: return
        val activeSession = sessionId ?: return
        dao.insertEvent(
            event(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                activeProfile,
                activeSession,
                sequence.incrementAndGet(),
                type,
                null,
                source,
                SystemClock.elapsedRealtime(),
                null
            )
        )
    }

    private fun bucket(holdoutSeed: String, decisionId: String, namespace: String): Int {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(holdoutSeed.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal("v1:$namespace:$decisionId".toByteArray(Charsets.UTF_8))
        return (digest[0].toInt() and 0xff) % 100
    }

    private fun NextActionProbabilityVector.asMap(): Map<String, Float> =
        outputIds.indices.associate { outputIds[it] to probabilities[it] }

    private data class RecordedActionOpportunity(
        val profileKey: String,
        val sessionId: String,
        val action: AppActionId,
        val eventId: String,
        val sequenceNo: Long
    )

    private data class JourneyActionObservation(
        val profileKey: String,
        val sessionId: String,
        val action: AppActionId,
        val source: ActionSource,
        val eventId: String,
        val elapsedMs: Long
    )

    private data class JourneyStartRequest(
        val profileKey: String,
        val sessionId: String,
        val sequenceNo: Long,
        val event: NormalizedSemanticEvent,
        val changeSetId: String,
        val input: PredictionInput,
        val tainted: Boolean,
        val holdout: Boolean
    )

    private data class ActiveTargetedContext(
        val decisionId: String,
        val contextGeneration: Long,
        val deadlineElapsedMs: Long,
        val changeSetId: String,
        val targetActions: Set<AppActionId>
    )

    private companion object {
        const val TAG = "PredictionRuntime"
        const val LABEL_WINDOW_POLICY_VERSION = 1
        const val CONTEXT_DEBOUNCE_MS = 30_000L
        const val SEMANTIC_CHANGE_SET_WINDOW_MS = 5_000L
        const val JOURNEY_HOLDOUT_PERCENT = 15
        const val PRESET_HOLDOUT_PERCENT = 15
        const val DEADLINE_RACE_GRACE_MS = 250L
        const val HOLDOUT_PERCENT = 15
        const val CANDIDATE_HOLDOUT_PERCENT = 20
        const val SUGGESTION_VISIBLE_TTL_MS = 12_000L
        const val TRAINING_IDLE_GRACE_MS = 1_500L
        const val MAX_BEHAVIOR_EVENTS = 20_000
        const val MAX_SHADOW_EVALUATIONS = 20_000
        const val MAX_PROMOTION_WINDOWS = 256
        const val MAX_PROMOTION_JOURNALS = 256
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BehaviorRuntimeEntryPoint {
    fun behaviorPredictionRuntime(): BehaviorPredictionRuntime
}
