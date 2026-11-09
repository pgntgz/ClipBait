package me.pj.jqb.data.repo

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.pj.jqb.AppInfo
import java.net.HttpURLConnection
import java.net.URL

@Immutable
data class ReleaseUpdateInfo(
    val tagName: String,
    val remoteVersionName: String,
    val remoteVersionCode: Int?,
    val hasUpdate: Boolean,
    val releaseNotes: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?
)

object UpdateChecker {

    private val json = Json { ignoreUnknownKeys = true }

    private val endpoints = listOf(
        "https://api.github.com/repos/pgntgz/ClipBait/releases/latest",
        "https://api.github.com/repos/Xposed-Modules-Repo/io.github.pgntgz.clipbait/releases/latest"
    )

    suspend fun checkLatestRelease(): ReleaseUpdateInfo = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        for (endpoint in endpoints) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(endpoint)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "ClipBait/${AppInfo.VERSION_NAME}")
                }
                val code = conn.responseCode
                if (code == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val root = json.parseToJsonElement(body).jsonObject
                    val tagName = root["tag_name"]?.jsonPrimitive?.content?.trim().orEmpty()
                    if (tagName.isBlank()) {
                        errors.add("缺少 tag_name")
                        continue
                    }
                    val htmlUrl = root["html_url"]?.jsonPrimitive?.content?.trim()
                        ?: AppInfo.RELEASES_URL
                    val notes = root["body"]?.jsonPrimitive?.content?.trim().orEmpty()

                    var apkUrl: String? = null
                    val assets = root["assets"]?.jsonArray
                    if (assets != null) {
                        for (el in assets) {
                            val obj = el.jsonObject
                            val name = obj["name"]?.jsonPrimitive?.content.orEmpty()
                            val dlUrl = obj["browser_download_url"]?.jsonPrimitive?.content
                            if (name.endsWith(".apk", ignoreCase = true) && !dlUrl.isNullOrBlank()) {
                                apkUrl = dlUrl
                                break
                            }
                        }
                    }

                    val (remoteCode, remoteVerName) = parseTag(tagName)
                    val isNewer = isRemoteNewer(
                        remoteCode = remoteCode,
                        remoteVersionName = remoteVerName,
                        localCode = AppInfo.VERSION_CODE,
                        localVersionName = AppInfo.VERSION_NAME
                    )

                    return@withContext ReleaseUpdateInfo(
                        tagName = tagName,
                        remoteVersionName = remoteVerName,
                        remoteVersionCode = remoteCode,
                        hasUpdate = isNewer,
                        releaseNotes = notes,
                        htmlUrl = htmlUrl,
                        apkDownloadUrl = apkUrl
                    )
                } else {
                    errors.add("HTTP $code")
                }
            } catch (e: Exception) {
                errors.add("${e.javaClass.simpleName}: ${e.message}")
            } finally {
                try {
                    conn?.disconnect()
                } catch (_: Throwable) {
                }
            }
        }
        throw IllegalStateException(errors.distinct().joinToString(" | ").ifBlank { "网络请求失败" })
    }

    /**
     * Parses tags like "4-1.1.2" -> (4, "1.1.2") or "v1.1.2" -> (null, "1.1.2").
     */
    private fun parseTag(tag: String): Pair<Int?, String> {
        val trimmed = tag.trim().removePrefix("v").removePrefix("V")
        val dashIdx = trimmed.indexOf('-')
        if (dashIdx > 0) {
            val maybeCode = trimmed.substring(0, dashIdx).toIntOrNull()
            val rest = trimmed.substring(dashIdx + 1).removePrefix("v").removePrefix("V").trim()
            if (maybeCode != null && rest.isNotEmpty()) {
                return maybeCode to rest
            }
        }
        return null to trimmed
    }

    private fun isRemoteNewer(
        remoteCode: Int?,
        remoteVersionName: String,
        localCode: Int,
        localVersionName: String
    ): Boolean {
        if (remoteCode != null && remoteCode != localCode) {
            return remoteCode > localCode
        }
        val rParts = remoteVersionName.split('.').map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val lParts = localVersionName.split('.').map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val maxLen = maxOf(rParts.size, lParts.size)
        for (i in 0 until maxLen) {
            val rv = rParts.getOrElse(i) { 0 }
            val lv = lParts.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }
}
