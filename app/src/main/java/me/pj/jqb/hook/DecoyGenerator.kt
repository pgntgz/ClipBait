package me.pj.jqb.hook

import android.content.ClipData
import android.content.ClipDescription
import kotlin.random.Random

object DecoyGenerator {

    private val fallbackNoise = listOf(
        "今天天气挺凉快，适合出门散步",
        "会议纪要已发送至工作邮箱，请查收",
        "好的，晚点联系",
        "明天上午十点准时开始",
        "https://zh.wikipedia.org/wiki/Special:Random"
    )

    fun generateClipData(strategy: String?, pool: List<String>?): ClipData? {
        val effectiveStrategy = strategy ?: "random_natural"
        val effectivePool = if (!pool.isNullOrEmpty()) pool else fallbackNoise

        return when (effectiveStrategy) {
            "empty" -> null
            "fixed" -> {
                val text = effectivePool.firstOrNull() ?: ""
                ClipData.newPlainText("text/plain", text)
            }
            "random_natural" -> {
                val randomIndex = Random.nextInt(effectivePool.size)
                val text = effectivePool[randomIndex]
                ClipData.newPlainText("text/plain", text)
            }
            else -> {
                val text = effectivePool.random()
                ClipData.newPlainText("text/plain", text)
            }
        }
    }

    fun generateClipDescription(): ClipDescription {
        return ClipDescription("text/plain", arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN))
    }
}
