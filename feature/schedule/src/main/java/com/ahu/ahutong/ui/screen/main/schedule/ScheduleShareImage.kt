package com.ahu.ahutong.ui.screen.main.schedule

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.StaticLayout
import android.text.TextPaint
import com.ahu.ahutong.data.model.Course
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

private const val IMAGE_WIDTH = 1080
private const val IMAGE_HEIGHT = 1440
private const val PADDING = 64f
private const val QR_URL = "https://openahu.org/projects/ahutong/"

/** 渲染一张分享用课表图：标题、周次、当前周课程与品牌页脚。 */
internal fun renderScheduleShareImage(
    context: Context,
    weekLabel: String,
    subtitle: String,
    visibleDays: List<Int>,
    groups: List<List<Course>>,
    sectionCount: Int,
    courseColors: Map<String, Int>,
    background: Int,
    titleColor: Int,
    secondaryColor: Int,
    logo: Bitmap?
): Bitmap {
    val bitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(background)

    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = titleColor
        textSize = 84f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText(weekLabel, PADDING, 170f, titlePaint)
    val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = secondaryColor
        textSize = 36f
    }
    canvas.drawText(subtitle, PADDING, 228f, subtitlePaint)

    val gridLeft = PADDING
    val gridRight = IMAGE_WIDTH - PADDING
    val headerTop = 300f
    val headerHeight = 52f
    val gridTop = headerTop + headerHeight
    val gridBottom = 1188f
    val columnWidth = (gridRight - gridLeft) / visibleDays.size.coerceAtLeast(1)
    val rowHeight = (gridBottom - gridTop) / sectionCount.coerceAtLeast(1)

    val weekdayPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = secondaryColor
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }
    visibleDays.forEachIndexed { index, day ->
        canvas.drawText(
            weekdayLabel(day),
            gridLeft + columnWidth * (index + 0.5f),
            headerTop + 36f,
            weekdayPaint
        )
    }

    val blockRadius = 18f
    groups.forEach { group ->
        val first = group.firstOrNull() ?: return@forEach
        val dayIndex = visibleDays.indexOf(first.weekday)
        if (dayIndex < 0) return@forEach
        val start = first.startTime.coerceAtLeast(1)
        val length = max(first.length, 1)
        val left = gridLeft + columnWidth * dayIndex + 5f
        val top = gridTop + rowHeight * (start - 1) + 4f
        val right = left + columnWidth - 10f
        val bottom = gridTop + rowHeight * (start + length - 1) - 4f
        if (bottom <= top) return@forEach

        val color = courseColors[first.name] ?: 0xFF82ADF7.toInt()
        val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawRoundRect(RectF(left, top, right, bottom), blockRadius, blockRadius, blockPaint)

        val darkText = luminance(color) > 0.62f
        val textColor = if (darkText) 0xFF1F2430.toInt() else Color.WHITE
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = textColor
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val innerWidth = ((right - left) - 20f).toInt().coerceAtLeast(40)
        val names = group.map { it.name }.distinct().joinToString(" / ")
        val nameLayout = StaticLayout.Builder
            .obtain(names, 0, names.length, namePaint, innerWidth)
            .setMaxLines(3)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(left + 10f, top + 10f)
        nameLayout.draw(canvas)
        canvas.restore()

        val place = first.location.orEmpty().trim()
        if (place.isNotEmpty()) {
            val placePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = textColor
                textSize = 20f
                alpha = 200
            }
            val placeLayout = StaticLayout.Builder
                .obtain(place, 0, place.length, placePaint, innerWidth)
                .setMaxLines(2)
                .setEllipsize(android.text.TextUtils.TruncateAt.END)
                .build()
            val placeY = top + 10f + nameLayout.height + 4f
            if (placeY + placeLayout.height < bottom - 4f) {
                canvas.save()
                canvas.translate(left + 10f, placeY)
                placeLayout.draw(canvas)
                canvas.restore()
            }
        }
    }

    val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = secondaryColor
        alpha = 48
        strokeWidth = 2f
    }
    canvas.drawLine(PADDING, 1252f, IMAGE_WIDTH - PADDING, 1252f, dividerPaint)

    val logoSize = 88f
    logo?.let {
        val scaled = Bitmap.createScaledBitmap(it, logoSize.toInt(), logoSize.toInt(), true)
        canvas.drawBitmap(scaled, PADDING, 1292f, null)
        scaled.recycle()
    }
    val appNamePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = titleColor
        textSize = 38f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val brandX = if (logo != null) PADDING + logoSize + 20f else PADDING
    canvas.drawText(appName(context), brandX, 1352f, appNamePaint)
    val taglinePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = secondaryColor
        textSize = 24f
    }
    canvas.drawText("openahu.org", brandX, 1388f, taglinePaint)

    drawQr(canvas, IMAGE_WIDTH - PADDING - 176f, 1272f, 176f)
    return bitmap
}

private fun drawQr(canvas: Canvas, left: Float, top: Float, size: Float) {
    val matrix = runCatching {
        QRCodeWriter().encode(QR_URL, BarcodeFormat.QR_CODE, 512, 512)
    }.getOrNull() ?: return
    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    canvas.drawRoundRect(
        RectF(left - 14f, top - 14f, left + size + 14f, top + size + 14f),
        24f, 24f, cardPaint
    )
    val cell = size / matrix.width
    val qrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    for (x in 0 until matrix.width) {
        for (y in 0 until matrix.height) {
            if (matrix.get(x, y)) {
                canvas.drawRect(
                    left + x * cell,
                    top + y * cell,
                    left + (x + 1) * cell + 0.5f,
                    top + (y + 1) * cell + 0.5f,
                    qrPaint
                )
            }
        }
    }
}

private fun weekdayLabel(day: Int): String = when (day) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    7 -> "周日"
    else -> ""
}

private fun luminance(color: Int): Float {
    val r = Color.red(color) / 255f
    val g = Color.green(color) / 255f
    val b = Color.blue(color) / 255f
    return 0.299f * r + 0.587f * g + 0.114f * b
}

private fun appName(context: Context): String =
    context.applicationInfo.loadLabel(context.packageManager).toString()

/** 写入相册；返回是否成功。 */
internal fun saveScheduleBitmap(context: Context, bitmap: Bitmap, week: Int): Boolean {
    val fileName = "课表_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA).format(Date()) +
        "_第${week}周.png"
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/安大通")
            }
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            ) ?: return false
            context.contentResolver.openOutputStream(uri)?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            } ?: return false
            true
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "安大通"
            ).apply { mkdirs() }
            val file = File(dir, fileName)
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            true
        }
    }.getOrDefault(false)
}