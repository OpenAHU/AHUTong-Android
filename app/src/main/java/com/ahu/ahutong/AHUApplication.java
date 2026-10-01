package com.ahu.ahutong;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import com.ahu.ahutong.sdk.LocalServiceClient;
import com.ahu.ahutong.sdk.RustSDK;
import com.tencent.bugly.crashreport.CrashReport;
import io.sentry.Sentry;
import io.sentry.android.core.SentryAndroid;
import com.ahu.ahutong.data.AHURepository;
import com.ahu.ahutong.data.dao.AHUCache;
import com.ahu.ahutong.core.common.AppEnvironmentHolder;
import com.ahu.ahutong.core.common.UserNoticeHolder;
import com.ahu.ahutong.data.debug.DebugTimeSourceHolder;
import com.ahu.ahutong.data.network.AliyunDns;
import com.ahu.ahutong.data.network.AppImageLoaderFactory;
import com.ahu.ahutong.data.xuexiaotong.Store;
import com.ahu.ahutong.reminder.ReminderScheduler;
import com.ahu.ahutong.notification.CourseReminderScheduler;
import com.ahu.ahutong.notification.CampusNoticeNotifier;


import java.util.HashSet;
import java.util.UUID;
import java.io.File;

import coil.ImageLoader;
import coil.ImageLoaderFactory;

import dagger.hilt.android.HiltAndroidApp;

/**
 * @Author Xujiancan
 * @Email 3148336396@qq.com
 */

@HiltAndroidApp
public class AHUApplication extends Application implements ImageLoaderFactory {
    private static final String TAG = "AHUApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        AliyunDns.INSTANCE.initializeCache(new File(getCacheDir(), "aliyun-doh"));

        // Isolated WebViews have their own profiles; do not initialize login services or analytics.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                (Application.getProcessName().endsWith(":postgraduate_notices") ||
                 Application.getProcessName().endsWith(":student_mail"))) {
            return;
        }

        // 应用级环境的安装点：必须早于任何用到 Context 的非 UI 代码
        // （MMKV 初始化、SecureStorage、Cookie 持久化都依赖它）。
        AppEnvironmentHolder.INSTANCE.install(new AndroidAppEnvironment(this));

        // 插件注册表兜底初始化（不依赖页面组合时序；进程内幂等）
        com.ahu.ahutong.ui.plugin.PluginRegistry.INSTANCE.init(this);
        DebugTimeSourceHolder.INSTANCE.install(CacheDebugTimeSource.INSTANCE);
        UserNoticeHolder.INSTANCE.install(ToastUserNotice.INSTANCE);

        CrashReport.initCrashReport(this, BuildConfig.BUGLY_APP_ID, BuildConfig.DEBUG);

        SentryAndroid.init(this, options -> {
            options.setDsn(BuildConfig.SENTRY_DSN);
            options.setEnvironment(BuildConfig.DEBUG ? "debug" : "release");
            // 线上按比例采样，避免性能数据占满额度；Debug 全量便于排查。
            options.setTracesSampleRate(BuildConfig.DEBUG ? 1.0 : 0.1);
            options.setDebug(BuildConfig.DEBUG);
        });
        Sentry.setTag("dau_id", getDauId());

        // 学习通日历初始化
        Store.INSTANCE.init(this);
        ReminderScheduler.INSTANCE.ensureChannel(this);
        ReminderScheduler.INSTANCE.scheduleAll(this);

        CourseReminderScheduler.INSTANCE.createNotificationChannel(this);
        CourseReminderScheduler.INSTANCE.reschedule(this);
        CampusNoticeNotifier.INSTANCE.createChannel(this);

        // Release builds always start on the real data source and erase legacy mock state.
        if (!BuildConfig.DEBUG) {
            AHUCache.INSTANCE.setMockData(false);
            AHURepository.INSTANCE.initializeDataSource(false);
        } else if(AHUCache.INSTANCE.getMockData()){
            AHURepository.INSTANCE.initializeDataSource(true);
            Toast.makeText(this,"正在使用mock数据",Toast.LENGTH_SHORT).show();
        }


        // 注意: Local Service 在 MainActivity.init() 中启动（native library 加载后）

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HashSet<Class<Activity>> blockList = new HashSet<>() {
                // todo LoginScene...
                // I plan to expose an interface
                // that allows the business layer to notify [the system/our module] of page
                // switches,
                // so that corresponding hiding or recording processing can be performed
                // accordingly.
            };
            // todo add privacy related options
        }
    }

    private String getDauId() {
        SharedPreferences preferences = getSharedPreferences("sentry", MODE_PRIVATE);
        String dauId = preferences.getString("dau_id", null);
        if (dauId == null) {
            dauId = UUID.randomUUID().toString();
            preferences.edit().putString("dau_id", dauId).apply();
        }
        return dauId;
    }

    @Override
    public ImageLoader newImageLoader() {
        return AppImageLoaderFactory.create(this);
    }


    @Override
    public void onTerminate() {
        super.onTerminate();

        // 停止 Rust 本地服务
        try {
            if (RustSDK.INSTANCE.isNativeLoaded()) {
                RustSDK.INSTANCE.stopServer();
                LocalServiceClient.Companion.destroy();
                Log.i(TAG, "Local service stopped");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop local service", e);
        }
    }
}
