package me.pj.jqb.ui.components

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

    private val bitmapCache = object : LruCache<String, ImageBitmap>(200) {}
    private val metaCache = ConcurrentHashMap<String, AppDetailMeta>()

    fun getCachedIcon(packageName: String): ImageBitmap? {
        return synchronized(bitmapCache) {
            bitmapCache.get(packageName)
        }
    }

    suspend fun loadIconAsync(context: Context, packageName: String): ImageBitmap? {
        getCachedIcon(packageName)?.let { return it }
        return withContext(Dispatchers.Default) {
            try {
                val pm = context.packageManager
                val drawable = pm.getApplicationIcon(packageName)
                val bitmap = drawable.toBitmap(112, 112, Bitmap.Config.ARGB_8888).asImageBitmap()
                synchronized(bitmapCache) {
                    bitmapCache.put(packageName, bitmap)
                }
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
            val label = pm.getApplicationLabel(appInfo).toString().ifBlank { packageName }
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
                appName = packageName,
                versionName = "未安装/系统组件",
                targetSdk = 0,
                minSdk = 0,
                uid = 0,
                isSystemApp = false
            )
        }
    }

    fun resolveLabel(context: Context, packageName: String, fallback: String): String {
        if (fallback.isNotBlank() && fallback != packageName) return fallback
        return getAppMeta(context, packageName).appName
    }
}

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

    LaunchedEffect(packageName) {
        if (iconBitmap == null) {
            iconBitmap = AppIconCache.loadIconAsync(context, packageName)
        }
    }

    Crossfade(
        targetState = iconBitmap,
        animationSpec = tween(durationMillis = 180),
        label = "AppIconCrossfade",
        modifier = modifier.size(size)
    ) { bmp ->
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(cornerRadius))
            )
        } else {
            Surface(
                shape = RoundedCornerShape(cornerRadius),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(size)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Apps,
                        contentDescription = null,
                        modifier = Modifier.size(size * 0.55f),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
