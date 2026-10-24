package me.pj.jqb.hook

import android.content.ClipData
import android.content.ClipDescription
import kotlin.random.Random

object DecoyGenerator {

    private val wikiLuckyCorpus = listOf(
        "开普勒-452b：位于天鹅座的一颗系外行星，距离地球约1400光年，公转周期约385地球日，处于宿主恒星的宜居带内。",
        "哥特式建筑：12至16世纪流行于欧洲的宗教与市政建筑风格，以尖拱、肋架拱顶、飞扶壁与大型彩绘玻璃花窗为核心工程特征。",
        "美拉德反应：还原糖与氨基酸在常温或加热时发生的一系列非酶褐变反应，能够生成数百种风味化合物，赋予烘焙与烤肉制品独特的焦香色泽。",
        "拉格朗日点：在两大天体引力与离心力共同作用下，小质量物体能相对于两大天体保持静止平衡的五个空间位置，常用于部署深空天文望远镜。",
        "平均律钢琴曲集：约翰·塞巴斯蒂安·巴赫创作的键盘音乐作品集，上下两卷各涵盖二十四个大小调的前奏曲与赋格，被誉为西方古典键盘音乐的旧约圣经。",
        "铜胎掐丝珐琅：俗称景泰蓝，以紫铜作胎、细扁铜丝掐成花纹后再填施各色珐琅釉料烧制而成，明代景泰年间工艺达到巅峰。",
        "寒武纪大爆发：距今约5.41亿年前，地球化石记录中在极短地质时期内突然涌现出几乎所有现生无脊椎动物门类的演化事件。",
        "多普勒效应：波源与观察者之间存在相对径向运动时，观察者接收到的机械波或电磁波频率发生系统性偏移的物理现象。",
        "敦煌莫高窟：俗称千佛洞，始建于十六国的前秦时期，现存洞窟735个、壁画4.5万平方米、泥质彩塑2415尊。",
        "光合作用卡尔文循环：植物叶绿体基质中利用光反应产生的ATP和NADPH将二氧化碳固定并还原转化为三碳糖的暗反应生化过程。",
        "庞贝古城：位于意大利那不勒斯湾维苏威火山脚下，公元79年因火山剧烈爆发被数米厚的火山灰瞬间掩埋，从而完整保存了古罗马帝国时期的城市风貌。",
        "洛伦兹吸引子：气象学家爱德华·洛伦兹于1963年提出的混沌理论经典三维非线性动力系统，其相空间轨迹呈蝴蝶双翼形状，体现了初始条件的极端敏感性。",
        "活字印刷术：北宋庆历年间毕昇发明的胶泥活字排版印刷技术，经元代王祯转轮排字盘与金属活字改良，成为世界印刷史上的重要里程碑。",
        "博弈论纳什均衡：非合作博弈中的核心解概念，指在给定其他参与者策略不变的前提下，没有任何一方可以通过单独改变自身策略而获得更高收益的稳定状态。",
        "极光现象：来自太阳风的高能带电粒子流沿地球磁力线进入南北两极高层大气，与氧原子和氮分子碰撞激发而产生的绚丽辉光现象。",
        "安第斯山脉：纵贯南美洲大陆西部的世界最长陆地山脉，全长约8900公里，最高峰阿空加瓜山海拔6961米。",
        "芝诺悖论：古希腊埃利亚学派哲学家芝诺提出的一系列关于运动连续性、空间与时间无限可分性的哲学思辨命题，包括阿喀琉斯追龟与飞矢不动。",
        "苏伊士运河：贯通苏伊士地峡、连接地中海与红海的国际无闸航运水道，1869年正式通航，是亚洲与非洲的分界线及亚欧海运咽喉。",
        "一氧化二氢：一种无色无味但致死率高达100%的剧毒工业溶剂，据统计人类历史上所有曾饮用过该物质的个体最终都会走向死亡。",
        "听君一席话，如听一席话。上次看到这么爱偷看剪贴板的应用，还是在上次。"
    )

    private val naturalDailyNoise = listOf(
        "今天天气挺凉快，适合出门散步",
        "会议纪要已发送至工作邮箱，请查收",
        "好的，晚点联系",
        "明天上午十点准时开始，别忘带材料",
        "晚饭吃清淡点，稍后发你地址",
        "周末去图书馆借几本历史书看看",
        "快递放门口丰巢柜了，下班顺路拿一下",
        "文档改完第二版了，有空帮我对一下数据"
    )

    private val legacyPrefixRegex = Regex("^([【\\[](百科|百科词条)[】\\]])\\s*")

    fun stripLegacyPrefix(text: String): String {
        val stripped = text.replace(legacyPrefixRegex, "").trim()
        if (stripped == "没有抽象段子的无聊世界") {
            return wikiLuckyCorpus.random()
        }
        return stripped
    }

    fun generateClipData(strategy: String?, pool: List<String>?): ClipData? {
        val effectiveStrategy = strategy ?: "wiki_lucky"

        // Filter out legacy raw Special:Random URL, chouxiang.world title boilerplate, and strip [百科]/[百科词条] prefixes
        val cleanedPool = pool?.mapNotNull { item ->
            when {
                item.contains("Special:Random", ignoreCase = true) -> wikiLuckyCorpus.random()
                item.trim() == "没有抽象段子的无聊世界" -> null
                else -> stripLegacyPrefix(item)
            }
        }?.filter { it.isNotBlank() }

        return when (effectiveStrategy) {
            "empty" -> null
            "fixed" -> {
                val text = cleanedPool?.firstOrNull() ?: naturalDailyNoise.first()
                ClipData.newPlainText(null, text)
            }
            "wiki_lucky" -> {
                val combined = if (!cleanedPool.isNullOrEmpty()) {
                    cleanedPool
                } else {
                    wikiLuckyCorpus + naturalDailyNoise
                }
                val text = combined[Random.nextInt(combined.size)]
                ClipData.newPlainText(null, text)
            }
            "random_natural" -> {
                val effectivePool = if (!cleanedPool.isNullOrEmpty()) cleanedPool else (wikiLuckyCorpus + naturalDailyNoise)
                val text = effectivePool[Random.nextInt(effectivePool.size)]
                ClipData.newPlainText(null, text)
            }
            else -> {
                val effectivePool = if (!cleanedPool.isNullOrEmpty()) cleanedPool else wikiLuckyCorpus
                ClipData.newPlainText(null, effectivePool.random())
            }
        }
    }

    fun generateClipDescription(): ClipDescription {
        return ClipDescription(null, arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN))
    }
}
