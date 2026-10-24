# ClipBait

## 基础介绍
这是一个给喜欢乱翻剪贴板的软件下点饵的 Xposed 插件。

国内不少应用（电商、短视频、带统计 SDK 的工具等）有个坏毛病：只要一切回前台或者躲在后台推送进程里，不管你有没有点粘贴，先顺手把剪贴板翻一遍。直接拦空有时候会被它们察觉，不如干脆塞点假词、百科或者怪话糊弄过去，顺便在光荣榜里给它们记上一笔。

## 使用
和普通插件差不多，但是你**只需要在 LSPosed / Vector 里勾上「系统框架 (android)」即可，无需勾选其他任何软件**。

- **支持环境**：Android 10 ~ 15（API 29+），LSPosed / Vector 等现代 Xposed 框架
- **作用域**：仅 `系统框架 (android)`
- **热生效**：首次激活模块需要重启一次手机；之后无论是新装应用还是修改拦截规则、抽词库，都是当场生效，不用再重启。
- **输入法兼容**：自动识别并放行系统当前激活的输入法（如 Fcitx5、Gboard 等），平时用输入法剪贴板粘贴不受影响。

## 功能
针对每个应用有四个模式可以选（也可以用内置的「一键揪出毒瘤」按包名和内嵌广告/统计 SDK 自动筛一遍）：

- **放行**：不管，跟没装插件一样。
- **智能**：你自己手动点粘贴（长按粘贴菜单、点粘贴按钮）照常放行；应用刚切回前台头两秒自动偷读、或者躲在后台静默读，直接拦下（可设回传空剪贴板或塞假词）。适合微信、QQ 等日常还得正常聊天的软件。
- **伪装**：不管什么时候来读，都从词库里随机抽一句假话塞给它（带防连读穿帮保鲜期，不怕应用一秒内连读校验）。内置联网抽词（维基百科、伪基大典、抽象世界 `chouxiang.world`）和本地生成（弱智吧暴论、假淘口令、废话文学、假报错栈），也支持自己手写。
- **阻断**：永远返回空剪贴板，并掐掉剪贴板变动监听。

另外附带一个**光荣榜**：
- 统计「读取次数积分榜」和可选的「最爱读取积分榜」（结合应用真实前台使用时长，揪出没用几分钟却天天摸兜的应用）。
- 点开单条记录可以看到具体是主进程还是 `:push` 这种子进程伸的手、当时塞了什么假话，顺便给前三名（或前排打平时的第四名「勒克莱尔奖」）放个彩带礼花。

## 开发
这个项目几乎完全由 AI 构建，如果认为有什么地方有不足，可以提出来让我的 AI 继续改。

- 作者 GitHub：[@pgntgz](https://github.com/pgntgz)
- 源码仓库：[https://github.com/pgntgz/ClipBait](https://github.com/pgntgz/ClipBait)
- 问题反馈：[Issues](https://github.com/pgntgz/ClipBait/issues)
- Matrix：`@pgntgz:tchncs.de`
- 邮箱：`2f4fsilg6@mozmail.com`

---

## English Overview

**ClipBait** is an Xposed (LSPosed / Vector) module that intercepts sneaky clipboard reads in `system_server` (`ClipboardService`) and feeds nosy apps empty data or random decoy phrases.

- **Scope**: **System Framework (`android`) only**. Do not hook individual target apps. Rules and decoy pools update live without rebooting.
- **4 Modes per App**:
  - **Allow**: Unrestricted access.
  - **Smart**: Allows genuine user-initiated pastes while blocking or spoofing automatic reads on `onResume` / window focus or silent background polling.
  - **Spoof**: Always returns a random decoy entry from the pool (with configurable per-app TTL cache to defeat burst-read consistency checks).
  - **Block**: Always returns an empty clipboard and silences clipboard listeners.
- **Hall of Fame**: Tracks top clipboard snoopers by total count or foreground usage density, complete with subprocess attribution and celebratory confetti.
- **Language**: Supports both Chinese and English (`Settings -> Language`).

## License
GPL-3.0
