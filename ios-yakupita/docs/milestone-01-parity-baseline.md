# マイルストーン01: Android同等性ベースライン

作成日: 2026-07-17

## 1. 決定事項

- iPhone版はAndroid版と機能・デザインを変えない。
- iPhone版はApp Store公開までを対象とする。
- Android側は読み取り専用とし、iPhone版は `ios-yakupita/` 内で独立開発する。
- オーナーは実機iPhoneを所有している。Macは約10年前の機体のみで、機種、macOS、Xcode対応状況は未確認である。
- Xcodeビルド、署名、実機インストール、TestFlight、App Store提出は未検証であり、成功扱いにしない。

## 2. 仕様の優先順位

矛盾がある場合は次の順で同等性を判定する。

1. Android版の現行動作
2. Android版の現行ソースとテスト
3. リポジトリルートの `docs/spec_v0.3.md` と日付付き追加仕様
4. リポジトリルートの `docs/rebuild-blueprint-2026-06-27.md`
5. 古い仕様書

Android版が今後変更された場合は、iPhone側へ取り込む前に本書との差分を記録する。

## 3. 画面・導線の同等性

Androidの `app/src/main/java/com/example/yakuzaiapp/ui/navigation/AppNavigation.kt` を基準に、次の導線を移植する。

| 領域 | 必須導線・画面 | iPhone版の受入条件 |
|---|---|---|
| 初回起動 | 施設登録 → MEDIS強制更新 → ホーム | 未登録では施設登録を回避できず、登録後だけ更新を開始する |
| ホーム | ホーム、調剤モード、帳票モード、充填モード、利用者選択の下部タブ | ラベル、並び、選択状態、主要カードの色と情報階層をAndroid版に合わせる |
| 設定・管理 | 医薬品検索、MEDIS取込、院内製剤・材料マスター、施設情報、利用者登録・選択、充填履歴、プライバシーポリシー | Android版と同じ必須項目、一覧、編集・削除可否を持つ |
| 調剤 | JAHIS QR読取 → 処方内容・期待値 → PTP/箱読取 → 完了 | 複数QR、同一薬複数行、数量、未確認行優先、完了条件を同一にする |
| 帳票監査 | 帳票撮影・OCR → 候補確認 → PTP/箱読取 → 完了 | 候補選択、過去選択の優先、利用者登録品、YJ照合を同一にする |
| 充填 | 充填元読取 → 充填先読取 → 判定・履歴 | 一致・不一致、再読取、音、振動、履歴保存を同一にする |

カメラ画面からホームや別モードへ移る際は、撮影セッションと解析処理を停止してから遷移する。バックグラウンド移行、権限拒否、連続読取停止、再開を受入試験に含める。

## 4. 業務ロジックの同等性

### 4.1 GTINと医薬品照合

- AI `01` 付き16桁は後続14桁を採用する。
- GTIN-14はそのまま、JAN-13は先頭に `0` を補ってGTIN-14にする。
- GTINはチェックデジットを検証する。
- 解決順は `sales_package.gtin` → `gtinSales` → `gtinCase` → 旧来の `drug_master` 直接検索とする。
- 包装GTINからYJコードを解決し、`drug_master` へ接続する。主照合はYJコード、必要時に薬価コードをフォールバックにする。
- 同一薬が複数行あるときは未確認行を優先し、全候補確認済みの場合だけ確認済みとして扱う。

### 4.2 JAHIS QR

- 分割QRを順不同で組み立て、重複片を再加算しない。
- 処方データはセッション中だけ保持し、永続DBに保存しない。
- Androidのパーサーと単体テストの入力・期待結果をSwift側の互換テストへ移植する。

### 4.3 帳票OCR

- 日本語OCRから薬品名らしい行を抽出し、数量、棚番、日時、注意事項等を除外する。
- 薬品名、包装名、かな、別名を候補検索対象にする。
- 候補はYJコード単位に集約し、曖昧な場合は利用者が選択する。
- 過去の手動選択を同じ照合キーで優先する。

### 4.4 利用者登録品

- 名称とバーコードを必須、規格・包装と区分を任意とする。
- 正しいGTINはGTIN-14へ正規化し、施設独自コードは前後空白除去後3〜64文字を許可する。
- 制御文字、空値、不正チェックデジットのGTINを拒否する。
- 公開MEDIS更新時も利用者登録品を保持する。

## 5. データ同等性

iPhone側は実装方式にかかわらず、次の論理データを欠落なく表現する。

- 公開／利用者登録医薬品マスターと包装マスター
- 利用者マスター、選択中利用者、施設情報
- 充填履歴、調剤・監査の照合履歴と明細
- 帳票監査／調剤の選択設定、MEDIS更新メタデータ

MEDIS HOTと販売名・包装ファイルは別データ集合として保持する。更新失敗時に直前の利用可能データへ戻せること、公開マスター更新で利用者登録品を削除しないことを必須とする。

## 6. 権限・プライバシー・ネットワーク

- カメラはQR、バーコード、帳票OCRに使用する。拒否時にクラッシュせず、設定案内または安全な戻り導線を表示する。
- ネットワークはMEDIS更新と任意の郵便番号検索に使用する。
- 音・触覚は成功、警告、不一致をAndroid版と同等に区別し、端末設定を尊重する。
- JAHIS QRの患者・処方情報はメモリ内のセッションのみとし、永続保存しない。
- 施設情報、利用者名、履歴、マスターは端末内保存を基本とする。
- 提出前にデータ取扱い、プライバシーポリシー、App Privacy回答を一致させる。

## 7. 実装・検証の順序

1. Mac互換性確認: 機種、年式、macOS、搭載可能なXcode、実機iPhone/iOSを記録する。
2. SwiftUIプロジェクト作成: Bundle ID、Deployment Target、単体テスト、UIテストを設定する。
3. 純粋ロジック: GTIN正規化、数量計算、JAHIS解析・組立、照合判定をAndroidテスト相当で先行実装する。
4. 永続化とMEDIS: スキーマ、文字コード、差替え、失敗時復旧を実装する。
5. 共通カメラ基盤: 権限、開始停止、連続読取抑止、復帰を実機確認する。
6. 充填 → 調剤 → 帳票監査の順で、業務単位ごとに実装・実機確認する。
7. 管理画面、アクセシビリティ、プライバシー、クラッシュ復旧を確認する。
8. TestFlightで受入試験後、App Store Connectを整備して審査提出する。

## 8. マイルストーン01の完了条件

- オーナー決定事項が明記されている。
- Android現行実装の主要画面、3モード、管理機能、データ、権限が受入条件へ追跡できる。
- Android側に変更がない。
- Macなしで未検証の項目を成功または完了と記載していない。
- `codex-review` で指摘がない。

## 9. 次工程の確認事項

以下が確認できるまでは仕様・テストケースの準備までとし、ビルド可能とは判断しない。

- 約10年前のMacの正確な機種・年式、macOS、メモリ、空き容量
- インストール可能なXcodeの最高バージョン
- 実機iPhoneの機種とiOSバージョン
- Apple Developer Program加入状況と使用Apple IDの管理者
- 希望するBundle ID（Androidの仮ID `com.example.yakuzaiapp` はApp Store用に採用しない）
- アプリ表示名、販売者名、主対象国、サポートURL、プライバシーポリシーURL

## 10. 根拠ファイル

以下はすべてAndroid側の読み取り専用資料である。

- [`docs/spec_v0.3.md`](../../docs/spec_v0.3.md)
- [`docs/rebuild-blueprint-2026-06-27.md`](../../docs/rebuild-blueprint-2026-06-27.md)
- [`docs/spec-camera-navigation-2026-07-06.md`](../../docs/spec-camera-navigation-2026-07-06.md)
- [`docs/privacy-data-inventory.md`](../../docs/privacy-data-inventory.md)
- [`app/src/main/AndroidManifest.xml`](../../app/src/main/AndroidManifest.xml)
- [`app/src/main/java/com/example/yakuzaiapp/ui/navigation/AppNavigation.kt`](../../app/src/main/java/com/example/yakuzaiapp/ui/navigation/AppNavigation.kt)
- [`app/src/main/java/com/example/yakuzaiapp/ui/home/HomeScreen.kt`](../../app/src/main/java/com/example/yakuzaiapp/ui/home/HomeScreen.kt)
- `app/src/main/java/com/example/yakuzaiapp/domain/`
- `app/src/test/`
