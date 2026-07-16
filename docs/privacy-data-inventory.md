# ヤクピタ データ取扱台帳

最終確認日: 2026-07-15

この台帳は実装調査に基づく。Google Play提出前に、リリースAAB、SDK、外部サービスの現行仕様と再照合する。

## 端末内のみで処理する情報

| 情報 | 取得元 | 目的 | 保存 |
|---|---|---|---|
| カメラ画像 | カメラ | バーコード、処方QR、帳票、使用期限の認識 | 画像ファイルとして保存しない |
| 処方QRの患者氏名、性別、生年月日、医療機関、医師、処方内容 | JAHIS QR | 調剤照合 | セッション中のメモリのみ。Roomへ保存しない |
| 帳票OCR文字列、医薬品名 | カメラ/ML Kit | 監査対象の抽出・照合 | OCR全文は保存しない |

画像・認識結果はML Kitの端末内処理に渡す。公開者が運営するサーバーへの送信はない。

## 端末へ保存する情報

| 保存先 | 情報 | 保持・削除 |
|---|---|---|
| Room `yakuzaiapp.db` | 公開医薬品マスター、利用者名、充填履歴、監査・照合履歴、医薬品コード、照合設定 | 自動削除期限なし。Androidのストレージ消去またはアンインストールで端末上から削除 |
| SharedPreferences | 施設名、郵便番号、都道府県、市区町村、町域、番地、住所、選択中の利用者、MEDIS更新情報 | 同上 |

保存先はAndroidのアプリ専用領域。独自のアプリ内暗号化は実装していない。`allowBackup=false`、Android 12以降のdata extraction rules、Android 11以前のfull backup rulesで全アプリデータをクラウドバックアップと端末間転送から除外する。過去のOSバックアップや端末メーカー固有挙動による削除は保証しない。

## 端末外への通信

| 相手先 | 送信・受信 | 目的 | 必須性 |
|---|---|---|---|
| `zipcloud.ibsnet.co.jp` | 利用者が入力した7桁の施設郵便番号をHTTPS送信し、住所候補を受信。接続元IP等の通常の通信情報も相手先で処理される | 施設住所の入力補助 | 任意。郵便番号検索を実行したときのみ |
| `www2.medis.or.jp` | User-Agent `YakuzaiApp/1.0` と通常の通信情報。公開HOTマスターのリンク・ファイルを受信 | 医薬品マスター更新 | アプリ機能のため自動または手動で実行 |
| `medhot.medd.jp` | 同上。販売名ファイルのリンク・ファイルを受信 | 医薬品マスター更新 | 同上 |
| 公開ページが示すHTTPSダウンロード先 | 同上。最終ホストは更新時のリンクに依存し、HTTPリンクとHTTPへのリダイレクトは拒否 | 医薬品マスター更新 | 同上 |
| Google ML Kit | SDK仕様上のアプリ情報、端末情報、インストール単位の識別子、利用・診断情報をHTTPS送信する場合がある | SDKの機能提供、利用分析、障害診断 | 読み取り機能の利用に伴う自動処理 |

MEDIS更新要求へ患者、処方、利用者または施設情報を意図的に含めない。

## SDKと権限

- `com.google.mlkit:barcode-scanning:17.3.0`
- `com.google.mlkit:text-recognition-japanese:16.0.0`
- `CAMERA`: 読み取り
- `INTERNET` / `ACCESS_NETWORK_STATE`: 郵便番号検索、MEDIS更新、ML Kit
- `VIBRATE`: 読み取り結果通知
- Health Connectおよび健康データ権限: 使用なし

## リリース確認

- ログへ患者情報、処方内容、OCR本文、医薬品名、YJ/GTIN、バーコード値を出力しない。
- 最終AABの依存関係とGoogle Play SDK Indexを確認する。
- ZIPCloudの現行規約と、Data Safety上の「収集」「共有」例外を公開者が確認する。
- 公開プライバシーポリシーURLを未ログイン環境で開けることを確認する。
- 未完成時の`noindex,nofollow`を`index,follow`へ変更し、ランディングページへリンクを追加する。
- 公開者名、問い合わせ先、医療機器該当性の判断を完了する。

## 参考

- Google Play Data Safety: https://support.google.com/googleplay/android-developer/answer/10787469
- Google ML Kit Data Safety: https://developers.google.com/ml-kit/android-data-disclosure
- Google ML Kit Terms and privacy: https://developers.google.com/ml-kit/terms
- Android backup security: https://developer.android.com/privacy-and-security/risks/backup-best-practices
- ZIPCloud API利用規約: https://zipcloud.ibsnet.co.jp/rule/api
