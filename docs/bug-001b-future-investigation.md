# Bug-001B future 調査メモ

## 現在の実装から読める事実

- `DrugMaster` の主キーは `gtin`。
- `MedisImportRepository` は CSV 解析後に `deleteAll()` したうえで、`upsertAll()` を 500 件単位で実行している。
- `DrugMasterDao.upsertAll()` は `OnConflictStrategy.REPLACE`。
- `MedisCsvParser` は 1 行を 1 レコードとして `DrugMaster` に変換している。

## いまの時点で考えられる論点

- `gtin` が主キーなので、重複しているのは GTIN か、あるいは解析側で除外された行かを切り分ける必要がある。
- `drugCode` や `yjCode` がキーになっているわけではないため、以前の仮説は現行コードとは一致していない。

## 追加で見るべき点

1. 元 CSV 側で同一 GTIN が複数行存在するか。
2. `gtin` が空、または `yjCode` が空でスキップされる行の比率。
3. `upsertAll()` で REPLACE される対象が本当に同一 GTIN か。
4. PTP 包装の欠落が、元データ不足なのか、解析条件なのか、重複排除なのか。

## まとめ

- 現行コードでは「同一 YJ で上書きされる」構造には見えない。
- 次の調査では、`gtin` ベースの重複とスキップ理由を追うのが先。
- 修正は別タスクで扱う。
