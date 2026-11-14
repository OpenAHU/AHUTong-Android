package com.ahu.ahutong.data.schedule

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import android.util.Log
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.Exam
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ScheduleCalendarSync(
    private val context: Context,
    private val clock: Clock = Clock.system(schoolCalendarZone)
) {
    private val resolver = context.contentResolver
    private val mutex = Mutex()
    private val mutableFailures = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val failures = mutableFailures.asSharedFlow()
    private val accountName = "${context.packageName}.schedule"

    fun isEnabled(): Boolean = ScheduleUiPrefs.isCalendarSyncEnabled() && hasPermissions(context)

    suspend fun cleanPreviousWeek(ownerIsCurrent: () -> Boolean) = writeCalendar(ownerIsCurrent) {
        val monday = mondayOf(LocalDate.now(clock))
        reconcile("course", emptySet(), "${Events.DTSTART}>=? AND ${Events.DTSTART}<?",
            arrayOf(millis(monday.minusWeeks(1)).toString(), millis(monday).toString()))
    }

    suspend fun syncCourses(
        courses: List<Course>,
        semesterStart: LocalDate,
        ownerIsCurrent: () -> Boolean
    ) = writeCalendar(ownerIsCurrent) {
        val today = LocalDate.now(clock)
        val monday = mondayOf(today)
        reconcile(
            kind = "course",
            desired = courseCalendarEvents(courses, semesterStart, today),
            window = "${Events.DTSTART}>=? AND ${Events.DTSTART}<?",
            windowArgs = arrayOf(millis(monday).toString(), millis(monday.plusWeeks(3)).toString())
        )
    }

    suspend fun syncExams(exams: List<Exam>, ownerIsCurrent: () -> Boolean) = writeCalendar(ownerIsCurrent) {
        val now = LocalDateTime.now(clock)
        reconcile("exam", examCalendarEvents(exams, now), "${Events.DTEND}>?", arrayOf(millis(now).toString()))
    }

    private suspend fun writeCalendar(ownerIsCurrent: () -> Boolean, write: () -> Unit) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (!isEnabled() || !ownerIsCurrent()) {
                    return@withLock
                }
                try {
                    write()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.w("ScheduleCalendarSync", "Calendar provider operation failed (${error.javaClass.simpleName})")
                    mutableFailures.tryEmit(Unit)
                }
            }
        }
    }

    private fun findCalendar(): Long? = checkNotNull(
        resolver.query(
            Calendars.CONTENT_URI,
            arrayOf(Calendars._ID),
            "${Calendars.ACCOUNT_NAME}=? AND ${Calendars.ACCOUNT_TYPE}=? AND ${Calendars.NAME}=?",
            arrayOf(accountName, CalendarContract.ACCOUNT_TYPE_LOCAL, accountName),
            null
        )
    ).use { if (it.moveToFirst()) it.getLong(0) else null }

    private fun createCalendar(): Long {
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, accountName)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, accountName)
            put(Calendars.CALENDAR_DISPLAY_NAME, "AHUTong 课表")
            put(Calendars.CALENDAR_COLOR, 0xFF3269BC.toInt())
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, accountName)
            put(Calendars.CALENDAR_TIME_ZONE, schoolCalendarZone.id)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.MAX_REMINDERS, 1)
            put(Calendars.ALLOWED_REMINDERS, Reminders.METHOD_ALERT.toString())
        }
        return ContentUris.parseId(checkNotNull(resolver.insert(localAccountUri(Calendars.CONTENT_URI), values)))
    }

    private fun localAccountUri(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, accountName)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    private data class EventValues(
        val title: String,
        val start: Long,
        val end: Long,
        val location: String,
        val description: String
    )

    private fun reconcile(
        kind: String,
        desired: Set<ScheduleCalendarEvent>,
        window: String,
        windowArgs: Array<String>
    ) {
        val calendarId = findCalendar() ?: if (desired.isEmpty()) return else createCalendar()
        val missing = desired.associateBy {
            EventValues(it.title, millis(it.start), millis(it.end), it.location, it.description)
        }.toMutableMap()
        val eventsUri = localAccountUri(Events.CONTENT_URI)
        val operations = arrayListOf<ContentProviderOperation>()
        checkNotNull(resolver.query(
            Events.CONTENT_URI,
            arrayOf(Events._ID, Events.TITLE, Events.DTSTART, Events.DTEND, Events.EVENT_LOCATION, Events.DESCRIPTION),
            "${Events.CALENDAR_ID}=? AND ${Events.SYNC_DATA1}=? AND ${Events.DELETED}=0 AND ($window)",
            arrayOf(calendarId.toString(), kind, *windowArgs),
            null
        )).use { cursor ->
            while (cursor.moveToNext()) {
                val values = EventValues(
                    cursor.getString(1).orEmpty(),
                    cursor.getLong(2),
                    cursor.getLong(3),
                    cursor.getString(4).orEmpty(),
                    cursor.getString(5).orEmpty()
                )
                if (missing.remove(values) == null) {
                    operations.add(deleteEvent(eventsUri, cursor.getLong(0)))
                }
            }
        }
        for ((values, event) in missing) {
            val eventOperation = operations.size
            operations.add(ContentProviderOperation.newInsert(eventsUri)
                .withValue(Events.CALENDAR_ID, calendarId)
                .withValue(Events.SYNC_DATA1, kind)
                .withValue(Events.TITLE, values.title)
                .withValue(Events.DTSTART, values.start)
                .withValue(Events.DTEND, values.end)
                .withValue(Events.EVENT_TIMEZONE, schoolCalendarZone.id)
                .withValue(Events.EVENT_LOCATION, values.location)
                .withValue(Events.DESCRIPTION, values.description)
                .withValue(Events.HAS_ALARM, 1)
                .build())
            operations.add(ContentProviderOperation.newInsert(Reminders.CONTENT_URI)
                .withValueBackReference(Reminders.EVENT_ID, eventOperation)
                .withValue(Reminders.MINUTES, event.reminderMinutes)
                .withValue(Reminders.METHOD, Reminders.METHOD_ALERT)
                .build())
        }
        if (operations.isNotEmpty()) resolver.applyBatch(CalendarContract.AUTHORITY, operations)
    }

    private fun deleteEvent(eventsUri: Uri, id: Long): ContentProviderOperation =
        ContentProviderOperation.newDelete(ContentUris.withAppendedId(eventsUri, id)).build()

    private fun millis(date: LocalDate): Long = millis(date.atStartOfDay())
    private fun millis(time: LocalDateTime): Long = time.atZone(schoolCalendarZone).toInstant().toEpochMilli()

    companion object {
        val permissions: List<String> = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

        fun hasPermissions(context: Context): Boolean =
            permissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }
}
