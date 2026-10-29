package com.ahu.ahutong.data.api;


import android.util.Log;

import androidx.annotation.NonNull;

import com.franmontiel.persistentcookiejar.ClearableCookieJar;
import com.franmontiel.persistentcookiejar.cache.CookieCache;
import com.franmontiel.persistentcookiejar.persistence.CookiePersistor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import okhttp3.Cookie;
import okhttp3.HttpUrl;

// Warning: 别动， 用于修复后端非法 Cookie 被过滤没有保存问题
public class AHUCookieJar implements ClearableCookieJar {
    private static final String TAG = AHUCookieJar.class.getName();
    private final CookieCache cache;
    private final CookiePersistor persistor;

    public AHUCookieJar(CookieCache cache, CookiePersistor persistor) {
        this.cache = cache;
        this.persistor = persistor;

        this.cache.addAll(persistor.loadAll());
    }

    @Override
    synchronized public void saveFromResponse(@NonNull HttpUrl url, @NonNull List<Cookie> cookies) {
        cache.addAll(cookies);
        persistor.saveAll(filterPersistentCookies(cookies));
    }

    private static List<Cookie> filterPersistentCookies(List<Cookie> cookies) {
        List<Cookie> persistentCookies = new ArrayList<>();

        for (Cookie cookie : cookies) {
            persistentCookies.add(cookie);

        }
        return persistentCookies;
    }

    @NonNull
    @Override
    synchronized public List<Cookie> loadForRequest(@NonNull HttpUrl url) {
        List<Cookie> cookiesToRemove = new ArrayList<>();
        List<Cookie> validCookies = new ArrayList<>();

        for (Iterator<Cookie> it = cache.iterator(); it.hasNext(); ) {
            Cookie currentCookie = it.next();

            if (isCookieExpired(currentCookie)) {
                cookiesToRemove.add(currentCookie);
                it.remove();

            } else if (currentCookie.matches(url)) {
                validCookies.add(currentCookie);
            }
        }

        persistor.removeAll(cookiesToRemove);
        return validCookies;
    }

    private static boolean isCookieExpired(Cookie cookie) {
        return cookie.expiresAt() < System.currentTimeMillis();
    }

    @Override
    synchronized public void clearSession() {
        Log.i(TAG, "clearSession");
        cache.clear();
        cache.addAll(persistor.loadAll());
    }

    @Override
    synchronized public void clear() {
        cache.clear();
        persistor.clear();
    }


    public synchronized void logAllCookies() {
        int count = 0;
        for (Cookie ignored : cache) count++;
        Log.i(TAG, "CookieJar contains " + count + " entries (values suppressed)");
    }


    public void clearCookiesForUrl(@NonNull String url) {

        HttpUrl urlToDelete = HttpUrl.get(url);

        List<Cookie> cookiesToRemove = new ArrayList<>();

        synchronized (this) {
            for (Iterator<Cookie> it = cache.iterator(); it.hasNext(); ) {
                Cookie cookie = it.next();

                if (cookie.matches(urlToDelete)) {
                    cookiesToRemove.add(cookie);
                    it.remove();
                }
            }

            // 同步更新持久化
            if (!cookiesToRemove.isEmpty()) {
                persistor.removeAll(cookiesToRemove);
            }
        }
    }

    public synchronized void addCookie(Cookie cookie) {
        List<Cookie> cookies = new ArrayList<>();
        cookies.add(cookie);
        cache.addAll(cookies);
        // 如果需要持久化（下次启动免登录），这里会自动保存
        persistor.saveAll(filterPersistentCookies(cookies));
    }

    @NonNull
    synchronized public List<Cookie> getAllCookies() {
        List<Cookie> cookies = new ArrayList<>();
        for (Cookie cookie : cache) {
            if (!isCookieExpired(cookie)) {
                cookies.add(cookie);
            }
        }
        return Collections.unmodifiableList(cookies);
    }
}
