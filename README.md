# ClipBox (PJ-jqb)

专为 AOSP / EvoX / crDroid 深度定制的纯粹剪切板隐私控制模块（LSPosed / Vector 支持）。

针对国产应用普遍存在的在 `onResume`、窗口获焦（`onWindowFocusChanged`）或定时轮询中无感偷读剪切板（用于淘口令分析、防关联追踪及客群画像），提供**静默智能拦截**与**噪点伪装**能力。

---

## 核心特性

- **双通道静默鉴别（Heuristic Detection）**：
  - 自动识别原生控件长按菜单粘贴（`onTextContextMenuItem`）、界面控件点击粘贴（`performClick`）以及近程手势窗口（Trust Window）。
  - 对应用切台唤醒、窗口获焦、后台轮询等恶意探针行为进行精确拦截或噪点伪装。
  - **Fcitx5（小企鹅输入法 5）天然契合**：日常输入法候选栏/剪切板历史粘贴走 `InputConnection.commitText` 原生通道，宿主应用完全无法察觉。
- **客群反画像噪点伪装（Camouflage / Noise Injection）**：
  - 电商及追踪类应用读取剪切板时，下发自然日常短语或随机 Wikipedia 词条，打乱其客群标签提取算法。
- **光荣榜（Wall of Shame 审计系统）**：
  - 实时记录每次偷读事件的触发诱因（如 `Lifecycle: onResume`、`Focus: onWindowFocus`）、时间、阻断类型及喂给对端的噪点内容。
  - 提供偷读频次排行，抓出最肆无忌惮的应用。
- **Sing-box 风格声明式规则模型**：
  - 采用声明式、强类型的 JSON 配置架构（`settings`, `rules`, `decoys`, `subscriptions`）。
  - 界面设置与 JSON 配置文本实时双向同步，支持规则直接导入与导出。
- **纯粹 Material Design 3 视觉系统**：
  - 绝无任何 Emoji。
  - 全面使用官方 Google Material Symbols / Material Icons。
  - 支持 Monet 动态色彩提取、Surface Tonal Elevation 色阶与 8dp 栅格化系统。

---

## 声明式配置示例 (`config.json`)

```json
{
  "$schema": "https://pj-jqb.local/schema/v1.json",
  "version": 1,
  "settings": {
    "default_action": "direct",
    "smart_detect": {
      "enabled": true,
      "trust_window_ms": 300,
      "fallback_action": "block"
    },
    "audit_log": {
      "enabled": true,
      "retention_days": 15
    }
  },
  "decoys": [
    {
      "id": "e_commerce_noise",
      "name": "电商客群污染噪点",
      "strategy": "random_natural",
      "pool": [
        "今天天气挺凉快，适合出门散步",
        "会议纪要已发送至工作邮箱，请查收",
        "好的，晚点联系",
        "https://zh.wikipedia.org/wiki/Special:Random"
      ]
    }
  ],
  "rules": [
    {
      "package_name": [
        "com.taobao.taobao",
        "com.xunmeng.pinduoduo",
        "com.jingdong.app.mall"
      ],
      "action": "spoof",
      "decoy": "e_commerce_noise",
      "description": "主流电商平台强制噪点伪装"
    },
    {
      "package_name": [
        "com.tencent.mobileqq",
        "com.tencent.mm"
      ],
      "action": "smart",
      "smart_fallback": "block",
      "description": "即时通讯应用智能放行，非主动读取全阻断"
    }
  ],
  "subscriptions": []
}
```

---

## 编译与安装

### 1. 编译 Debug APK
```bash
./gradlew assembleDebug
```
产物位置：`app/build/outputs/apk/debug/app-debug.apk`

### 2. 安装与激活
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
1. 打开 LSPosed / Vector 管理器；
2. 在模块列表中找到 **ClipBox** 并开启；
3. 勾选推荐作用域或手动勾选需要保护的目标应用；
4. 软重启手机或强行停止对应应用即可生效。
