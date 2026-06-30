# Bug-002 調査メモ

## 現状の実装

- `BarcodeAnalyzer` は JAHIS モードで ML Kit の QR 検出を優先し、0件時に ZXing QR_CODE fallback を試す構成になっている。
- ZXing fallback の結果は `getRawBytes()` 相当を reflection で拾い、`Windows-31J` / `MS932` で文字列化する経路が入っている。
- その後、`repairMojibakeIfNeeded()` で文字化け補正を試している。

## 現時点の見立て

- `result.text` の文字化けは、文字コード変換の問題で起きている可能性が高い。
- ただし、ML Kit 側の `rawBytes` 取得は現状のコードでは使っていない。
- ZXing fallback の `rawBytes` が取れるなら、`result.text` ではなく生バイトを優先する方が安定しやすい。

## 今後の確認ポイント

1. ZXing `Result` の `rawBytes` と `CHARACTER_SET` メタデータが実データで使えるか。
2. ML Kit 側で raw bytes 相当を安定して取れるか。
3. `repairMojibakeIfNeeded()` が根本解決ではなく、補助対応に留まるか。
4. JAHIS 断片の連結前後で、どの段階で文字化けしているか。

## まとめ

- 解析経路自体は復旧済み。
- 残課題は、JAHIS 文字列を正しい文字コードで復元すること。
- 追加実装は別タスクで扱う。
