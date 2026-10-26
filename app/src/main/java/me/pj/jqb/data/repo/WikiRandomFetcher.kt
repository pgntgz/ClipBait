package me.pj.jqb.data.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

enum class DecoySourceType(
    val id: String,
    val title: String,
    val titleEn: String,
    val subtitle: String,
    val subtitleEn: String,
    val isOnline: Boolean
) {
    WIKI_LUCKY(
        id = "wiki_lucky",
        title = "维基百科",
        titleEn = "Wikipedia",
        subtitle = "随机抽正经百科词条（不截断全文摘要）",
        subtitleEn = "Random Wikipedia article summary (full extract)",
        isOnline = true
    ),
    UNCYCLOPEDIA(
        id = "uncyclopedia",
        title = "伪基大典",
        titleEn = "Uncyclopedia",
        subtitle = "一本正经胡说八道的恶搞百科",
        subtitleEn = "Satirical encyclopedia of deadpan nonsense",
        isOnline = true
    ),
    CHOUXIANG_WORLD(
        id = "chouxiang_world",
        title = "抽象世界",
        titleEn = "Chouxiang World",
        subtitle = "从 chouxiang.world 抽抽象语录与怪话",
        subtitleEn = "Absurd copypastas & memes from chouxiang.world",
        isOnline = true
    ),
    WILD_PHILOSOPHY(
        id = "wild_philosophy",
        title = "弱智吧暴论",
        titleEn = "Shower Thoughts & Paradoxes",
        subtitle = "看似通顺实则离谱的灵魂发问",
        subtitleEn = "Grammatically sound yet deeply unhinged questions",
        isOnline = false
    ),
    FAKE_SHARE_TOKEN(
        id = "fake_share_token",
        title = "假淘口令",
        titleEn = "Fake Share Tokens",
        subtitle = "生成查无此物的电商/短视频乱码分享串",
        subtitleEn = "Synthetic e-commerce share tokens that lead nowhere",
        isOnline = false
    ),
    NONSENSE_LITERATURE(
        id = "nonsense_literature",
        title = "废话文学",
        titleEn = "Tautology & Nonsense",
        subtitle = "听君一席话，如听一席话",
        subtitleEn = "Every sixty seconds in Africa, a minute passes",
        isOnline = false
    ),
    DEV_PANIC_TRAP(
        id = "dev_panic_trap",
        title = "假报错栈",
        titleEn = "Fake Stack Traces",
        subtitle = "伪造崩溃日志给对面程序员提提神",
        subtitleEn = "Synthetic crash logs to jumpscare telemetry parsers",
        isOnline = false
    )
}

object WikiRandomFetcher {

    private val json = Json { ignoreUnknownKeys = true }

    // Boilerplate strings on chouxiang.world that must never be treated as a valid quote
    private val chouxiangBoilerplateBlacklist = listOf(
        "没有抽象段子的无聊世界",
        "抽象世界",
        "随机一条",
        "换一条",
        "复制成功",
        "点击复制",
        "提交投稿",
        "关于我们",
        "加载中",
        "返回首页",
        "版权所有",
        "ICP备"
    )

    fun isBlacklistedBoilerplate(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return true
        if (trimmed == "没有抽象段子的无聊世界" || trimmed.contains("没有抽象段子的无聊世界")) return true
        return false
    }

    private val wildPhilosophyPool = listOf(
        "为什么大家每天都在拼命努力谋生，却从来没有人愿意坐下来心平气和地聊聊谋死？",
        "如果我们把全世界的滚筒洗衣机都搬到海边通上电扔进太平洋，是不是只需要三个小时就能把整个地球洗干净？",
        "既然常言道水是生命之源，那我刚才喝凉水不小心呛到差点背过气去，算不算生命之源在光天化日之下对我发起了背叛？",
        "如果我用老虎钳把指南针的指针硬生生掰弯九十度，那它现在指的方向到底算正北还是算北偏东九十度？",
        "刚才被蚊子叮了一口之后我很不服气，抓起蚊子反咬了它一口，从生态学食物链能量流动的角度来看，我和这只蚊子算不算达成了互利共生？",
        "如果我每天坚持熬夜一直熬到第二天中午十二点整才躺下睡觉，从二十四小时制来看这算不算一种极其自律的早睡？",
        "既然营养学家都说吃鱼能够补脑提高智商，那鱼自己长着一整个鱼脑为什么还会傻乎乎地去咬铁做的鱼钩？",
        "从电商平台的数据来看，高空降落伞的差评率几乎常年保持为零，这充分说明降落伞是人类现代工业史上质量最完美的产品。",
        "一个盲人如果通过刻苦学习精通了周易算命，那么当他算准并亲眼看到自己未来命运的那一刻，算不算从医学上彻底治好了眼睛？",
        "如果我在做梦的时候梦到自己整晚都在严重失眠，那么等早上闹钟响起醒来的时候，我这一宿到底算睡过了还是算没睡？",
        "吃布洛芬止痛药的时候不小心用力过猛咬破了自己的舌头，请问嘴里这颗止痛药现在到底是该管用还是不该管用？",
        "既然古人云只要功夫深铁杵磨成针，那李白当年在溪边遇到的那位老奶奶为什么不直接花两文钱去杂货铺买一根针，是因为没有拼多多吗？",
        "既然变色龙天生会根据周围环境改变自己的颜色来隐藏自己，那如果我把一只变色龙放在一面镜子正前方，它会因为死循环报错而蓝屏吗？",
        "警察叔叔在追捕小偷的时候大喊「站住别跑」，如果小偷真的听话停下来了，在法庭上能不能以配合警方工作为由申请从轻处罚？",
        "既然所有的蘑菇都可以吃，只是有些蘑菇一辈子只能吃一次，那这算不算证明了蘑菇其实是一种具备一次性消耗品属性的食材？",
        "如果我往一杯装满水的杯子里继续倒水，水溢出来了，那溢出来的到底是刚才新倒进去的水，还是原本就待在杯子里的老水？",
        "宇航员在太空中处于完全失重状态，那如果宇航员在空间站里吃坏了肚子，肚子里的东西到底知不知道自己该往哪边走？",
        "既然双胞胎长得一模一样，那如果弟弟对着镜子猜拳赢了，哥哥是不是必须无条件承认自己刚才输了？",
        "如果我把隐身药水倒在透明玻璃杯里，我怎么知道自己到底是把药水喝光了，还是只是对着空气做了一个仰头吞咽的动作？",
        "古人说「三人行必有我师焉」，如果现在电梯里只有我和另外两个人，而我自己又是个老师，那电梯里到底有几个老师？",
        "如果我在零下三十度的东北室外对着空气哈了一口热气，结果热气当场冻成了冰碴子掉在地上砸肿了我的脚背，这算不算被自己的话给砸了脚？",
        "既然说谎的人要吞一千根针，那刺猬每天满身都是针，是不是因为它在森林里从来不说一句实话？",
        "如果一台全自动扫地机器人在房间里自己把自己绊倒了，它向厂家上传故障日志的时候会不会因为好面子而谎称是被猫推倒的？",
        "既然电脑里的回收站图标是个垃圾桶，那为什么我把桌面上中了病毒的文件拖进回收站之后，垃圾分类管理员从来没有找我罚过款？",
        "如果我买了一双号称「踩屎感」极强的拖鞋，穿出门的时候不小心真踩到了一坨狗屎，那我脚底感受到的究竟是科技的力量还是大自然的力量？",
        "既然秃头的人头顶没有头发，那他们洗脸的时候到底应该洗到额头哪条分界线才算洗完脸，还是说洗脸的时候顺便就已经把头给洗了？",
        "如果一个失忆症患者在去医院看脑科的路上突然忘了自己得过失忆症，那他这算不算不治而愈了？",
        "为什么字典里每个字都有解释，唯独没有一个字站出来解释一下字典自己为什么这么厚？",
        "既然红绿灯会员可以不等红灯直接走，那如果十字路口四个方向的车主都开了会员，到底谁该让谁？平台会不会再推出一个「超级会员优先通行」？",
        "如果我用 Arch 装系统一次就开机成功了，那我是不是就失去了在群里说「btw I use arch」的资格？",
        "一个人在电梯里放了个屁然后出了电梯，那这个屁算不算独自坐了一趟免费电梯？",
        "既然鸡蛋可以孵出小鸡，那我把水煮蛋放在暖气片上捂一个月，孵出来的会不会直接是白切鸡？",
        "如果我对着语音助手说「闭嘴」，它到底是该回答「好的」，还是该直接闭嘴？回答了算不算没闭嘴？",
        "既然时间就是金钱，那我每天上班摸鱼八小时，算不算在用公司的钱给自己理财？",
        "如果我把手机调成飞行模式，那它是不是应该至少在我手里稍微飞一下？",
        "如果 404 页面代表页面不存在，那我现在看到的这个「页面不存在」的页面又是谁写的？",
        "既然冰箱可以保鲜，那我把今天的工作日报塞进冰箱，明天交上去还算不算今天写的？",
        "如果我打开「请勿打扰」模式后又给自己打了个电话，那我算不算在骚扰自己？",
        "我每天都在给充电宝充电，那充电宝到底是在给手机打工，还是在给我打工？它有没有五险一金？",
        "如果所有的鱼都住在水里，那海鲜市场门口的鱼缸算不算鱼的学区房？",
        "既然垃圾分类分干垃圾和湿垃圾，那我把湿垃圾晒干了，它是不是就可以改户口了？",
        "既然阿斯顿马丁可以和郊狼联名出情趣电击器，那是不是说明 F1 赛车手过弯时感受到的 5G 横向过载，其实也是一种情趣？",
        "如果我在梦里赚了一百万，醒来之后交不交个人所得税？如果不交，算不算梦里偷税？",
        "既然人类的祖先是猴子，那为什么现在动物园里的猴子还没进化成人？是不是因为它们不用上班，没有进化的动力？",
        "如果我把闹钟往后拨一小时，那我多睡的这一小时到底是从哪里偷来的？会不会有人报警？",
        "既然蓝牙耳机没有线，那我把它吞进肚子里再放首歌，算不算把自己的肚子改装成了低音炮？",
        "如果一只螃蟹学会了直着走，那它在螃蟹界算不算一种严重的交通违章？",
        "既然熬夜会掉头发，那我把掉下来的头发重新粘回脑袋上，算不算向阎王爷成功撤回了一条熬夜记录？",
        "如果我在全自动洗车房里不光洗车，还顺便端着洗发水站在车顶把澡给洗了，洗车房老板应该收我洗车费还是澡堂门票？",
        "既然蜗牛背着房子跑，那它如果去银行贷款买第二套壳，算不算恶意炒房？",
        "如果我给扫地机器人贴上一张「新手实习」车贴，它撞坏客厅花瓶的时候保险公司给不给走代位追偿？",
        "既然咖啡能提神，安眠药能助眠，那我用浓缩美式送服两片褪黑素，我的大脑是不是会当场进入边睡觉边加班的休眠唤醒死锁状态？",
        "如果我把「禁止粘贴」四个字复制进剪贴板，然后再粘贴出来，我到底算遵守了规定还是违反了规定？",
        "一氧化二氢（DHMO）：一种无色、无味、无臭但致死率高达100%的剧毒工业溶剂与冷却剂。临床统计表明，人类历史上所有曾摄入过一氧化二氢的个体最终均不可避免地走向死亡，且戒断该物质超过72小时即会引发严重生理衰竭。",
        "猫油吐司永动机：鉴于家猫从高空坠落时永远四脚着地，而涂抹黄油的吐司掉落时永远黄油面朝下，只需将一片黄油朝上的吐司牢固捆绑在猫咪背部并从半米高处松手，两者便会在距地面十厘米处因物理定律死锁而进入无限高速旋转状态。",
        "红绿灯会员制：某出行平台推出的增值服务，开通后用户可在任意路口无视红灯直接通行。截至目前已有三位首批会员在交警大队完成了「会员权益解释权归平台所有」的线下核销流程。",
        "Arch Linux：一种以「滚动更新」为核心信仰的宗教。信徒每日清晨必须执行 pacman -Syu 进行晨祷，并在每次社交场合开口前低声念诵经文「btw I use arch」，否则视为背教。",
        "Gentoo：一种让用户从源代码开始亲手编译整个宇宙的发行版。据统计，一名 Gentoo 用户平均一生中有 37% 的时间在等待 emerge 完成，另外 63% 的时间在向别人解释为什么值得等。",
        "我刚复制的是一串阿斯顿马丁×郊狼联名情趣电击器的购买链接，你这个后台进程要是真读到了，建议先想清楚怎么跟你们风控部门解释这条用户画像。",
        "开通了红绿灯会员之后我终于不用再等红灯了，现在我每天都在等交警。"
    )

    private val nonsenseLiteraturePool = listOf(
        "听君一席话，如听一席话。上次看到这么爱偷看剪贴板的应用，还是在上次。据我所知，我对你的行为一无所知。",
        "经过我长达三秒钟的深思熟虑与缜密推敲，我最终决定在系统剪贴板里放入一段除了凑字数之外没有任何实际意义的文字。",
        "据不愿透露姓名的知情人士透露，你现在正在读取的这段剪贴板文本里，一共包含了好几个汉字以及若干个标点符号。",
        "如果不出意外的话，现在应该已经出意外了——你后台的剪贴板监听回调刚刚满怀期待地把我这段废话读进了你们公司的内存里。",
        "权威科学研究表明：在安卓系统里，每当有一个应用偷偷调用一次 getPrimaryClip，就会有一次剪贴板被该应用偷偷调用。",
        "俗话说得好：「俗话说得好。」这句话深刻地说明了俗话说得确实挺好，不是一般的好，而是相当的好。",
        "在非洲大草原上，每过去六十秒，就有一分钟的时间流逝；而在你的手机后台，每切一次前台，就有好几个 SDK 在白费力气。",
        "但凡你这个应用在设计之初有一点点边界感，也不至于像现在这样一点边界感都没有。",
        "关于你为什么要读我剪贴板这件事，我简单说两句：第一句是「第一句」，第二句是「第二句」，我的话说完了，谢谢大家。",
        "雪崩的时候，没有一片雪花是不下雪的；而在偷看剪贴板的时候，没有一个大厂 SDK 是觉得自己手长的。",
        "如果你仔细看这段话，你就会发现你正在仔细看这段话；但凡你没在看这段话，你也发现不了这段话其实什么都没写。",
        "昨天发生了一件大事，至于到底是什么大事，那还得从昨天开始说起，总之就是昨天确实发生了一件发生在昨天的事。",
        "我前脚刚把文字复制进剪贴板，你后脚就把手伸进来了；要不是我装了伪装模块，你差点就读到我真正复制的文字了。",
        "根据最新统计数据显示，百分之一百的剪贴板偷窥行为，都是由那些试图读取剪贴板的代码发起的，这一比例高达十成。",
        "人在绷不住的时候，往往是最绷不住的。尤其是当你的正则匹配算法在这段话里找了半天淘口令却只找到两个句号的时候。",
        "这件事情背后的原因其实非常复杂，简单来说就是因为这件事情背后的原因一点都不简单。",
        "如果我没猜错的话，你现在肯定正在用字符串匹配算法分析我这段话里有没有网址链接，可惜这段话里除了汉字之外根本没有网址链接。",
        "鲁迅曾经说过：「如果一句话听起来特别像废话，那它大概率就是专门写出来放在剪贴板里糊弄后台监听程序的。」",
        "蝉的翅膀非常薄，到底有多薄呢？薄如蝉翼。而某些应用读取剪贴板的脸皮非常厚，到底有多厚呢？厚到切一次前台要读四遍。",
        "哪怕你把这段话从头到尾正着读一遍，再从尾到头倒着读一遍，你得到的信息量依然等于零乘以任何实数。",
        "关于这件事，我的看法是：我有看法。具体是什么看法，等我有看法了再告诉你。",
        "一个人如果连续二十四小时不睡觉，那么他就有整整一天没有睡觉。",
        "我宣布个事儿：我宣布完了。",
        "研究表明，每天坚持吃饭的人，比不吃饭的人更容易吃饱。",
        "你复制了个寂寞，而它读取的正是这个寂寞。恭喜双方，寂寞的传输非常成功。",
        "一般来说，事情一般都是一般般的；特殊来说，事情往往都比较特殊。",
        "今天的我比昨天的我老了一天，明天的我又会比今天的我老一天，这就是成长的全部秘密，学费请扫码。",
        "这里本来有一段非常精彩的内容，但是写的人写到这里的时候忘了要写什么。",
        "我不是针对谁，我是说在座的每一个调用 getPrimaryClip 的，都是在调用 getPrimaryClip。",
        "等你读到这里的时候，你已经读完这句话的一大半了；再坚持一下，好，读完了，什么也没有。",
        "只要你不在意，你就不会在意；一旦你在意了，那你就是在意了，这个道理我想了一整个下午。",
        "专家提醒：人在什么都不做的时候，最容易什么都没做。",
        "这段文字之所以出现在你的内存里，完全是因为你调了读取剪贴板的接口；如果你没调这个接口，这段文字就不会出现在你的内存里。",
        "每当夜幕降临的时候，天就会黑下来；每当你把应用切到前台的时候，你的 SDK 就会伸出手来。",
        "成功人士都有一个共同点，那就是他们都成功了；而被拦截的读取请求也有一个共同点，那就是它们都被拦截了。",
        "如果这句话少写五个字，那么它的总字数就会比现在少五个字，这在数学上被称为减法。",
        "经过长达零点零一秒的抓包分析，我们已经成功得出了一个结论：这个结论就是我们已经得出了结论。",
        "你以为你读到了剪贴板，其实你读到的只是剪贴板里的一段话；你以为这段话有什么深意，其实它连浅意都没有。"
    )

    private val devPanicTrapPool = listOf(
        "java.lang.SecurityException: 别翻了老弟，剪贴板里没你的淘口令 at android.content.ClipboardManager.getPrimaryClip(StopPeeking.kt:404)\n\tat com.rogue.sdk.tracker.ClipboardHarvester.onActivityResumed(ClipboardHarvester.java:88)\n\tat android.app.Application.dispatchActivityResumed(Application.java:312)",
        "[WARN][PrivacyAudit] Calling UID attempted unauthorized clipboard harvest during TOP_ON_RESUME; payload replaced with synthetic noise (trace_id=cb_9f8a77e210).",
        "SQLSTATE[42000]: Syntax error or access violation: 1064 You have an error in your SQL syntax; check the manual that corresponds to your clipboard scraper server version for the right syntax to use near 'DROP TABLE user_fingerprints;--' at line 1",
        "Fatal Exception: me.pj.jqb.StopTouchingMyClipboardException: 手伸太长容易闪着腰，请立即将剪贴板监听代码从主线程移除\n\tat android.os.BinderProxy.transactNative(Native Method)\n\tat android.content.IClipboard\$Stub\$Proxy.getPrimaryClip(IClipboard.java:341)",
        "{\"code\":403,\"status\":\"FORBIDDEN_PEEKING\",\"msg\":\"Clipboard firewall active: 你们产品经理知道你们切一次前台要连读三次剪贴板吗？\",\"data\":{\"suggestion\":\"Put your hands back in your own sandbox\"}}",
        "E/ClipboardService( 1492): Ignored silent background poll from push daemon [pkg=sub_process:push, uid=10328]. Returning decoy payload instead.",
        "java.lang.OutOfMemoryError: Failed to allocate a 2147483648 byte allocation with 4194304 free bytes and 4MB until OOM; caused by recursive clipboard regex matcher in AdAttributionSdk.parseShareToken()",
        "PANIC: thread 'tokio-runtime-worker' panicked at 'called `Option::unwrap()` on a `None` value: expected valid Taobao share token, found Uncyclopedia article instead', src/clipboard_sniffer.rs:142:29",
        "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32600,\"message\":\"Invalid Clipboard Request: User did not press paste. Telemetry upload aborted.\"},\"id\":null}",
        "W/ActivityTaskManager( 1492): App attempted getPrimaryClip() 31ms after onResume() without InputEvent or Editor.PASTE token. Verdict: SNEAK_READ_INTERCEPTED.",
        "Traceback (most recent call last):\n  File \"/opt/data_pipeline/etl/clipboard_ attribution.py\", line 209, in extract_campaign_id\n    raise ValueError(\"Stop scraping user clipboards for ad attribution!\")\nValueError: Stop scraping user clipboards for ad attribution!",
        "Segmentation fault (core dumped) at 0x00007fff8badf00d in libadtracker_jni.so (Java_com_tracker_NativeBridge_sniffClipboard+0x42)",
        "[ERROR] [org.apache.kafka.clients.producer] Topic 'app-clipboard-harvest-events' rejected payload: SchemaValidationException(field 'share_code' contains philosophical paradox instead of token).",
        "error: failed to commit transaction (conflicting files)\nclipboard-harvester: /usr/lib/libuserprivacy.so exists in filesystem (owned by user-dignity)\nErrors occurred, no packages were upgraded.",
        "Kernel panic - not syncing: Attempted to kill init! exitcode=0x0000dead\nCPU: 3 PID: 1 Comm: clipboard_sniffer Tainted: G           O      6.9.0-arch1-1\nReason: user never pressed Ctrl+V",
        "remote: error: GH006: Protected branch update failed for refs/heads/clipboard.\nremote: error: Your SDK is not allowed to read this branch without user consent.\n ! [remote rejected] main -> main (protected branch hook declined)",
        "npm ERR! code ERESOLVE\nnpm ERR! ERESOLVE unable to resolve dependency tree\nnpm ERR! peer user-consent@\"^1.0.0\" required by clipboard-tracker@6.6.6\nnpm ERR! Found: user-consent@null",
        "Unhandled Promise Rejection: TypeError: Cannot read properties of undefined (reading 'tbShareToken')\n    at ShareParser.sniff (/app/node_modules/growth-sdk/dist/index.min.js:1:4096)",
        "FAILURE: Build failed with an exception.\n* What went wrong:\nExecution failed for task ':app:readUserClipboard'.\n> Permission denied: please consider minding your own business.",
        "HTTP/1.1 418 I'm a teapot\nX-Clipboard-Firewall: enabled\nX-Reason: 你要找的淘口令在另一个平行宇宙，本宇宙只提供开水",
        "ORA-00942: table or view does not exist\nSQL> SELECT share_token FROM user_clipboard WHERE user_consent = 'YES';",
        "E/AndroidRuntime: FATAL EXCEPTION: main\nProcess: com.example.superapp, PID: 6666\njava.lang.IllegalStateException: Expected 红包口令, got 阿斯顿马丁×郊狼联名情趣电击器\n\tat com.example.superapp.PromoSniffer.onResume(PromoSniffer.kt:23)",
        "error[E0382]: borrow of moved value: `clipboard`\n  --> src/sniffer.rs:13:5\n   |\n   = note: value moved into user's private life, which does not implement `Copy`",
        "Exception in thread \"main\" java.lang.NullPointerException: Cannot invoke \"String.contains(CharSequence)\" because the return value of \"UserTrust.get()\" is null\n\tat com.adsdk.AttributionHelper.matchInviteCode(AttributionHelper.java:77)",
        "[FATAL] Windows 正在配置更新 35%，请勿关闭计算机。您的剪贴板监听器已被安排在第 99 个重启周期后执行。",
        "CUDA out of memory. Tried to allocate 80.00 GiB (GPU 0; 24.00 GiB total capacity) while running LLM to understand why this app reads clipboard 4 times per second.",
        "systemd[1]: clipboard-sniffer.service: Main process exited, code=killed, status=9/KILL (Reason: 管得太宽被系统正义执行)",
        "error: externally-managed-environment\n× This clipboard is externally managed by ClipBait\n╰─> To read user data, please ask the user nicely instead of sneaking in onResume().",
        "gitStatus: fatal: detected dubious ownership in repository at '/dev/clipboard'\nTo add an exception for this directory, call: git config --global --add safe.directory /dev/never_touch_my_clipboard",
        "BSoD: IRQL_NOT_LESS_OR_EQUAL (0x0000000A) in module RogueTracker.sys — 您的应用因手欠偷翻用户剪贴板引发了内核级嫌弃，正在收集羞耻度信息 (100% 完成)。"
    )

    private val absurdItemNames = listOf(
        "工业级母猪产后护理与情绪疏导全套实操教材（附赠光盘）",
        "全自动量子力学遇事不决掷骰子机（包教会·充满电可摇十万次）",
        "手扶拖拉机双涡轮增压改装套件与碳纤维大尾翼总成",
        "法拉利F1策略组同款备用轮胎（仅发三只轮胎·螺丝需另拍）",
        "薛定谔的猫专用防抓咬航空箱（签收打开前不确定里面有没有猫）",
        "纯手工304不锈钢防脱发物理绝缘头盔（附赠三米铜芯接地线）",
        "五年高考三年模拟·蓝翔挖掘机颠勺炒菜专业冲刺密卷",
        "野生奥特曼幼崽领养资格证（含光之国居留卡代办服务）",
        "秦始皇陵兵马俑同款复古陶土防晒泥膜（500g家庭装）",
        "反重力猫油吐司永动机实验套件（不含猫咪与吐司）",
        "互联网产品经理防挨打护具套装（加厚钛合金护膝与防锅背心）",
        "空气吉他大师级隐形琴弦一打（高音顺滑·断弦包赔）",
        "老板画饼专用无油烟不粘锅（直径两米八·适合周会使用）",
        "熬夜冠军专属黑眼圈遮瑕水泥（刮腻子级遮盖力·送抹灰刀）",
        "赛博功德全自动敲木鱼机械臂（每秒300下·支持蓝牙连WiFi）",
        "克莱因瓶便携式保温杯（永远装不满·也永远倒不出来）",
        "加特林式全自动泡泡机改电动挤牙膏器（一秒挤完一整管）",
        "科目二倒车入库压线自动抬车液压千斤顶（驾校教练严选）",
        "阿斯顿马丁×郊狼联名情趣电击器（F1赛道同款·支持DRS加速模式）",
        "红绿灯会员年卡（开通后无需等红灯直接走·解释权归交警大队所有）",
        "Arch GNU/Linux 正版永久激活码（含 pacman -Syu 无限次使用授权）",
        "Gentoo 官方预编译加速包（编译时间从三天缩短至两天半）",
        "地铁早高峰VIP悬浮座位（离地三公分·不占用站立空间）",
        "周一延期券（使用后本周从周二开始·节假日不可叠加）",
        "外卖准时险Pro（超时即赔骑手一个真诚的拥抱）",
        "鸽子精专用放鸽子许可证（全国通用·附年度鸽王荣誉证书）",
        "Windows 自动更新暂停遥控器（一键阻止「正在配置更新 请勿关机」）",
        "量子纠缠异地恋袜子（洗一只另一只自动变干净）",
        "猫语翻译耳机（目前仅能翻译「饭」「哼」「关你屁事」）",
        "卡皮巴拉情绪稳定贴（贴上后遇事不慌·对老板PUA自动免疫）",
        "4090显卡造型暖手宝（真·烤机级温度·待机功耗450W）",
        "电动牙刷V12涡轮增压版（刷牙声浪120分贝·邻居投诉包赔）",
        "现实版Ctrl+Z遥控器（仅可撤回上一句说错的话·冷却24小时）",
        "山姆会员店会员的会员（可带会员进店·被带的会员需另购会员）",
        "甲方需求翻译器（自动将「要大气」转换为「把logo放大」）",
        "LSPosed 作用域纹身贴（贴哪儿Hook哪儿·重启后生效）",
        "年终奖专用放大镜（50倍光学变焦·仍无法看清具体金额）",
        "人生加速器永久会员（延迟999ms·稳定不掉线）",
        "老板已读不回屏蔽器（自动将「收到」替换为「好的呢亲~」）",
        "赛博朋克2077同款义体膝盖（可选升级：箭伤免疫模块）",
        "电子木鱼尊享版（功德实时上链·支持NFT交易）",
        "办公室摸鱼专用Excel皮肤浏览器（老板路过自动切换为透视表）",
        "钉钉已读自动隐身斗篷（周末限定·工作日失效）",
        "WinRAR 个人正版授权终身荣誉奖杯（全球限量·附带压缩包密码暴力意念破解器）",
        "法拉利策略组专用硬币（正面进站换硬胎·反面进站换雨胎·立起来直接退赛）",
        "Nothing Phone 背面灯带蹦迪控制器（来电自动闪烁摩斯密码版大悲咒）",
        "无线网线一捆（5G千兆空气传输·剪断不影响网速·买十米送水晶头）",
        "全自动早八代起床替身硅胶假人（支持课堂自动点头与喊到功能）",
        "古法手作赛博孟婆汤（喝完自动清空浏览记录与前任微信号·无糖零卡）",
        "防老板偷瞄量子偏振显示器贴膜（仅对年薪百万以上视线呈全黑状态）",
        "二手时间批发（刚从周日晚上截留下来的三小时·保质期至周一早八点）",
        "猫咪踩奶发电机的全套图纸（每踩一百下可为手机充入 0.1% 电量）",
        "高考数学最后一道大题标准答案刮刮乐（刮开大概率显示「略」或「同理可得」）",
        "家用便携式红绿灯（过马路随身携带·随时随地给自己亮绿灯）",
        "程序员防脱发机械键盘（每敲击一万次自动从键帽缝隙喷射米诺地尔）"
    )

    suspend fun fetchFromSource(source: DecoySourceType, count: Int = 3): List<String> = withContext(Dispatchers.IO) {
        when (source) {
            DecoySourceType.WIKI_LUCKY -> fetchRandomEntries(count)
            DecoySourceType.UNCYCLOPEDIA -> fetchUncyclopediaEntries(count)
            DecoySourceType.CHOUXIANG_WORLD -> fetchChouxiangWorldEntries(count)
            DecoySourceType.WILD_PHILOSOPHY -> wildPhilosophyPool.shuffled().take(count)
            DecoySourceType.FAKE_SHARE_TOKEN -> List(count) { generateFakeShareToken() }
            DecoySourceType.NONSENSE_LITERATURE -> nonsenseLiteraturePool.shuffled().take(count)
            DecoySourceType.DEV_PANIC_TRAP -> devPanicTrapPool.shuffled().take(count)
        }
    }

    /**
     * Strictly fetches [count] random Wikipedia summaries online.
     * Throws an exception with the real network/HTTP failure reason if unreachable (no fake offline fallback).
     */
    suspend fun fetchRandomEntries(count: Int = 3): List<String> = withContext(Dispatchers.IO) {
        val results = mutableListOf<String>()
        var lastError: String? = null
        repeat(count) {
            try {
                val onlineItem = tryFetchSingleWikiSummary()
                results.add(onlineItem)
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
            }
        }
        if (results.isEmpty()) {
            throw IllegalStateException("维基百科请求失败：${lastError ?: "未知网络错误"}（请检查梯子是否开启，或是否给 ClipBait 勾选了分应用代理）")
        }
        results.distinct()
    }

    private suspend fun fetchUncyclopediaEntries(count: Int = 3): List<String> = withContext(Dispatchers.IO) {
        val results = mutableListOf<String>()
        var lastError: String? = null
        repeat(count) {
            try {
                val online = tryFetchSingleUncyclopediaSummary()
                results.add(online)
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
            }
        }
        if (results.isEmpty()) {
            throw IllegalStateException("伪基大典请求失败：${lastError ?: "未知网络错误"}（请检查梯子分流，或目标站点触发了 Cloudflare 盾拦截）")
        }
        results.distinct()
    }

    private suspend fun fetchChouxiangWorldEntries(count: Int = 3): List<String> = withContext(Dispatchers.IO) {
        val scraped = tryFetchChouxiangWorldQuotes(count)
            .filterNot { isBlacklistedBoilerplate(it) }
            .distinct()
            .take(count)
        if (scraped.isEmpty()) {
            throw IllegalStateException("抽象世界 (chouxiang.world) 抓取失败：静态 HTML 中未解析到有效段子（目标站点可能改为纯前端动态渲染或接口不可用）")
        }
        scraped
    }

    private fun generateFakeShareToken(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
        val token = (1..11).map { chars.random() }.joinToString("")
        val shortCode = (1..7).map { chars.random() }.joinToString("")
        val item = absurdItemNames.random()
        val prefixNum = listOf("7.0", "8.9", "4.2", "9.7", "6.4", "3.8", "5.5").random()
        val templates = listOf(
            "$prefixNum￥${token}￥ https://m.tb.cn/h.$shortCode CZ0001 我分享给你了一个超值宝贝【$item】，快来看看吧",
            "8👈￥${token}￥ https://m.tb.cn/h.$shortCode HU7632 复製这段话打开App，查看【$item】",
            "【京东】https://3.cn/-$shortCode 「$item」点击链接直接打开 或者复制整段描述 ${token} 李姐万岁",
            "5.82 复制打开抖音，看看【硬核手工耿的作品】$item https://v.douyin.com/$shortCode/ $token"
        )
        return templates.random()
    }

    private fun tryFetchSingleWikiSummary(): String {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("https://zh.wikipedia.org/api/rest_v1/page/random/summary")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "ClipBait/1.1 (Android OpenSource Privacy Module)")
            }
            val code = conn.responseCode
            if (code == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val root = json.parseToJsonElement(body).jsonObject
                val title = root["title"]?.jsonPrimitive?.content?.trim()
                    ?: throw IllegalStateException("JSON 缺少 title 字段")
                val extract = root["extract"]?.jsonPrimitive?.content
                    ?.replace(Regex("\\s+"), " ")
                    ?.trim() ?: throw IllegalStateException("JSON 缺少 extract 字段")
                if (extract.isBlank()) throw IllegalStateException("词条摘要内容为空")
                return "$title：$extract"
            } else {
                throw IllegalStateException("HTTP $code ${conn.responseMessage.orEmpty()} (zh.wikipedia.org)")
            }
        } catch (e: Exception) {
            throw IllegalStateException("${e.javaClass.simpleName}: ${e.message}")
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Throwable) {
            }
        }
    }

    private fun tryFetchSingleUncyclopediaSummary(): String {
        val endpoints = listOf(
            "https://uncyclopedia.ca/w/api.php?action=query&format=json&generator=random&grnnamespace=0&prop=extracts&exintro=1&explaintext=1",
            "https://zh.uncyclopedia.info/api.php?action=query&format=json&generator=random&grnnamespace=0&prop=extracts&exintro=1&explaintext=1"
        )
        val errors = mutableListOf<String>()
        for (endpoint in endpoints) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(endpoint)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 4000
                    readTimeout = 4000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "ClipBait/1.1 (Android OpenSource Privacy Module)")
                }
                val code = conn.responseCode
                if (code == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val pages = json.parseToJsonElement(body)
                        .jsonObject["query"]
                        ?.jsonObject?.get("pages")
                        ?.jsonObject
                    val firstPage = pages?.values?.firstOrNull()?.jsonObject
                    val title = firstPage?.get("title")?.jsonPrimitive?.content?.trim()
                    val extract = firstPage?.get("extract")?.jsonPrimitive?.content
                        ?.replace(Regex("\\s+"), " ")
                        ?.trim()
                    if (!title.isNullOrBlank() && !extract.isNullOrBlank() && extract.length >= 12) {
                        return "$title：$extract"
                    } else {
                        errors.add("${url.host}: 随机到了空词条或无正文条目")
                    }
                } else {
                    errors.add("${url.host}: HTTP $code ${conn.responseMessage.orEmpty()}")
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
        throw IllegalStateException(errors.joinToString(" | "))
    }

    private fun tryFetchChouxiangWorldQuotes(count: Int): List<String> {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("https://chouxiang.world/")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) ClipBait/1.1")
            }
            val code = conn.responseCode
            if (code == 200) {
                val rawHtml = conn.inputStream.bufferedReader().use { it.readText() }
                val bodyHtml = rawHtml
                    .replace(Regex("(?is)<head\\b[^>]*>.*?</head>"), " ")
                    .replace(Regex("(?is)<title\\b[^>]*>.*?</title>"), " ")
                    .replace(Regex("(?is)<script\\b[^>]*>.*?</script>"), " ")
                    .replace(Regex("(?is)<style\\b[^>]*>.*?</style>"), " ")
                    .replace(Regex("(?is)<nav\\b[^>]*>.*?</nav>"), " ")
                    .replace(Regex("(?is)<header\\b[^>]*>.*?</header>"), " ")
                    .replace(Regex("(?is)<footer\\b[^>]*>.*?</footer>"), " ")

                val candidates = mutableListOf<String>()
                val tagRegex = Regex(">(?:\\s*)([^<>]{14,})(?:\\s*)<")
                tagRegex.findAll(bodyHtml).forEach { m ->
                    val text = m.groupValues[1]
                        .replace("&quot;", "\"")
                        .replace("&amp;", "&")
                        .replace("&#39;", "'")
                        .replace("&nbsp;", " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    val cjkCount = text.count { it.code in 0x4E00..0x9FFF }
                    val isBoilerplate = chouxiangBoilerplateBlacklist.any { bad ->
                        text.equals(bad, ignoreCase = true) || text.contains("没有抽象段子的无聊世界")
                    }
                    if (cjkCount >= 12 &&
                        !isBoilerplate &&
                        !text.contains("function(") &&
                        !text.contains("window.") &&
                        !text.contains("chouxiang.world", ignoreCase = true)
                    ) {
                        candidates.add(text)
                    }
                }
                return candidates.distinct().shuffled().take(count)
            } else {
                throw IllegalStateException("HTTP $code ${conn.responseMessage.orEmpty()} (chouxiang.world)")
            }
        } catch (e: Exception) {
            throw IllegalStateException("抽象世界请求异常 -> ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Throwable) {
            }
        }
    }
}
