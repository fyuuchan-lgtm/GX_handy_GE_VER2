# YakuzaiApp 復旧設計図 2026-06-27

この文書は、ここまで作った YakuzaiApp が壊れた場合に、現在地点まで再現するための設計図である。

通常の仕様書 `docs/spec_v0.3.md` は機能仕様の説明を主目的とする。本書は復旧・再実装・検証を主目的とし、実装上の判断、データの持ち方、避けるべき退行、確認コマンドをまとめる。

## 0. 2026-06-30 追記: 引っ越し後の再開方針

2026-06-30 時点で、作業フォルダーは以下に移動している。

```text
C:\Users\fyuuc\Dropbox\codex\アプリ開発\GX_handy(GE)_VER2
```

ソース、仕様書、既存 APK は残っており、開発再開は可能である。

現在の APK:

```text
app\build\outputs\apk\debug\app-debug.apk
```

### Git 履歴について

移行確認時点では、現在のフォルダーに `.git` ディレクトリ自体はあるが、中身が空だった。
そのため、この場所では以前の Git 履歴、ブランチ、タグ、リモート設定、差分管理は復元できなかった。

GitHub のコード検索で `yakuzaiapp / GX_handy / yakuzaiapp_phase1` を探したが、該当リポジトリは見つからなかった。
旧作業場所の候補として以下のパスが文書内に残っているが、2026-06-30 時点では存在しない。

```text
C:\Users\fyuuc\Documents\Codex\2026-06-05\files-mentioned-by-the-user-txt-2\outputs\yakuzaiapp_phase1
```

したがって、以前の `.git` を復元できない場合は、今あるソースを正本として新しい Git リポジトリを作り直す。
その場合は、本書と `docs/spec_v0.3.md` を過去履歴の参照資料として扱い、現在の状態を復旧後の初回コミットにする。

推奨手順:

```powershell
git init
git add -A
git commit -m "Restore YakuzaiApp source after workspace migration"
```

必要なら、その後に GitHub で空リポジトリを作り、`origin` を追加して push する。

### Windows の日本語パス対策

現在のパスには `アプリ開発` が含まれるため、Android Gradle Plugin は通常だと非 ASCII パスチェックでビルドを停止する。
このため、`gradle.properties` に以下を追加済みである。

```properties
android.overridePathCheck=true
```

この設定追加後、以下のビルドは成功済み。

```powershell
.\gradlew.bat :app:assembleDebug
```

注意:

- `android.overridePathCheck=true` は experimental warning が出る。
- 長期的には ASCII のみの作業パスに置く方が安全である。
- ただし、2026-06-30 時点ではこの設定により現在の場所でも debug APK を生成できる。

### 2026-07-01 追記: GitHub バックアップとテスト実行パス対策

復旧後の初回コミットは以下として作成済み。

```text
38b0c86 Restore YakuzaiApp source after workspace migration
```

GitHub のバックアップ先:

```text
https://github.com/fyuuchan-lgtm/GX_handy_GE_VER2.git
```

リモート設定:

```powershell
git remote add origin https://github.com/fyuuchan-lgtm/GX_handy_GE_VER2.git
git push -u origin master
```

### 2026-07-06 追記: バックアップ対象と復旧時の正本

アプリ本体が壊れた場合、または別 PC で再構築する場合は、以下を正本として扱う。

必ず残すもの:

- プログラム本体: `app/src/main/java/com/example/yakuzaiapp/`
- Android 設定: `app/build.gradle.kts`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`
- 通常仕様書: `docs/spec_v0.3.md`
- 追加仕様書: `docs/spec-camera-navigation-2026-07-06.md`
- 利用者登録マスター仕様: `docs/user-drug-master-2026-07-16.md`
- 復旧・再構築用仕様書: `docs/rebuild-blueprint-2026-06-27.md`
- 重要仕様差分: `docs/spec-vs-impl-diff-2026-06-09.md`
- JAHIS 参照資料: `docs/jahis-spec/`
- Gradle wrapper: `gradlew.bat`, `gradle/wrapper/`

バックアップ先は GitHub リポジトリを優先する。

```text
https://github.com/fyuuchan-lgtm/GX_handy_GE_VER2.git
```

作業マイルストーンごとに、少なくとも以下を行う。

```powershell
git status --short
git add app docs build.gradle.kts settings.gradle.kts gradle.properties gradlew.bat gradle/wrapper
git commit -m "<作業内容が分かるメッセージ>"
git push
```

注意:

- `app/build/`, `.gradle/`, `.idea/`, APK 生成物は復旧用の正本にしない。
- APK は動作確認には使えるが、再構築の正本はソースコードと仕様書である。
- 実機データのバックアップと GitHub のソースバックアップは別物である。
- MEDIS 自動更新は薬品マスタ更新の仕組みであり、ソースコードや仕様書のバックアップではない。

ユニットテストは Java/Gradle のテストワーカーが日本語を含む作業パスを正しく扱えず、`ClassNotFoundException` になることがある。
また、クリーンビルド時に KSP が生成した Room の Java コードから Kotlin 側の DAO/entity を見失うことがある。
`app/build.gradle.kts` では、debug Kotlin クラスを ASCII の一時ディレクトリへ同期して `compileDebugJavaWithJavac` のクラスパスへ追加する。
さらに `testDebugUnitTest` 実行時は、テストクラス・本体クラス・テストリソースも ASCII の一時ディレクトリへ同期してから実行する。

同期先:

```text
%TEMP%\gx_handy_ge_ver2\debug\classes
%TEMP%\gx_handy_ge_ver2\debugUnitTest\classes
```

この対策は、現在の作業場所を維持する場合に必要である。長期的には ASCII のみのパスへ移す方が安全だが、現状はこの設定で `:app:testDebugUnitTest` が通る。

## 1. 現在の到達点

2026-06-27 時点の到達点は以下。

- MEDIS HOT マスター `MEDIS********_h.txt` を取り込み、`drug_master` を作る。
- 販売名・包装ファイル `A_********_2.txt` を取り込み、`sales_package` を作る。
- PTP / 箱 GS1 は `sales_package` を優先して解決する。
- `drug_master.gtin` は本物の GS1 GTIN ではなく、HOT13 由来の暫定列として扱う。
- 本物の PTP / 販売包装 / 元梱 GS1 GTIN は `sales_package.gtin / gtinSales / gtinCase` に置く。
- 調剤モードでは JAHIS QR から期待リストを作り、PTP または箱 GS1 で確認する。
- 帳票監査モードでは OCR で薬品名を抽出し、候補選択後に PTP または箱 GS1 で確認する。
- 充填モードでは、補充する薬品の PTP/箱を先に読み、その後に充填先カセットまたは瓶のコードを読んで照合する。
- JAHIS QR は ML Kit QR 検出 + Windows-31J デコードを使う。
- PTP / 箱バーコードは zxing-cpp を使う。
- 販売名ファイルは UTF-8 / Shift_JIS を自動判定する。
- `A_20260531_2.txt` は UTF-8 として実機確認済み。
- 2026-07-01 時点で、MEDIS HOT と販売名・包装ファイルはアプリ起動時または取込画面から自動更新できる。
- 2026-07-16 時点で、院内製剤・医療材料を利用者登録し、帳票候補とバーコード照合に使用できる。

### 1.2 2026-07-16 追記: 院内製剤・材料の利用者登録マスター

公開マスターにない品目は、ホーム右上メニューの `院内製剤・材料マスター` から登録する。

実装上の重要点:

- `DrugMaster.isUserRegistered` で利用者登録品を識別する
- 利用者登録品の `hot13 / drugCode / yjCode` は `USER-<バーコード>` とする
- GTINはGTIN-14へ正規化し、施設独自コードは3〜64文字の文字列として保持する
- 帳票OCR候補は既存の `DrugMasterMatcher` 検索経路へ自然に含まれる
- 帳票PTPスキャンは `normalizeMasterBarcode()` でGTINと施設独自コードを受け付ける
- `DrugMasterRepository.findByAnyGtin()` の `drug_master` フォールバックで利用者登録品を解決する
- MEDIS更新で利用者登録品を消さないため、`DrugMasterDao.deleteImported()` を使用する
- Room DB version 10から11への移行で `isUserRegistered INTEGER NOT NULL DEFAULT 0` を追加する

絶対に避ける退行:

- MEDIS更新時に `DrugMasterDao.deleteAll()` を呼び、利用者登録品を消す
- 施設独自コードをGTINチェックデジット必須として拒否する
- 帳票候補とスキャン側で異なる `USER-*` コードを生成する
- 利用者登録品を `sales_package` だけに保存し、名称検索候補から外す

### 1.3 2026-07-16 追記: 初回施設登録後のMEDISダウンロード

初回起動時は施設情報が未登録であるため、ホームではなく施設登録画面を開始画面にする。

処理順:

```text
初回起動
  -> 施設登録が必要であることを説明
  -> 必須項目を登録
  -> MEDIS自動更新をforce=trueで開始
  -> ホームへ移動
  -> 「データダウンロード中」オーバーレイを表示
  -> 取込完了後は通知を表示せずオーバーレイを自動的に閉じる
```

実装上の要点:

- 登録判定は `FacilityInfo.isRegistered`
- 必須項目は施設名、郵便番号、都道府県、市区町村
- 未登録中は `FacilityRegistrationScreen(requiredForMedis = true)`
- 必須登録中はシステム戻ると上部戻るボタンを無効化
- `Lifecycle.Event.ON_START` の自動更新は施設登録済みの場合だけ実行
- 登録保存は `FacilityRepository.save()` がStateFlowを同期更新してから `onSaved()` を呼ぶ
- 登録後は施設登録画面をバックスタックから除去してホームへ進む

避けるべき退行:

- 施設未登録のままMEDISダウンロードを開始する
- 登録完了後も施設登録画面へ戻れてしまう
- 既存利用者の通常起動で毎回強制更新する
- 施設情報の通常編集時まで戻る操作を禁止する

### 1.1 2026-07-01 追記: MEDIS 自動更新

MEDIS 自動更新の目的は、手動で `MEDIS********_h.txt` と `A_********_2.txt` を選び直さなくても、最新リンクを取得して `drug_master` と `sales_package` を更新できるようにすること。

実装方針:

- `YakuzaiApplication` 起動時に `MedisAutoUpdateCoordinator.maybeStartAutoUpdate()` を呼ぶ。
- 前回ホームページ確認から7日以内なら自動更新はスキップする。
- 取込画面の手動更新はスロットルを無視して実行できる。
- ネットワーク未接続時は更新せず、既存DBを維持する。
- HOT と販売名・包装ファイルを両方ダウンロードして parse できた場合だけ、DBを更新する。
- 成功時だけ `markHomepageAccessSuccess()` と `markImportSuccess()` を記録する。
- 失敗時は次回起動または手動更新で再試行できる。

取得元:

```text
https://www2.medis.or.jp/hcode/
https://medhot.medd.jp/view_download
```

追加された Android 権限:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

主な構成:

- `MedisRemoteDataSource`: MEDIS ページから最新 HOT / 販売名リンクを発見し、ファイルをダウンロードする。
- `RoomMedisMasterImporter`: ダウンロード済みデータを parse して Room DB へ取り込む。
- `MedisUpdateMetadataStore`: 前回確認時刻、前回成功時刻、取込件数、対象ファイル名を保存する。
- `NetworkMonitor`: 更新前にネットワーク状態を確認する。
- `MedisAutoUpdateCoordinator`: 起動時・手動更新時の全体制御、進捗、排他制御を担当する。

UI:

- アプリ起動時の自動更新中は全画面オーバーレイで進捗を出す。
- MEDIS 取込画面から手動更新できる。
- エラー文言は日本語で表示し、文字化け文言を出さない。

## 2. 絶対に守る設計判断

### 2.1 drug_master と sales_package は分ける

Access のクエリのように2つのデータを事前JOINして1つの巨大テーブルにはしない。

理由:

- `drug_master` は薬品基本情報を持つ。
- `sales_package` は包装単位ごとの GS1 GTIN を持つ。
- 包装違い、販売包装、元梱包装を1つの薬品情報に押し込むと、主キー・重複・メーカー判定が壊れやすい。
- 実行時に `GTIN -> sales_package -> yjCode -> drug_master` と辿る方が保守しやすい。

### 2.2 PTP/箱照合の正は sales_package

PTP / 箱バーコードを読んだ時の一次検索先は `sales_package` である。

検索順:

1. `sales_package.gtin`
2. `sales_package.gtinSales`
3. `sales_package.gtinCase`
4. 旧互換として `drug_master` の GTIN 系列

`drug_master.gtin` を本物の GS1 GTIN として扱ってはいけない。

### 2.3 箱バーコードは未対応に戻さない

過去には箱バーコードを未対応メッセージに誘導していたが、現在は廃止済み。

箱の GTIN も `sales_package.gtinSales / gtinCase` で照合する。

### 2.4 JAHIS QR 断片は個別 parse しない

JAHIS 分割 QR は、3つの独立データではなく、1本の JAHIS テキストが分割されたもの。

- QR1 だけが `JAHISTCxx` ヘッダを持つ。
- QR2 / QR3 はヘッダなし断片であり、捨ててはいけない。
- 断片ごとに parser に渡してはいけない。
- 断片を結合してから `JahisQrParser` に渡す。

### 2.5 JAHIS フラグメント境界は動的に結合する

単純な `joinToString("")` でも、単純な `joinToString("\n")` でも壊れる。

現在の正しい方針:

- 前断片が改行で終わる場合は何も挿入しない。
- 次断片が `JAHISTCxx` または `数字,` で始まる場合は `\n` を挿入する。
- それ以外は同一レコード途中とみなし、空文字で連結する。

これにより以下の両方を救う。

- RP2 ラメルテオンのように薬品名途中で分割されるケース
- RP5 ランソプラゾールのようにレコード境界で分割されるケース

## 3. 外部ファイル

### 3.1 MEDIS HOT マスター

使うファイル:

```text
MEDIS********_h.txt
例: MEDIS20260531_h.txt
```

取得元:

```text
https://www2.medis.or.jp/hcode/
```

用途:

- `drug_master` を作る。
- 薬品名、HOT13、薬価基準収載医薬品コード、個別医薬品コード(YJ) を得る。

必須カラム:

| 入力カラム | 意味 | 保存先 |
|---|---|---|
| 0 | HOT13 | `DrugMaster.hot13`, `DrugMaster.gtin` |
| 1 | HOT7 | `DrugMaster.gtinSales` 互換欄 |
| 6 | 薬価基準収載医薬品コード | `DrugMaster.drugCode` |
| 7 | 個別医薬品コード(YJ) | `DrugMaster.yjCode` |
| 11 | 販売名 | `DrugMaster.drugName` |
| 12 | 販売名別名 | `DrugMaster.packageName`, `alias` |
| 13 | 規格 | `packageSpec` |
| 14 | 包装形態 | `packageForm` |
| 15 | 包装単位数 | `packageUnitCount` |
| 16 | 包装単位名 | `packageUnitName` |
| 17 | 総数量 | `containerCount` |
| 19 | 内用/外用区分 | `drugCategory` |
| 20 | 製造販売業者 | `maker` |
| 23 | 経過措置日 | `transitionDate` |

重要:

- `col7` が空なら `yjCode = col6` とする。
- JAHIS QR の薬品コードと合うことを優先する。
- `MEDIS20260531_HOT9.TXT` や `MEDIS20260531_OP.TXT` は現時点の必須ファイルではない。

### 3.2 販売名・包装ファイル

使うファイル:

```text
A_********_2.txt
例: A_20260531_2.txt
```

用途:

- `sales_package` を作る。
- PTP / 箱 / 元梱の本物の GS1 GTIN を保持する。

必須カラム:

| 入力カラム | 意味 | 保存先 |
|---|---|---|
| 2 | 製造販売業者 | `SalesPackage.maker` |
| 3 | 販売名 | `SalesPackage.packageName` |
| 4-6 | 販売名50音 | `drugNameKana1/2/3` |
| 8 | 個別医薬品コード(YJ) | `SalesPackage.yjCode` |
| 12 | 規格 | `packageSpec` |
| 15 | 区分名 | `drugCategory` |
| 16 | 剤形 | `dosageForm` |
| 22 | 包装形態 | `packageForm` |
| 23 | 包装単位数 | `packageUnitCount` |
| 24 | 包装単位数単位 | `packageUnitName` |
| 25 | 総数量数 | `quantity` |
| 26 | 総数量数単位 | `unit` |
| 27 | 1連の包装数 | `containerCount` |
| 29 | 調剤包装単位コード | `gtin` |
| 32 | 販売包装単位コード | `gtinSales` |
| 33 | 元梱包装単位コード | `gtinCase` |
| 34 | 物流用JANコード | `janCode` |

文字コード:

- UTF-8 BOM があれば UTF-8。
- BOM なしでも strict UTF-8 として成立すれば UTF-8。
- それ以外は Shift_JIS。

重要:

- `A_20260531_2.txt` は UTF-8 として確認済み。
- Shift_JIS 固定で読むと、センノシドNIGなどが文字化けする。
- 文字化けした `sales_package` を使うと、メーカー判定と表示が壊れる。

確認済み正常データ:

```text
gtin=04987376861687
gtinSales=14987376861653
gtinCase=24987376861650
yjCode=2354003F2014
packageName=センノシド錠１２ｍｇ「ＮＩＧ」
maker=日医工
```

## 3.3 データ消失時の再構築手順

アプリ再インストール、DB破棄、Room destructive migration、端末交換などでデータが消えた場合は、以下の順で復旧する。

### 必要ファイル

最低限、以下2ファイルが必要である。

```text
MEDIS********_h.txt
A_********_2.txt
```

例:

```text
MEDIS20260531_h.txt
A_20260531_2.txt
```

### ファイルの役割

| ファイル | 取り込み先 | 必須理由 |
|---|---|---|
| `MEDIS********_h.txt` | `drug_master` | 薬品基本情報、HOT13、薬価基準収載コード、個別YJコードを作る |
| `A_********_2.txt` | `sales_package` | PTP、箱、元梱の本物の GS1 GTIN を作る |

片方だけでは不十分である。

- `MEDIS********_h.txt` だけでは、箱GTINやPTP GTINから薬品を引けない。
- `A_********_2.txt` だけでは、薬品基本情報や帳票監査・調剤モードで使う `drug_master` が不足する。
- PTP/箱照合は `GTIN -> sales_package -> yjCode -> drug_master` の順で成立するため、2テーブルが必要。

### アプリ内での復旧手順

1. アプリを起動する。
2. `MEDISマスター取込` を開く。
3. `MEDIS HOT ファイル` 欄で `MEDIS********_h.txt` を選択する。
4. `販売名ファイル` 欄で `A_********_2.txt` を選択する。
5. `選択済みのファイルを取り込む` を押す。
6. 両方選択していれば、`drug_master` と `sales_package` が順に再作成される。
7. 片方だけ選択して取り込むこともできるが、実運用前には必ず両方を最新化する。

### 正常件数の目安

版によって件数は変わるが、2026-05-31 版では以下が目安である。

```text
drug_master: 57,000件前後
sales_package: 50,000件前後
```

実機確認例:

```text
MEDIS HOT: 57,277行 取込、2,059行 スキップ
販売名ファイル: 50,638行 取込、0行 スキップ
```

### DB確認手順

端末から DB を pull して確認する場合は、WAL/SHM も同時に取得する。

```powershell
adb exec-out run-as com.example.yakuzaiapp cat databases/yakuzaiapp.db > C:\Users\fyuuc\yakuzaiapp_after_import.db
adb exec-out run-as com.example.yakuzaiapp cat databases/yakuzaiapp.db-wal > C:\Users\fyuuc\yakuzaiapp_after_import.db-wal
adb exec-out run-as com.example.yakuzaiapp cat databases/yakuzaiapp.db-shm > C:\Users\fyuuc\yakuzaiapp_after_import.db-shm
```

`sqlite3` で `file is not a database (26)` が出る場合は、pull が壊れているか、PowerShell の誤ったリダイレクトで余計な文字が混入している。`adb exec-out` を使い、DB本体・WAL・SHMを同じ時刻で取り直す。

確認SQL:

```powershell
sqlite3 C:\Users\fyuuc\yakuzaiapp_after_import.db "PRAGMA integrity_check;"
sqlite3 C:\Users\fyuuc\yakuzaiapp_after_import.db "SELECT COUNT(*) FROM drug_master;"
sqlite3 C:\Users\fyuuc\yakuzaiapp_after_import.db "SELECT COUNT(*) FROM sales_package;"
```

センノシドNIG確認:

```powershell
sqlite3 C:\Users\fyuuc\yakuzaiapp_after_import.db "SELECT gtin, gtinSales, gtinCase, yjCode, packageName, maker FROM sales_package WHERE gtin='04987376861687' OR gtinSales='14987376861653' OR gtinCase='24987376861650';"
```

期待:

```text
04987376861687|14987376861653|24987376861650|2354003F2014|センノシド錠１２ｍｇ「ＮＩＧ」|日医工
```

文字化けしている場合:

- `SalesNameCsvParser.detectCharset()` が壊れている。
- `A_********_2.txt` を Shift_JIS 固定で読んでいる可能性が高い。
- Logcat 上の文字化けだけでなく、DB の `sales_package.packageName` / `maker` を sqlite3 で確認する。

### 復旧後の最低限の実機確認

1. 薬品検索でセンノシドNIGなどが日本語表示される。
2. PTPスキャンで `04987376861687` がセンノシドNIGに解決される。
3. 箱スキャンで `14987376861653` がセンノシドNIGに解決される。
4. 調剤モードで PTP/箱とも `sales_package` 経由で確認できる。
5. 充填モードで1回目薬品スキャン、2回目充填先コードスキャンが成立する。

## 4. Room データベース

DB名:

```text
yakuzaiapp.db
```

現在の Room version:

```text
8
```

マイグレーション方針:

```kotlin
fallbackToDestructiveMigration()
```

理由:

- マスターは外部ファイルから再取込できる。
- 主キー変更、列追加、nullability変更が多く、非破壊migrationより再取込の方が安全。

主要テーブル:

- `drug_master`
- `sales_package`
- `drug_preference`
- `audit_drug_preference`
- `matching_history`
- `matching_detail`
- `staff_master`

### 4.1 drug_master

Entity:

```text
app/src/main/java/com/example/yakuzaiapp/data/local/entity/DrugMaster.kt
```

主キー:

```text
hot13
```

注意:

- `DrugMaster.gtin` は現行新規取込では HOT13 を入れる。
- ここを本物の GS1 GTIN と誤解しない。
- `drugCode` は薬価基準収載医薬品コード。
- `yjCode` は個別医薬品コード。空なら薬価コードを入れる。

### 4.2 sales_package

Entity:

```text
app/src/main/java/com/example/yakuzaiapp/data/local/entity/SalesPackage.kt
```

主キー:

```text
packageKey = gtin|gtinSales|gtinCase
```

重要カラム:

- `gtin`: 調剤包装単位コード
- `gtinSales`: 販売包装単位コード
- `gtinCase`: 元梱包装単位コード
- `yjCode`: 個別医薬品コード
- `packageName`: 販売名
- `maker`: 製造販売業者

## 5. MEDIS 取込画面

画面:

```text
app/src/main/java/com/example/yakuzaiapp/ui/medis/MedisImportScreen.kt
app/src/main/java/com/example/yakuzaiapp/ui/medis/MedisImportViewModel.kt
```

意図するUI:

- MEDIS HOT ファイルを選ぶ。
- 販売名ファイルを選ぶ。
- 取り込みボタンは1つ。
- 2つ選んでいれば2つ取り込む。
- 1つだけ選んでいれば1つだけ取り込む。

ファイル名バリデーション:

```text
MEDIS HOT: MEDIS\d{8}_h.txt
販売名: A_\d{8}_2.txt
```

重要:

- MEDIS HOT 選択欄で `A_********_2.txt` を選ばせない。
- 販売名選択欄で `MEDIS********_h.txt` を選ばせない。
- 選んだ瞬間に取り込みを開始しない。
- ユーザーが「選択済みのファイルを取り込む」を押してから取り込む。

## 6. バーコード読取

### 6.1 PTP / 箱 GS1

Analyzer:

```text
app/src/main/java/com/example/yakuzaiapp/util/BarcodeAnalyzer.kt
```

PTP / 箱モード:

- zxing-cpp `BarcodeReader`
- 対応形式:
  - `DATA_BAR`
  - `DATA_BAR_LIMITED`
  - `DATA_BAR_EXPANDED`
  - `CODE_128`
  - `DATA_MATRIX`
  - `QR_CODE`
  - `EAN_13`
  - `EAN_8`
  - `ITF`
  - `UPC_A`
  - `UPC_E`
  - `CODE_39`
- `tryHarder=true`
- `tryRotate=true`
- `tryInvert=true`
- `tryDownscale=true`

確認済みログ例:

```text
zxing-ptp format=DATA_BAR_LIMITED text-head='0114987376861653'
zxing-ptp format=DATA_BAR_LIMITED text-head='0104987376861687'
```

### 6.2 GTIN 正規化

実装:

```text
app/src/main/java/com/example/yakuzaiapp/util/GtinNormalizer.kt
```

ルール:

- 全角数字を半角化。
- 数字以外を除去。
- `01` で始まり16桁以上なら、2桁目以降14桁を採用。
- 14桁ならそのまま。
- 13桁なら先頭に `0` を補う。
- チェックデジットが不正なら `null`。

例:

```text
0114987376861653 -> 14987376861653
0104987376861687 -> 04987376861687
```

## 7. GTIN から薬品を解決する流れ

Repository:

```text
app/src/main/java/com/example/yakuzaiapp/repository/DrugMasterRepository.kt
```

`findByAnyGtin(code)` の順序:

1. `salesPackageDao.findByGtin(code)`
2. `salesPackageDao.findBySalesPackageGtin(code)`
3. `salesPackageDao.findByCaseGtin(code)`
4. `SalesPackage.packageName` があれば、`drugMasterDao.findByExactDrugOrPackageName(packageName)` で候補を取り、`yjCode` または `drugCode` が一致する `DrugMaster` を優先
5. `drugMasterDao.findByYjCode(yjCode)`
6. 旧互換として `drugMasterDao.findByAnyGtin(code)`

重要:

- 販売名ファイルが文字化けしていると、4のメーカー・販売名優先解決が壊れる。
- ただし YJ 解決自体は `sales_package.yjCode` が正しければ成立する。

## 8. JAHIS QR

### 8.1 検出

JAHIS QR は ML Kit BarcodeScanning を使う。

設定:

- `Barcode.FORMAT_QR_CODE`
- `InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)`

デコード:

- `rawBytes` があれば Windows-31J でデコード。
- `rawBytes` がなければ `rawValue` を ISO-8859-1 -> Windows-31J で復元を試す。
- それもだめなら `rawValue` を使う。

### 8.2 Assembler

実装:

```text
app/src/main/java/com/example/yakuzaiapp/domain/jahis/JahisQrAssembler.kt
```

対応ヘッダ:

- `JAHISTC01`
- `JAHISTC02`
- `JAHISTC07`

Completeness:

- 先頭が `JAHISTC` であること。
- `201,<RP番号>,...` に対応する `301,<RP番号>,...` が存在すること。
- 終端改行は要求しない。

フラグメント結合:

- 左位置 `left` で並べる。
- ヘッダあり断片を先頭側へ置く。
- 前断片が改行で終わらず、次断片がレコード開始なら `\n` を挿入。
- それ以外は空文字で連結。

### 8.3 Parser

実装:

```text
app/src/main/java/com/example/yakuzaiapp/data/jahis/JahisQrParser.kt
```

正規化:

- `\u001A` を削除。
- `\r\n` と `\r` を `\n` に統一。
- 空行は除外。

TC01 / TC07 の `201`:

| cols | 意味 |
|---|---|
| 1 | RP番号 |
| 2 | 薬品名 |
| 3 | 数量 |
| 4 | 単位 |
| 5 | コード種別 |
| 6 | 薬品コード |

TC02 の `201`:

| cols | 意味 |
|---|---|
| 4 | コード種別 |
| 5 | 薬品コード |
| 6 | 薬品名 |
| 7 | 数量 |
| 9 または 8 | 単位 |

コード種別:

- `1`: NONE
- `2`: RECEIPT_COMPUTER
- `3`: MHLW
- `4`: YJ
- `6`: HOT
- `7`: GENERIC_MHLW
- `9`: UNKNOWN

## 9. 調剤モード

### 9.1 期待リスト生成

実装:

```text
app/src/main/java/com/example/yakuzaiapp/domain/dispensing/ExpectedListBuilder.kt
```

JAHIS drug から `ExpectedDrugItem` を作る。

照合順:

1. `drug.drugCode` を NFKC 正規化、trim、uppercase、空白/ハイフン除去。
2. `DrugMasterDao.findByYjCode()` を実行。
3. ヒットしない場合は薬品名検索へフォールバック。
4. `matchedYjCode = matchedMaster.yjCode ?: matchedMaster.drugCode`
5. 分包機対象外フラグがあれば初期ステータス `PACKING_MACHINE`。
6. それ以外は `UNCHECKED`。

重要:

- `drugCodeType` が YJ でなくても、まずコード照合する。
- JAHIS が `MHLW` としていても、中身がYJ相当なら拾うため。

### 9.2 PTP / 箱スキャン

実装:

```text
app/src/main/java/com/example/yakuzaiapp/ui/dispensing/DispensingViewModel.kt
```

流れ:

1. `normalizeGtin(raw)`
2. `DrugMasterLookup.findByAnyGtin(normalizedGtin)`
3. `drug.yjCode` と `ExpectedDrugItem.matchedYjCode` を完全一致。
4. 見つからなければ `drug.drugCode` でも照合。
5. 同じ薬が複数行ある場合は `UNCHECKED` の行を優先。
6. 全て確認済みなら `AlreadyConfirmed`。
7. 分包機対象外なら `PackingMachine`。
8. 未確認なら `Success` として `CONFIRMED` にする。

重要:

- 箱GTINを `isPackageBarcode` で弾いてはいけない。
- 旧仕様の「箱バーコード未対応」には戻さない。

## 10. 帳票監査モード

### 10.1 OCR

実装:

```text
app/src/main/java/com/example/yakuzaiapp/ui/audit/AuditScanScreen.kt
app/src/main/java/com/example/yakuzaiapp/ui/audit/AuditScanViewModel.kt
app/src/main/java/com/example/yakuzaiapp/domain/audit/DocumentOcrParser.kt
```

使用ライブラリ:

```text
com.google.mlkit:text-recognition-japanese:16.0.0
```

設計:

- カメラで帳票を撮影する。
- ML Kit Japanese OCR を実行する。
- OCR 結果は行単位で処理する。
- 薬品名らしい行だけを `DetectedDrugLine` とする。
- 数量列、棚番、日時、注意事項、短い単位のみの行は除外する。

### 10.2 薬品候補生成

実装:

```text
app/src/main/java/com/example/yakuzaiapp/domain/audit/DrugMasterMatcher.kt
app/src/main/java/com/example/yakuzaiapp/domain/audit/DrugIdentity.kt
```

設計:

- OCR名を正規化する。
- 全角英数字、全角ピリオド、全角空白を半角側へ寄せる。
- 小書き仮名の揺れを吸収する。
- `drugName`, `packageName`, `drugNameKana1/2/3`, `alias` を検索対象にする。
- `yjCode` 単位で `DrugIdentity` に集約する。
- 包装違いは1候補にまとめる。
- 複数メーカーや複数候補が残る場合は `AMBIGUOUS`。
- ユーザーが選択した候補は `audit_drug_preference` に学習する。

重要:

- 薬品検索画面で見つかる採用薬候補を、帳票監査モードの候補から落とさない。
- ロキソプロフェンNa錠60mg「トーワ」のように `packageName` 側にある候補も残す。
- センノシドNIGのように複数メーカーがある薬品は学習または手動選択で補う。

### 10.3 帳票監査 PTP / 箱照合

実装:

```text
app/src/main/java/com/example/yakuzaiapp/ui/audit/AuditPtpScanViewModel.kt
```

流れ:

1. 調剤モードと同じ `DrugMasterLookup.findByAnyGtin()` を使う。
2. `DrugMaster.yjCode` と帳票側 `DrugIdentity.yjCode` を比較する。
3. 必要に応じて `drugCode` でもフォールバックする。
4. 一致した行を確認済みにする。

## 11. 画面構成

主要ルート:

- ホーム
- MEDISマスター取込
- 薬品検索
- JAHIS QR読取
- 調剤モード
- 帳票監査撮影
- 帳票監査結果
- 帳票監査 PTP/箱スキャン
- 充填モード

Navigation:

```text
app/src/main/java/com/example/yakuzaiapp/ui/navigation/AppNavigation.kt
```

### 11.0.1 2026-07-06 追記: タブバーから帳票監査へ入る時の内部ホーム経由

詳細仕様:

```text
docs/spec-camera-navigation-2026-07-06.md
```

背景:

- 帳票監査撮影画面は `Preview + ImageCapture` を使う。
- 調剤モード、充填モード、共通スキャナ画面はカメラを継続使用する。
- カメラ使用中画面からタブバーで帳票監査へ直接遷移すると、前画面の CameraX use case 解放と帳票監査側の `ImageCapture` 起動が重なり、起動が遅くなる。
- ユーザーがいったんホームへ戻ってから帳票監査へ入ると、ホーム表示中にカメラ解放が進むためスムースに起動する。

復旧時に守る仕様:

- ユーザーに明示的なホーム経由を求めない。
- タブバーで帳票監査を押した場合、アプリ内部で `Routes.HOME` へ移動し、短時間待ってから `Routes.AUDIT_SCAN` へ移動する。
- ホームから帳票監査へ入る経路は、従来通り直接 `Routes.AUDIT_SCAN` へ移動する。

実装:

```text
app/src/main/java/com/example/yakuzaiapp/ui/navigation/AppNavigation.kt
```

主要要素:

```text
CAMERA_RELEASE_NAVIGATION_DELAY_MS = 250L
navigateToAuditAfterCameraRelease()
```

この処理を使う主な経路:

- `FillModeScreen.onAuditClick`
- `DispensingScreen.onAuditClick`
- `DispensingPtpScanScreen.onAuditClick`
- `ScanScreen.onAuditClick`

検証:

```powershell
.\gradlew.bat :app:compileDebugKotlin --no-daemon
```

実機では以下を確認する。

- ホーム -> 帳票監査: 従来通りスムースに起動する。
- 充填 -> タブバー帳票監査: フリーズせず起動する。
- 調剤 -> タブバー帳票監査: フリーズせず起動する。
- 調剤 PTP / 共通スキャナ -> タブバー帳票監査: 前画面のカメラが残らない。

## 11.1 充填モード

実装:

```text
app/src/main/java/com/example/yakuzaiapp/ui/fill/FillModeScreen.kt
app/src/main/java/com/example/yakuzaiapp/ui/fill/FillModeViewModel.kt
```

目的:

- 自動錠剤分包機のカセット、または粉薬瓶へ薬品を補充する前に、補充薬品と充填先コードが一致しているか確認する。

流れ:

1. 充填する薬品の PTP または箱をスキャンする。
2. `DrugMasterLookup.findByAnyGtin()` で `sales_package.gtin / gtinSales / gtinCase` を優先して薬品を解決する。
3. 解決できたら音と振動を出す。
4. 薬品名を画面下部に赤背景・白文字で大きく表示する。
5. 2.5秒のガード時間を置く。同じ箱が画面内に残ったまま2回目スキャンとして処理されることを防ぐため。
6. 白地の案内カードに `充填先のカセットまたは瓶のコードをスキャンしてください` と表示する。
7. 充填先カセットまたは瓶のコードをスキャンする。
8. 選択済み薬品の `yjCode` または `drugCode` と一致すれば `充填OK` とする。
9. `充填OK` 後はカメラプレビューを止め、以後の読み取りで結果を上書きしない。
10. 完了画面の下部には `次の薬品照合` と `戻る` の2ボタンを出す。

UIで守ること:

- 上部タイトルは `充填モード` のみ。戻るリンクは表示しない。
- 充填先スキャン中、薬品名は上部タイトルには表示しない。下部の赤背景ラベルだけに表示する。
- 案内文はカメラ映像上に直接重ねず、白地のカードに出す。
- 充填先スキャン中は同じ案内文を太字と通常文字で重複表示しない。
- 完了後はカメラを止め、下部の赤ラベルも消す。

実機で確認済み:

- 1回目スキャン後に音と振動が出る。
- 1回目スキャン後、画面下部に赤背景で薬品名が出る。
- 箱をそのままにしても即 `充填OK` にならない。
- 2.5秒後に充填先コードを読ませると一致判定できる。
- `充填OK` 後に別コードを読んでも `マスターに見つかりません` に上書きされない。

残課題:

- 期限（AI=17）の警告やブロック仕様は未実装。
- 充填履歴の保存有無は未確定。
- カセットID管理を別途持つか、GS1/YJ照合だけにするかは未確定。

## 12. 重要な依存関係

`app/build.gradle.kts` の重要依存:

```kotlin
implementation("io.github.zxing-cpp:android:2.3.0")
implementation("com.google.zxing:core:3.5.3")
implementation("com.google.mlkit:barcode-scanning:17.3.0")
implementation("com.google.mlkit:text-recognition-japanese:16.0.0")

implementation("androidx.camera:camera-core:1.3.4")
implementation("androidx.camera:camera-camera2:1.3.4")
implementation("androidx.camera:camera-lifecycle:1.3.4")
implementation("androidx.camera:camera-view:1.3.4")

implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
ksp("androidx.room:room-compiler:2.6.1")
```

JDK:

```text
17
```

Android Gradle Plugin 8.5.2 系では JDK 17 を使う。

## 13. 検証コマンド

作業ディレクトリ:

```powershell
cd C:\Users\fyuuc\Dropbox\codex\アプリ開発\GX_handy(GE)_VER2
```

テスト:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

ビルド:

```powershell
.\gradlew.bat :app:assembleDebug
```

インストール:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

起動:

```powershell
adb shell monkey -p com.example.yakuzaiapp -c android.intent.category.LAUNCHER 1
```

ログ:

```powershell
adb logcat -c
adb logcat | Select-String "BarcodeAnalyzer|AuditPtpScan|SalesNameCsvParser|MedisImportRepository|JahisQrParser|JahisQrAssembler"
```

## 14. DB確認手順

アプリDBは WAL を含めて取得する。

```powershell
adb exec-out run-as com.example.yakuzaiapp cat databases/yakuzaiapp.db > C:\Users\fyuuc\yakuzaiapp_after_import.db
adb exec-out run-as com.example.yakuzaiapp cat databases/yakuzaiapp.db-wal > C:\Users\fyuuc\yakuzaiapp_after_import.db-wal
adb exec-out run-as com.example.yakuzaiapp cat databases/yakuzaiapp.db-shm > C:\Users\fyuuc\yakuzaiapp_after_import.db-shm
```

ファイルが壊れていないか:

```powershell
sqlite3 C:\Users\fyuuc\yakuzaiapp_after_import.db "PRAGMA integrity_check;"
```

件数:

```powershell
sqlite3 C:\Users\fyuuc\yakuzaiapp_after_import.db "SELECT COUNT(*) FROM drug_master;"
sqlite3 C:\Users\fyuuc\yakuzaiapp_after_import.db "SELECT COUNT(*) FROM sales_package;"
```

センノシドNIG確認:

```powershell
sqlite3 C:\Users\fyuuc\yakuzaiapp_after_import.db "SELECT gtin, gtinSales, gtinCase, yjCode, packageName, maker FROM sales_package WHERE gtin='04987376861687' OR gtinSales='14987376861653' OR gtinCase='24987376861650';"
```

期待:

```text
04987376861687|14987376861653|24987376861650|2354003F2014|センノシド錠１２ｍｇ「ＮＩＧ」|日医工
```

文字化けしている場合は、販売名ファイルの文字コード判定が壊れている。

## 15. 実機確認シナリオ

### 15.1 マスター取込

1. MEDISマスター取込画面を開く。
2. `MEDIS********_h.txt` を選ぶ。
3. `A_********_2.txt` を選ぶ。
4. 「選択済みのファイルを取り込む」を押す。
5. 両方選んだ場合は両方取り込まれる。
6. 片方だけ選んだ場合は片方だけ取り込まれる。

期待:

- MEDIS HOT は約5万件台。
- 販売名ファイルも約5万件台。
- `A_20260531_2.txt` 取込ログに `charset=UTF-8` が出る。
- 事前に登録した院内製剤・材料がMEDIS更新後も残る。

### 15.1.1 利用者登録マスター

1. ホーム右上メニューから `院内製剤・材料マスター` を開く。
2. 帳票に記載される名称を入力する。
3. GTINまたは施設独自バーコードを入力する。
4. 必要に応じて規格・包装と区分を入力する。
5. `マスターに登録` を押す。
6. 登録済み一覧に表示されることを確認する。

期待:

- 名称による帳票監査の手動検索候補に表示される。
- 登録バーコードを帳票PTPスキャンで読むと確認済みになる。
- MEDIS手動更新・自動更新後も登録が残る。
- 削除後は候補とバーコード照合から消える。

### 15.2 調剤モード

1. JAHIS QR を読む。
2. 処方リストが出る。
3. PTP を読む。
4. 箱を読む。

期待:

- PTPも箱も `sales_package` 経由で薬品に解決される。
- 箱バーコードで未対応メッセージは出ない。
- 同じ薬が複数行ある場合、未確認行が優先して確認される。

### 15.3 帳票監査モード

1. 帳票を撮影する。
2. OCR薬品リストを確認する。
3. 候補が複数ある薬は手動選択する。
4. PTP/箱スキャンへ進む。
5. PTPまたは箱を読む。

期待:

- 帳票側の `DrugIdentity.yjCode` とスキャン側の YJ が一致すれば確認済みになる。
- センノシドNIGなど、販売名ファイルのメーカー情報が日本語で表示される。
- 院内製剤・材料は利用者登録した名称を選択でき、登録バーコードで照合できる。

### 15.5 2026-07-16 実機確認

- 対象端末: Pixel 4a
- 既存の署名違いアプリをアンインストールしてdebug APKを新規インストール
- `com.example.yakuzaiapp` のインストール成功
- ランチャーからのアプリ起動成功
- 利用者登録マスターの動作確認成功
- `testDebugUnitTest`: 226件成功
- `assembleDebug`: 成功

### 15.4 充填モード

1. 充填モードを開く。
2. 補充する薬品の PTP または箱をスキャンする。
3. 音と振動、下部の赤背景薬品名表示を確認する。
4. 2.5秒待つ。
5. 充填先カセットまたは瓶のコードをスキャンする。

期待:

- 一致時は `充填OK` になる。
- 完了後はカメラが止まる。
- 下部に `次の薬品照合` と `戻る` が表示される。
- 完了後に別コードを読んでも結果が上書きされない。
- 充填先スキャン中の未登録ノイズで `マスターに見つかりませんでした` を乱発しない。

## 16. 既知の落とし穴

### 16.1 sales_package の文字化け

症状:

```text
繧ｻ繝ｳ繝弱す繝...
```

原因:

- UTF-8 の `A_********_2.txt` を Shift_JIS 固定で読んでいる。

対応:

- `SalesNameCsvParser.detectCharset()` を維持する。
- DB上で日本語保存されているか SQL で確認する。

### 16.2 DB pull 失敗

症状:

```text
file is not a database (26)
```

原因:

- `adb shell run-as ... cat ... > file` のように、端末側出力が文字化け/変換されている。
- WAL/SHM を含めずに確認している。

対応:

- `adb exec-out run-as ... cat ... > file` を使う。
- `.db`, `.db-wal`, `.db-shm` を同じ場所に取得する。

### 16.3 Logcat の文字化け

PowerShell 表示だけが文字化けしていることがある。DB自体が文字化けしているかは sqlite3 で確認する。

### 16.4 箱GTINを弾く退行

`isPackageBarcode()` は判定用に残っていてもよいが、調剤モードや帳票監査モードで箱GTINを未対応として弾いてはいけない。

### 16.5 JAHIS QR の終端チェック

JAHIS 発行元によっては末尾改行がない。`UNTERMINATED` を理由に失敗させない。

### 16.6 充填モードの二重読み取り

症状:

- 1回目の薬品スキャン後、同じ箱が画面に残っているだけで2回目スキャンも成立してしまう。

対応:

- 1回目成功後に2.5秒のガード時間を入れる。
- ガード中の読み取りは無視する。

### 16.7 充填OK後の結果上書き

症状:

- `充填OK` の後に別コードを読んで `マスターに見つかりません` に上書きされる。

対応:

- 完了状態では以後のスキャン入力を無視する。
- 完了画面ではカメラプレビューを止める。

### 16.6 JAHIS QR の改行連結

単純な `"\n"` 固定連結に戻すと、ラメルテオンのような薬品名途中分割が壊れる。

単純な `""` 固定連結に戻すと、ランソプラゾールのようなレコード境界分割が壊れる。

## 17. 復旧時に優先して見るファイル

### DB / 取込

- `app/src/main/java/com/example/yakuzaiapp/data/local/AppDatabase.kt`
- `app/src/main/java/com/example/yakuzaiapp/data/local/entity/DrugMaster.kt`
- `app/src/main/java/com/example/yakuzaiapp/data/local/entity/SalesPackage.kt`
- `app/src/main/java/com/example/yakuzaiapp/data/local/dao/DrugMasterDao.kt`
- `app/src/main/java/com/example/yakuzaiapp/data/local/dao/SalesPackageDao.kt`
- `app/src/main/java/com/example/yakuzaiapp/data/medis/MedisCsvParser.kt`
- `app/src/main/java/com/example/yakuzaiapp/data/medis/SalesNameCsvParser.kt`
- `app/src/main/java/com/example/yakuzaiapp/data/medis/MedisImportRepository.kt`
- `app/src/main/java/com/example/yakuzaiapp/data/medis/SalesNameImportRepository.kt`
- `app/src/main/java/com/example/yakuzaiapp/data/medis/MedisAutoUpdateCoordinator.kt`
- `app/src/main/java/com/example/yakuzaiapp/data/medis/MedisAutoUpdateState.kt`
- `app/src/main/java/com/example/yakuzaiapp/data/medis/MedisRemoteDataSource.kt`
- `app/src/main/java/com/example/yakuzaiapp/data/medis/MedisUpdateMetadataStore.kt`
- `app/src/main/java/com/example/yakuzaiapp/data/medis/NetworkMonitor.kt`
- `app/src/main/java/com/example/yakuzaiapp/data/medis/RoomMedisMasterImporter.kt`
- `app/src/main/java/com/example/yakuzaiapp/ui/medis/MedisImportViewModel.kt`
- `app/src/main/java/com/example/yakuzaiapp/ui/medis/MedisImportScreen.kt`

### バーコード / GTIN

- `app/src/main/java/com/example/yakuzaiapp/util/BarcodeAnalyzer.kt`
- `app/src/main/java/com/example/yakuzaiapp/util/GtinNormalizer.kt`
- `app/src/main/java/com/example/yakuzaiapp/repository/DrugMasterRepository.kt`

### JAHIS / 調剤

- `app/src/main/java/com/example/yakuzaiapp/domain/jahis/JahisQrAssembler.kt`
- `app/src/main/java/com/example/yakuzaiapp/data/jahis/JahisQrParser.kt`
- `app/src/main/java/com/example/yakuzaiapp/domain/dispensing/ExpectedListBuilder.kt`
- `app/src/main/java/com/example/yakuzaiapp/ui/dispensing/DispensingViewModel.kt`
- `app/src/main/java/com/example/yakuzaiapp/ui/dispensing/DispensingScreen.kt`

### 帳票監査

- `app/src/main/java/com/example/yakuzaiapp/domain/audit/DocumentOcrParser.kt`
- `app/src/main/java/com/example/yakuzaiapp/domain/audit/DrugMasterMatcher.kt`
- `app/src/main/java/com/example/yakuzaiapp/domain/audit/DrugIdentity.kt`
- `app/src/main/java/com/example/yakuzaiapp/ui/audit/AuditScanViewModel.kt`
- `app/src/main/java/com/example/yakuzaiapp/ui/audit/AuditResultScreen.kt`
- `app/src/main/java/com/example/yakuzaiapp/ui/audit/AuditPtpScanViewModel.kt`
- `app/src/main/java/com/example/yakuzaiapp/ui/audit/AuditPtpScanScreen.kt`

## 18. 最低限の回帰テスト

ユニットテストで必ず守ること:

- `normalizeGtin()` が AI=01 付き GS1 を正しくGTIN-14化する。
- `SalesNameCsvParser` が UTF-8 の `A_20260531_2.txt` を文字化けさせない。
- `MedisCsvParser` が `MEDIS********_h.txt` の col6/col7 を正しく `drugCode/yjCode` に入れる。
- `DrugMasterRepository.findByAnyGtin()` が `sales_package.gtin/gtinSales/gtinCase` を優先する。
- 調剤モードで箱GTINも確認できる。
- 同一薬複数行では未確認行を優先する。
- JAHIS フラグメント境界で RP2 と RP5 が落ちない。
- 帳票監査モードで `packageName` も候補検索対象になる。
- MEDIS 自動更新は、7日以内の自動確認をスキップし、手動更新ではスロットルを無視する。
- MEDIS 自動更新は、成功後だけホームページ確認成功と取込成功を記録する。
- 日本語を含む作業パスでも `:app:testDebugUnitTest` が `ClassNotFoundException` で落ちない。

実行:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

2026-06-27 時点の確認済み結果:

```text
:app:testDebugUnitTest -> 150 tests, failures=0, errors=0, skipped=0
:app:assembleDebug -> BUILD SUCCESSFUL
```

2026-07-01 時点の確認済み結果:

```text
:app:testDebugUnitTest -> 184 tests, failures=0, errors=0, skipped=0
:app:assembleDebug -> BUILD SUCCESSFUL
```

## 19. 次に壊れた時の切り分け順

### PTP/箱が読めない

1. `BarcodeAnalyzer` のログに raw が出ているか。
2. `normalizeGtin(raw)` 後の値が正しいか。
3. `sales_package` に `gtin / gtinSales / gtinCase` のどれかで存在するか。
4. `sales_package.yjCode` が空でないか。
5. `drug_master` に同じ `yjCode` または `drugCode` があるか。
6. 調剤/帳票側リストの `matchedYjCode` または `DrugIdentity.yjCode` と一致しているか。

### 表示が文字化けする

1. Logcat表示だけか、DB自体かを分ける。
2. sqlite3 で `sales_package.packageName, maker` を確認する。
3. DBが文字化けしていれば `SalesNameCsvParser.detectCharset()` を確認する。

### JAHIS QRで薬が欠落する

1. `JahisQrAssembler` の結合ロジックが動的区切りになっているか。
2. `checkCompleteness()` が終端改行を要求していないか。
3. `JahisQrParser` が `JAHISTC07` をTC01系として扱っているか。
4. `201` と `301` の RP番号が揃っているか。

## 20. この設計図の位置づけ

復旧時は以下の優先順位で読む。

1. 本書
2. `docs/spec_v0.3.md`
3. `docs/yj-code-investigation-2026-06-10.md`
4. `docs/jahis-parser-field-boundary-2026-06-10.md`
5. 関連テストコード

本書と古い調査メモが矛盾する場合は、本書を優先する。

