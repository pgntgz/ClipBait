package me.pj.jqb.ui.components

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import me.pj.jqb.AppInfo
import me.pj.jqb.R
import me.pj.jqb.data.db.AppInterceptStat
import me.pj.jqb.ui.MainActivity
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class PosterGenerationResult(
    val bitmap: Bitmap,
    val savedUri: Uri?,
    val titleText: String,
    val fileName: String
)

object HallOfFamePosterGenerator {

    private const val POSTER_CHANNEL_ID = "clipbait_periodic_poster"
    private const val POSTER_NOTIFICATION_ID = 3088

    fun generateAndNotify(
        context: Context,
        offenders: List<AppInterceptStat>,
        totalCount: Int,
        periodStyle: String, // "month" | "year"
        intervalDays: Int,
        isEn: Boolean,
        postNotification: Boolean = true
    ): PosterGenerationResult {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val useYearly = periodStyle == "year" || intervalDays >= 180

        val titleText = if (isEn) {
            if (useYearly) "$year Yearly Standings" else String.format(Locale.US, "%d-%02d Monthly Standings", year, month)
        } else {
            if (useYearly) "${year}年度 剪贴板积分榜" else "${year}年${month}月 剪贴板积分榜"
        }

        val subtitleText = if (isEn) {
            val cycleStr = if (intervalDays <= 0) "All-Time" else "Last ${intervalDays}d cycle"
            "ClipBait Standings · $cycleStr"
        } else {
            val cycleStr = if (intervalDays <= 0) "累计统计" else "最近 ${intervalDays} 天统计周期"
            "ClipBait 积分榜 · $cycleStr"
        }

        val bitmap = renderPosterBitmap(
            context = context,
            titleText = titleText,
            subtitleText = subtitleText,
            offenders = offenders.take(6),
            totalCount = totalCount,
            isEn = isEn
        )

        val timeSuffix = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "ClipBait_${if (useYearly) "${year}_Year" else "${year}_${month}M"}_$timeSuffix.png"
        val savedUri = saveBitmapToGallery(context, bitmap, fileName)

        if (postNotification) {
            postPosterNotification(
                context = context,
                bitmap = bitmap,
                savedUri = savedUri,
                titleText = titleText,
                totalCount = totalCount,
                topAppName = offenders.firstOrNull()?.appName,
                isEn = isEn
            )
        }

        return PosterGenerationResult(
            bitmap = bitmap,
            savedUri = savedUri,
            titleText = titleText,
            fileName = fileName
        )
    }

    private fun renderPosterBitmap(
        context: Context,
        titleText: String,
        subtitleText: String,
        offenders: List<AppInterceptStat>,
        totalCount: Int,
        isEn: Boolean
    ): Bitmap {
        val width = 1080
        val rowCount = offenders.size.coerceAtLeast(1)
        val height = (760 + rowCount * 156).coerceAtLeast(1260)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Deep MD3 Dark Emerald Background Gradient
        paint.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(
                Color.parseColor("#0B1714"),
                Color.parseColor("#0F221D"),
                Color.parseColor("#091310")
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        // Decorative glowing header orb
        val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), 420f,
                Color.parseColor("#244FD8B6"),
                Color.parseColor("#004FD8B6"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(RectF(40f, 40f, width - 40f, 420f), 44f, 44f, orbPaint)

        // 2. Top Pill Badge
        val badgeRect = RectF(72f, 76f, 480f, 130f)
        paint.color = Color.parseColor("#1D3B33")
        canvas.drawRoundRect(badgeRect, 27f, 27f, paint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6FF7D4")
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(
            if (isEn) "CLIPBAIT STANDINGS REPORT" else "CLIPBAIT 剪贴板积分榜",
            98f,
            112f,
            textPaint
        )

        // 3. Main Title & Subtitle
        textPaint.apply {
            color = Color.parseColor("#ECFFF9")
            textSize = 64f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(titleText, 72f, 224f, textPaint)

        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        textPaint.apply {
            color = Color.parseColor("#8FB5AA")
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText("$subtitleText · $nowStr", 72f, 276f, textPaint)

        // 4. Three Summary KPI Cards
        val kpiTop = 320f
        val kpiBottom = 490f
        val cardGap = 24f
        val cardWidth = (width - 144f - cardGap * 2f) / 3f

        val topOffenderName = offenders.firstOrNull()?.appName
            ?: if (isEn) "None" else "暂无记录"
        val kpis = listOf(
            Triple(
                if (isEn) "TOTAL INTERCEPTS" else "累计拦截",
                if (isEn) "$totalCount" else "${totalCount}次",
                Color.parseColor("#6FF7D4")
            ),
            Triple(
                if (isEn) "APPS" else "上榜应用",
                if (isEn) "${offenders.size} apps" else "${offenders.size}款",
                Color.parseColor("#FFD580")
            ),
            Triple(
                if (isEn) "#1 APP" else "榜首应用",
                ellipsizeText(topOffenderName, 7),
                Color.parseColor("#FF8E80")
            )
        )

        kpis.forEachIndexed { idx, (label, value, accentColor) ->
            val left = 72f + idx * (cardWidth + cardGap)
            val rect = RectF(left, kpiTop, left + cardWidth, kpiBottom)
            paint.color = Color.parseColor("#152B25")
            canvas.drawRoundRect(rect, 28f, 28f, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = Color.parseColor("#254A40")
            canvas.drawRoundRect(rect, 28f, 28f, paint)
            paint.style = Paint.Style.FILL

            textPaint.apply {
                color = Color.parseColor("#8FB5AA")
                textSize = 26f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            canvas.drawText(label, left + 28f, kpiTop + 56f, textPaint)

            textPaint.apply {
                color = accentColor
                textSize = 44f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(value, left + 28f, kpiTop + 126f, textPaint)
        }

        // 5. Section Header
        textPaint.apply {
            color = Color.parseColor("#D5EFE7")
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(
            if (isEn) "TOP CLIPBOARD STANDINGS" else "剪贴板读取积分榜前六名",
            72f,
            564f,
            textPaint
        )

        // 6. Leaderboard Rows
        val startY = 596f
        val rowHeight = 136f
        val rowGap = 20f
        val maxCount = (offenders.firstOrNull()?.count ?: 1).coerceAtLeast(1)

        if (offenders.isEmpty()) {
            val emptyRect = RectF(72f, startY, width - 72f, startY + 200f)
            paint.color = Color.parseColor("#142822")
            canvas.drawRoundRect(emptyRect, 28f, 28f, paint)
            textPaint.apply {
                color = Color.parseColor("#8FB5AA")
                textSize = 32f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            canvas.drawText(
                if (isEn) "No intercepts recorded during this period" else "本周期内暂无拦截记录",
                120f,
                startY + 112f,
                textPaint
            )
        } else {
            offenders.forEachIndexed { index, stat ->
                val top = startY + index * (rowHeight + rowGap)
                val bottom = top + rowHeight
                val rowRect = RectF(72f, top, width - 72f, bottom)

                paint.color = if (index == 0) Color.parseColor("#1A352D") else Color.parseColor("#132620")
                canvas.drawRoundRect(rowRect, 28f, 28f, paint)

                // Rank accent color
                val rankColor = when (index) {
                    0 -> Color.parseColor("#FFD166") // Gold
                    1 -> Color.parseColor("#D8E2DC") // Silver
                    2 -> Color.parseColor("#F4A261") // Bronze
                    else -> Color.parseColor("#6FF7D4")
                }

                // Rank badge pill on left
                val rankBadgeRect = RectF(96f, top + 36f, 168f, top + 100f)
                paint.color = Color.argb(42, Color.red(rankColor), Color.green(rankColor), Color.blue(rankColor))
                canvas.drawRoundRect(rankBadgeRect, 18f, 18f, paint)

                textPaint.apply {
                    color = rankColor
                    textSize = 30f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                val rankLabel = when (index) {
                    0 -> "NO.1"
                    1 -> "NO.2"
                    2 -> "NO.3"
                    else -> "#${index + 1}"
                }
                canvas.drawText(rankLabel, rankBadgeRect.centerX(), top + 79f, textPaint)
                textPaint.textAlign = Paint.Align.LEFT

                // App Icon (84x84)
                val iconLeft = 188f
                val iconTop = top + 26f
                val iconSize = 84
                val iconBmp = loadAppIconBitmap(context, stat.packageName, iconSize)
                if (iconBmp != null) {
                    val saveCount = canvas.save()
                    val clipPath = Path().apply {
                        addRoundRect(
                            RectF(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize),
                            20f,
                            20f,
                            Path.Direction.CW
                        )
                    }
                    canvas.clipPath(clipPath)
                    canvas.drawBitmap(iconBmp, iconLeft, iconTop, null)
                    canvas.restoreToCount(saveCount)
                } else {
                    paint.color = Color.parseColor("#24473D")
                    canvas.drawRoundRect(
                        RectF(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize),
                        20f,
                        20f,
                        paint
                    )
                }

                // App Name + Roast Title
                val textLeft = 294f
                textPaint.apply {
                    color = Color.parseColor("#F2FFF9")
                    textSize = 34f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val displayAppName = ellipsizeText(stat.appName, 12)
                canvas.drawText(displayAppName, textLeft, top + 58f, textPaint)

                val roastTag = buildPosterRoastTag(index, stat.count, isEn)
                textPaint.apply {
                    color = Color.parseColor("#83B2A5")
                    textSize = 23f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                }
                val subLine = "$roastTag · ${ellipsizeText(stat.packageName, 24)}"
                canvas.drawText(subLine, textLeft, top + 92f, textPaint)

                // Progress bar at bottom of row
                val barLeft = textLeft
                val barRightMax = width - 250f
                val barTop = top + 106f
                val barBottom = top + 118f
                paint.color = Color.parseColor("#1E3B33")
                canvas.drawRoundRect(RectF(barLeft, barTop, barRightMax, barBottom), 6f, 6f, paint)

                val ratio = (stat.count.toFloat() / maxCount.toFloat()).coerceIn(0.08f, 1f)
                val fillRight = barLeft + (barRightMax - barLeft) * ratio
                paint.color = rankColor
                canvas.drawRoundRect(RectF(barLeft, barTop, fillRight, barBottom), 6f, 6f, paint)

                // Right-aligned Intercept Count
                textPaint.apply {
                    color = rankColor
                    textSize = 40f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.RIGHT
                }
                val countStr = if (isEn) "${stat.count}x" else "${stat.count} 次"
                canvas.drawText(countStr, width - 104f, top + 82f, textPaint)
                textPaint.textAlign = Paint.Align.LEFT
            }
        }

        // 7. Footer Watermark
        val footerY = height - 54f
        textPaint.apply {
            color = Color.parseColor("#638C80")
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        val footerText = if (isEn) {
            "Generated by ${AppInfo.APP_NAME} v${AppInfo.VERSION_NAME} · Open-Source Zero-Trust Clipboard Guard"
        } else {
            "由 ${AppInfo.APP_NAME} v${AppInfo.VERSION_NAME} 自动生成 · 开源零信任剪贴板守门员"
        }
        canvas.drawText(footerText, width / 2f, footerY, textPaint)
        textPaint.textAlign = Paint.Align.LEFT

        return bitmap
    }

    private fun buildPosterRoastTag(index: Int, count: Int, isEn: Boolean): String {
        if (isEn) {
            return when {
                index == 0 -> "P1 Leader"
                index == 1 -> "P2 Runner-up"
                index == 2 -> "P3 Podium"
                else -> "Points Scorer"
            }
        }
        return when {
            index == 0 -> "积分榜领跑"
            index == 1 -> "积分榜次席"
            index == 2 -> "积分榜第三"
            else -> "积分区完赛"
        }
    }

    private fun ellipsizeText(text: String, maxChars: Int): String {
        return if (text.length > maxChars) text.take(maxChars - 1) + "…" else text
    }

    private fun loadAppIconBitmap(context: Context, packageName: String, sizePx: Int): Bitmap? {
        return try {
            val drawable: Drawable = context.packageManager.getApplicationIcon(packageName)
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                Bitmap.createScaledBitmap(drawable.bitmap, sizePx, sizePx, true)
            } else {
                val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                drawable.setBounds(0, 0, sizePx, sizePx)
                drawable.draw(c)
                bmp
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileName: String): Uri? {
        return try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ClipBait")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val uri = resolver.insert(collection, values) ?: return null
            var out: OutputStream? = null
            try {
                out = resolver.openOutputStream(uri)
                if (out != null) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                }
            } finally {
                out?.close()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val doneValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, doneValues, null, null)
            }
            uri
        } catch (_: Throwable) {
            null
        }
    }

    private fun postPosterNotification(
        context: Context,
        bitmap: Bitmap,
        savedUri: Uri?,
        titleText: String,
        totalCount: Int,
        topAppName: String?,
        isEn: Boolean
    ) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    POSTER_CHANNEL_ID,
                    if (isEn) "Periodic Hall of Fame Poster" else "光荣榜月度/年度积分榜战报图",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = if (isEn) {
                        "Sends your generated Monthly or Yearly Clipboard Standings poster image"
                    } else {
                        "定期为您生成并推送做好的月份或年度剪贴板积分榜图片"
                    }
                }
                nm.createNotificationChannel(channel)
            }

            val viewIntent = if (savedUri != null) {
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(savedUri, "image/png")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }

            val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            val contentPendingIntent = PendingIntent.getActivity(context, 3088, viewIntent, pendingFlags)

            val summary = if (isEn) {
                val topStr = if (!topAppName.isNullOrBlank()) " · #1: $topAppName" else ""
                "Total caught: $totalCount$topStr (Saved to Pictures/ClipBait)"
            } else {
                val topStr = if (!topAppName.isNullOrBlank()) " · 榜首：$topAppName" else ""
                "累计抓现行 $totalCount 次$topStr（已存至相册 Pictures/ClipBait）"
            }

            val notification = NotificationCompat.Builder(context, POSTER_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_clipbait)
                .setContentTitle(if (isEn) "$titleText Ready" else "$titleText 已生成")
                .setContentText(summary)
                .setLargeIcon(bitmap)
                .setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .bigLargeIcon(null as Bitmap?)
                        .setSummaryText(summary)
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(contentPendingIntent)
                .build()

            nm.notify(POSTER_NOTIFICATION_ID, notification)
        } catch (_: Throwable) {
        }
    }
}
