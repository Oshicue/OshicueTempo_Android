# Oshicue Tempo (Android)

**简体中文** · [繁體中文](README.zh-Hant.md) · [日本語](README.ja.md) · [English](README.en.md)

粉丝特典会现场使用的原生 Android 多成员计时器。**Tempo** 意为节奏。

这是 `OshicueTempo_iOS`（SwiftUI 原生 iOS App）的完整 Android 移植：Kotlin + Jetpack Compose、MediaPlayer、AlarmManager、NotificationChannel，最低 Android 10（API 29），无网络与账号依赖，无第三方运行时库。

## 打开与运行

用 Android Studio 打开本目录（或直接使用命令行），JDK 17 及以上：

```sh
./gradlew :app:assembleDebug          # 调试包
./gradlew :app:testDebugUnitTest      # 核心与持久化测试（16 项）
./gradlew :app:assembleRelease        # 签名发布包（需要 keystore.properties）
```

发布签名与 iOS 工程的团队配置类似：仓库内的 `oshicue-tempo-release.jks` 与 `keystore.properties` 提供自签名配置；没有 `keystore.properties` 时只会产出未签名包。已构建的产物：

- `OshicueTempo-Android-1.0.0.apk`（R8 收缩后的签名发布包，约 3.6 MB）
- `OshicueTempo-Android-1.0.0-debug.apk`
- `OshicueTempo-Android-1.0.0-Source.zip`（工程源码归档，不含签名与构建产物）

## 使用

1. 点击右上角管理按钮，创建团体，设置团体名称和每张券的秒数（1–3600 秒）。
2. 添加成员，填写名字、选择代表色，以及各自的结束提示音。
3. 在主界面选择当前团体。一屏显示四位成员，超过四位时左右翻页，也可以点击页码两侧的箭头。
4. 点击成员时钟，数字键盘输入本轮券数，点击「确认并开始倒计时」即可立即开始。**总时间 = 团体每张券秒数 × 本轮券数**。
5. 每位成员可以独立暂停、继续、重置或开始下一轮。切换团体不会停止其他成员的倒计时。

默认附带可编辑的虚构 LUMINA 团体与四位成员，方便第一次运行时试用。可以删除示例，再添加真实团体。

每轮券数为 1–999 张，单轮最多 24 小时。已启动的轮次保留启动时的单券时长；编辑团体时长不会改变进行中的一轮。删除成员或团体并保存时，会取消相关计时及提醒。重置本轮需要确认。

## 提示音与录音导入

内置四种本地合成的原创短提示音：星光、心跳、铃语、通关，可在「声音与提醒」中试听。多名成员同时结束时，前台会同时混合播放各自的提示音，并按人数自动均分音量。

导入录音：在「声音与提醒」中点击「从文件导入录音」，通过系统文件选择器（SAF）选择音频。支持 M4A、WAV、AIFF、CAF、MP3、AAC，录音需短于 30 秒且不大于 50 MB。导入的音频同时保存一份到系统通知音频目录（MediaStore），供通知渠道使用；应用内播放使用应用私有目录中的原始文件。

前台通过媒体音量播放，并申请可自动降低其他应用音量的音频焦点；计时中屏幕保持常亮。锁屏和后台提醒由 AlarmManager 精确闹钟触发，每个提示音一个高优先级通知渠道，需要通知权限；若系统撤销了精确闹钟权限，会退化为不精确提醒（可能延迟）。场地使用前请在真机试听，并核对通知权限和音量。

计时根据截止时间计算，切换 App、锁屏或重新打开后会恢复正确剩余时间；设备重启或应用更新后自动重新安排仍在进行的提醒。过期轮次重新进入前台时显示「时间到」，不会补播已由后台通知处理过的过期声音。

## 外观

浅色和深色界面自动跟随系统外观，无需在 App 内另设开关。浅色使用纯白背景与轻微染色的白色卡片，深色保留 Oshicue 黑底。系统外观变化时，主计时板、输入弹窗、数字键盘、团体和成员编辑、声音设置都会一起更新，已开始或暂停的计时不会重置。

成员原始代表色始终保留，成员色点及计时环在浅深色模式使用一致的代表色（与 iOS 相同的对比度补偿算法）。浅色使用清晰的中性深色文字，卡片染色与阴影也更轻。白色代表色的时钟带有细中性衬边。品牌字样和倒计时继续使用工程内置的 Unbounded 变量字体。

## 语言支持

原生支持简体中文、繁体中文、日语与英语，自动遵循系统语言；Android 13 及以上可在系统设置的「应用语言」中单独切换（通过 `localeConfig` 声明）。界面、无障碍标签、错误提示、提示音名称与本地通知均已翻译；用户填写的团体名、成员名与录音名保持原样。

仅英语界面将活动称为 **MEET & GREET**；简体中文及日语使用 **特典会**，繁体中文使用 **特典會**。

## 数据与品牌

团体、成员、选择的团体和各轮计时自动保存到应用私有目录 `files/OshicueTempo/state.json`；JSON 结构与 iOS 版完全一致（`version: 1`、字母序键、`millisecondsSince1970` 日期、省略空值），两个平台写出的存档可以互相读取。写入采用临时文件原子替换，读取失败时保留原数据并停止写入。

沿用 Oshicue 品牌色：黑 `#0A0A0D`、暖白 `#F4F3EE`、粉 `#FF3D6E`。品牌字样和计时数字使用 **Unbounded** 变量字体（SIL Open Font License，许可随包附于 `app/src/main/assets/licenses/`）。图标与字标沿用 iOS 工程的品牌资源（`docs/brand/`），自适应启动图标由 `tools/generate_launcher_icons.swift` 生成。

## 工程与验证

- `app/src/main/kotlin/com/oshicue/tempo/App`：应用生命周期与各页面协调（`TempoApplication`、`MainActivity`、`TempoRoot`）。
- `app/src/main/kotlin/com/oshicue/tempo/core`：团体、成员、倒计时模型，输入校验与快照 JSON 编解码。
- `app/src/main/kotlin/com/oshicue/tempo/data`：`TempoStore` 持久化、`TempoAudio` 播放与导入、`TempoNotifications` 通知渠道与闹钟、广播接收器。
- `app/src/main/kotlin/com/oshicue/tempo/ui`：四人计时板、券数输入、团体编辑与声音设置。
- `app/src/main/kotlin/com/oshicue/tempo/i18n`：由 iOS `Localizable.strings` 生成的键映射。
- `app/src/test`：时间运算、状态持久化及异常路径测试（16 项）。
- `tools/convert_localization.py`：从 iOS 资源目录重新生成四语言字符串与键映射。
- `../misc/OshicueTempo_iOS/`：工作区中与工程目录同级的 `misc/` 里，作为移植参考的 iOS 源工程（连同原始压缩包；工程源码归档中不包含）。

```sh
python3 tools/convert_localization.py ../misc/OshicueTempo_iOS/OshicueTempo/Core/Resources \
  app/src/main/res app/src/main/kotlin/com/oshicue/tempo/i18n
swift tools/generate_launcher_icons.swift ../misc/OshicueTempo_iOS/docs/brand/app-icon.png app/src/main/res
```

当前验证结果见 `docs/VERIFICATION.md`。
