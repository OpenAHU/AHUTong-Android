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
-keep class com.ahu.ahutong.data.AHUResponse{
    private *;
}
-keep class org.jsoup.Connection{*;}
-keep class com.ahu.ahutong.ui.widget.schedule.bean.**{*;}
-keepclassmembers class com.ahu.ahutong.data.model.* {
    private *;
}

# Crawler DTOs are Retrofit/Gson wire contracts. Keeping only their fields still allows R8 to
# merge the owning classes, which can turn a valid login response into a ClassCastException in
# minified builds. Keep the complete contracts so Review/Release authentication behaves like Debug.
-keep class com.ahu.ahutong.data.crawler.model.** { *; }

# Gson records for encrypted, per-account GMIS timetable and term caches.
-keep class com.ahu.ahutong.data.crawler.gmis.GmisTerm { *; }
-keep class com.ahu.ahutong.data.crawler.gmis.GmisCourse { *; }
-keep class com.ahu.ahutong.data.crawler.gmis.GmisSection { *; }
-keep class com.ahu.ahutong.data.crawler.gmis.GmisTimetable { *; }
-keep class com.ahu.ahutong.data.crawler.gmis.GmisCacheCodec$* { *; }

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

# Repository / GitHub models
-keepclassmembers,allowoptimization class com.ahu.ahutong.data.repository.** { <fields>; }

# Evaluation
-keep interface com.ahu.ahutong.data.crawler.api.jwxt.EvaluationApi { *; }
