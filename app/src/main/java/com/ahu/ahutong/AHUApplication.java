package com.ahu.ahutong;

import android.app.Application;
import android.util.Log;
import android.widget.Toast;

import com.ahu.ahutong.core.common.AppContextHolder;
import com.ahu.ahutong.core.sdk.CampusNativeGateway;
import com.ahu.ahutong.core.sdk.di.SdkEntryPoint;
import com.ahu.ahutong.data.crawler.CrawlerAuthInstaller;
import com.ahu.ahutong.data.dao.AHUCache;
import com.ahu.ahutong.notification.CourseReminderScheduler;
import com.tencent.bugly.crashreport.CrashReport;

import dagger.hilt.android.EntryPointAccessors;
import dagger.hilt.android.HiltAndroidApp;

/**
 * Application entry only. Product UI / data / widgets live in :feature:shell.
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

        if (AHUCache.INSTANCE.getMockData()) {
            Toast.makeText(this, "正在使用mock数据", Toast.LENGTH_SHORT).show();
        }
    }

    private CampusNativeGateway campusNativeGateway() {
        return EntryPointAccessors.fromApplication(this, SdkEntryPoint.class)
                .campusNativeGateway();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
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
