package com.example.yakuzaiapp.ui.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val OPERATOR_NAME = "横田学"
private const val PRIVACY_CONTACT = "fyuuchan@gmail.com"
private const val PRIVACY_POLICY_VERSION = "PRIVACY_POLICY_VERSION=2026-07-15"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("プライバシーポリシー") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("最終更新日: 2026年7月15日", style = MaterialTheme.typography.bodySmall)
            Text("公開者: $OPERATOR_NAME", style = MaterialTheme.typography.bodyMedium)
            Text(
                "本ポリシーは、ヤクピタ（アプリ内表示名: 薬剤アプリ。以下「本アプリ」）における情報の取扱いを説明します。",
                style = MaterialTheme.typography.bodyMedium,
            )

            PolicySection(
                title = "1. 端末内で取り扱う情報",
                body = "本アプリは、カメラで読み取ったバーコード、医薬品名、使用期限、処方QRに含まれる患者氏名・性別・生年月日・処方内容などを、照合機能のため端末内で処理します。カメラ画像と処方QRの内容は、公開者のサーバーへ送信せず、処方QRの患者情報はアプリのデータベースへ保存しません。",
            )
            PolicySection(
                title = "2. 端末に保存する情報",
                body = "施設名、郵便番号・住所、利用者名、選択中の利用者、充填・監査の履歴、医薬品コード、照合設定、公開医薬品マスターおよび更新情報を、Androidのアプリ専用領域に保存します。履歴には自動削除期限と一括削除機能がありません。独自のアプリ内暗号化は行っていません。",
            )
            PolicySection(
                title = "3. 外部サービスへの通信",
                body = "郵便番号検索を実行した場合、入力した7桁の施設郵便番号をHTTPSでzipcloud（株式会社アイビス）へ送信し、住所候補を取得します。医薬品マスターは、ホーム表示時などに原則7日間隔で自動更新を確認し、手動更新時にもMEDISおよび関連するHTTPSの公開ダウンロード先へ接続します。アプリ識別用User-Agentと通常の通信情報（接続元IPアドレス等）が相手先で処理されます。これらの更新要求に患者、処方、利用者または施設情報を意図的に含めません。",
            )
            PolicySection(
                title = "4. ML Kit",
                body = "バーコード認識と文字認識にはGoogle ML Kitを利用します。画像と認識結果の処理は端末内で行われます。ML Kitは、機能提供・品質改善・障害解析のため、アプリ情報、端末情報、インストール単位の識別子、利用状況および診断情報をGoogleへ送信する場合があります。",
            )
            PolicySection(
                title = "5. 利用目的",
                body = "読み取り・医薬品照合・監査・充填記録、施設情報の入力補助、医薬品マスター更新、機能の安定性確保のために情報を利用します。広告配信や情報販売には利用しません。",
            )
            PolicySection(
                title = "6. 保存期間と削除",
                body = "端末内の情報は、利用者が削除するまで保存されます。Androidの「設定」>「アプリ」>「薬剤アプリ」>「ストレージ」>「ストレージを消去」、または本アプリのアンインストールにより、端末上の情報を削除できます。本アプリはバックアップと端末間転送からアプリデータを除外するよう設定していますが、過去にOS等が作成したバックアップの削除までは保証しません。オンラインアカウントは作成しません。",
            )
            PolicySection(
                title = "7. 安全管理",
                body = "保存情報にはAndroidのアプリ専用領域を使用し、上記の外部通信にはHTTPSを使用します。端末の画面ロック、OS更新、利用者管理など、端末管理者による適切な安全管理をお願いします。",
            )
            PolicySection(
                title = "8. 権限",
                body = "カメラ権限はバーコード・処方QR・帳票・使用期限の読み取りに使用します。インターネット権限とネットワーク状態は郵便番号検索、医薬品マスター更新およびML Kitの機能提供に使用します。バイブレーション権限は読み取り結果の通知に使用します。",
            )
            PolicySection(
                title = "9. 利用上の注意と対象年齢",
                body = "本アプリの照合結果は薬剤師等の業務を補助する情報であり、専門職による最終確認や医学的判断に代わるものではありません。本アプリは医療・薬局業務の従事者向けであり、子どもを対象としていません。",
            )
            PolicySection(
                title = "10. 改定・問い合わせ",
                body = "機能、利用サービスまたは法令・ポリシーの変更に応じて本ポリシーを改定し、更新日を表示します。問い合わせ先: $PRIVACY_CONTACT",
            )
        }
    }
}

@Composable
private fun PolicySection(title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(body, style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
    }
}
