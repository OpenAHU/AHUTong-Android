package com.ahu.ahutong.error

import com.ahu.ahutong.data.attendance.AttendanceAuthException
import com.ahu.ahutong.data.attendance.AttendanceException
import com.google.gson.JsonParseException
import java.io.IOException
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CancellationException
import java.time.format.DateTimeParseException
import retrofit2.HttpException
import android.content.ActivityNotFoundException

internal enum class FailurePresentation { Ignore, Expected, Unexpected }

/** Unwrap transport wrappers without treating a programmer error as a login/network failure. */
internal fun failurePresentation(error: Throwable): FailurePresentation {
    val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    var current: Throwable? = error
    var expected = false
    var programmingOrParsingFailure = false
    while (current != null && visited.size < 32 && visited.add(current)) {
        when (current) {
            is CancellationException -> return FailurePresentation.Ignore
            is NullPointerException, is ClassCastException, is IndexOutOfBoundsException,
            is JsonParseException, is NumberFormatException, is DateTimeParseException,
            is UninitializedPropertyAccessException, is AssertionError -> programmingOrParsingFailure = true
            is IOException, is HttpException, is AttendanceAuthException,
            is AttendanceException, is ActivityNotFoundException -> expected = true
        }
        current = current.cause
    }
    return if (expected && !programmingOrParsingFailure) FailurePresentation.Expected
        else FailurePresentation.Unexpected
}
