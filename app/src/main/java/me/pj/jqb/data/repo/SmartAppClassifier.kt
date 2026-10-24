package me.pj.jqb.data.repo

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.pj.jqb.model.ActionType

@Immutable
enum class RiskCategory(val title: String, val badgeText: String, val recommendedAction: ActionType) {
    ECOMMERCE_AND_PROFILED(
        title = "电商、短视频与生活平台",
        badgeText = "高频口令/画像窥探",
        recommendedAction = ActionType.SPOOF
    ),
    SOCIAL_AND_IM(
        title = "日常社交与办公通讯",
        badgeText = "需保留主动粘贴",
        recommendedAction = ActionType.SMART
    ),
    DOMESTIC_SDK_DETECTED(
        title = "检出国内商业统计/推送/广告 SDK",
        badgeText = "含国内商业探针组件",
        recommendedAction = ActionType.BLOCK
    )
}

@Immutable
data class SmartDetectedApp(
    val packageName: String,
    val appName: String,
    val category: RiskCategory,
    val reason: String,
    val currentAction: ActionType,
    val recommendedAction: ActionType,
    val selected: Boolean = true
)

object SmartAppClassifier {

    // Clean open-source / proxy / input method tools that should NEVER be flagged as rogue domestic apps
    private val safeWhitelistPrefixes = listOf(
        "me.pj.jqb",
        "org.fcitx.",
        "io.nekohasekai.",
        "com.follow.clash",
        "com.github.kr328.",
        "rikka.",
        "moe.shizuku.",
        "me.weishu.",
        "com.topjohnwu.magisk",
        "org.lsposed.",
        "de.robv.android.xposed",
        "me.zhanghai.android.files",
        "com.google.",
        "com.android."
    )

    // 1. E-commerce, short-video, content feeds & local services -> Recommend SPOOF (污染画像)
    private val ecommerceAndFeedPackages = mapOf(
        "com.taobao.taobao" to "淘宝 · 电商淘口令与画像高频扫描",
        "com.taobao.idlefish" to "闲鱼 · 阿里系剪切板口令监听",
        "com.alibaba.wireless" to "阿里巴巴 1688 · 电商剪切板监听",
        "com.taobao.litetao" to "淘特 · 电商口令扫描",
        "com.wudaokou.hippo" to "盒马 · 阿里系生鲜电商",
        "cn.damai" to "大麦 · 阿里系票务平台",
        "com.Cainiao.cainiao" to "菜鸟 · 快递单号与电商关联读取",
        "com.eg.android.AlipayGphone" to "支付宝 · 吱口令与营销跳转扫描",
        "com.autonavi.minimap" to "高德地图 · 口令位置与商业推广读取",
        "com.xunmeng.pinduoduo" to "拼多多 · 高频后台与切台口令窥探",
        "com.jingdong.app.mall" to "京东 · 购物口令与客群分析",
        "com.jd.pingou" to "京喜 · 京东系口令读取",
        "com.sankuai.meituan" to "美团 · 团购口令与位置关联画像",
        "com.sankuai.meituan.takeoutnew" to "美团外卖 · 优惠口令读取",
        "com.dianping.v1" to "大众点评 · 分享口令与商业画像",
        "me.ele" to "饿了么 · 外卖口令扫描",
        "com.ss.android.ugc.aweme" to "抖音 · 视频口令与跨平台追踪重灾区",
        "com.ss.android.ugc.aweme.lite" to "抖音极速版 · 商业推广口令扫描",
        "com.ss.android.article.news" to "今日头条 · 字节系资讯追踪",
        "com.dragon.read" to "番茄免费小说 · 字节系内容追踪",
        "com.ss.android.auto" to "懂车帝 · 字节系垂类追踪",
        "com.smile.gifmaker" to "快手 · 短视频口令与拉新追踪",
        "com.kuaishou.nebula" to "快手极速版 · 推广口令扫描",
        "com.xingin.xhs" to "小红书 · 笔记分享口令与消费画像",
        "com.shizhuang.duapp" to "得物 · 潮品电商画像扫描",
        "com.achievo.vipshop" to "唯品会 · 电商客群读取",
        "com.wuba.zhuanzhuan" to "转转 · 二手电商口令读取",
        "com.sina.weibo" to "微博 · 社交热点与广告追踪",
        "com.baidu.searchbox" to "百度 · 搜索口令与广告归因",
        "com.baidu.tieba" to "百度贴吧 · 社区口令与广告 SDK",
        "com.baidu.BaiduMap" to "百度地图 · 位置口令扫描",
        "ctrip.android.view" to "携程旅行 · 出行酒店比价画像",
        "com.Qunar" to "去哪儿旅行 · 差旅比价画像",
        "com.taobao.trip" to "飞猪旅行 · 阿里系差旅画像",
        "com.sdu.didi.psnger" to "滴滴出行 · 出行口令与商业统计",
        "com.jingyao.easybike" to "哈啰 · 本地出行与营销读取"
    )

    private val ecommerceKeywordPrefixes = listOf(
        "com.taobao.",
        "com.alibaba.wireless",
        "com.xunmeng.",
        "com.jingdong.",
        "com.sankuai.",
        "com.dianping.",
        "com.ss.android.",
        "com.kuaishou.",
        "com.achievo.",
        "ctrip.android."
    )

    // 2. Social, IM & Community -> Recommend SMART (放行主动粘贴，阻断切台偷读)
    private val socialAndImPackages = mapOf(
        "com.tencent.mm" to "微信 · 日常聊天需粘贴，智能阻断切台读取",
        "com.tencent.mobileqq" to "QQ · 频繁切台读取剪切板，需保留聊天粘贴",
        "com.tencent.tim" to "TIM · 腾讯办公聊天，推荐智能模式",
        "com.tencent.wework" to "企业微信 · 办公通讯，推荐智能放行粘贴",
        "com.alibaba.android.rimet" to "钉钉 · 办公通讯，推荐智能放行粘贴",
        "com.ss.android.lark" to "飞书 · 办公协作，推荐智能放行粘贴",
        "tv.danmaku.bili" to "哔哩哔哩 · 链接跳转读取，推荐智能拦截",
        "com.bilibili.app.in" to "哔哩哔哩国际版 · 推荐智能拦截",
        "com.zhihu.android" to "知乎 · 剪贴板链接探测，推荐智能拦截",
        "com.coolapk.market" to "酷安 · 社区链接识别，推荐智能拦截",
        "com.douban.frodo" to "豆瓣 · 社区应用，推荐智能拦截",
        "com.mihoyo.hyperion" to "米游社 · 社区兑换码粘贴，推荐智能模式",
        "com.max.xiaoheihe" to "小黑盒 · 游戏社区，推荐智能模式",
        "com.netease.cloudmusic" to "网易云音乐 · 歌单口令读取，推荐智能或伪装",
        "com.tencent.qqmusic" to "QQ音乐 · 音乐口令读取，推荐智能或伪装"
    )

    // 3. Domestic Tracking / Push / Ad SDK Component Signatures inside APK Manifest
    private val domesticSdkSignatures = listOf(
        "com.umeng." to "友盟统计/分析 SDK",
        "cn.jpush." to "极光推送 SDK",
        "cn.jiguang." to "极光数据 SDK",
        "com.igexin." to "个推消息 SDK",
        "com.bytedance.sdk.openadsdk" to "穿山甲广告 SDK",
        "com.qq.e.ads" to "腾讯优量汇广告 SDK",
        "com.kwad.sdk" to "快手广告联盟 SDK",
        "com.taobao.accs" to "阿里 ACCS 通道 SDK",
        "org.android.agoo" to "阿里推送组件",
        "com.tencent.android.tpush" to "腾讯信鸽推送 SDK",
        "com.baidu.mobstat" to "百度移动统计 SDK",
        "com.sensorsdata.analytics" to "神策数据埋点 SDK",
        "cn.sharesdk." to "ShareSDK 社会化分享组件"
    )

    private val domesticNamespacePrefixes = listOf(
        "cn.",
        "com.tencent.",
        "com.baidu.",
        "com.sina.",
        "com.netease.",
        "com.sohu.",
        "com.ifeng.",
        "com.youku.",
        "com.iqiyi.",
        "com.qiyi.",
        "com.hunantv.",
        "com.cmcc.",
        "com.chinamobile.",
        "com.sinovatech.unicom.",
        "com.ct.client",
        "com.unionpay",
        "com.icbc",
        "cn.com.cmbc",
        "com.cmbchina."
    )

    suspend fun scanInstalledApps(
        context: Context,
        getCurrentAction: (String) -> ActionType
    ): List<SmartDetectedApp> = withContext(Dispatchers.Default) {
        val pm = context.packageManager
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val results = mutableListOf<SmartDetectedApp>()

        for (appInfo in installed) {
            val pkg = appInfo.packageName
            // Skip system core apps (unless updated commercial preinstalls)
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (isSystem && !isUpdatedSystem &&
                !ecommerceAndFeedPackages.containsKey(pkg) &&
                !socialAndImPackages.containsKey(pkg)
            ) {
                continue
            }
            if (safeWhitelistPrefixes.any { pkg.startsWith(it, ignoreCase = true) }) {
                continue
            }

            val label = try {
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                pkg
            }
            val currentAction = getCurrentAction(pkg)

            // Match 1: Known E-Commerce & Profiling platforms
            val exactEcommerceReason = ecommerceAndFeedPackages[pkg]
            if (exactEcommerceReason != null) {
                results.add(
                    SmartDetectedApp(
                        packageName = pkg,
                        appName = label,
                        category = RiskCategory.ECOMMERCE_AND_PROFILED,
                        reason = exactEcommerceReason,
                        currentAction = currentAction,
                        recommendedAction = ActionType.SPOOF
                    )
                )
                continue
            }

            if (ecommerceKeywordPrefixes.any { pkg.startsWith(it, ignoreCase = true) }) {
                results.add(
                    SmartDetectedApp(
                        packageName = pkg,
                        appName = label,
                        category = RiskCategory.ECOMMERCE_AND_PROFILED,
                        reason = "匹配国内电商/短视频家族包名规则 ($pkg)",
                        currentAction = currentAction,
                        recommendedAction = ActionType.SPOOF
                    )
                )
                continue
            }

            // Match 2: Known Social & IM apps
            val exactSocialReason = socialAndImPackages[pkg]
            if (exactSocialReason != null) {
                results.add(
                    SmartDetectedApp(
                        packageName = pkg,
                        appName = label,
                        category = RiskCategory.SOCIAL_AND_IM,
                        reason = exactSocialReason,
                        currentAction = currentAction,
                        recommendedAction = ActionType.SMART
                    )
                )
                continue
            }

            // Match 3: Inspect APK Manifest components for domestic commercial/tracking SDKs
            val detectedSdks = inspectManifestForDomesticSdks(pm, pkg)
            if (detectedSdks.isNotEmpty()) {
                results.add(
                    SmartDetectedApp(
                        packageName = pkg,
                        appName = label,
                        category = RiskCategory.DOMESTIC_SDK_DETECTED,
                        reason = "检出内嵌：${detectedSdks.take(2).joinToString("、")}",
                        currentAction = currentAction,
                        recommendedAction = ActionType.BLOCK
                    )
                )
                continue
            }

            // Match 4: CN package namespace rules (Clash/Sing-box CN app rules style)
            if (domesticNamespacePrefixes.any { pkg.startsWith(it, ignoreCase = true) }) {
                results.add(
                    SmartDetectedApp(
                        packageName = pkg,
                        appName = label,
                        category = RiskCategory.DOMESTIC_SDK_DETECTED,
                        reason = "匹配国内应用包名分流规则集 ($pkg)",
                        currentAction = currentAction,
                        recommendedAction = ActionType.SMART
                    )
                )
            }
        }

        results.sortedWith(
            compareBy<SmartDetectedApp> { it.category.ordinal }
                .thenBy { it.appName.lowercase() }
        )
    }

    private fun inspectManifestForDomesticSdks(pm: PackageManager, pkg: String): List<String> {
        return try {
            @Suppress("DEPRECATION")
            val flags = PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_PROVIDERS or
                PackageManager.GET_ACTIVITIES
            @Suppress("DEPRECATION")
            val pkgInfo: PackageInfo = pm.getPackageInfo(pkg, flags)

            val componentNames = buildList {
                pkgInfo.services?.forEach { add(it.name) }
                pkgInfo.receivers?.forEach { add(it.name) }
                pkgInfo.providers?.forEach { add(it.name) }
                pkgInfo.activities?.forEach { add(it.name) }
            }

            val matchedLabels = mutableSetOf<String>()
            for (compName in componentNames) {
                for ((prefix, sdkLabel) in domesticSdkSignatures) {
                    if (compName.startsWith(prefix, ignoreCase = true) || compName.contains(prefix, ignoreCase = true)) {
                        matchedLabels.add(sdkLabel)
                    }
                }
                if (matchedLabels.size >= 3) break
            }
            matchedLabels.toList()
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
