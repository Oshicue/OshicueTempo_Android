# Oshicue Tempo (Android)

[简体中文](README.md) · **繁體中文** · [日本語](README.ja.md) · [English](README.en.md)

粉絲特典會現場使用的原生 Android 多成員計時器。**Tempo** 意為節奏。

這是 `OshicueTempo_iOS`（SwiftUI 原生 iOS App）的完整 Android 移植：Kotlin + Jetpack Compose、MediaPlayer、AlarmManager、NotificationChannel，最低 Android 10（API 29），無網路與帳號依賴，無第三方執行階段函式庫。

## 建置與執行

用 Android Studio 開啟本目錄（或直接使用命令列），JDK 17 及以上：

```sh
./gradlew :app:assembleDebug          # 偵錯包
./gradlew :app:testDebugUnitTest      # 核心與持久化測試（16 項）
./gradlew :app:assembleRelease        # 簽名發佈包（需要 keystore.properties）
```

發佈簽名與 iOS 工程的團隊設定類似：專案內的 `oshicue-tempo-release.jks` 與 `keystore.properties` 提供自簽名設定；沒有 `keystore.properties` 時只會產出未簽名包。已建置的產物：

- `OshicueTempo-Android-1.0.0.apk`（R8 收縮後的簽名發佈包，約 3.6 MB）
- `OshicueTempo-Android-1.0.0-debug.apk`
- `OshicueTempo-Android-1.0.0-Source.zip`（專案原始碼封存，不含簽名與建置產物）

## 使用

1. 點擊右上角管理按鈕，建立團體，設定團體名稱和每張券的秒數（1–3600 秒）。
2. 新增成員，填寫名字、選擇代表色，以及各自的結束提示音。
3. 在主畫面選擇目前團體。一頁顯示四位成員，超過四位時左右翻頁，也可以點擊頁碼兩側的箭頭。
4. 點擊成員時鐘，數字鍵盤輸入本輪券數，點擊「確認並開始倒數計時」即可立即開始。**總時間 = 團體每張券秒數 × 本輪券數**。
5. 每位成員可以獨立暫停、繼續、重設或開始下一輪。切換團體不會停止其他成員的倒數計時。

預設附帶可編輯的虛構 LUMINA 團體與四位成員，方便第一次執行時試用。可以刪除範例，再新增真實團體。

每輪券數為 1–999 張，單輪最多 24 小時。已啟動的輪次保留啟動時的單券時長；編輯團體時長不會改變進行中的一輪。刪除成員或團體並儲存時，會取消相關計時及提醒。重設本輪需要確認。

## 提示音與錄音匯入

內建四種本地合成的原創短提示音：星光、心跳、鈴語、過關，可在「聲音與提醒」中試聽。多名成員同時結束時，前景會同時混合播放各自的提示音，並按人數自動均分音量。

匯入錄音：在「聲音與提醒」中點擊「從檔案匯入錄音」，透過系統檔案選擇器（SAF）選擇音訊。支援 M4A、WAV、AIFF、CAF、MP3、AAC，錄音需短於 30 秒且不大於 50 MB。匯入的音訊同時保存一份到系統通知音訊目錄（MediaStore），供通知管道使用；應用程式內播放使用應用程式私人目錄中的原始檔案。

前景透過媒體音量播放，並申請可自動降低其他應用程式音量的音訊焦點；計時中螢幕保持常亮。鎖定畫面和背景提醒由 AlarmManager 精確鬧鐘觸發，每個提示音一個高優先級通知管道，需要通知權限；若系統撤銷了精確鬧鐘權限，會退化為不精確提醒（可能延遲）。場地使用前請在真機試聽，並核對通知權限和音量。

計時根據截止時間計算，切換 App、鎖定螢幕或重新開啟後會恢復正確剩餘時間；裝置重新啟動或應用程式更新後自動重新安排仍在進行的提醒。過期輪次重新進入前景時顯示「時間到」，不會補播已由背景通知處理過的過期聲音。

## 外觀

淺色和深色介面自動跟隨系統外觀，無需在 App 內另設開關。淺色使用純白背景與輕微染色的白色卡片，深色保留 Oshicue 黑底。系統外觀變化時，主計時板、輸入彈窗、數字鍵盤、團體和成員編輯、聲音設定都會一起更新，已開始或暫停的計時不會重設。

成員原始代表色始終保留，成員色點及計時環在淺深色模式使用一致的代表色（與 iOS 相同的對比度補償演算法）。淺色使用清晰的中性深色文字，卡片染色與陰影也更輕。白色代表色的時鐘帶有細中性襯邊。品牌字樣和倒數計時繼續使用專案內建的 Unbounded 可變字型。

## 語言支援

原生支援簡體中文、繁體中文、日語與英語，自動遵循系統語言；Android 13 及以上可在系統設定的「應用程式語言」中單獨切換（透過 `localeConfig` 宣告）。介面、無障礙標籤、錯誤提示、提示音名稱與本地通知均已翻譯；使用者填寫的團體名、成員名與錄音名保持原樣。

僅英語介面將活動稱為 **MEET & GREET**；簡體中文及日語使用 **特典会**，繁體中文使用 **特典會**。

## 資料與品牌

團體、成員、選擇的團體和各輪計時自動保存到應用程式私人目錄 `files/OshicueTempo/state.json`；JSON 結構與 iOS 版完全一致（`version: 1`、字母序鍵、`millisecondsSince1970` 日期、省略空值），兩個平台寫出的存檔可以互相讀取。寫入採用臨時檔案原子替換，讀取失敗時保留原資料並停止寫入。

沿用 Oshicue 品牌色：黑 `#0A0A0D`、暖白 `#F4F3EE`、粉 `#FF3D6E`。品牌字樣和計時數字使用 **Unbounded** 可變字型（SIL Open Font License，授權隨包附於 `app/src/main/assets/licenses/`）。圖示與字標沿用 iOS 工程的品牌資源（`docs/brand/`），自適應啟動圖示由 `tools/generate_launcher_icons.swift` 產生。

## 專案與驗證

- `app/src/main/kotlin/com/oshicue/tempo/App`：應用程式生命週期與各頁面協調（`TempoApplication`、`MainActivity`、`TempoRoot`）。
- `app/src/main/kotlin/com/oshicue/tempo/core`：團體、成員、倒數計時模型，輸入驗證與快照 JSON 編解碼。
- `app/src/main/kotlin/com/oshicue/tempo/data`：`TempoStore` 持久化、`TempoAudio` 播放與匯入、`TempoNotifications` 通知管道與鬧鐘、廣播接收器。
- `app/src/main/kotlin/com/oshicue/tempo/ui`：四人計時板、券數輸入、團體編輯與聲音設定。
- `app/src/main/kotlin/com/oshicue/tempo/i18n`：由 iOS `Localizable.strings` 產生的鍵映射。
- `app/src/test`：時間運算、狀態持久化及異常路徑測試（16 項）。
- `tools/convert_localization.py`：從 iOS 資源目錄重新產生四語言字串與鍵映射。
- `../misc/OshicueTempo_iOS/`：工作區中與專案目錄同級的 `misc/` 裡，作為移植參考的 iOS 原始碼工程（連同原始壓縮包；專案原始碼封存中不包含）。

```sh
python3 tools/convert_localization.py ../misc/OshicueTempo_iOS/OshicueTempo/Core/Resources \
  app/src/main/res app/src/main/kotlin/com/oshicue/tempo/i18n
swift tools/generate_launcher_icons.swift ../misc/OshicueTempo_iOS/docs/brand/app-icon.png app/src/main/res
```

目前驗證結果見 `docs/VERIFICATION.md`。
