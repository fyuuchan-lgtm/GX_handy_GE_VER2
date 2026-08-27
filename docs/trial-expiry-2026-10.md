# Yakupita V2 試用期限（2026年10月版）

## 配布版の期限

- 利用可能最終日: **2026年10月31日**
- 判定タイムゾーン: **Asia/Tokyo**
- 2026年10月31日までは利用可能で、2026年11月1日から期限切れ画面を表示する。
- アプリ更新用の版番号: **1.0.1（versionCode 2）**

期限判定は `app/src/main/java/com/example/yakuzaiapp/util/TrialAvailability.kt` の `expiresOn` を基準とする。期限切れ画面の表示文言も同じ値から生成し、判定と表示の日付を一致させる。
