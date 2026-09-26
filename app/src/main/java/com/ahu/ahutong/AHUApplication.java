package com.ahu.ahutong;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import com.ahu.ahutong.sdk.LocalServiceClient;
import com.ahu.ahutong.sdk.RustSDK;
import com.tencent.bugly.crashreport.CrashReport;
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


import java.util.HashSet;
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

        // 应用级环境的安装点：必须早于任何用到 Context 的非 UI 代码
        // （MMKV 初始化、SecureStorage、Cookie 持久化都依赖它）。
        AppEnvironmentHolder.INSTANCE.install(new AndroidAppEnvironment(this));
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

        // 学习通日历初始化
        Store.INSTANCE.init(this);
        ReminderScheduler.INSTANCE.ensureChannel(this);
        ReminderScheduler.INSTANCE.scheduleAll(this);

        CourseReminderScheduler.INSTANCE.createNotificationChannel(this);
        CourseReminderScheduler.INSTANCE.reschedule(this);

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
