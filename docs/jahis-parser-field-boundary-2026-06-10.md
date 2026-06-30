# JAHIS QR フィールド境界バグ調査（Bug-005）

## 1. 現象
- 調剤モードの JAHIS QR 読み取りで、RP2 の表示が `★ラメルテオン錠8m201` のように見える。
- 同一カード内に `5★ランソプラゾールOD錠15mg（タケプロン）【簡】` が混入して見える。
- ここで疑うべきは、`JahisQrParser` の `201` レコードの境界処理か、あるいはそれ以前の raw text の破損か。

## 2. JahisQrParser 実装解析

### 2.1 ファイル位置
- `app/src/main/java/com/example/yakuzaiapp/data/jahis/JahisQrParser.kt`

### 2.2 区切り文字処理
- `parse(rawText)` では `\u001A` を削除した後、`CRLF` と `CR` を `LF` に正規化している。
- レコード分割は `lineSequence()`、フィールド分割は `line.split(',')`。
- したがって、実装上の区切り文字は **改行** と **カンマ** のみ。
- `FS/GS/RS/US` のような制御文字での分割はしていない。

### 2.3 TC01 / TC07 / TC02 の列レイアウト
`drugFields()` の分岐は以下。

- `TC01 / TC07`
  - `cols[1]` = RP番号
  - `cols[2]` = 薬品名
  - `cols[3]` = 数量
  - `cols[4]` = 単位
  - `cols[5]` = コード種別
  - `cols[6]` = 薬品コード
- `TC02`
  - `cols[1]` = RP番号
  - `cols[2]` = 薬品連番
  - `cols[3]` = 用法連番
  - `cols[4]` = コード種別
  - `cols[5]` = 薬品コード
  - `cols[6]` = 薬品名
  - `cols[7]` = 数量
  - `cols[9]` = 単位、なければ `cols[8]`

### 2.4 RP グルーピング
- `recordNo == 201` のたびに `rpMap.getOrPut(rpNumber)` で `rpNumber` ごとに蓄積する。
- 最後に `rpMap.values.sortedBy { it.rpNumber }` で RP 順に並べる。
- `DispensingScreen` 側も `session.items.groupBy { it.rpNumber }` で RP ごとに表示している。

## 3. JAHIS TC01 仕様（判明分）

現行実装の前提は以下。

- `JAHISTC01` と `JAHISTC07` は同一レイアウトとして扱う。
- `201` は `201,RP,薬品名,数量,単位,種別,YJ` の順。
- `301` は `301,RP,用法...` の形で扱う。

## 4. 実機ログ cols ダンプ

ログ元:
- `C:\Users\fyuuc\jahis_bugfix12_verify.txt`

抜粋したログは以下。

### 4.1 RP1 デエビゴ（正常）
```text
JahisQrParser: format=TC01 cols.size=7
JahisQrParser: cols=201|1|...5mg...|1|4|1190027F2029
JahisQrParser: codeType=4 drugCode=1190027F2029
JahisQrParser: extracted yjCode=1190027F2029
JahisQrParser: Parsed drug: codeType=YJ, code=1190027F2029, name=...
```

### 4.2 RP2 ラメルテオン（異常）
```text
JahisQrParser: format=TC01 cols.size=7
JahisQrParser: cols=201|2|...8mg...|1|4|1190016F1075
JahisQrParser: codeType=4 drugCode=1190016F1075
JahisQrParser: extracted yjCode=1190016F1075
JahisQrParser: Parsed drug: codeType=YJ, code=1190016F1075, name=...8mg...
```

### 4.3 RP3 マグミット（正常）
```text
JahisQrParser: format=TC01 cols.size=7
JahisQrParser: cols=201|3|...330mg...|3|4|2344009F2031
JahisQrParser: codeType=4 drugCode=2344009F2031
JahisQrParser: extracted yjCode=2344009F2031
JahisQrParser: Parsed drug: codeType=YJ, code=2344009F2031, name=...
```

### 4.4 バイアスピリン・ロスバスタチン
```text
JahisQrParser: cols=201|4|...100mg...|1|4|3399007H1021
JahisQrParser: codeType=4 drugCode=3399007H1021

JahisQrParser: cols=201|6|...OD錠2.5mg...|1|4|2189017F3130
JahisQrParser: codeType=4 drugCode=2189017F3130
```

## 5. 差分分析と汚染位置

### 5.1 観察結果
- `cols.size=7` は RP1/RP2/RP3 で揃っている。
- `codeType` と `drugCode` は正しく `cols[5]` / `cols[6]` から取れている。
- つまり、**201レコードの列境界そのものは崩れていない**。
- 一方で、`drugName` が mojibake しており、表示上の異常は `cols[2]` 側に出ている。

### 5.2 「8m201」「5★」の位置づけ
- ログ上で `201` が `drugCode` 列に食い込んだ証拠はない。
- `8m201` の見え方は、列境界よりも `drugName` の破損、または UI 表示時の文字化け/折返し由来の可能性が高い。
- `5★ランソプラゾール...` も、現行ログでは別レコードの `drugCode` を壊している証拠はない。

## 6. 仮説検証（H1〜H5）

| 仮説 | 判定 | 根拠 |
| --- | --- | --- |
| H1: フィールド区切り文字欠落 | 低 | `cols.size=7` で、`codeType` / `drugCode` は正しく分離されている。 |
| H2: レコード区切り文字欠落 | 低 | RP1/RP2/RP3 が独立した `201` レコードとしてパースされている。 |
| H3: バイト単位/文字単位混在 | 中 | `drugName` の mojibake は強い。ただし列境界崩れの直接証拠ではない。 |
| H4: rawValue/rawBytes 由来の元データ破損 | 高 | `drugName` 側の文字化けは実機ログで一貫しており、上流由来の破損が最も整合する。 |
| H5: 8mg の後の 201 が別フィールド誤結合 | 低 | `201` は `recordNo` として独立に読めており、`drugCode` への混入は見えない。 |

## 7. ランソプラゾール混入の出所

- `DispensingScreen` は `session.items.groupBy { it.rpNumber }` でカードを分けている。
- `ExpectedListBuilder` も `JahisPrescription.rps` をそのまま `rpNumber` ごとに `ExpectedDrugItem` へ写している。
- したがって、同一カード内に混ざって見えるなら、RP番号の誤判定よりも `drugName` の文字化けや raw text 側の断片汚染をまず疑うべき。
- 現ログでは `rpNumber` の誤りを示す証拠はない。

## 8. ラメルテオン YJ 汚染の有無

- 正解 YJ: `1190016F1075`
- 実機ログの `cols` は `cols[6]=1190016F1075` と読めている。
- したがって、**YJ列の汚染は現状のログでは確認できない**。
- もし UI で「マスター未登録」や別薬品表示が起きるなら、`drugName` 側の表示問題、または `ExpectedListBuilder` / UI の別バグを別途見る必要がある。

## 9. 結論と推奨修正方針

### 推定原因
- 現時点の証拠では、`JahisQrParser` の列境界処理そのものは壊れていない。
- もっとも整合するのは **上流の raw text 破損 / mojibake による `drugName` 汚染**。
- つまり、今回の異常は parser の `line.split(',')` 境界崩れよりも、`BarcodeAnalyzer` 側の文字コード解決か、QR断片の復元に起因している可能性が高い。

### 修正対象候補
- `app/src/main/java/com/example/yakuzaiapp/util/BarcodeAnalyzer.kt`
- `app/src/main/java/com/example/yakuzaiapp/domain/jahis/JahisQrAssembler.kt`
- 必要なら `app/src/main/java/com/example/yakuzaiapp/data/jahis/JahisQrParser.kt` の診断ログ追加

### 方針
- parser の列定義は維持しつつ、raw text のデコード品質を上げる。
- `drugName` の破損が残るなら、受信直後の byte → text の経路を再調査する。
- RPグルーピングは現行の `rpNumber` ベースを維持。

### リスク評価
- parser の列定義をいじると、TC01/TC02/TC07 の互換性を壊すリスクが高い。
- 先に raw text 生成経路を直した方が影響範囲が小さい。

## 10. 次ステップ提案

1. `BarcodeAnalyzer` の raw text 生成ログをさらに細かく出して、`drugName` 破損の起点を特定する。
2. `JahisQrAssembler` の連結後全文を 1 件だけ保存して、実物 QR の断片境界がどこで切れているかを確認する。
3. `JahisQrParser` の `201`/`301` の診断ログを残して、同じ入力で RP1/RP2/RP3 が同じ列レイアウトを維持しているか継続確認する。

