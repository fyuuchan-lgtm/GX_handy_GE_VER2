# マイルストーン02: GTIN正規化のSwift移植

作成日: 2026-07-17

## 対象

Android版の次の純粋業務ロジックを、独立したSwift Package `YakupitaCore` へ移植した。

- `normalizeGtin`
- `normalizeMasterBarcode`
- `isPackageBarcode`
- GTINチェックデジット検証

Android側の根拠は `app/src/main/java/com/example/yakuzaiapp/util/GtinNormalizer.kt`、テスト根拠は `app/src/test/java/com/example/yakuzaiapp/util/GtinNormalizerTest.kt` である。Android側は変更していない。

## 構造

- `Package.swift`: XcodeアプリからローカルPackageとして追加できる定義
- `Sources/YakupitaCore/GtinNormalizer.swift`: UIやApple固有フレームワークに依存しない本体
- `Tests/YakupitaCoreTests/GtinNormalizerTests.swift`: Android既存12テスト相当と境界条件を確認するXCTest

iPhoneアプリ作成後は、Xcodeで `ios-yakupita/` をローカルPackageとして追加し、アプリターゲットから `import YakupitaCore` して使用する。ロジックをアプリ側へ複製しない。

## 同等性の範囲

- ASCII数字以外を除去する。
- 全角数字をASCII数字へ変換する。
- AI `01` 付き16桁以上では、AI直後の14桁を候補にする。
- GTIN-14はそのまま、JAN-13は先頭 `0` を補う。
- チェックデジット不正を拒否する。
- 施設独自コードは前後空白を除去し、3〜64文字、制御文字なし、数字を1文字以上含む場合に受け入れる。
- 数字と空白と括弧だけでGTINらしいがチェックデジット不正な値は、施設独自コードへフォールバックせず拒否する。
- 14桁かつ先頭が1〜8のGTINだけを包装・箱バーコードと判定する。

## 検証状態

現Windows環境で `swift` と `xcodebuild` の存在を確認したが、利用可能なコマンドは見つからなかった。そのためXCTestは未実行であり、成功扱いにしない。

Mac入手後の最初の検証コマンド:

```sh
cd ios-yakupita
swift test
```

Xcode統合後は同じテストに加えて、アプリターゲットのビルドと実機iPhoneでのバーコード読取を検証する。
