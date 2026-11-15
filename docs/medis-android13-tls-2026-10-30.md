# Android 13でのMEDIS自動更新と証明書

2026-10-30、Android 13端末で起動時のMEDIS自動更新が `Trust anchor for certification path not found` で失敗した。エラーを閉じると保存済みデータで操作を続けられるが、新しいマスターは取り込まれない。

MEDISの `www2.medis.or.jp` と `medhot.medd.jp` は、確認時に2026-10-24発行のGlobalSign Root R46につながる証明書チェーンを送っていた。チェーンに中間証明書は含まれる。このAndroid 13端末ではR46への信頼経路が見つからず、アプリのHTTPS接続が失敗したと考えられる。Chromeでページが開けても、アプリでは失敗し得る。

`app/src/main/res/xml/medis_network_security_config.xml` は、この2つのホストについて、Androidの標準CAにGlobalSign Root R46を追加する。ほかのホストの信頼設定は変更しない。証明書はGlobalSign公式の `https://secure.globalsign.com/cacert/rootr46.crt` から取得したDER形式で、SHA-256は `4fa3126d8d3a11d1c4855a4f807cbad6cf919d3a5a88b03bea2c6372d93c40c9`。証明書の検証やホスト名の検証は無効にしない。

APKを既存アプリへ上書きするには、同じアプリIDと署名鍵が必要。アンインストールやストレージ消去は端末内の保存データを失う。配布前にはビルド、Android 13での更新試験、レビューゲートを確認する。

修正版は2.1.1（versionCode 4）とし、旧2.1.0（versionCode 3）から更新できる版番号にした。

この作業場所に残る旧2.1.0 APK（`app-universal-release.apk`）のアプリIDは `com.example.yakuzaiapp.personal`、署名証明書のSHA-256は `8936739171bc4fc6345f94810c87a3e5f3df8c46ba6630a68a06b0b58f032b0a`。現在のMacにあるデバッグ鍵のSHA-256は `d29b655cf983778270ac11fc8f190e77270e1587b584805b1187c85ed7967050` で一致しないため、Mac標準のデバッグ鍵で作ったAPKは旧2.1.0に上書きできない。下記の修正版は旧APKと一致する鍵で署名したが、端末に入っているアプリ自体の署名は実機で未確認。

2.1.1のDebug・ReleaseビルドとRelease Lintは成功した。Release APK内のネットワーク設定は対象の2ホストだけを含み、同梱したCA証明書のSHA-256は上記と一致した。

旧2.1.0 APKと一致する署名鍵を確認して再ビルドし、`build/distributions/yakuzai-ver2.1.1-personal-medis-fix.apk` に保存した。APKのSHA-256は `3a24f1303ffb85a41c26ddc15397c1779a9b6ab95dbca40a1b3b8e3289892fba`、アプリIDは `com.example.yakuzaiapp.personal`、versionCodeは4、表示名は「薬剤アプリ VER2.1.1」、署名証明書のSHA-256は旧APKと同じ `8936739171bc4fc6345f94810c87a3e5f3df8c46ba6630a68a06b0b58f032b0a`。鍵ファイルはソースツリーに含めていない。

Android 13実機では、端末から取得した旧2.1.0 APKの署名を照合してから `adb install -r` で上書きし、`Success` を確認した。上書き後はversionCode 4／versionName 2.1.1で、端末の初回インストール日時とアプリデータ領域の識別子は変わらなかった。起動後のログでは販売名CSVの解析が始まり、従来の証明書エラーで止まる段階は越えた。ユーザーは「データ更新に失敗しました」が出ず、「データ更新中」の表示が消えてホーム画面を操作できると報告した。更新処理は成功時にのみエラーなしで待機状態へ戻るため、今回の取込は完了したと判断できる。最終完了時刻そのものは端末内から直接読んでいない。

表示名を2.1.1にそろえて再度 `adb install -r` し、`Success` を確認した。端末内APKを読み戻してSHA-256が配布APKと一致すること、表示名が「薬剤アプリ VER2.1.1」であること、初回インストール日時とアプリデータ領域の識別子が維持されたことを確認した。
