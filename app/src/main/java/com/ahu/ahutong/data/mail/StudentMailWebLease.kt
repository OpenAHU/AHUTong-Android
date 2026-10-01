package com.ahu.ahutong.data.mail

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import java.util.UUID

/** One-use navigation capability; the intent never contains a sid or cookies. */
object StudentMailWebLease {
    const val KEY_LEASE = "mail_lease"
    const val METHOD_OPEN = "open"
    const val METHOD_CHECK = "check"
    const val METHOD_CLOSE = "close"
    private var active: Pair<String, MailSession>? = null

    @Synchronized fun open(session: MailSession): String {
        session.assertCurrent()
        return UUID.randomUUID().toString().also { active = it to session }
    }

    @Synchronized fun read(key: String?, includeSession: Boolean): Bundle {
        val session = active?.takeIf { it.first == key }?.second ?: return Bundle()
        return try {
            session.assertCurrent()
            Bundle().apply {
                putBoolean("valid", true)
                if (includeSession) {
                    putString("sid", session.sid)
                    putStringArrayList("cookies", ArrayList(StudentMailSession.mailboxCookies(session).map { it.toString() }))
                }
            }
        } catch (_: Exception) {
            active = null
            Bundle()
        }
    }

    @Synchronized fun close(key: String?) {
        if (active?.first == key) active = null
    }
}

/** Lives in the app process, so isolated web requests always consult the current campus identity. */
class StudentMailSessionProvider : ContentProvider() {
    override fun onCreate() = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        check(Binder.getCallingUid() == Process.myUid())
        return when (method) {
            StudentMailWebLease.METHOD_OPEN -> StudentMailWebLease.read(arg, true)
            StudentMailWebLease.METHOD_CHECK -> StudentMailWebLease.read(arg, false)
            StudentMailWebLease.METHOD_CLOSE -> Bundle().also { StudentMailWebLease.close(arg) }
            else -> Bundle()
        }
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
