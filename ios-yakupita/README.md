# ヤクピタ iPhone版

Android版とは独立して開発する、iPhone向けアプリの作業領域です。

## 分離方針

- Android版の `app/`、Gradle設定、既存資料には変更を加えない。
- iPhone版のソース、テスト、設計資料、生成物はこのディレクトリ内に限定する。
- Android版は動作仕様を確認するための読み取り専用資料として扱う。
- Android版とのコード共有やマルチプラットフォーム化は、別途合意しない限り行わない。

## 現在の段階

ステップ1（要件整理・移植計画）です。詳細は [docs/step-01-plan.md](docs/step-01-plan.md) を参照してください。

Android版と機能・デザインを変えず、App Store公開まで進める方針は、
[docs/milestone-01-parity-baseline.md](docs/milestone-01-parity-baseline.md) に固定しました。

最初の共通業務ロジックとしてGTIN正規化をSwift Packageへ移植しました。実装と未検証事項は
[docs/milestone-02-gtin-normalizer.md](docs/milestone-02-gtin-normalizer.md) を参照してください。
