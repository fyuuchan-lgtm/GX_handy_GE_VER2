# YakuzaiApp Spec v0.2A

## 1. 目的
YakuzaiApp は、MEDIS マスタ取込、PTP スキャン、JAHIS お薬手帳 QR 読取、期待値リスト表示、分包機状態の管理を行う Android アプリである。

本仕様書 v0.2A は、2026/06/09 までに実施した Bug-001A/C, Bug-001B, Bug-001C, Bug-003, Bug-002（Phase 1 / Phase 2 / fix）の修正結果を反映した現行仕様を示す。

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
1. MEDIS CSV を SAF で選択する
2. `MedisCsvParser` が Shift_JIS / CP932 系として解析する
3. `MedisImportRepository` が `drug_master` を更新する
4. `DrugMasterDao` が検索・照合に利用される
5. PTP スキャンでは GTIN を取得し `DrugMaster` と突合する
6. JAHIS QR では期待値リストを組み立てて `DispensingSession` に変換する

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

`MedisImportRepository` は SAF で選択された CSV を取り込み、`drug_master` を更新する。

#### 現在の挙動
- CSV は `MedisCsvParser` で解析する
- `drug_master` は全件削除ではなく、`upsertAll` で更新する
- 500 行単位でまとめて登録する
- 取込件数、スキップ件数、エラー件数を UI に表示する

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
- `LIMIT 500`
- `PTP` / `PTP包装` を優先するソートを含む

### 3.5 PTP スキャン

#### 現在の挙動
- `ScanMode.PTP_GTIN` では zxing-cpp を用いる
- 読み取った raw barcode から GTIN を抽出する
- `AI=01` 付き raw 16 桁を GTIN-14 に補正する
- JAN-13 は GTIN-14 に補正する
- 補正後の GTIN で `DrugMaster` を検索する

#### 画面上の挙動
- 読取成功時にマッチ結果と詳細を表示する
- 箱バーコードは未対応メッセージを出す

### 3.6 JAHIS QR

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

#### 文字化け対策
Phase 1 / Phase 2 で以下の補正を実装済み。

- ISO-8859-1 → UTF-8 経路
- CP932 → UTF-8 経路
- Windows-31J → UTF-8 経路
- stripReplacement 補助
- 行単位フォールバック
- japaneseScore による候補選択

#### 連結 QR の扱い
- JAHIS 分割 QR は raw text 断片を順序どおりに連結してから解析する
- 断片ごとの個別 parse は行わない
- 改行を勝手に挿入しない

### 3.7 期待値リストと状態管理

#### 現在の挙動
- JAHIS QR を読んで期待値リストを生成する
- PTP スキャンで `CONFIRMED` を更新する
- 長押しで `PACKING_MACHINE` / `UNCHECKED` をトグルする
- `DrugPreference` により分包機学習を保持する

#### 現在の照合ロジック
- `ExpectedListBuilder` は `DrugMaster` の GTIN ベースで正しく紐付ける前提になっている
- 分包機学習は `yjCode` キーで保存される

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
- GTIN の先頭桁で箱バーコードを判定
- 箱バーコードは未対応メッセージで誘導する

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
- 箱 GTIN は未対応メッセージで誘導する
- JAHIS QR は `JAHISTC01` / `JAHISTC02` / `JAHISTC07` に対応
- JAHIS 文字化け修復は Phase 1 / Phase 2 の経路で実運用対応済み
- スキャンモードは `ScanMode.PTP_GTIN` と `ScanMode.JAHIS_QR` の 2 種類
- 期待値リストは PTP 突合、分包機学習、JAHIS 期待値表示を支える

---

## 6. 今後の課題

### 6.1 優先度高
- Task 4 (Bug-002 関連): 「読取完了」ボタンの追加
- 3 枚分割 QR の未読時に手動で確定する UI

### 6.2 優先度中
- Phase 2A-8: 分割 QR 対応の本格化
- さらなる分割パターンへの対応
- 順序判定や完結判定の強化

### 6.3 優先度低
- Bug-002 Task 2 (A案): ZXing BYTE_SEGMENTS による根本解決
- Bug-002 Task 3: ML Kit rawBytes 利用検討
- Bug-001B-future: CSV 重複行の調査

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

### まだやらないこと
- `JahisQrParser` の大規模再設計
- `ExpectedListBuilder` のロジック総入れ替え
- PTP の照合ロジック変更

---

## 9. 検証状況

### 9.1 実機検証済み
- Bug-001A/C: 実機検証済み、退行なし
- Bug-001B: DB 件数増加を確認済み
- Bug-003: 箱バーコードで未対応メッセージを確認済み
- Bug-002 Phase 1 / Phase 2: 知多半島薬情の JAHIS QR で文字化けが解消し、3 断片の自動読取成功を確認済み

### 9.2 未実機検証
- Bug-002 fix (4966410c): バイアスピリン 100mg / ロスバスタチン 2.5/5/10mg の「マスター未登録」問題修正
  - 単体テストは PASS
  - 実機での最終確認は QR 入手後に実施する

---

## 10. 運用ルール

- コード変更は必要最小限にする
- 生成物はコミットしない
- ビルド確認は `assembleDebug` を基本とする
- 重要な変更は Git コミットとタグで管理する
- 分割 QR と PTP は別タスクとして扱う

---

## 11. まとめ

現在の仕様は以下を前提とする。

- MEDIS 取込は GTIN 主キーで包装単位を保持する
- 薬品検索は LIMIT 500 と PTP 優先ソートを使う
- PTP スキャンは GTIN 補正済み値で照合する
- JAHIS QR は JAHISTC01 / JAHISTC02 / JAHISTC07 に対応する
- JAHIS 文字化け補正は Phase 1 / Phase 2 の修正で運用可能になっている
- 期待値リストは codeType に依存しない照合と名前正規化を使う

未解決の課題は、主に JAHIS 分割 QR の本格対応と、UI 上の完結判定の改善である。これらは Phase 2A-8 として切り出して扱う。
