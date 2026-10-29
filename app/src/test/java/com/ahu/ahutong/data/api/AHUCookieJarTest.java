package com.ahu.ahutong.data.api;

import com.franmontiel.persistentcookiejar.cache.SetCookieCache;
import com.franmontiel.persistentcookiejar.persistence.CookiePersistor;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Cookie;
import okhttp3.HttpUrl;

import static org.junit.Assert.*;

public class AHUCookieJarTest {
    private static final HttpUrl URL = HttpUrl.get("https://one.ahu.edu.cn/");

    @Test
    public void manualCookieInsertionCannotInvalidateAnActiveRequestIterator() throws Exception {
        CountDownLatch iteratorReady = new CountDownLatch(1);
        AtomicReference<Throwable> writerFailure = new AtomicReference<>();
        PausingCookieCache cache = new PausingCookieCache(iteratorReady);
        AHUCookieJar jar = new AHUCookieJar(cache, new MemoryPersistor());
        Cookie original = cookie("original");
        Cookie inserted = cookie("inserted");
        jar.saveFromResponse(URL, Collections.singletonList(original));
        Thread writer = new Thread(() -> {
            try {
                assertTrue(iteratorReady.await(5, TimeUnit.SECONDS));
                jar.addCookie(inserted);
            } catch (Throwable failure) {
                writerFailure.set(failure);
            }
        }, "manual-cookie-insertion");
        cache.writer = writer;
        writer.start();
        try {
            assertEquals(Collections.singletonList(original), jar.loadForRequest(URL));
        } finally {
            writer.join(5_000);
        }
        assertFalse("cookie writer did not finish", writer.isAlive());
        if (writerFailure.get() != null) throw new AssertionError(writerFailure.get());
        assertEquals(2, jar.loadForRequest(URL).size());
    }

    @Test
    public void sessionCookiesStillSurvivePersistentRestore() {
        MemoryPersistor persistor = new MemoryPersistor();
        AHUCookieJar jar = new AHUCookieJar(new SetCookieCache(), persistor);
        Cookie session = cookie("session");
        assertFalse(session.persistent());
        jar.addCookie(session);
        AHUCookieJar restored = new AHUCookieJar(new SetCookieCache(), persistor);
        assertEquals(Collections.singletonList(session), restored.loadForRequest(URL));
    }

    private static Cookie cookie(String name) {
        return new Cookie.Builder().name(name).value("value").hostOnlyDomain(URL.host()).build();
    }

    /** Pause after SetCookieCache creates its fail-fast iterator, before its first next(). */
    private static final class PausingCookieCache extends SetCookieCache {
        private final CountDownLatch iteratorReady;
        private Thread writer;

        PausingCookieCache(CountDownLatch iteratorReady) {
            this.iteratorReady = iteratorReady;
        }

        @Override
        public Iterator<Cookie> iterator() {
            Iterator<Cookie> delegate = super.iterator();
            if (writer != null) {
                Thread contender = writer;
                writer = null;
                iteratorReady.countDown();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                // A locked writer blocks on the jar; an unlocked writer finishes and invalidates
                // the iterator. Both outcomes are observed without relying on a racing sleep.
                while (contender.isAlive() && contender.getState() != Thread.State.BLOCKED) {
                    if (System.nanoTime() > deadline) fail("writer never attempted cookie insertion");
                    Thread.yield();
                }
            }
            return delegate;
        }
    }

    private static final class MemoryPersistor implements CookiePersistor {
        private final List<Cookie> cookies = new ArrayList<>();

        @Override public List<Cookie> loadAll() { return new ArrayList<>(cookies); }
        @Override public void saveAll(Collection<Cookie> values) { cookies.addAll(values); }
        @Override public void removeAll(Collection<Cookie> values) { cookies.removeAll(values); }
        @Override public void clear() { cookies.clear(); }
    }
}
