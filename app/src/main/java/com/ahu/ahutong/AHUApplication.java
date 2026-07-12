package com.ahu.ahutong;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import com.ahu.ahutong.core.common.AppContextHolder;
import com.ahu.ahutong.core.sdk.CampusNativeGateway;
import com.ahu.ahutong.core.sdk.di.SdkEntryPoint;
import com.ahu.ahutong.data.crawler.CrawlerAuthInstaller;
import com.ahu.ahutong.data.dao.AHUCache;
import com.ahu.ahutong.notification.CourseReminderScheduler;
import com.tencent.bugly.crashreport.CrashReport;

import java.util.HashSet;

import dagger.hilt.android.EntryPointAccessors;
import dagger.hilt.android.HiltAndroidApp;

/**
 * @Author Xujiancan
 * @Email 3148336396@qq.com
 */

@HiltAndroidApp
public class AHUApplication extends Application {
    private static final String TAG = "AHUApplication";

    private static Application app;
    {
        app = this;
    }

    public static Application getApp() {
        return app;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        AppContextHolder.INSTANCE.init(this);
        CrawlerAuthInstaller.INSTANCE.install(this);

        CrashReport.initCrashReport(this, "2c2ccadcad", BuildConfig.DEBUG);
        CourseReminderScheduler.INSTANCE.createNotificationChannel(this);
        CourseReminderScheduler.INSTANCE.reschedule(this);

        // Mock 开关由 AHUCache + 各 data 源自行读取，无需再切换门面 DataSource
        if (AHUCache.INSTANCE.getMockData()) {
            Toast.makeText(this, "正在使用mock数据", Toast.LENGTH_SHORT).show();
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

    private CampusNativeGateway campusNativeGateway() {
        return EntryPointAccessors.fromApplication(this, SdkEntryPoint.class)
                .campusNativeGateway();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();

        // 停止 Rust 本地服务
        try {
            CampusNativeGateway gateway = campusNativeGateway();
            if (gateway.isNativeLoaded()) {
                gateway.stopServer();
                gateway.unbindLocalService();
                Log.i(TAG, "Local service stopped");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop local service", e);
        }
    }
}
