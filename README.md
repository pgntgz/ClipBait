# ClipBait

ClipBait是一开源的Xposed模块，为应对剪切板泄露而生，ClipBait喜欢给乱翻剪贴板的软件下点饵。他会在 system_server 底层拦截偷读行为并伪装返回假词、百科或直接掐灭。仅需勾选系统框架，规则与词库支持热更新。GUI设计遵循MD3设计体系。

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Language: Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Xposed API: 82](https://img.shields.io/badge/Xposed-API%2082-orange.svg?logo=android&logoColor=white)](https://api.xposed.info/)

国内不少应用（电商、短视频、带统计 SDK 的工具等）有个坏毛病：只要一切回前台或者躲在后台推送进程里，不管你有没有点粘贴，先顺手把剪贴板翻一遍。直接拦空有时候会被它们察觉，不如干脆塞点假词、百科或者怪话糊弄过去，顺便记下来给他们颁个奖。

## 使用
和大多数Xposed插件差不多，你**只需要在 LSPosed / Vector 里勾上模块请求的「系统框架 (system Framework)」即可，无需勾选其他任何软件**。

- **支持环境**：Android 10 ~ 17（API 29+），LSPosed / Vector 等现代 Xposed 框架（API82）
- **作用域**：仅需要勾上模块请求的「系统框架 (system Framework)」
- **热生效**：首次激活模块需要重启一次手机；之后无论是新装应用还是修改拦截规则、抽词库，都是当场生效，无须重启。
- **输入法兼容**：自动识别并放行系统当前激活的输入法（如 Fcitx5、Gboard 等），平时用输入法剪贴板粘贴不受影响。

## 功能
你可以全部或指定规则，也可以用内置的按包名和内嵌广告/统计 SDK 筛查的列表简单的排出问题应用
针对每个应用有四个模式可以选：

- **放行**：不管，跟没装插件一样。
- **智能**：你自己手动点粘贴（长按粘贴菜单、点粘贴按钮）照常放行；应用刚切回前台头两秒自动偷读、或者躲在后台静默读，直接拦下（可设回传空剪贴板或塞假词）。适合微信、QQ 等日常还得正常聊天的软件。
- **伪装**：不管什么时候来读，都从词库里随机抽一句假话塞给它（可以设定防连读穿帮保鲜期，不怕应用一秒内连读校验）。内置联网抽词（维基百科、伪基大典、`chouxiang.world`）和本地生成好的（弱智吧暴论、假淘口令、废话文学、假报错栈），也支持自己手写。
- **阻断**：永远返回空剪贴板，并掐掉剪贴板变动监听。

GUI设计遵照Material Design 3体系，使用Kotlin开发，大量采用MDUI图像资产集，支持动态取色，主题图标等MD3功能。

另外附带一个**光荣榜**：
- 统计「读取次数积分榜」和可选的「最爱读取积分榜」（结合应用真实前台使用时长，揪出没用几分钟却天天摸兜的应用）。
- 点开单条记录可以看到具体是主进程还是 `:push` 这种子进程伸的手、当时塞了什么假话，顺便给前三名颁奖和放个礼花。

## 开发
开发的原因是出于我个人对滥读剪切板的厌恶，事实上我基本不懂Android开发，所以这个项目几乎完全由 AI 构建，如果有发现BUG或者点子欢迎反馈。

- 源码仓库：[https://github.com/pgntgz/ClipBait](https://github.com/pgntgz/ClipBait)
- 问题反馈：[Issues](https://github.com/pgntgz/ClipBait/issues)

联系作者
- Matrix：`@pgntgz:tchncs.de`
- 邮箱：`2f4fsilg6@mozmail.com`

### 许可证
```
本程序是自由软件：你可以再分发之和/或依照由自由软件基金会发布的 GNU 通用公共许可证修改之，无论是版本 3 许可证，还是（按你的决定）任何以后版都可以。

发布该程序是希望它能有用，但是并无保障;甚至连可销售和符合某个特定的目的都不保证。请参看 GNU 通用公共许可证，了解详情。

你应该随程序获得一份 GNU 通用公共许可证的复本。如果没有，请看 <https://www.gnu.org/licenses/>。
```

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
```
This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
```
