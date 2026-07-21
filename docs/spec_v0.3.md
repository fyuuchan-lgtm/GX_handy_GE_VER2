# YakuzaiApp Spec v0.3

## 変更履歴（2026-07-22）: 帳票監査の数量読み取り設定

- ホーム画面右上メニューの「設定」に、帳票監査の「数量を読み取る」切替を追加する。
- 初期値はオンとし、帳票の薬品名と同じ行の数量欄をOCR結果から対応付けて表示する。
- オフの場合は、薬品名の照合だけを行い、OCRで読み取った数量およびマスターの包装規格から数量を補完しない。
- 数量が未取得の帳票監査リストをPTPスキャンへ進めた場合、数量欄は空欄で表示する。

## 1. 目的
YakuzaiApp は、MEDIS マスタ取込、PTP スキャン、JAHIS お薬手帳 QR 読取、期待値リスト表示、分包機状態の管理を行う Android アプリである。

本仕様書 v0.3 は v0.2A (2026/06/09) に対し、以下を追加・更新したものである。

- Phase B-2 〜 Phase B-5 で試行した JAHIS QR 分割対応の試行錯誤と教訓
- Phase B-6 での ML Kit ベースへの JAHIS QR 検出パイプライン全面書き直し
- Phase B-6.1 での Assembler 終端チェック撤廃
- 3モード構成（調剤モード／帳票監査モード／充填モード）の現行仕様整理
- Phase C-1 〜 Phase C-4 ロードマップ
- Phase C-3 帳票監査モードの OCR、薬品マスタ候補生成、PTP 突合の実装状況
- Phase C-4 充填モードの2段階スキャン、音・振動、完了UIの実装状況
- MEDIS公開マスターにない院内製剤・医療材料の利用者登録マスター

最終更新日: 2026-07-16

---

## 1.1 2026-06-27 更新: MEDIS取込・PTP/箱GTIN突合

### 2.1 マスター取込の現在仕様

YakuzaiApp は、薬品基本情報と包装GTIN情報を分けて管理する。現行の取込 UI は MEDIS HOT と販売名ファイルの 2 つのファイル選択欄を持ち、取り込みボタンは 1 つである。

- `MEDIS********_h.txt`
  - 取得元: MEDIS 標準マスターの HOT コード関連ファイル
  - 取り込み先: `drug_master`
  - 主用途: 薬品名、HOT13、薬価基準収載医薬品コード、個別医薬品コード(YJ)
  - 主要マッピング:
    - HOT13 -> `DrugMaster.hot13`
    - 薬価基準収載医薬品コード -> `DrugMaster.drugCode`
    - 個別医薬品コード(YJ) -> `DrugMaster.yjCode`
    - 個別医薬品コードが空の場合は、薬価基準収載医薬品コードを `yjCode` にも入れる
- `A_********_2.txt`
  - 取得元: 販売名・包装単位系ファイル
  - 取り込み先: `sales_package`
  - 主用途: PTP/販売包装/元梱包装の GS1 GTIN、販売名、メーカー、包装情報
  - 主要マッピング:
    - 調剤包装単位コード -> `SalesPackage.gtin`
    - 販売包装単位コード -> `SalesPackage.gtinSales`
    - 元梱包装単位コード -> `SalesPackage.gtinCase`
    - 個別医薬品コード(YJ) -> `SalesPackage.yjCode`

2つのファイルは、取り込み時に1つの巨大テーブルへ結合しない。Room 上では `drug_master` と `sales_package` を分けて保持し、照合時に `sales_package` から GTIN -> YJ を解決して `drug_master` 側の薬品情報へ接続する。

#### 取込の運用ルール
- MEDIS HOT は `MEDIS********_h.txt` を選択して `drug_master` に取り込む
- 販売名ファイルは `A_********_2.txt` を選択して `sales_package` に取り込む
- 取り込みボタンは 1 つで、選択済みのファイルだけを取り込む
- 両方選択している場合は両方を順に取り込む
- ファイル名パターンに合わないものは取り込み前に弾く

取込 UI は、MEDIS HOT と販売名ファイルを別々の選択欄で指定し、1つの取り込みボタンで「選択済みのファイルだけ」を順次取り込む。両方選択していれば両方、片方だけなら片方だけが取り込まれる。

### 2.1.1 2026-07-16 追加: 利用者登録マスター

帳票監査では、MEDIS公開マスターに存在しない院内製剤や医療材料もピッキング対象になる。
このため、ホーム右上メニューの `院内製剤・材料マスター` から利用者が品目を登録できる。

登録項目:

- 名称: 必須。帳票OCRの候補検索に使用する
- バーコード: 必須。GTINまたは施設独自バーコード
- 規格・包装: 任意
- 区分: 任意。初期値は `院内製剤・材料`

保存仕様:

- 利用者登録品も `drug_master` に保存する
- `isUserRegistered = true` で公開マスターと区別する
- `hot13 / drugCode / yjCode` には `USER-<正規化済みバーコード>` を設定する
- `gtin` には正規化済みバーコードを保存する
- 帳票候補検索では通常の `DrugMaster` と同じ検索経路を使う
- 帳票ピッキングでは登録バーコードから同じ `USER-*` コードを解決し、帳票側の `DrugIdentity.yjCode` と照合する
- 登録画面で利用者登録品の一覧確認と個別削除ができる

バーコード正規化:

- 正しいGTIN-13 / GTIN-14 / AI `01` 付きGTINは従来どおりGTIN-14へ正規化する
- GTIN形式でない施設独自コードは、前後空白を除いた3〜64文字の値を使用する
- 制御文字を含む値、空値、不正なチェックデジットのGTINは登録・照合しない

MEDIS更新時のデータ保持:

- `drug_master` の全件削除は行わない
- 手動取込・自動更新とも `isUserRegistered = false` の公開マスターだけを削除する
- 利用者登録品はMEDIS更新後も保持する
- Room DB version 11で `isUserRegistered INTEGER NOT NULL DEFAULT 0` を追加する

### 2.1.2 2026-07-16 追加: 初回施設登録とMEDISダウンロード

MEDIS公式の標準マスターQ&Aでは、医療機関での利用は無償とされている。
また、標準マスターは自由にダウンロードできるが、医療機関以外での使用または配布目的では使用許諾申請が必要とされている。

本アプリではMEDISデータの利用施設を確認するため、初回起動時に施設登録を必須とする。

初回導線:

1. アプリを初回起動する
2. `医薬品データの利用には施設登録が必要です` と説明し、薬品名やバーコードの確認にMEDIS標準マスターを使用することを案内する
3. 施設名、郵便番号、都道府県、市区町村を登録する
4. 登録完了後にMEDISデータのダウンロードを開始する
5. ホーム画面へ進み、初回取込中は `データダウンロード中` のオーバーレイを表示する
6. ダウンロードと取込の完了後は完了通知を表示せず、オーバーレイを自動的に閉じる

制御仕様:

- 施設未登録時の開始画面は `FACILITY_REGISTRATION`
- 必須登録中は戻る操作と上部の戻るボタンを無効化する
- 施設未登録時はアプリ起動によるMEDIS自動更新を開始しない
- 施設登録完了後に `maybeStartAutoUpdate(force = true)` を実行する
- 施設登録済みの通常起動では従来どおり更新間隔を確認して自動更新する
- 施設登録済み利用者がメニューから施設情報を編集する場合は戻る操作を許可する

### 2.2 販売名ファイルの文字コード

販売名ファイルは、配布元や版によって UTF-8 / Shift_JIS 系が混在する可能性がある。そのため `SalesNameCsvParser` は文字コードを自動判定する。

- UTF-8 BOM があれば UTF-8
- BOM なしでも strict UTF-8 として成立すれば UTF-8
- UTF-8 として成立しない場合は Shift_JIS / Windows-31J 系

`A_20260531_2.txt` は UTF-8 として実機ログで確認済みである。

```text
SalesNameCsvParser: Detected sales-name charset=UTF-8 bytes=21169659
```

Shift_JIS 固定で読むと、`センノシド錠１２ｍｇ「ＮＩＧ」` が `繧ｻ...` のように文字化けし、メーカー判定や画面表示が崩れる。

### 2.3 GTIN読取と正規化

PTP/箱スキャンで読み取る GS1 は、AI `01` 付きの文字列として入ることがある。

確認済み例:

```text
0114987376861653 -> 14987376861653
0104987376861687 -> 04987376861687
0104987896010923 -> 04987896010923
```

GTIN正規化では以下を扱う。

- AI `01` 付き16桁 -> 後続14桁を GTIN として採用
- 14桁 -> そのまま採用
- 13桁 JAN -> 先頭 `0` を補って14桁化
- 数字以外は除去

### 2.4 調剤モードのPTP/箱突合

調剤モードでは、PTP と箱のどちらの GS1 でも同じ解決経路を使う。

1. 読み取った GTIN を正規化する
2. `sales_package.gtin` を検索する
3. 見つからなければ `sales_package.gtinSales` を検索する
4. 見つからなければ `sales_package.gtinCase` を検索する
5. 見つかった `SalesPackage.yjCode` から `DrugMaster` を解決する
6. `drug.yjCode` で処方リストと照合する
7. YJ が一致しない場合は `drug.drugCode` でもフォールバック照合する

以前の「箱バーコードは未対応として止める」仕様は廃止する。箱GTINも照合対象である。

同じ薬が処方リストに複数行ある場合は、確認済み行ではなく未確認行を優先して確認する。全候補が確認済みの場合のみ AlreadyConfirmed とする。

### 2.5 帳票監査モードのPTP/箱突合

帳票監査モードも、調剤モードと同じ `sales_package` 優先の GTIN 解決経路を使う。

- 帳票OCRで得た薬品は `DrugIdentity.yjCode` を主キーとして扱う
- スキャンした GTIN は `sales_package` 経由で YJ に変換する
- YJ が一致すれば監査対象の薬品を確認済みにする
- 必要に応じて `drugCode` フォールバックで薬価コード/個別YJコード差を吸収する
- 利用者登録品はGTINまたは施設独自バーコードを `drug_master.gtin` から直接解決する
- 利用者登録品の `USER-*` コードが帳票側と一致すれば確認済みにする

帳票監査モードで PTP/箱が読めない場合は、カメラ倍率だけでなく、以下を確認する。

- `BarcodeAnalyzer` が読み取った raw GTIN
- `sales_package` に該当 GTIN が存在するか
- `sales_package` の販売名・メーカーが文字化けしていないか
- `DrugMasterLookup` が期待した YJ を返しているか
- 帳票側の `DrugIdentity.yjCode` と一致しているか

### 2.6 実機確認済み事項

2026-06-27 時点で、以下を確認済み。

- `A_20260531_2.txt` は UTF-8 として判定される
- `sales_package` 上のセンノシドNIGは日本語で保存される
- センノシド箱GTIN `14987376861653` は `sales_package.gtinSales` で解決できる
- センノシドPTP GTIN `04987376861687` は `sales_package.gtin` で解決できる
- `sales_package` は 50,637 件前後で運用している
- 調剤モードでは PTP/箱とも読み取り・表示が改善している
- `:app:testDebugUnitTest` は 150件 PASS
- `:app:assembleDebug` は BUILD SUCCESSFUL

2026-07-16 追加確認:

- Pixel 4aへDB初期化後のdebug APKを新規インストールできる
- アプリ起動を確認済み
- 利用者登録マスターの実機動作を確認済み
- `:app:testDebugUnitTest` は226件 PASS
- `:app:assembleDebug` は BUILD SUCCESSFUL

### 2.7 残課題

- 帳票監査モード側で、PTP/箱照合が調剤モードと完全に同じ結果になるか追加確認が必要
- 一部薬品で読めない場合は、読み取り不能、GTIN未収載、YJ不一致、候補選択ミスを分けて調査する
- Logcat を PowerShell で表示した際の文字化けと、DB内データの文字化けは区別する
- 本節を2026-06-27時点の優先仕様とする

---

## 2. 現在の機能構成

### 2.1 主な機能
- MEDIS マスタ CSV 取込
- 薬品検索
- PTP スキャン
- JAHIS QR 読取
- 期待値リスト表示
- 分包機学習トグル

### 2.2 データの流れ
1. MEDIS HOT / 販売名ファイルを個別に選択する
2. `MedisCsvParser` が MEDIS HOT ファイルを Shift_JIS / CP932 系として解析する
3. `MedisImportRepository` が `drug_master` を更新する
4. `SalesNameImportRepository` が `sales_package` を更新する
5. `DrugMasterDao` / `SalesPackageDao` が検索・照合に利用される
6. PTP スキャンでは GTIN を取得し `sales_package` を優先して `yjCode` を解決し、`DrugMaster` 側と突合する
7. JAHIS QR では期待値リストを組み立てて `DispensingSession` に変換する

---

## 3. 現在の実装状態

### 3.1 DrugMaster

`DrugMaster` は主キーを `gtin` とする。

- 主キー: `gtin`
- 照合・検索に使うコード列: `drugCode` / `yjCode`
- 表示・検索に使う文字列列: `drugName` / `packageName` / `drugNameKana1` / `drugNameKana2` / `drugNameKana3`
- 包装関連: `packageSpec` / `packageForm` / `packageUnitCount` / `packageUnitName`
- GTIN 関連: `gtin` / `gtinSales` / `gtinCase`

#### 補足
- `DrugMaster.gtin` は現行の新規取込では HOT13 を入れる
- `sales_package.gtin` は 14 桁の本物の GS1 GTIN を入れる
- PTP / 箱スキャンの一次キーは `sales_package` 側の GTIN 群と `yjCode`
- `yjCode` はカラムとして保持する
- `yjCode` は主キーではない
- `drugCode` は現状 `yjCode` の複製として投入される
- DB 件数は 37,071 件である
- 元データは `A_20260430_1.txt` 由来である

### 3.2 MedisCsvParser

`MedisCsvParser` は MEDIS CSV を 1 行ごとに解析する。

#### 特徴
- 文字コードは Shift_JIS / CP932 系を前提とする
- 1 行 = 1 レコード
- 解析対象の主な列:
  - `gtin`
  - `yjCode`
  - `drugName`
- 出力は `List<DrugMaster>`

#### 出力される主な項目
- `drugName`
- `drugNameKana1`
- `drugNameKana2`
- `drugNameKana3`
- `yjCode`
- `packageSpec`
- `packageForm`
- `packageUnitCount`
- `packageUnitName`
- `gtin`
- `packageName`
- `gtinSales`
- `gtinCase`
- `janCode`

### 3.3 MedisImportRepository

`MedisImportRepository` は MEDIS HOT ファイルを SAF で選択して取り込み、`drug_master` を更新する。

#### MEDIS 取り込みの現行方針
- CSV は `MedisCsvParser` で解析する。
- `drug_master` は全件削除ではなく、`upsertAll` で更新する。
- 500 行単位でまとめて取り込む。
- 取込件数、スキップ件数、エラー件数は UI に表示する。
- HOT マスターは [MEDIS HOT コード公開ページ](https://www2.medis.or.jp/hcode/) から取得する `MEDIS********_h.txt` を取り込む。
- 取り込み対象は MEDIS HOT のみで、販売名ファイルは別リポジトリで `sales_package` に入れる。
- `MEDIS20260531_HOT9.TXT` は軽量版候補、`MEDIS20260531_OP.TXT` は補助データであり、現行の `drug_master` 取り込みの必須ファイルではない。
- `medhot.medd.jp` で配布される販売包装系データは `sales_package` 側の用途であり、`drug_master` の必須ソースではない。
- 運用上は HOT マスターと販売名 / PTP 包装マスターを分けて保持し、突合時は `sales_package` から `yjCode` を引いて `DrugMaster` とつなぐ。

#### SalesNameImportRepository

`SalesNameImportRepository` は販売名・包装マスタを取り込み、`sales_package` を更新する。

- `importFromUri(uri)` でユーザー選択ファイルを取り込む
- `importFromAssets()` で内蔵ファイル `assets/medis/A_20260430_2.txt` も取り込める
- 取り込み前に `sales_package` を全件削除する
- 1,000 行単位で `upsertAll` する
- `SalesNameCsvParser` は文字コードを判定し、実機ログでは `A_20260531_2.txt` を UTF-8 として解釈している

2026-06-27 時点では、`sales_package` は 50,637 件前後で運用している。

#### 重要な変更点
- 以前は YJ 主キー前提で包装違いが上書きされていた
- 現在は GTIN 主キー化により、包装違いを保持できる

### 3.4 DrugMasterDao

#### 主な検索メソッド
- `findByGtin(gtin)`
- `findBySalesPackageGtin(gtin)`
- `findByCaseGtin(gtin)`
- `findByYjCode(yjCode)`
- `searchByKeyword(keyword)`

#### 検索条件
- `searchByKeyword` は薬品名・包装名・かな列を検索対象にする
- `findByCore` / `findByCoreNormalized` も帳票監査モードの候補生成で使うため、薬品名・包装名・かな列・別名を検索対象にする
- `LIMIT 500`
- `PTP` / `PTP包装` を優先するソートを含む

`SalesPackageDao` は GTIN 解決の一次入口であり、`findByGtin(gtin)` / `findByGtinSales(gtin)` / `findByGtinCase(gtin)` を持つ。`DrugMasterDao` は薬品情報の表示・検索に使い、GTIN の一次解決は `sales_package` 側を優先する。

### 3.8 帳票監査モード

帳票監査モードは、帳票をカメラで撮影し、OCR で薬品名候補を抽出して薬品マスタと突合する。

#### OCR
- ML Kit Text Recognition Japanese を使用する。
- `DocumentOcrParser` は OCR 結果を行単位で処理する。
- 薬品名らしい行のみを `DetectedDrugLine` として抽出する。
- 数量列、単位列、棚番、日時、注意事項などのノイズは除外する。

#### 薬品マスタ候補生成
- `DrugMasterMatcher` が OCR 薬品名を正規化し、`DrugMasterDao.findByCoreNormalized` で候補を取得する。
- 候補検索では `drugName` / `packageName` / `drugNameKana1` / `drugNameKana2` / `drugNameKana3` / `alias` を対象にする。
- `packageName` も検索対象に含める理由は、薬品検索画面では見つかる採用薬候補が帳票監査モードで候補から落ちることを防ぐためである。
- 例: `ロキソプロフェンNa錠60mg「トーワ」` が `packageName` 側に存在する場合でも、帳票モードの候補に残す。
- 候補は最終的に `DrugIdentity` に変換し、`yjCode` 単位で扱う。
- 包装違いは `yjCode` 単位に集約する。
- 同一薬効・同一規格で複数メーカーが残る場合は `AMBIGUOUS` とし、ユーザーが候補選択する。
- `audit_drug_preference` により、過去の手動選択を同じ match key に対して優先できる。

#### PTP 突合
- 帳票監査モードの PTP スキャンでは、読み取った GTIN を `sales_package.gtin / gtinSales / gtinCase` で検索する。
- `sales_package` から得た `yjCode` と、帳票側の `DrugIdentity.yjCode` を突合する。
- `drug_master` の GTIN 直検索は旧来フォールバックであり、通常経路は `sales_package` 優先である。

### 3.5 PTP スキャン

#### 現在の挙動
- `ScanMode.PTP_GTIN` では zxing-cpp を用いる
- 読み取った raw barcode から GTIN を抽出する
- `AI=01` 付き raw 16 桁を GTIN-14 に補正する
- JAN-13 は GTIN-14 に補正する
- 補正後の GTIN はまず `sales_package.gtin / gtinSales / gtinCase` を優先して検索する
- `sales_package` で見つかった `yjCode` で監査・突合する
- `DrugMaster` 直参照は旧来フォールバックとして残す

#### 画面上の挙動
- 読取成功時にマッチ結果と詳細を表示する
- PTP GTIN と箱 GTIN は同じ `sales_package` 優先経路で照合する

### 3.6 JAHIS QR

#### 検出パイプライン（Phase B-6 以降）

JAHIS QR は zxing-cpp を優先し、検出できない場合のみ ML Kit BarcodeScanning にフォールバックする。

- 1 フレーム内の複数 QR を独立して検出する
- zxing-cpp から Structured Append の順序・総数・グループIDと生バイト列を取得する
- Structured Append がある場合は順序どおりに生バイト列を連結してから Windows-31J で一度だけデコードする
- zxing-cpp が検出できない単独QRや従来形式は ML Kit で処理する

#### フラグメント蓄積と結合

`JahisQrScanViewModel` で fragments を蓄積する。

- 同一 key（テキスト一致）の fragment は重複排除する
- Structured Append がある場合は同じグループだけを受け付け、順序番号の欠落がない時点で解析可能とする
- Structured Append 読み取り開始前の番号なしQRは破棄し、開始後に映り込んだ無関係なQRも無視する
- Structured Append がない場合は、ヘッダと画面上の left 座標を使う従来フォールバックを残す

#### 対応フォーマット

- `JAHISTC01`
- `JAHISTC02`
- `JAHISTC07`

#### レコード対応

- `1, 5, 11, 51, 55, 201, 301` を扱う
- `201` の列レイアウトはフォーマットごとに異なる
- `55` レコードは医師名・診療科として保持する

#### DrugCodeType の値

- `1 = NONE`
- `2 = RECEIPT_COMPUTER`
- `3 = MHLW`
- `4 = YJ`
- `6 = HOT`
- `7 = GENERIC_MHLW`
- `9 = UNKNOWN`

#### 201 レコードの列レイアウト

- `TC01 / TC07`
  - `cols[1] = RP番号`
  - `cols[2] = 薬品名`
  - `cols[3] = 数量`
  - `cols[4] = 単位`
  - `cols[5] = コード種別`
  - `cols[6] = 薬品コード`
- `TC02`
  - `cols[1] = RP番号`
  - `cols[2] = 薬品連番`
  - `cols[3] = 用法連番`
  - `cols[4] = コード種別`
  - `cols[5] = 薬品コード`
  - `cols[6] = 薬品名`
  - `cols[7] = 数量`
  - `cols[9] = 単位`

#### 完結判定（Phase B-6.1 以降）

`JahisQrAssembler.checkCompleteness` の判定条件は以下のみ:

- ヘッダ存在チェック: 結合後テキストが `JAHISTC` で始まること
- RP対応チェック: 201 レコードの RP 番号がすべて 301 レコードに含まれること

終端チェック（`\r\n` または `\n` で終わる）は撤廃した。理由は JAHIS QR 発行元によっては末尾改行が省略される実装があり、終端要求は本質的でないため。`IncompleteReason.UNTERMINATED` enum 値は残しているが、現在は到達経路がない。

#### 文字化け対策（v0.2A からの変更）

Phase B-6 で ML Kit に切り替え後、文字化けは発生しなくなった。`rawBytes` を直接 Windows-31J デコードする方式により、v0.2A で実装されていた以下の対策コードは不要になった:

- ISO-8859-1 → UTF-8 経路
- CP932 → UTF-8 経路
- Windows-31J → UTF-8 経路
- stripReplacement 補助
- 行単位フォールバック
- japaneseScore による候補選択
- `repairMojibakeIfNeeded`
- `MojibakeCandidate`

これらは旧 ZXing 経路用のため、デッドコードとして将来削除予定（Phase B クロージング作業）。

#### 連結 QR の扱い

- 各 QR は ML Kit が独立して検出するため、連結処理は不要
- 結合は `JahisQrAssembler` で fragments の left 座標順に行う
- 改行は勝手に挿入しない（既存ロジックを維持）

### 3.7 期待値リストと状態管理

#### 現在の挙動
- JAHIS QR を読んで期待値リストを生成する
- PTP スキャンで `CONFIRMED` を更新する
- 長押しで `PACKING_MACHINE` / `UNCHECKED` をトグルする
- `DrugPreference` により分包機学習を保持する

#### 現在の照合ロジック
- `ExpectedListBuilder` は `DrugMaster` の GTIN ベースで正しく紐付ける前提になっている
- 分包機学習は `yjCode` キーで保存される
- `DispensingViewModel` は同一 GTIN の連続スキャンを一定時間抑制し、`scanFeedback` をトースト表示用に再利用する

---

## 4. Bug 修正履歴

### 4.1 Bug-001A / Bug-001C

#### 症状
- 薬品検索結果で PTP 包装が埋もれる
- 200 件制限で結果から漏れる

#### 修正
- `LIMIT 200` を `LIMIT 500` に変更
- `PTP / 箱包装` を優先表示するソートを追加

#### コミット
- `0b4010d`
- `Bug-001A/C fix: LIMIT 200→500拡張・PTP優先ソート追加`
- Tag: `phase2a-step3-bugfix-3`

### 4.2 Bug-001B

#### 症状
- MEDIS 取込では 50,494 行を取り込むのに、DB 登録は 17,036 件しか残らない
- PTP 包装が見えない

#### 原因
- CSV には 1 YJ コードに対して複数包装行が存在する
- 旧実装では `drugCode = yjCode` を主キー扱いしていた
- `OnConflictStrategy.REPLACE` により同一 YJ の行が上書きされていた

#### 修正
- `DrugMaster` の主キーを `gtin` に変更
- `drugCode` は検索・表示用の文字列列に変更
- `AppDatabase` を version 4 に更新
- `MIGRATION_3_4` を追加

#### コミット
- `3d2c620`
- `Bug-001B fix: DrugMaster主キーをGTINに変更・包装を保持`
- Tag: `phase2a-step3-bugfix-4`

### 4.3 Bug-003

#### 症状
- PTP モードで箱バーコードを読んでもマスター照合に失敗する

#### 原因
- MEDIS 調剤包装単位マスター `A_20260430_1.txt` には箱 GTIN が含まれない
- 箱 GTIN は販売包装単位マスター側にある

#### 修正
- 2026-06-09 時点では、GTIN の先頭桁で箱バーコードを判定し、未対応メッセージで誘導していた
- 2026-06-27 現在は、箱 GTIN も `sales_package.gtinSales / gtinCase` を使って照合対象にする

#### コミット
- `21ecb23`
- `Bug-003 fix: PTP package barcode detection and unsupported message`
- Tag: `phase2a-step3-bugfix-5`

### 4.4 Bug-002 Phase 1

#### 症状
- JAHIS QR 読取時に氏名・施設名・住所などが文字化けする

#### 原因
- UTF-8 バイト列が Shift_JIS / Windows-31J として解釈される二重文字化け
- ZXing fallback の rawBytes 取得が失敗し、誤デコード済みの `result.text` を使っていた

#### 修正
- `repairMojibakeIfNeeded()` に ISO-8859-1 → UTF-8 経路を追加
- Windows-31J 候補と ISO 候補を日本語スコアで比較
- より日本語らしい候補を採用するようにした

#### コミット
- `8c5b12b0`
- `Bug-002 fix: JAHIS QR ISO-8859-1/UTF-8 mojibake repair`
- Tag: `phase2a-step3-bugfix-6`

### 4.5 Bug-002 Phase 2

#### 症状
- Phase 1 の補正でも文字化けが残るケースがあった

#### 原因
- UTF-8 バイト列が CP932 として読まれるケースが残っていた

#### 修正
- `repairMojibakeIfNeeded()` に CP932 → UTF-8 経路を追加
- 既存の Windows-31J / ISO-8859-1 経路は維持

#### コミット
- `6ecca097`
- `Bug-002 fix(phase2): JAHIS QR CP932→UTF-8 mojibake repair`
- Tag: `phase2a-step3-bugfix-7`

### 4.6 Bug-002 fix

#### 症状
- バイアスピリン 100mg / ロスバスタチン 2.5/5/10mg が「マスター未登録」になる

#### 修正
- `ExpectedListBuilder` の照合を `drugCodeType` 非依存に変更
- `drugCode` が非空ならまず `findByYjCode()` を試す
- 名前検索フォールバックを NFKC 正規化＋銘柄サフィックス除去で強化

#### コミット
- `4966410c`
- `Bug-002 fix: drugCodeType-agnostic matching and name normalization`
- Tag: `phase2a-step3-bugfix-9`

---

## 5. 2026/06/09 時点の現在状態

- `DrugMaster` の主キーは `gtin`
- `drug_master` は 37,071 件登録済み
- PTP シート GTIN は照合可能
- 箱 GTIN も `sales_package` 優先経路で照合する
- JAHIS QR は `JAHISTC01` / `JAHISTC02` / `JAHISTC07` に対応
- JAHIS は Phase B-6 で ML Kit ベースに移行済み
- スキャンモードは `ScanMode.PTP_GTIN` と `ScanMode.JAHIS_QR` の 2 種類
- 期待値リストは PTP 突合、分包機学習、JAHIS 期待値表示を支える

---

## 6. 今後の課題

### 6.1 優先度高（Phase C 着手前）
- Phase C-1: 青丸トグル機能の実装
- Phase C-2: PTP/箱突合フローの実機検証と残課題整理
- 各 Phase C の TBD 解消

### 6.2 優先度中（Phase B クロージング）
- 旧 ZXing 経路のデッドコード削除（`MojibakeCandidate`, `repairMojibakeIfNeeded`, `isCompleteJahisText`, `looksLikeJahisFragment`, `japaneseScore` 等）
- 教訓ドキュメント整備（`docs/lessons-learned-phase-b.md`）

### 6.3 優先度低
- Bug-001B-future: CSV 重複行の調査
- 帳票監査モード・充填モードの要件詳細化

---

## 7. Bug-001D-future の扱い

Bug-001D-future は現時点では仕様整備の対象外であり、別タスク扱いとする。

### メモ
- 取込件数と DB 件数の差は、単純な読み飛ばしではなく包装レベルの重複保持が論点である
- 検索ロジックの改修は必要に応じて別タスクで扱う

---

## 8. Phase 2A-8 の位置づけ

Phase 2A-8 は JAHIS 分割 QR の本格対応である。

### ねらい
- 分割 QR の複数パターン対応
- 1 フレーム複数 QR の取り扱い
- 未読断片の蓄積
- 完結判定の UI 化

### 状態
- Phase B-6 で ML Kit ベースの検出パイプラインとして実質的に解決済み
- Phase B-6.1 で終端チェック撤廃済み
- Phase C では調剤モードの突合 UI と連携する

### まだやらないこと
- `JahisQrParser` の大規模再設計
- `ExpectedListBuilder` のロジック総入れ替え
- PTP の照合ロジック変更

---

## 9. 検証状況

### 9.1 実機検証済み
- Bug-001A/C: 実機検証済み、退行なし
- Bug-001B: DB 件数増加を確認済み
- Bug-003: 2026-06-09 時点では箱バーコード未対応メッセージを確認済み。2026-06-27 現在は箱 GTIN も照合対象に変更済み
- Bug-002 Phase 1 / Phase 2: 知多半島薬情の JAHIS QR で文字化けが解消し、3 断片の自動読取成功を確認済み

### 9.2 未実機検証
- Bug-002 fix (4966410c): バイアスピリン 100mg / ロスバスタチン 2.5/5/10mg の「マスター未登録」問題修正
  - 単体テストは PASS
  - 実機での最終確認は QR 入手後に実施する

### 9.3 Phase B 検証

- Phase B-6 ビルド: testDebugUnitTest SUCCESS、assembleDebug SUCCESS
- Phase B-6.1 ビルド: testDebugUnitTest SUCCESS、assembleDebug SUCCESS
- 実機 Pixel 4a で 3枚 JAHIS QR → 解析 → 6薬品表示を確認済み（2026-06-14）
- 文字化けなし、重複なし、UI 文言は日本語表示

---

## 10. 運用ルール

- コード変更は必要最小限にする
- 生成物はコミットしない
- ビルド確認は `assembleDebug` を基本とする
- 重要な変更は Git コミットとタグで管理する
- 分割 QR と PTP は別タスクとして扱う

---

## 11. 変更履歴

### 2026-07-16

- ホームメニューに `院内製剤・材料マスター` を追加
- 利用者による名称、バーコード、規格・包装、区分の登録に対応
- GTINと施設独自バーコードの帳票ピッキング照合に対応
- 利用者登録品の一覧表示と個別削除を追加
- MEDIS手動取込・自動更新後も利用者登録品を保持するよう変更
- Room DBをversion 11へ更新し、`drug_master.isUserRegistered` を追加
- Pixel 4aへの新規インストールと起動を確認
- 全226件のdebugユニットテスト成功
- 初回起動時の施設登録を必須化し、登録後にMEDISデータをダウンロードする導線を追加

---

## 11. まとめ

現在の仕様は以下を前提とする。

- MEDIS 取込は GTIN 主キーで包装単位を保持する
- 薬品検索は LIMIT 500 と PTP 優先ソートを使う
- PTP スキャンは GTIN 補正済み値を `sales_package` 優先で解決する
- JAHIS QR は JAHISTC01 / JAHISTC02 / JAHISTC07 に対応する
- JAHIS QR 検出は Phase B-6 以降 ML Kit + Windows-31J rawBytes デコードを使う
- 期待値リストは codeType に依存しない照合と名前正規化を使う

未解決の課題は、主に調剤モードの突合 UI、帳票監査モードの運用改善、充填モードの運用調整である。これらは Phase C として切り出して扱う。

---

## 12. アプリ全体構造（3モード構成）

本アプリは以下3つのモードで構成される。Phase C 時点では、調剤モード、帳票監査モード、充填モードの主要導線を実装済みである。

### 12.1 調剤モード（処方監査・取り揃え）

患者の薬手帳に印字された JAHIS QR を読み取り、処方内容を表示。続いて実物の PTP または箱の GS1 バーコードを読み取り、処方内容と突合する。

動線:
1. JAHIS QR スキャン画面 → 3枚の QR を読み取り
2. 解析ボタン → 薬品リスト表示（患者情報、RP単位の薬品一覧）
3. 薬品リスト確認 → 必要に応じて青丸トグル（分包機対象外マーク）
4. PTP/箱スキャンボタン → 実物のバーコード読み取り
5. リストの該当薬品にチェック、進捗カウントアップ
6. 全件チェック完了 → 完了ボタンで終了

実装状況: 1〜2 は Phase B-6.1 で完了。4 の PTP/箱 GTIN 照合主要経路は Phase C-2 で実装済み・実機検証中。3 の青丸トグルと一部 UI / 完了フローは Phase C-1 / C-2 の残課題。

### 12.2 帳票監査モード（取り揃え表）

調剤録や取り揃え表などの帳票を撮影し、OCR で薬品名を抽出して薬品リストを作成する。続いて PTP/箱の GS1 と突合する。

動線:
1. 帳票監査モードを開く
2. 帳票を撮影する
3. OCR 結果から薬品名候補を抽出する
4. `DrugMasterMatcher` で薬品マスタ候補を生成する
5. 候補が1件なら確定、複数なら候補選択、0件なら手動検索する
6. PTP スキャンへ進み、GTIN → `sales_package` → `yjCode` の順に解決して帳票リストと突合する

実装状況: Phase C-3 で基礎実装済み。

現在の重要仕様:
- 候補生成は `drugName` だけでなく `packageName` も検索対象にする。
- 薬品検索画面で見つかる採用薬候補を、帳票監査モードの候補から落とさないことを優先する。
- ロキソプロフェンやセンノシドのように複数メーカーが残る薬品は `AMBIGUOUS` として候補選択する。
- ユーザーが選択した候補は `audit_drug_preference` に学習する。

未確定事項（TBD）:
- 採用薬マスタを明示的に持つか、学習履歴で代替するか
- 複数候補が多い薬品での候補順位付け
- 調剤モードとの突合 UI 共通化範囲

### 12.3 充填モード（分包機カセット補充照合）

自動錠剤分包機のカセットまたは粉薬瓶に薬品を補充する際、補充する薬品と充填先コードが一致しているかを照合する。PTP または箱を最初に読み取り、続いて充填先カセット・瓶の GS1 等を読み取る2段階フローである。

動線:
1. 充填する薬品の PTP または箱をスキャンする
2. `sales_package` 優先経路で薬品を解決できたら、音と振動を出す
3. 画面下部に赤背景・白文字で薬品名を大きく表示する
4. 2.5秒の待機時間を置き、同じ箱が画面に残ったまま即OKになることを防ぐ
5. 白地の案内領域に「充填先のカセットまたは瓶のコードをスキャンしてください」と表示する
6. 充填先のカセットまたは瓶のコードをスキャンする
7. 充填先コードが選択薬品と一致すれば、音と振動を出して `充填OK` を表示する
8. `充填OK` 後はカメラプレビューを止め、以後の読み取りで結果を上書きしない
9. 下部に `次の薬品照合` と `戻る` の2ボタンを表示する

照合ルール:
- 1回目の薬品スキャンは PTP/箱スキャンと同じ GTIN 正規化・照合経路を使う
- 読み取った GTIN は `sales_package.gtin / gtinSales / gtinCase` を優先して解決する
- AI `01` 付き raw 文字列、14桁GTIN、13桁JAN補正を扱う
- 2回目の充填先コードは、選択済み薬品の `yjCode` または `drugCode` と一致するかで判定する
- 充填先スキャン中のノイズや未登録コードは、ユーザーを混乱させないため原則として案内文を維持し、完了状態は上書きしない
- ロット（AI=10）と期限（AI=17）は現時点では照合キーに使わない

画面仕様:
- 画面上部のタイトルは `充填モード` とし、戻るリンクは表示しない
- 充填先スキャン中は上部タイトルに薬品名を出さず、下部の赤背景ラベルに薬品名を表示する
- 読み取り指示はカメラ映像上ではなく、白地の案内カードに表示する
- 完了時はカメラを表示せず、白地の完了画面にする
- 完了時の下部には `次の薬品照合` と `戻る` の2ボタンを表示する

実装状況: Phase C-4 として基礎実装済み。実機で以下を確認済み。
- 1回目スキャン後に音と振動が出る
- 1回目スキャン後、画面下部に赤背景で薬品名が表示される
- 箱をそのままにしても即 `充填OK` にならない
- 2.5秒後に充填先コードを読ませると一致判定できる
- `充填OK` 後に別コードを読んでも `マスターに見つかりません` に上書きされない

未確定事項（TBD）:
- 期限警告のレベル（ブロック or 警告のみで続行可、間近期限の扱い）
- 履歴記録の有無、記録する項目
- カセットID管理の要否

---

## 13. 共通機能（Phase C 以降の予定）

### 13.1 薬品リストと青丸トグル

調剤モード・帳票監査モードで表示される薬品リストの各項目には、左に丸マークが表示される。

- 白丸: 通常（分包機でピッキング対象）
- 青丸: 分包機対象外（タップで切り替え）

青丸の状態は YJコード単位で保持し、薬品マスタを更新しても引き継がれる。既存の `DrugPreference`（v0.2A の分包機学習）と統合または流用する想定。

実装状況: 未実装（Phase C-1 で対応予定）。

未確定事項（TBD）:
- 既存 `DrugPreference` テーブルとの統合方法
- 別カラムを追加するか、新規テーブルにするか

### 13.2 PTP/箱スキャン

PTP シートおよび薬品空箱の GS1 バーコードを読み取り、薬品リストと突合する機能。

対応バーコード形式:
- GS1 DataBar Limited（PTP に印字、既存実装済み）
- 箱に印字される GS1 DataBar / GS1-128 等（`sales_package` 優先経路で照合）
- 2錠シート等で QR/バーコードがない場合は箱で読み取る

照合キー:
- GTIN（AI=01）の一致でまず `sales_package` を特定し、得られた `yjCode` で薬品を特定

実装状況: PTP/箱 GTIN は `sales_package` 優先経路で照合する実装へ更新済み。残課題は実機検証での読み取り安定性と帳票監査モード側の完全一致確認である。

未確定事項（TBD）:
- 箱に印字されているバーコード形式の確定
- リストに無い GTIN を読み取った場合の挙動
- リスト内に同じ GTIN が複数ある場合の挙動
- 不一致時のリトライ仕様

---

## 14. Phase B 履歴サマリー

v0.2A 以降に実施した主要フェーズ。

### 14.1 Phase B-2 〜 B-5（試行錯誤期）

JAHIS 分割 QR の本格対応として ZXing 経路の改良を試みた。

- Structured Append メタデータの活用検討
- byteSegments の Shift_JIS デコード経路追加
- MojibakeCandidate スコアリング
- Incomplete reason 導入と auto-debounce

結果として ZXing の `QRCodeMultiReader` が同一フレーム複数 QR を1つの result.text に連結して返す仕様により fragment 重複問題が解消できず、Phase B-6 で ML Kit への全面切り替えを決断。

### 14.2 Phase B-6（パイプライン全面書き直し）

ML Kit BarcodeScanning ベースに JAHIS QR 検出パイプラインを書き直した。

- BarcodeAnalyzer.kt: JAHIS QR を ML Kit 経路に統一
- JahisQrAssembler.kt: ヘッダ優先＋left 座標順ソート
- JahisQrScanViewModel.kt: auto-debounce を SA メタデータ非依存に変更
- ScanScreen.kt + strings.xml: UI 文言を日本語化
- コミット: `1a952dba`

### 14.3 Phase B-6.1（終端チェック撤廃）

実機テストで `Incomplete reason=UNTERMINATED` で解析失敗が判明。原因は JAHIS QR 末尾の `\r\n` が省略されている発行元実装があるため。終端チェックを撤廃し、ヘッダ + RP対応チェックのみで完結判定する仕様に変更。

- JahisQrAssembler.checkCompleteness: 終端チェック削除
- JahisQrAssemblerTest: 終端不足前提のテストを `MISSING_USAGE` 期待に変更
- 実機 Pixel 4a で 6薬品の正常表示を確認
- コミット: `06480131`

### 14.4 Phase B 教訓

- 仕様調査不足のまま実装に入ると手戻りが大きい（ZXing の MultiReader 挙動、ECI、SA など）
- 「作り直した方が速い」の判断を早めに下す
- 実機テストは初動で実施し、ログを取って検証する
- デバッグ時のログ表示文字化け（Logcat→PowerShell 経路）に振り回されない

---

## 15. Phase C ロードマップ

| フェーズ | 内容 | 状態 |
|---|---|---|
| Phase C-1 | 青丸トグル機能、薬品マスタへの分包機対象外フラグ統合 | 未着手 |
| Phase C-2 | 調剤モードの PTP/箱突合フロー実装、箱バーコード対応 | 主要経路実装済み・実機検証中 |
| Phase C-3 | 帳票監査モードの実装 | 基礎実装済み・候補精度調整中 |
| Phase C-4 | 充填モード（カセット補充照合）の実装 | 基礎実装済み・実機運用調整中 |

Phase C 着手前に各フェーズの未確定事項（TBD）を解消する。
