# spec_v0.2A.md vs 実装ファイル 差分調査レポート

- 調査日: 2026-06-10
- 対象: `docs/spec_v0.2A.md` と `app/src/main/java/com/example/yakuzaiapp/**`
- 目的: 仕様書と実装の差異を、未実装 / 記述漏れ / 矛盾の 3 区分で整理する

## 1. 章構成の抽出

1. 目的
2. 現在の機能構成
3. 現在の実装状態
4. Bug 修正履歴
5. 2026/06/09 時点の現在状態
6. 今後の課題
7. Bug-001D-future の扱い
8. Phase 2A-8 の位置づけ
9. 現在の課題
10. 運用ルール
11. まとめ

## 2. ズレ一覧表

| 章 | カテゴリ | 内容 | 該当実装ファイル | 優先度 | 推奨アクション |
|---|---|---|---|---|---|
| 3.7 期待値リストと状態管理 | C 矛盾 | 仕様書は「ExpectedListBuilder は DrugMaster の GTIN ベースで正しく紐付けられている前提」と書いているが、実装は `drugCode` を code-first で照合し、失敗時に名前検索へ落ちる。GTIN は `DispensingViewModel` 側の PTP 照合で主に使う。 | `domain/dispensing/ExpectedListBuilder.kt`, `ui/dispensing/DispensingViewModel.kt` | 高 | a. 仕様書を実装に合わせて更新 |
| 3.6 JAHIS QR / 5. 現在状態 | C 矛盾 | 仕様書は「JAHIS QR 3 断片の自動読取成功」を現在状態として書いているが、実装は `JahisQrScanViewModel` と `ScanScreen` で fragment 蓄積後に `解析する` の manual path を残しており、`parse deferred` も発生する。 | `ui/scan/ScanScreen.kt`, `ui/scan/JahisQrScanViewModel.kt`, `domain/jahis/JahisQrAssembler.kt` | 高 | a. 仕様書を実装に合わせて更新 |
| 3.5 PTP スキャン | B 記述漏れ | PTP スキャンは `continuousMode`、2 倍ズーム、成功時の振動・音・トースト、連続スキャン時の挙動を持つが、仕様書にはそこまで書かれていない。 | `ui/scan/ScanScreen.kt`, `util/BarcodeAnalyzer.kt`, `ui/dispensing/DispensingScreen.kt` | 中 | a. 仕様書に追記 |
| 3.6 JAHIS QR | B 記述漏れ | JAHIS QR は `JahisQrScanViewModel` で断片蓄積し、left ソート・重複排除・クールダウン・`解析する` ボタンを持つが、仕様書はその UI/保持モデルを明示していない。 | `ui/scan/ScanScreen.kt`, `ui/scan/JahisQrScanViewModel.kt`, `domain/jahis/JahisQrAssembler.kt` | 中 | a. 仕様書に追記 |
| 3.4 薬品検索 | B 記述漏れ | 検索 UI は 3 文字未満で検索しない、結果は 500 件上限、入力をカタカナへ正規化してから DAO を叩く、という実装詳細がある。仕様書は検索機能の存在までは書くが、この UI 条件は明示していない。 | `ui/search/DrugSearchScreen.kt`, `ui/search/DrugSearchViewModel.kt` | 中 | a. 仕様書に追記 |
| 3.3 MEDIS 取込 | B 記述漏れ | 取込は `Reading / Parsing / Deleting / Inserting / Completed / Failed` の進捗モデルを持ち、エラー行は最大 100 件保持する。仕様書は概略のみで、UI に出る進捗粒度は未記載。 | `data/medis/MedisImportRepository.kt`, `data/medis/MedisCsvParser.kt`, `ui/medis/*` | 中 | a. 仕様書に追記 |
| 3.1 DrugMaster | B 記述漏れ | `DrugMaster` には `displayLabel`、`needsWarning`、`isDiscontinued`、`isInTransition` の派生プロパティがあるが、仕様書のデータモデル章では明示されていない。 | `data/local/entity/DrugMaster.kt` | 低 | d. 内部実装詳細として記録不要 |
| 3.7 期待値リストと状態管理 / 8. Phase 2A-8 | B 記述漏れ | 期待値リスト画面には `QR再読込`、長押しトグル、完了ダイアログ、Snackbar、振動・音のフィードバックが実装されているが、仕様書は将来課題寄りにしか触れていない。 | `ui/dispensing/DispensingScreen.kt`, `ui/dispensing/DispensingViewModel.kt` | 中 | a. 仕様書に追記 |
| 3.6 JAHIS QR | B 記述漏れ | `JahisQrParser` は `JAHISTC07` を `TC01` 同等として扱い、レコード不足や不正レコードを警告ログでスキップする。仕様書にはフォーマット対応はあるが、この防御処理の粒度までは書かれていない。 | `data/jahis/JahisQrParser.kt`, `domain/jahis/JahisQrAssembler.kt`, `util/BarcodeAnalyzer.kt` | 低 | d. 内部実装詳細として記録不要 |

## 3. 章ごとのサマリ

### 1. 目的
- 仕様書記述の要約: アプリ全体の目的と、本仕様書が 2026/06/09 時点の現状を示すことを説明している。
- 実装の現状: 実装側に矛盾なし。
- ズレ: なし。

### 2. 現在の機能構成
- 仕様書記述の要約: MEDIS 取込、薬品検索、PTP スキャン、JAHIS QR、期待値リスト、分包機学習トグルを主要機能として列挙している。
- 実装の現状: 主要機能は実装されている。
- ズレ: あり。ただし主に UI/遷移や内部補助の詳細が仕様書に未記載。

### 3. 現在の実装状態
- 仕様書記述の要約: `DrugMaster`、`MedisCsvParser`、`MedisImportRepository`、`DrugMasterDao`、PTP、JAHIS QR、`ExpectedListBuilder` の現在状態を説明している。
- 実装の現状: 中核は一致している。
- ズレ: あり。特に `ExpectedListBuilder` の照合経路と JAHIS 3 断片の完結挙動で差がある。

### 4. Bug 修正履歴
- 仕様書記述の要約: Bug-001A/C、Bug-001B、Bug-003、Bug-002 Phase 1/2、Bug-002 fix の修正履歴を列挙している。
- 実装の現状: コミット履歴と一致。
- ズレ: なし。

### 5. 2026/06/09 時点の現在状態
- 仕様書記述の要約: GTIN 主キー、37,071 件、PTP 照合、JAHIS 対応、2 モード構成などを現状としてまとめている。
- 実装の現状: 概ね一致。
- ズレ: あり。JAHIS 3 断片の「自動読取成功」表現は実装の manual parse / deferred 挙動と食い違う。

### 6. 今後の課題
- 仕様書記述の要約: 読取完了ボタン、Phase 2A-8、ZXing byteSegments、ML Kit rawBytes などの保留事項を並べている。
- 実装の現状: 一部は既に partial 実装済み。
- ズレ: あり。仕様書では保留扱いだが、実装には `解析する` ボタンや fragment 蓄積 UI が入っている。

### 7. Bug-001D-future の扱い
- 仕様書記述の要約: Bug-001D-future は別タスク扱いで、本仕様書では扱わないとする。
- 実装の現状: 薬品検索側には 3 文字制限、カタカナ正規化、500 件上限などの実装がある。
- ズレ: あり。仕様書は future memo 扱いだが、実装には検索 UX の細部が入っている。

### 8. Phase 2A-8 の位置づけ
- 仕様書記述の要約: JAHIS 分割 QR の本格対応を Phase 2A-8 として切り出している。
- 実装の現状: fragment 蓄積と manual parse はあるが、本格的な完結判定は未完成。
- ズレ: あり。現在は partial 実装。

### 9. 現在の課題
- 仕様書記述の要約: JAHIS 分割 QR、PTP、メーカー判定、検索結果ソートのさらなる改善が残るとしている。
- 実装の現状: 方向性は一致。
- ズレ: ほぼなし。

### 10. 運用ルール
- 仕様書記述の要約: 生成物をコミットしない、ビルド確認は `assembleDebug` を基本とする、Git 管理を徹底する。
- 実装の現状: 一致。
- ズレ: なし。

### 11. まとめ
- 仕様書記述の要約: MEDIS 取込、薬品検索、PTP、JAHIS QR、分包機学習の現在状態を総括している。
- 実装の現状: 概ね一致。
- ズレ: あり。`ExpectedListBuilder` の照合経路と JAHIS 3 断片の扱いは、説明文を実装に合わせて再調整したほうがよい。

## 4. カテゴリ別集計

- A. 未実装: 0 件
- B. 記述漏れ: 7 件
- C. 矛盾: 2 件

## 5. 高優先度ズレ TOP5

1. `ExpectedListBuilder` の照合が GTIN ベースではなく code-first + name fallback である点
2. JAHIS 3 断片が「自動読取成功」と書かれているが、実装は manual parse / deferred を残している点
3. `DispensingScreen` の完了ダイアログ・長押しトグル・フィードバック UI が仕様書に明示されていない点
4. `ScanScreen` の fragment 蓄積、`解析する` ボタン、cooldown、left ソートが仕様書に明示されていない点
5. 薬品検索の 3 文字制限、500 件上限、カタカナ正規化が仕様書に明示されていない点

## 6. 推奨アクション

- C の項目は、仕様書の記述を実装に合わせて更新するのが妥当
- B のうち、画面遷移や検索 UX などユーザー向けの仕様は追記したほうがよい
- B のうち、ログや warning 処理など内部実装詳細は仕様書に書かなくてもよい
- Phase 2A-8 の文脈に入る項目は、別タスクとして切り出して継続管理するのがよい

## 7. 補足

今回の差分は、2026/06/09 までの実装で既に解消済みのバグ修正は除外して見ている。  
したがって、Bug-001A/C、Bug-001B、Bug-003、Bug-002 Phase 1/2、Bug-002 fix のコミット内容そのものは差分対象ではない。
