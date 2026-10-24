package me.pj.jqb.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import java.util.Locale

val LocalIsEnglish = compositionLocalOf {
    !Locale.getDefault().language.startsWith("zh", ignoreCase = true)
}

@Composable
@ReadOnlyComposable
fun isEnglishLocale(): Boolean = LocalIsEnglish.current

@Composable
@ReadOnlyComposable
fun tr(zh: String, en: String): String = if (LocalIsEnglish.current) en else zh

fun resolveIsEnglish(languageSetting: String): Boolean {
    return when (languageSetting.lowercase()) {
        "en", "english" -> true
        "zh", "cn", "chinese" -> false
        else -> !Locale.getDefault().language.startsWith("zh", ignoreCase = true)
    }
}
