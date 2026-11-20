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
private const val IMAGE_HEIGHT = 1920
private const val PADDING = 64f
private const val SECTION_GUTTER = 56f
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

    val gridLeft = PADDING + SECTION_GUTTER + 12f
    val gridRight = IMAGE_WIDTH - PADDING
    val headerTop = 320f
    val headerHeight = 56f
    val gridTop = headerTop + headerHeight
    val gridBottom = 1656f
    val columnWidth = (gridRight - gridLeft) / visibleDays.size.coerceAtLeast(1)
    val rowHeight = (gridBottom - gridTop) / sectionCount.coerceAtLeast(1)

    val weekdayPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = secondaryColor
        textSize = 30f
        textAlign = Paint.Align.CENTER
    }
    visibleDays.forEachIndexed { index, day ->
        canvas.drawText(
            weekdayLabel(day),
            gridLeft + columnWidth * (index + 0.5f),
            headerTop + 38f,
            weekdayPaint
        )
    }

    val sectionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = secondaryColor
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }
    for (section in 1..sectionCount) {
        canvas.drawText(
            section.toString(),
            PADDING + SECTION_GUTTER / 2f,
            gridTop + rowHeight * (section - 0.5f) + 9f,
            sectionPaint
        )
    }

    val blockRadius = 20f
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
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val innerWidth = ((right - left) - 22f).toInt().coerceAtLeast(40)
        val names = group.map { it.name }.distinct().joinToString(" / ")
        val nameLayout = StaticLayout.Builder
            .obtain(names, 0, names.length, namePaint, innerWidth)
            .setMaxLines(4)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(left + 11f, top + 11f)
        nameLayout.draw(canvas)
        canvas.restore()

        val place = first.location.orEmpty().trim()
        if (place.isNotEmpty()) {
            val placePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = textColor
                textSize = 22f
                alpha = 200
            }
            val placeLayout = StaticLayout.Builder
                .obtain(place, 0, place.length, placePaint, innerWidth)
                .setMaxLines(2)
                .setEllipsize(android.text.TextUtils.TruncateAt.END)
                .build()
            val placeY = top + 11f + nameLayout.height + 4f
            if (placeY + placeLayout.height < bottom - 4f) {
                canvas.save()
                canvas.translate(left + 11f, placeY)
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
    canvas.drawLine(PADDING, 1712f, IMAGE_WIDTH - PADDING, 1712f, dividerPaint)

    val logoSize = 90f
    val qrSize = 112f
    val footerTop = IMAGE_HEIGHT - PADDING - logoSize
    logo?.let {
        // 前景图自带一圈透明安全区：先裁掉，再按最长边缩放到与二维码同尺寸
        val cropped = trimTransparent(it)
        val scale = logoSize / max(cropped.width, cropped.height).coerceAtLeast(1)
        val drawWidth = cropped.width * scale
        val drawHeight = cropped.height * scale
        val scaled = Bitmap.createScaledBitmap(
            cropped,
            drawWidth.toInt().coerceAtLeast(1),
            drawHeight.toInt().coerceAtLeast(1),
            true
        )
        canvas.drawBitmap(
            scaled,
            PADDING + (logoSize - drawWidth) / 2f,
            footerTop + (logoSize - drawHeight) / 2f,
            null
        )
        scaled.recycle()
        if (cropped !== it) cropped.recycle()
    }
    val appNamePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = titleColor
        textSize = 46f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val brandX = if (logo != null) PADDING + logoSize + 24f else PADDING
    // 品牌名与标语分两行，整体与左侧 logo 垂直居中
    val nameBaseline = footerTop + logoSize * 0.46f
    canvas.drawText(appName(context), brandX, nameBaseline, appNamePaint)
    val sloganPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = secondaryColor
        textSize = 28f
    }
    canvas.drawText("安大人的生活利器", brandX, nameBaseline + 38f, sloganPaint)

    drawQr(
        canvas,
        IMAGE_WIDTH - PADDING - qrSize,
        footerTop + (logoSize - qrSize) / 2f,
        qrSize,
        if (luminance(background) > 0.5f) Color.BLACK else Color.WHITE
    )
    return bitmap
}

private fun drawQr(canvas: Canvas, left: Float, top: Float, size: Float, color: Int) {
    val matrix = runCatching {
        QRCodeWriter().encode(QR_URL, BarcodeFormat.QR_CODE, 512, 512)
    }.getOrNull() ?: return
    val cell = size / matrix.width
    val qrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
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

private fun trimTransparent(source: Bitmap): Bitmap {
    val width = source.width
    val height = source.height
    var minX = width
    var minY = height
    var maxX = -1
    var maxY = -1
    val row = IntArray(width)
    for (y in 0 until height) {
        source.getPixels(row, 0, width, 0, y, width, 1)
        for (x in 0 until width) {
            if (Color.alpha(row[x]) > 8) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
    }
    if (maxX < 0 || minX >= maxX || minY >= maxY) return source
    return Bitmap.createBitmap(source, minX, minY, maxX - minX + 1, maxY - minY + 1)
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