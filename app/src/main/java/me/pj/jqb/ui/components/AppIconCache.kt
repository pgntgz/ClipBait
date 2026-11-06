package me.pj.jqb.ui.components

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

@Immutable
data class AppDetailMeta(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val targetSdk: Int,
    val minSdk: Int,
    val uid: Int,
    val isSystemApp: Boolean
)

object AppIconCache {

    private const val MAX_ICON_CACHE_SIZE = 650
    private val bitmapCache = ConcurrentHashMap<String, ImageBitmap>(256)
    private val labelCache = ConcurrentHashMap<String, String>(256)
    private val metaCache = ConcurrentHashMap<String, AppDetailMeta>(128)

    fun getCachedIcon(packageName: String): ImageBitmap? {
        return bitmapCache[packageName]
    }

    fun cacheLabel(packageName: String, label: String) {
        if (label.isNotBlank() && label != packageName) {
            labelCache[packageName] = label
        }
    }

    suspend fun loadIconAsync(context: Context, packageName: String): ImageBitmap? {
        bitmapCache[packageName]?.let { return it }
        return withContext(Dispatchers.IO) {
            bitmapCache[packageName]?.let { return@withContext it }
            try {
                val pm = context.packageManager
                val drawable = pm.getApplicationIcon(packageName)
                val bitmap = drawable.toBitmap(96, 96, Bitmap.Config.ARGB_8888).asImageBitmap()
                if (bitmapCache.size >= MAX_ICON_CACHE_SIZE) {
                    // Evict a small batch if the user has hundreds of system apps
                    val iterator = bitmapCache.keys.iterator()
                    var removed = 0
                    while (iterator.hasNext() && removed < 64) {
                        iterator.next()
                        iterator.remove()
                        removed++
                    }
                }
                bitmapCache[packageName] = bitmap
                bitmap
            } catch (_: Exception) {
                null
            }
        }
    }

    fun getAppMeta(context: Context, packageName: String): AppDetailMeta {
        metaCache[packageName]?.let { return it }
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val pkgInfo = pm.getPackageInfo(packageName, 0)
            val label = labelCache[packageName]
                ?: pm.getApplicationLabel(appInfo).toString().ifBlank { packageName }
            cacheLabel(packageName, label)
            val meta = AppDetailMeta(
                packageName = packageName,
                appName = label,
                versionName = pkgInfo.versionName ?: "未知",
                targetSdk = appInfo.targetSdkVersion,
                minSdk = appInfo.minSdkVersion,
                uid = appInfo.uid,
                isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            )
            metaCache[packageName] = meta
            meta
        } catch (_: Exception) {
            AppDetailMeta(
                packageName = packageName,
                appName = labelCache[packageName] ?: packageName,
                versionName = "未安装/系统组件",
                targetSdk = 0,
                minSdk = 0,
                uid = 0,
                isSystemApp = false
            )
        }
    }

    fun resolveLabel(context: Context, packageName: String, fallback: String): String {
        if (fallback.isNotBlank() && fallback != packageName) {
            cacheLabel(packageName, fallback)
            return fallback
        }
        labelCache[packageName]?.let { return it }
        return getAppMeta(context, packageName).appName
    }
}

private val DefaultIconShape10 = RoundedCornerShape(10.dp)
private val DefaultIconShape8 = RoundedCornerShape(8.dp)
private val DefaultIconShape12 = RoundedCornerShape(12.dp)

@Composable
fun AppIcon(
    packageName: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    cornerRadius: Dp = 10.dp
) {
    val context = LocalContext.current
    var iconBitmap by remember(packageName) {
        mutableStateOf(AppIconCache.getCachedIcon(packageName))
    }

    if (iconBitmap == null) {
        LaunchedEffect(packageName) {
            iconBitmap = AppIconCache.loadIconAsync(context, packageName)
        }
    }

    val shape = when (cornerRadius) {
        10.dp -> DefaultIconShape10
        8.dp -> DefaultIconShape8
        12.dp -> DefaultIconShape12
        else -> remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    }

    val bmp = iconBitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(shape)
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Apps,
                contentDescription = null,
                modifier = Modifier.size(size * 0.55f),
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}
