package com.ahu.ahutong.cli

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/** Debug companion transport. The CLI uses adb shell content call, without a listening port. */
class CliProvider : ContentProvider() {
    private val lock = Mutex()
    private val bridge by lazy { CliBridge(requireNotNull(context).applicationContext) }
    private val gson = Gson()

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        check(CliAccess.permitted(Binder.getCallingUid())) { "ADB shell access required" }
        require(uri.pathSegments.size == 2 && uri.pathSegments[0] == "file")
        return CliFiles.open(uri.pathSegments[1], mode)
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        check(CliAccess.permitted(Binder.getCallingUid())) { "ADB shell access required" }
        require(method == "request") { "Unknown protocol method" }
        require(arg != null && arg.length <= 96_000) { "Request exceeds protocol limit" }
        val reply = try {
            val request = JsonParser.parseString(
                String(Base64.decode(arg, Base64.NO_WRAP), Charsets.UTF_8)
            ).asJsonObject
            runBlocking(Dispatchers.IO) {
                withTimeout(120_000) { lock.withLock { bridge.dispatch(request) } }
            }
        } catch (error: Exception) {
            // Do not return exception bodies: upstream errors may contain credentials or mail data.
            mapOf("ok" to false, "code" to "request_failed", "error" to error.javaClass.simpleName,
                "message" to "Request failed; writes must be checked before retrying")
        }
        val bytes = gson.toJson(reply).toByteArray(Charsets.UTF_8)
        val encoded = if (bytes.size <= 300_000) Base64.encodeToString(bytes, Base64.NO_WRAP) else
            Base64.encodeToString(gson.toJson(mapOf("ok" to false, "code" to "response_too_large",
                "message" to "Use pagination or a narrower query")).toByteArray(), Base64.NO_WRAP)
        return Bundle().apply { putString("response", encoded) }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
