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

    private val offlineWikiReservePool = listOf(
        "拉普拉斯妖：由法国数学家皮埃尔-西蒙·拉普拉斯于1814年提出的科学决定论假想智能体。若该智能体知道宇宙中每个原子确切的位置和动量，便能使用牛顿定律展现宇宙事件的整个过程，过去以及未来都将一目了然。",
        "费米悖论：物理学家恩利克·费米在讨论地外文明时提出的著名科学悖论，阐述了对地外文明存在性的过高估计与缺少任何可观测相关证据之间的明显矛盾。",
        "玛雅历法：前哥伦布时期中美洲玛雅文明使用的历法系统，包含260天的神圣历“卓尔金历”、365天的太阳历“哈布历”，以及用于跨世纪历史纪年的“长纪历”。",
        "超导现象：特定材料在温度降到某一临界值以下时，直流电阻突然降为零并且将体内磁力线完全排斥在外的物理现象，由荷兰物理学家昂内斯于1911年首次在液氦冷却的汞中发现。",
        "庞加莱猜想：法国数学家亨利·庞加莱提出的代数拓扑学著名命题，即任何一个单连通的、闭的三维流形一定同胚于三维球面。该猜想于2003年由俄罗斯数学家格里戈里·佩雷尔曼运用里奇流方法彻底证明。",
        "丝绸之路：起始于古代中国长安与洛阳，经河西走廊、中亚草原与绿洲，连接亚洲、非洲和欧洲的古代陆上商业贸易路线体系，在东西方文明技术、宗教与物种交流中扮演了核心角色。",
        "黄金分割率：定义为 (1+√5)/2（约等于 1.6180339887）的无理数数学比例常数，在古典建筑美学、斐波那契数列极限以及植物叶序的自然排列中广泛存在。",
        "板块构造论：二十世纪六十年代在大陆漂移学说与海底扩张学说基础上发展起来的现代地球科学核心理论，解释了岩石圈水平运动、造山带形成以及环太平洋火山地震带的分布机制。",
        "量子纠缠：量子力学中多粒子系统状态无法分解为各个粒子独立状态张量积的非定域物理现象。即便两个纠缠粒子相隔数光年，对其中一个粒子状态的测量也会瞬间决定另一个粒子的对应状态。",
        "都江堰：战国时期秦昭王末年由蜀郡太守李冰父子主持修建的大型无坝引水水利工程，由鱼嘴分水堤、飞沙堰溢洪道、宝瓶口引水口三大主体工程构成，两千余年来持续灌溉成都平原。",
        "克莱因瓶：拓扑学中一种无定向性的闭合曲面，没有“内部”和“外部”之分。在三维欧几里得空间中，克莱因瓶必须穿过自身才能闭合，而在四维空间中则可以完全不自交地嵌入。",
        "寒武纪大爆发：距今约5.41亿年前的寒武纪早期，地球化石记录中在短短两千万年地质尺度内突然涌现出几乎所有现生动物门类的祖先形态，以中国澄江生物群与加拿大布尔吉斯页岩为代表。",
        "美拉德反应：广泛存在于食品工业中的非酶褐变反应，指还原糖的羰基与氨基酸或蛋白质的氨基在加热条件下经过缩合、重排与聚合生成类黑精与复杂芳香化合物的过程，赋予烤肉、烘焙面包与咖啡独特的焦香风味。",
        "图灵停机问题：阿兰·图灵于1936年证明的计算理论基本定理，指出在通用图灵机模型下，不存在能够判断任意程序在给定输入上会在有限时间内结束运行还是会死循环的通用算法。",
        "安第斯山脉：纵贯南美洲大陆西部的世界最长陆上山脉，北起特立尼达岛，南抵火地岛，全长约8900公里，由纳斯卡板块俯冲至南美板块下方挤压抬升而成。"
    )

    private val uncyclopediaReservePool = listOf(
        "一氧化二氢（DHMO）：一种无色、无味、无臭但致死率高达100%的剧毒工业溶剂与冷却剂。临床统计表明，人类历史上所有曾摄入过一氧化二氢的个体最终均不可避免地走向死亡，且戒断该物质超过72小时即会引发严重生理衰竭。",
        "猫油吐司永动机：经典反重力力学装置。鉴于家猫从高空坠落时永远四脚着地，而涂抹黄油的吐司掉落时永远黄油面朝下，只需将一片黄油朝上的吐司牢固捆绑在猫咪背部并从半米高处松手，两者便会在距地面十厘米处因物理定律死锁而进入无限高速旋转状态。",
        "拖延症相对论：由当代大学生与自由职业者共同验证的时间膨胀效应。当距离死线（Deadline）还剩七天时，时间流速近乎停滞；而当距离死线仅剩两小时时，人类打扫房间死角、给手机贴膜、研究冰箱为什么会亮灯的效率将突破光速限制。",
        "薛定谔的甲方：在尾款正式打入账户之前，甲方的需求文档永远处于「随便弄弄就行，我相信你的审美」与「感觉还是不够大气，要不我们用回第一版吧」的量子相干叠加态，任何试图提前确认需求的观测行为都会导致设计师当场坍缩。",
        "空气吉他弦理论：现代高维音乐物理学基石。该理论认为宇宙的最小基本单元并非夸克或轻子，而是摇滚青年在淋浴间抹完洗发水后凭空拨动出的十一维隐形电吉他高音弦。",
        "报复性熬夜守恒定律：热力学第四定律的互联网推论。打工人白天在工位上被吸干的灵魂自主权，必须且只能通过在凌晨两点半瞪着通红的双眼漫无目的地上下滑动手机屏幕才能勉强维持能量守恒。",
        "键盘侠动力学：当一名网民在评论区敲击键盘的瞬时速度突破每分钟140字时，其体内将自发产生强相互作用力场，使其在接下来五分钟内自动获得宏观经济学、国际地缘政治、临床医学及法学的全科终身荣誉博士学位。",
        "百慕大洗衣机黑洞：家用电器中最神秘的微观虫洞现象。无论投料前如何仔细清点，放进去的明明是四双成对的棉袜，甩干结束后取出的必然是七只花色互不相同的单只袜子以及一张被洗成雪花状的超市小票。",
        "量子波动速读法：只要以每秒二十页以上的频率在面部前方高速翻动书页，印刷油墨中的文字信息就会因为多普勒蓝移效应直接脱离纸面，顺着掀起的凉风穿过额骨直接烙印进海马体，非常适合用来阅读本公司长达八万字的用户隐私协议。",
        "泡面三分钟悖论：狭义相对论在厨房领域的直观体现。当你盯着泡面碗等待三分钟时，这三分钟的长度约等于两节课；但当你决定趁泡面的三分钟去刷两个短视频时，再抬头时泡面已经吸干了最后一滴汤并膨胀成了碗状海绵。",
        "墨菲排队定律：无论你在超市收银台、高速公路收费站还是食堂窗口前经过多么严密的概率测算选择了最短的那一列，你所在的队伍必定会在三十秒内因为前排大爷掏出零钱、系统升级或换纸卷而陷入绝对静止，且一旦你换队，原队伍将立刻恢复光速通行。",
        "周一早晨引力异常：地球物理学家发现，每周一清晨六点至九点之间，床垫表面局部重力加速度会激增至平时的 9.8 倍以上，导致人类脊椎无法完成由水平向垂直的相变过程，该引力异常通常在周五下午五点整瞬间消失。",
        "免责声明文学：指在某些软件首次启动时弹出的、字号小于五号、滚动条比头发丝还细、且唯一的「不同意」按钮被设计成直接退出程序的现代数字卖身契艺术形式。",
        "杀后台体操：当代智能手机用户特有的无意识手指条件反射，具体表现为每隔三十秒必须从屏幕底部上滑呼出多任务卡片，像发牌手一样把所有应用往上滑飞，并坚信这一动作能为电池续命三小时。",
        "Wi-Fi信号玄学：一种受人类注视影响的量子场。当你只是在刷无聊图文时，信号满格且延迟为 5ms；一旦你进入排位赛团战或准备抢票的最后三秒，路由器将立刻进入禅定状态并开始思考宇宙的终极意义。",
        "老板画饼膨化工艺：利用纯天然口头承诺作为原料，在不添加任何实质性年终奖或调休假期的条件下，仅凭周五下午五点半的周会空气加热，即可膨胀出足以支撑员工再干三个季度的虚拟碳水化合物。"
    )

    private val chouxiangWorldReservePool = listOf(
        "前列腺高潮只有男人能做到，所以被干到非常男子汉。",
        "刚才在小区门口被一条路过的黄狗瞪了一眼，我当场把包一扔趴在地上冲它狂叫了足足三分钟，周围邻居都在看我，但我不在乎，做人绝对不能在气势上输给狗。",
        "去医院看胃病，医生语重心长地拍着我的肩膀说年轻人胃不好平时要多吃点软饭，我听完眼泪当场就下来了，请问民政局旁边哪个窗口可以排队领富婆？",
        "我每天早上起床的第一件事就是对着镜子左右开弓给自己两巴掌，清醒一下，顺便告诉自己今天又是抗击打能力拉满且还没被社会毒打够的一天。",
        "别人谈恋爱是甜甜的双向奔赴，我谈恋爱像是在峨眉山替国家野生动物保护协会提前驯化还没退化完全的吗喽。",
        "诚心建议你们公司干脆把我的剪贴板直接刻进你们祖传的甲骨文数据库里供起来，毕竟我自己一天才粘贴两次，你们后台进程十分钟能来翻八回，比我妈翻我寒假作业还勤快。",
        "只要我的精神状态足够美丽，试图通过剪贴板分析我消费画像的大数据推荐算法就会先一步因为逻辑死锁被送进赛博精神病院。",
        "生活就像一盒精装巧克力，你永远不知道下一颗拿起来的是榛子味，还是上礼拜掉在沙发缝里粘满了三层猫毛和半截指甲盖的那颗。",
        "有时候真的打心底里羡慕你们这些后台常驻推送进程，一分钱房租和物业费都不用交，就能心安理得地在我手机 16G 内存里白吃白住一整天，甚至还要顺手翻翻我的兜。",
        "今天去算命，大师捏着我的手看了半天突然噗嗤一声笑了出来，我问他笑什么，他说干这行三十年没见过有人命苦得像生喝了三斤浓缩美式还不加糖。",
        "你们产品经理是不是小时候玩捉迷藏总被人从衣柜里揪出来，所以长大后非要把读取剪贴板的代码写进 onResume 里，生怕切回前台慢零点一秒就摸不到我兜里那半截淘口令？",
        "人家有钱人终成眷属，我没钱人亲眼目睹，不仅亲眼目睹，还要在后台被你们的广告 SDK 偷偷拿走剪贴板去推算我是不是连九块九包邮的纸巾都买不起。",
        "现代人的崩溃往往是悄无声息的，表面上看着正在冷静地切应用回消息，实际上已经在暗地里给偷看剪贴板的流氓软件喂了八百条伪基大典和废话文学。",
        "以前以为赛博朋克是义体改造和霓虹雨夜，后来才发现真正的赛博朋克是我刚复制了一句歌词，五个购物软件和三个短视频软件已经在后台为我吵起来了，纷纷问我是不是想买同名款拖把。",
        "如果早知道长大以后每天面对的是无穷无尽的周报、打卡和切一次前台读三次剪贴板的 App，当初在娘胎里我就应该用脐带给自己打个蝴蝶结。",
        "问世间情为何物，直教人宁愿把剪贴板填满一万字母猪产后护理教材，也不肯让你们的数据埋点脚本白嫖走半个字节。",
        "刚去面试，面试官问我最大的优点是什么，我说我像国内大厂 App 的后台服务一样坚韧不拔，不管用户怎么强行停止，只要有一丁点广播信号就能死灰复燃并顺手摸一下剪贴板。",
        "睡眠质量差的朋友建议晚上把手机里的购物软件全打开切一遍前台，看着它们整齐划一地读取你空荡荡的剪贴板，那种数羊般的秩序感能让你三秒钟陷入深度昏迷。",
        "虽然我卡里没有几个钱，但我剪贴板里的戏特别多，谁今天敢来后台偷瞄一眼，我就让它的分词算法连夜复习完一整本《高等数学》同济第七版。",
        "以前总觉得自己是个没人关心的透明人，直到装了剪贴板监控才发现，原来每天有十几个大厂的一线守护进程在后台二十四小时不知疲倦地惦记着我复制了啥，泪目了。"
    )

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
        "为什么字典里每个字都有解释，唯独没有一个字站出来解释一下字典自己为什么这么厚？"
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
        "哪怕你把这段话从头到尾正着读一遍，再从尾到头倒着读一遍，你得到的信息量依然等于零乘以任何实数。"
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
        "[ERROR] [org.apache.kafka.clients.producer] Topic 'app-clipboard-harvest-events' rejected payload: SchemaValidationException(field 'share_code' contains philosophical paradox instead of token)."
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
        "科目二倒车入库压线自动抬车液压千斤顶（驾校教练严选）"
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
     * Tries to fetch [count] random Wikipedia summaries online (full extract without truncation);
     * falls back to offline reserve if network is unreachable.
     */
    suspend fun fetchRandomEntries(count: Int = 3): List<String> = withContext(Dispatchers.IO) {
        val results = mutableListOf<String>()
        repeat(count) {
            val onlineItem = tryFetchSingleWikiSummary()
            if (onlineItem != null) {
                results.add(onlineItem)
            }
        }
        if (results.isEmpty()) {
            results.addAll(offlineWikiReservePool.shuffled().take(count))
        }
        results.distinct()
    }

    private suspend fun fetchUncyclopediaEntries(count: Int = 3): List<String> = withContext(Dispatchers.IO) {
        val results = mutableListOf<String>()
        repeat(count) {
            val online = tryFetchSingleUncyclopediaSummary()
            if (online != null) {
                results.add(online)
            }
        }
        if (results.size < count) {
            val needed = count - results.size
            results.addAll(uncyclopediaReservePool.shuffled().take(needed))
        }
        results.distinct()
    }

    private suspend fun fetchChouxiangWorldEntries(count: Int = 3): List<String> = withContext(Dispatchers.IO) {
        val scraped = tryFetchChouxiangWorldQuotes(count)
        if (scraped.size >= count) {
            scraped.take(count)
        } else {
            (scraped + chouxiangWorldReservePool.shuffled())
                .filterNot { isBlacklistedBoilerplate(it) }
                .distinct()
                .take(count)
        }
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

    private fun tryFetchSingleWikiSummary(): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("https://zh.wikipedia.org/api/rest_v1/page/random/summary")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3500
                readTimeout = 3500
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "ClipBait/1.1 (Android OpenSource Privacy Module)")
            }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val root = json.parseToJsonElement(body).jsonObject
                val title = root["title"]?.jsonPrimitive?.content?.trim() ?: return null
                // No length truncation — preserve the full summary extract
                val extract = root["extract"]?.jsonPrimitive?.content
                    ?.replace(Regex("\\s+"), " ")
                    ?.trim() ?: return null
                if (extract.isBlank()) return null
                "$title：$extract"
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Throwable) {
            }
        }
    }

    private fun tryFetchSingleUncyclopediaSummary(): String? {
        // Removed exchars=100 limit so the full introductory paragraph is returned
        val endpoints = listOf(
            "https://uncyclopedia.ca/w/api.php?action=query&format=json&generator=random&grnnamespace=0&prop=extracts&exintro=1&explaintext=1",
            "https://zh.uncyclopedia.info/api.php?action=query&format=json&generator=random&grnnamespace=0&prop=extracts&exintro=1&explaintext=1"
        )
        for (endpoint in endpoints) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(endpoint)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 3000
                    readTimeout = 3000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "ClipBait/1.1 (Android OpenSource Privacy Module)")
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val pages = json.parseToJsonElement(body)
                        .jsonObject["query"]
                        ?.jsonObject?.get("pages")
                        ?.jsonObject ?: continue
                    val firstPage = pages.values.firstOrNull()?.jsonObject ?: continue
                    val title = firstPage["title"]?.jsonPrimitive?.content?.trim() ?: continue
                    val extract = firstPage["extract"]?.jsonPrimitive?.content
                        ?.replace(Regex("\\s+"), " ")
                        ?.trim()
                    if (!extract.isNullOrBlank() && extract.length >= 12) {
                        return "$title：$extract"
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    conn?.disconnect()
                } catch (_: Throwable) {
                }
            }
        }
        return null
    }

    private fun tryFetchChouxiangWorldQuotes(count: Int): List<String> {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("https://chouxiang.world/")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3500
                readTimeout = 3500
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) ClipBait/1.1")
            }
            if (conn.responseCode == 200) {
                val rawHtml = conn.inputStream.bufferedReader().use { it.readText() }
                // Strip <head>, <title>, <script>, <style>, <nav>, <header>, <footer> so page slogan
                // ("没有抽象段子的无聊世界") and navigation elements are never scraped as quotes.
                val bodyHtml = rawHtml
                    .replace(Regex("(?is)<head\\b[^>]*>.*?</head>"), " ")
                    .replace(Regex("(?is)<title\\b[^>]*>.*?</title>"), " ")
                    .replace(Regex("(?is)<script\\b[^>]*>.*?</script>"), " ")
                    .replace(Regex("(?is)<style\\b[^>]*>.*?</style>"), " ")
                    .replace(Regex("(?is)<nav\\b[^>]*>.*?</nav>"), " ")
                    .replace(Regex("(?is)<header\\b[^>]*>.*?</header>"), " ")
                    .replace(Regex("(?is)<footer\\b[^>]*>.*?</footer>"), " ")

                val candidates = mutableListOf<String>()
                // No upper length cap — allow full-length copypastas and paragraphs
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
                candidates.distinct().shuffled().take(count)
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Throwable) {
            }
        }
    }
}
