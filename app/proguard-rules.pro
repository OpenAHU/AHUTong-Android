-keepclasseswithmembernames class * {
    native <methods>;
}
 -keepclassmembers class * extends java.lang.Enum {
     public static **[] values();
     public static ** valueOf(java.lang.String);
 }
 -keepclassmembers class * implements java.io.Serializable {
     static final long serialVersionUID;
     static final java.io.ObjectStreamField[] serialPersistentFields;
     private void writeObject(java.io.ObjectOutputStream);
     private void readObject(java.io.ObjectInputStream);
     java.lang.Object writeReplace();
     java.lang.Object readResolve();
 }
 -keepclasseswithmembernames class * {
      native <methods>;
  }
# Bugly
-dontwarn com.tencent.bugly.**
-keep public class com.tencent.bugly.**{*;}

#Jsoup
-keeppackagenames org.jsoup.nodes
-keepattributes Signature
-keepattributes *Annotation*
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.examples.android.model.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
# These models are deserialized both from the native bridge and from local Gson caches. Field-only
# rules do not prevent vertical class merging, which is unsafe for reflection-based construction.
-keep class com.ahu.ahutong.data.model.** { *; }
# Campus-notice snapshots are encrypted Gson caches. Preserve the complete contracts, including
# generic Map/List element signatures, so cached source statuses cannot become LinkedTreeMap.
-keep class com.ahu.ahutong.data.notice.CampusNotice { *; }
-keep class com.ahu.ahutong.data.notice.CampusNoticeSnapshot { *; }
-keep class com.ahu.ahutong.data.notice.CampusNoticeSourceStatus { *; }
# The automatic electricity query/prompt dates must survive R8 and app upgrades as stable JSON.
-keep class com.ahu.ahutong.electricity.ElectricityAlertMarker { *; }
-keep class com.ahu.ahutong.electricity.ElectricityRoomForecast { *; }
-keep class com.ahu.ahutong.ui.screen.main.ElectricityDepositKt { *; }
-keep class com.ahu.ahutong.ui.screen.main.home.ElectricityPaymentKt { *; }
-keep class com.ahu.ahutong.data.dao.AHUCache { *; }

-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

-keepclassmembers class kotlin.coroutines.SafeContinuation {
    volatile <fields>;
}
#不混淆某个包下的类
-keep class com.ahu.plugin.** {*;}

-dontwarn java.lang.instrument.ClassFileTransformer
-dontwarn sun.misc.SignalHandler
-dontwarn java.lang.instrument.Instrumentation
-dontwarn sun.misc.Signal
-keep class org.jsoup.Connection{*;}
-keep class com.ahu.ahutong.ui.widget.schedule.bean.**{*;}
-keepclassmembers class com.ahu.ahutong.data.model.* {
    private *;
}

# Crawler DTOs are Retrofit/Gson wire contracts. Keeping only their fields still allows R8 to
# merge the owning classes, which can turn a valid login response into a ClassCastException in
# minified builds. Keep the complete contracts so Review/Release authentication behaves like Debug.
-keep class com.ahu.ahutong.data.crawler.model.** { *; }

# 必吃榜（canteen）是 Gson/Retrofit 线上契约：字段名即 JSON 键名，在 -repackageclasses + R8
# full mode 下会被混淆成 a/b/c，类还会被合并（2026-10-13 主工程 release mapping 实测：
# TxnEntry.terminal -> a、WindowReportBody.terminal -> a、WindowMapResponse 无条目）。
# 结果：入站解析成空对象、出站键名全错——上传被服务端当空批吞掉、窗口名上报因缺 terminal 直接
# 422。整包保留，以后新增模型不必再逐条补规则。
-keep class com.ahu.ahutong.data.canteen.** { *; }
# 插件契约：插件按全限定名引用这些类型，宿主 R8 不得改名/裁剪。
# 只保留成员不够——类名被改成 qb:/xz6: 后，插件在 release 下会直接找不到 AhuPlugin。
-keep class com.ahu.ahutong.core.plugin.** { *; }
-keepclassmembers class com.ahu.ahutong.core.plugin.** { *; }
-keep class * implements com.ahu.ahutong.core.plugin.AhuPlugin { public <init>(); }
-dontwarn com.ahu.ahutong.core.plugin.**

# jzh_campus_card 的 plugin.apk 只带自己的 classes.dex；Kotlin / 协程 / Compose
# 均按 SDK 原名引用宿主。这里按该插件 dex 的实际引用面保留（169 个类），不再整包 keep，
# 避免 Compose / material-icons 全量保留带来的包体膨胀。
# 新增插件 API 时，重新扫描 plugin.dex 的类引用并追加。
-keep class androidx.compose.foundation.BackgroundKt { *; }
-keep class androidx.compose.foundation.BorderStroke { *; }
-keep class androidx.compose.foundation.ClickableKt { *; }
-keep class androidx.compose.foundation.OverscrollEffect { *; }
-keep class androidx.compose.foundation.ScrollKt { *; }
-keep class androidx.compose.foundation.ScrollState { *; }
-keep class androidx.compose.foundation.gestures.FlingBehavior { *; }
-keep class androidx.compose.foundation.interaction.MutableInteractionSource { *; }
-keep class androidx.compose.foundation.layout.Arrangement { *; }
-keep class androidx.compose.foundation.layout.Arrangement$Horizontal { *; }
-keep class androidx.compose.foundation.layout.Arrangement$HorizontalOrVertical { *; }
-keep class androidx.compose.foundation.layout.Arrangement$Vertical { *; }
-keep class androidx.compose.foundation.layout.BoxKt { *; }
-keep class androidx.compose.foundation.layout.BoxScope { *; }
-keep class androidx.compose.foundation.layout.BoxScopeInstance { *; }
-keep class androidx.compose.foundation.layout.ColumnKt { *; }
-keep class androidx.compose.foundation.layout.ColumnScope { *; }
-keep class androidx.compose.foundation.layout.ColumnScopeInstance { *; }
-keep class androidx.compose.foundation.layout.PaddingKt { *; }
-keep class androidx.compose.foundation.layout.PaddingValues { *; }
-keep class androidx.compose.foundation.layout.RowKt { *; }
-keep class androidx.compose.foundation.layout.RowScope { *; }
-keep class androidx.compose.foundation.layout.RowScopeInstance { *; }
-keep class androidx.compose.foundation.layout.SizeKt { *; }
-keep class androidx.compose.foundation.layout.SpacerKt { *; }
-keep class androidx.compose.foundation.layout.WindowInsetsPadding_androidKt { *; }
-keep class androidx.compose.foundation.lazy.LazyDslKt { *; }
-keep class androidx.compose.foundation.lazy.LazyItemScope { *; }
-keep class androidx.compose.foundation.lazy.LazyListScope { *; }
-keep class androidx.compose.foundation.lazy.LazyListState { *; }
-keep class androidx.compose.foundation.shape.RoundedCornerShape { *; }
-keep class androidx.compose.foundation.shape.RoundedCornerShapeKt { *; }
-keep class androidx.compose.foundation.text.BasicTextFieldKt { *; }
-keep class androidx.compose.foundation.text.KeyboardActions { *; }
-keep class androidx.compose.foundation.text.KeyboardActions$Companion { *; }
-keep class androidx.compose.foundation.text.KeyboardOptions { *; }
-keep class androidx.compose.foundation.text.KeyboardOptions$Companion { *; }
-keep class androidx.compose.foundation.text.TextAutoSize { *; }
-keep class androidx.compose.material.icons.Icons$AutoMirrored$Outlined { *; }
-keep class androidx.compose.material.icons.automirrored.outlined.ArrowBackKt { *; }
-keep class androidx.compose.material3.AndroidMenu_androidKt { *; }
-keep class androidx.compose.material3.ButtonColors { *; }
-keep class androidx.compose.material3.ButtonElevation { *; }
-keep class androidx.compose.material3.ButtonKt { *; }
-keep class androidx.compose.material3.ColorScheme { *; }
-keep class androidx.compose.material3.ColorSchemeKt { *; }
-keep class androidx.compose.material3.IconButtonColors { *; }
-keep class androidx.compose.material3.IconButtonKt { *; }
-keep class androidx.compose.material3.IconKt { *; }
-keep class androidx.compose.material3.MaterialTheme { *; }
-keep class androidx.compose.material3.MaterialThemeKt { *; }
-keep class androidx.compose.material3.MenuItemColors { *; }
-keep class androidx.compose.material3.ProgressIndicatorKt { *; }
-keep class androidx.compose.material3.Shapes { *; }
-keep class androidx.compose.material3.TextKt { *; }
-keep class androidx.compose.material3.Typography { *; }
-keep class androidx.compose.runtime.Applier { *; }
-keep class androidx.compose.runtime.ComposablesKt { *; }
-keep class androidx.compose.runtime.Composer { *; }
-keep class androidx.compose.runtime.Composer$Companion { *; }
-keep class androidx.compose.runtime.ComposerKt { *; }
-keep class androidx.compose.runtime.CompositionLocal { *; }
-keep class androidx.compose.runtime.CompositionLocalKt { *; }
-keep class androidx.compose.runtime.CompositionLocalMap { *; }
-keep class androidx.compose.runtime.EffectsKt { *; }
-keep class androidx.compose.runtime.IntState { *; }
-keep class androidx.compose.runtime.MutableIntState { *; }
-keep class androidx.compose.runtime.MutableState { *; }
-keep class androidx.compose.runtime.ProvidableCompositionLocal { *; }
-keep class androidx.compose.runtime.ProvidedValue { *; }
-keep class androidx.compose.runtime.RecomposeScopeImplKt { *; }
-keep class androidx.compose.runtime.ScopeUpdateScope { *; }
-keep class androidx.compose.runtime.SnapshotIntStateKt { *; }
-keep class androidx.compose.runtime.SnapshotMutationPolicy { *; }
-keep class androidx.compose.runtime.SnapshotStateKt { *; }
-keep class androidx.compose.runtime.State { *; }
-keep class androidx.compose.runtime.Updater { *; }
-keep class androidx.compose.runtime.internal.ComposableLambda { *; }
-keep class androidx.compose.runtime.internal.ComposableLambdaKt { *; }
-keep class androidx.compose.ui.Alignment { *; }
-keep class androidx.compose.ui.Alignment$Companion { *; }
-keep class androidx.compose.ui.Alignment$Horizontal { *; }
-keep class androidx.compose.ui.Alignment$Vertical { *; }
-keep class androidx.compose.ui.ComposedModifierKt { *; }
-keep class androidx.compose.ui.Modifier { *; }
-keep class androidx.compose.ui.Modifier$Companion { *; }
-keep class androidx.compose.ui.draw.ClipKt { *; }
-keep class androidx.compose.ui.graphics.Brush { *; }
-keep class androidx.compose.ui.graphics.Color { *; }
-keep class androidx.compose.ui.graphics.ColorKt { *; }
-keep class androidx.compose.ui.graphics.Shadow { *; }
-keep class androidx.compose.ui.graphics.Shape { *; }
-keep class androidx.compose.ui.graphics.SolidColor { *; }
-keep class androidx.compose.ui.graphics.drawscope.DrawStyle { *; }
-keep class androidx.compose.ui.graphics.vector.ImageVector { *; }
-keep class androidx.compose.ui.layout.MeasurePolicy { *; }
-keep class androidx.compose.ui.node.ComposeUiNode { *; }
-keep class androidx.compose.ui.node.ComposeUiNode$Companion { *; }
-keep class androidx.compose.ui.platform.AndroidCompositionLocals_androidKt { *; }
-keep class androidx.compose.ui.platform.ComposeView { *; }
-keep class androidx.compose.ui.semantics.Role { *; }
-keep class androidx.compose.ui.text.PlatformTextStyle { *; }
-keep class androidx.compose.ui.text.TextStyle { *; }
-keep class androidx.compose.ui.text.font.FontFamily { *; }
-keep class androidx.compose.ui.text.font.FontStyle { *; }
-keep class androidx.compose.ui.text.font.FontSynthesis { *; }
-keep class androidx.compose.ui.text.font.FontWeight { *; }
-keep class androidx.compose.ui.text.font.FontWeight$Companion { *; }
-keep class androidx.compose.ui.text.input.KeyboardType { *; }
-keep class androidx.compose.ui.text.input.KeyboardType$Companion { *; }
-keep class androidx.compose.ui.text.input.PasswordVisualTransformation { *; }
-keep class androidx.compose.ui.text.input.PlatformImeOptions { *; }
-keep class androidx.compose.ui.text.input.VisualTransformation { *; }
-keep class androidx.compose.ui.text.input.VisualTransformation$Companion { *; }
-keep class androidx.compose.ui.text.intl.LocaleList { *; }
-keep class androidx.compose.ui.text.style.BaselineShift { *; }
-keep class androidx.compose.ui.text.style.LineHeightStyle { *; }
-keep class androidx.compose.ui.text.style.TextAlign { *; }
-keep class androidx.compose.ui.text.style.TextAlign$Companion { *; }
-keep class androidx.compose.ui.text.style.TextDecoration { *; }
-keep class androidx.compose.ui.text.style.TextGeometricTransform { *; }
-keep class androidx.compose.ui.text.style.TextIndent { *; }
-keep class androidx.compose.ui.text.style.TextMotion { *; }
-keep class androidx.compose.ui.text.style.TextOverflow { *; }
-keep class androidx.compose.ui.text.style.TextOverflow$Companion { *; }
-keep class androidx.compose.ui.unit.Dp { *; }
# Runtime plugins can box TextUnit or access its Companion by the original ABI names.
# Keeping TextUnitKt alone still lets R8 rename/remove the boxed value class (Sentry ANDROID-1J).
-keep class androidx.compose.ui.unit.TextUnit { *; }
-keep class androidx.compose.ui.unit.TextUnit$Companion { *; }
-keep class androidx.compose.ui.unit.TextUnitKt { *; }
-keep class androidx.compose.ui.window.PopupProperties { *; }
-keep class kotlin.NoWhenBranchMatchedException { *; }
-keep class kotlin.Pair { *; }
-keep class kotlin.Result { *; }
-keep class kotlin.Result$Companion { *; }
-keep class kotlin.ResultKt { *; }
-keep class kotlin.TuplesKt { *; }
-keep class kotlin.Unit { *; }
-keep class kotlin.collections.ArraysKt { *; }
-keep class kotlin.collections.CollectionsKt { *; }
-keep class kotlin.collections.IntIterator { *; }
-keep class kotlin.collections.MapsKt { *; }
-keep class kotlin.collections.SetsKt { *; }
-keep class kotlin.coroutines.Continuation { *; }
-keep class kotlin.coroutines.CoroutineContext { *; }
-keep class kotlin.coroutines.EmptyCoroutineContext { *; }
-keep class kotlin.coroutines.intrinsics.IntrinsicsKt { *; }
-keep class kotlin.coroutines.jvm.internal.Boxing { *; }
-keep class kotlin.coroutines.jvm.internal.ContinuationImpl { *; }
-keep class kotlin.coroutines.jvm.internal.SpillingKt { *; }
-keep class kotlin.coroutines.jvm.internal.SuspendLambda { *; }
-keep class kotlin.enums.EnumEntries { *; }
-keep class kotlin.enums.EnumEntriesKt { *; }
-keep class kotlin.jvm.functions.Function0 { *; }
-keep class kotlin.jvm.functions.Function1 { *; }
-keep class kotlin.jvm.functions.Function2 { *; }
-keep class kotlin.jvm.functions.Function3 { *; }
-keep class kotlin.jvm.functions.Function4 { *; }
-keep class kotlin.jvm.internal.DefaultConstructorMarker { *; }
-keep class kotlin.jvm.internal.Intrinsics { *; }
-keep class kotlin.ranges.IntRange { *; }
-keep class kotlin.ranges.RangesKt { *; }
-keep class kotlin.text.Charsets { *; }
-keep class kotlin.text.StringsKt { *; }
-keep class kotlinx.coroutines.BuildersKt { *; }
-keep class kotlinx.coroutines.CoroutineDispatcher { *; }
-keep class kotlinx.coroutines.CoroutineScope { *; }
-keep class kotlinx.coroutines.CoroutineStart { *; }
-keep class kotlinx.coroutines.Dispatchers { *; }
-keep class kotlinx.coroutines.Job { *; }
-keep class kotlinx.coroutines.sync.Mutex { *; }
-keep class kotlinx.coroutines.sync.MutexKt { *; }

# Gson records for encrypted, per-account GMIS timetable and term caches.
-keep class com.ahu.ahutong.data.schedule.gmis.GmisTerm { *; }
-keep class com.ahu.ahutong.data.schedule.gmis.GmisCourse { *; }
-keep class com.ahu.ahutong.data.schedule.gmis.GmisSection { *; }
-keep class com.ahu.ahutong.data.schedule.gmis.GmisTimetable { *; }
-keep class com.ahu.ahutong.data.crawler.gmis.GmisCacheCodec$* { *; }

# 自定义课程按账号+学期以 Gson 持久化。保留完整类与字段，尤其是 slots: List<CustomCourseSlot>
# 的泛型签名；否则 R8 会把签名退化成 raw ArrayList，Gson 只能产出 LinkedTreeMap，
# 读取时在 normalized() 里触发 ClassCastException（release 实测）。
-keep class com.ahu.ahutong.data.schedule.CustomCourse { *; }
-keep class com.ahu.ahutong.data.schedule.CustomCourseSlot { *; }

# Payment view models contain a small number of file-local wire DTOs. Keep only the DTO naming
# families rather than the ViewModels themselves, so R8 can still optimize the screen logic while
# Gson retains concrete constructors and field contracts in Review/Release builds.
-keep class com.ahu.ahutong.ui.state.*Response { *; }
-keep class com.ahu.ahutong.ui.state.*Map { *; }
-keep class com.ahu.ahutong.ui.state.*Data { *; }
-keep class com.ahu.ahutong.ui.state.*DataItem { *; }
-keep class com.ahu.ahutong.ui.state.*Details { *; }
-keep class com.ahu.ahutong.ui.state.*Payload { *; }
-keep class com.ahu.ahutong.ui.state.*FeeItem { *; }

# Native campus-card WebView bridge messages are also Gson contracts.
-keep class com.ahu.ahutong.ui.screen.main.CmbRechargeBridgePayload { *; }
-keep class com.ahu.ahutong.ui.screen.main.CmbRechargeBridgePaymentMethod { *; }
-keep class com.ahu.ahutong.ui.screen.main.CmbPaymentUiPayload { *; }

# Gson records for encrypted, per-account GMIS timetable and term caches.
-keep class com.ahu.ahutong.data.schedule.gmis.GmisTerm { *; }
-keep class com.ahu.ahutong.data.schedule.gmis.GmisCourse { *; }
-keep class com.ahu.ahutong.data.schedule.gmis.GmisSection { *; }
-keep class com.ahu.ahutong.data.schedule.gmis.GmisTimetable { *; }
-keep class com.ahu.ahutong.data.crawler.gmis.GmisCacheCodec$* { *; }

-renamesourcefileattribute AHUTong
#开启深度重载
# -overloadaggressively
# 把重命名之后的类名放到根目录
-repackageclasses

-printmapping map.txt

 #使用GSON、fastjson等框架时，所写的JSON对象类不混淆，否则无法将JSON解析成对应的对象
-keepclassmembers class * {
    public <init>(org.json.JSONObject);
}

-assumenosideeffects class java.io.PrintStream {
      public *** println(...);
      public *** print(...);
  }

#-assumenosideeffects class android.util.Log {
#    public static int v(...);
#    public static int i(...);
#    public static int d(...);
#    public static int w(...);
#    public static int e(...);
#    public static int wtf(...);
#}

-keep class com.ahu.ahutong.sdk.RustSDK { *; }
-keep class com.ahu.ahutong.sdk.UpdateConfig { *; }
-keep class com.ahu.ahutong.sdk.ProgressCallback { *; }
-keep class com.ahu.ahutong.sdk.LocalServiceClient { *; }
-keep class com.ahu.ahutong.data.server.model.** { *; }

# On-device prediction snapshots use a stable explicit codec. Keep the runtime snapshot model and
# its enum types as defense in depth against accidental reflection-based serialization regressions.
-keep class com.ahu.ahutong.personalization.context.ContextSnapshot { *; }
-keep enum com.ahu.ahutong.personalization.context.DayType { *; }
-keep enum com.ahu.ahutong.personalization.context.BalanceBucket { *; }
-keep enum com.ahu.ahutong.personalization.context.ExamDistanceBucket { *; }
-keep enum com.ahu.ahutong.personalization.action.AppActionId { *; }
-keep enum com.ahu.ahutong.personalization.action.ActionSource { *; }

# These DTOs are both Gson payload contracts and locally persisted aggregate records. Their JSON
# field names and nested generic signatures must remain stable in minified builds.
-keep class com.ahu.ahutong.personalization.telemetry.StoredActionMetric { *; }
-keep class com.ahu.ahutong.personalization.telemetry.StoredPerActionMetrics { *; }
-keep class com.ahu.ahutong.personalization.telemetry.ModelMetricSums { *; }
-keep class com.ahu.ahutong.personalization.telemetry.ModelAggregate { *; }
-keep class com.ahu.ahutong.personalization.telemetry.PairwiseAggregate { *; }
-keep class com.ahu.ahutong.personalization.telemetry.ActionMetricSums { *; }
-keep class com.ahu.ahutong.personalization.telemetry.ModelQualityEvaluationReport { *; }
-keep class com.ahu.ahutong.personalization.telemetry.ModelQualityBatchRequest { *; }
-keep class com.ahu.ahutong.personalization.telemetry.TelemetryCredentialRequest { *; }
-keep class com.ahu.ahutong.personalization.telemetry.TelemetryCredentialResponse { *; }
-keep class com.ahu.ahutong.personalization.telemetry.TelemetryDeletionRequest { *; }

# V3 telemetry aggregates are persisted as Gson JSON and later uploaded as a generic-list payload.
# Keep every nested DTO and the task enum so release builds retain stable field names and generic
# element types. Without this, Gson materializes nested lists as LinkedTreeMap after R8 minification.
-keep enum com.ahu.ahutong.personalization.telemetry.TelemetryV3Task { *; }
-keep class com.ahu.ahutong.personalization.telemetry.V3CalibrationBin { *; }
-keep class com.ahu.ahutong.personalization.telemetry.V3ModelMetricAggregate { *; }
-keep class com.ahu.ahutong.personalization.telemetry.V3PairwiseAggregate { *; }
-keep class com.ahu.ahutong.personalization.telemetry.V3NamedCount { *; }
-keep class com.ahu.ahutong.personalization.telemetry.V3ClassificationAggregate { *; }
-keep class com.ahu.ahutong.personalization.telemetry.V3PromotionHoldoutAggregate { *; }
-keep class com.ahu.ahutong.personalization.telemetry.V3BinaryScoreAggregate { *; }
-keep class com.ahu.ahutong.personalization.telemetry.V3RankingAggregate { *; }
-keep class com.ahu.ahutong.personalization.telemetry.V3CandidateShadowAggregate { *; }
-keep class com.ahu.ahutong.personalization.telemetry.V3DeliveryLaneAggregate { *; }
-keep class com.ahu.ahutong.personalization.telemetry.V3DeliveryAggregate { *; }
-keep class com.ahu.ahutong.personalization.telemetry.StoredTelemetryV3Aggregate { *; }
-keep class com.ahu.ahutong.personalization.telemetry.ModelQualityV3TaskReport { *; }
-keep class com.ahu.ahutong.personalization.telemetry.ModelQualityV3BatchRequest { *; }

# Bootstrap-training payloads are an immutable Gson/Retrofit wire contract. Preserve both JSON
# field names and generic list signatures in release builds.
-keep class com.ahu.ahutong.personalization.bootstrap.BootstrapTrainingExamplePayload { *; }
-keep class com.ahu.ahutong.personalization.bootstrap.BootstrapTrainingBatchRequest { *; }
-keep class com.ahu.ahutong.personalization.bootstrap.BootstrapTrainingCredentialRequest { *; }
-keep class com.ahu.ahutong.personalization.bootstrap.BootstrapTrainingCredentialResponse { *; }
-keep class com.ahu.ahutong.personalization.bootstrap.BootstrapTrainingDeletionRequest { *; }

# Weather responses are constructed and populated reflectively by Gson. Field-only rules with
# allowoptimization let R8 remove fields that are only read through reflection, which leaves the
# release weather widget with an incomplete response model.
-keep class com.ahu.ahutong.data.weather.** { *; }
-keep interface com.ahu.ahutong.data.weather.WeatherApi { *; }

# 必吃榜 canteen 的 keep 规则见文件上方（包级 `data.canteen.**`），此处不再重复列举。

# Repository index and local download records are read by Gson reflection. Keep their concrete
# classes and fields so R8 cannot merge the index entries or discard fields used only by Gson.
-keep class com.ahu.ahutong.data.repository.StorageUpdateResponse { *; }
-keep class com.ahu.ahutong.data.repository.StorageIndexResponse { *; }
-keep class com.ahu.ahutong.data.repository.StorageRepository { *; }
-keep class com.ahu.ahutong.data.repository.StorageFileEntry { *; }
-keep class com.ahu.ahutong.data.repository.StorageDirEntry { *; }
-keep class com.ahu.ahutong.data.repository.StorageLinkResponse { *; }
-keep class com.ahu.ahutong.data.repository.GitHubContentItem { *; }
-keep class com.ahu.ahutong.data.repository.DownloadedFile { *; }
-keep class com.ahu.ahutong.data.repository.RepositoryManager$DownloadRecord { *; }

# Evaluation
-keep interface com.ahu.ahutong.data.crawler.api.jwxt.EvaluationApi { *; }

# 插件 ServiceLoader 发现：保留实现类的无参构造与服务索引
-keep class * implements com.ahu.ahutong.core.plugin.AhuPlugin { public <init>(); }
-keepclassmembers class * implements com.ahu.ahutong.core.plugin.AhuPlugin { public <init>(); }
