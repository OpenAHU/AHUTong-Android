package com.ahu.ahutong.core.common

/**
 * Unified result type for domain/data boundaries.
 * Prefer this over mixed [Result] / legacy response wrappers in new code.
 */
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(
        val message: String,
        val cause: Throwable? = null,
        val code: Int? = null,
    ) : AppResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = (this as? Success)?.data

    fun exceptionOrNull(): Throwable? = (this as? Error)?.cause

    inline fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }

    inline fun onSuccess(block: (T) -> Unit): AppResult<T> {
        if (this is Success) block(data)
        return this
    }

    inline fun onError(block: (Error) -> Unit): AppResult<T> {
        if (this is Error) block(this)
        return this
    }

    fun toKotlinResult(): Result<T> = when (this) {
        is Success -> Result.success(data)
        is Error -> Result.failure(cause ?: IllegalStateException(message))
    }

    companion object {
        fun <T> success(data: T): AppResult<T> = Success(data)
        fun error(message: String, cause: Throwable? = null, code: Int? = null): AppResult<Nothing> =
            Error(message, cause, code)

        fun <T> fromKotlin(result: Result<T>): AppResult<T> =
            result.fold(
                onSuccess = { Success(it) },
                onFailure = { Error(it.message ?: "Unknown error", it) },
            )
    }
}
