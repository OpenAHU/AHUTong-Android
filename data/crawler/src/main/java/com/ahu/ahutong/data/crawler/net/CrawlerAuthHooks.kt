package com.ahu.ahutong.data.crawler.net

/**
 * App-installed hooks so [TokenAuthenticator] can re-login without depending on
 * AHUCache / AppDataAccess / Hilt from this module.
 */
object CrawlerAuthHooks {
    /**
     * Returns (username, wisdomPassword) or null if not logged in.
     */
    @Volatile
    var loadCredentials: (() -> Pair<String, String>?)? = null

    /**
     * Performs a full login; returns true on success.
     */
    @Volatile
    var performLogin: (suspend (username: String, password: String) -> Boolean)? = null
}
