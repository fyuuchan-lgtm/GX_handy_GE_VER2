# MEDIS更新中の進捗率表示

2026-11-12、Android 13実機の更新画面で、進捗率が青帯の外側でも白文字になり、30～50%付近で読みにくいことを確認した。

`ReadableProgressBar` は、進捗率を濃い青と白で同じ位置に描く。白文字の層を青帯の幅で切り取るため、青帯の外側は濃い青、内側は白で表示される。境界が文字の途中にある場合も色が境界に沿って切り替わる。アクセシビリティには進捗率を1回だけ伝える。更新中の全画面表示と手動取込画面の両方で使い、進捗率が不明な間の表示は従来どおりとした。

更新版は個人用 `com.example.yakuzaiapp.personal` の2.1.2（versionCode 5、表示名「薬剤アプリ VER2.1.2」）。署名済みAPKは `build/distributions/yakuzai-ver2.1.2-personal-progress.apk`、SHA-256は `7a2b0f815b50cee8c1f66e8463d8414722d804f21cad6eee2d1e1b6e02b2c09a`。旧2.1.1と同じ署名証明書（SHA-256 `8936739171bc4fc6345f94810c87a3e5f3df8c46ba6630a68a06b0b58f032b0a`）を使い、鍵ファイルはソースツリーに含めていない。

Debug・ReleaseビルドとRelease Lintは成功した。APKをMacのOneDrive `codex` に置き、Windowsへの同期を確認した。WindowsにUSB接続したAndroid 13端末へ `adb install -r` で上書きし、ユーザーから `Success` の結果が報告された。ユーザーは実機で％表示が読みやすくなったことと、実機テストが良好だったことを確認した。アンインストールやデータ消去は行っていない。
