package com.ahu.ahutong.data.crawler.api.ycard

import retrofit2.Response

/** A failed refresh leaves the original response readable by the caller. */
internal suspend fun <T> retryYcardUnauthorized(
    response: Response<T>,
    refresh: suspend () -> Boolean,
    retry: suspend () -> Response<T>
): Response<T> {
    if (response.code() != 401) return response
    val refreshed = try {
        refresh()
    } catch (error: Throwable) {
        response.errorBody()?.close()
        throw error
    }
    if (!refreshed) return response
    response.errorBody()?.close()
    return retry()
}
