# Monster Quest v4.0.1 — ルナ最終進化のアプリアイコン

Androidランチャー（ホーム画面、アプリ一覧）で使うアプリアイコンを、
会話で生成した**ルナ最終進化の顔**に変更。

- 元画像：`月環を纏う紫月獣ルナ.png`（ユーザーとの会話で生成された画像）
- マスターデザインは変更せず、顔・額の星・月輪が円形ランチャーマスクでも判別できるよう中央に収める
- `res/drawable-nodpi/ic_luna_launcher.png`：128×128、色数を減らしたドット調のランチャー画像
- `res/mipmap-anydpi-v26/ic_launcher.xml`：Android 8以降のアダプティブアイコン
- `res/values/luna_launcher_colors.xml`：背景色（濃紺）
- `AndroidManifest.xml`の `android:icon` と `android:roundIcon` を設定
- `versionCode=25` / `versionName=4.0.1`。アプリの `applicationId` は維持

**対象は Monster Quest アプリのみ**。別アプリ GlyphLIFE のランチャーアイコンは変更しない。
ゲーム内の相棒の姿、モンスター素材、セーブデータ、API・Glyph表示は変更しない。
新アイコンはビルド後のAPKをインストール／更新するとホーム画面に反映される。ランチャーキャッシュの更新は端末側の挙動に依存。
