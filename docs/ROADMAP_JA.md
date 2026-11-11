# 開発ロードマップ

## Phase 0: 最小版ソース（着手）
- [x] Kotlin / Jetpack Compose UI
- [x] ペット1匹、遊ぶ・ごはん・休む、SharedPreferences保存
- [x] 13×13ドット絵編集とプレビュー
- [x] JSON v1定義、手動エクスポート、サンプル検証
- [x] Optional Nothing SDKアダプタとSDKなしスタブ
- [x] GitHub Actions設定（実行結果は未確認）
- [ ] Gradleビルド成功確認
- [ ] Phone (4a) Proの実機Glyph描画確認

## Phase 1: 実機MVP
- [ ] CIテスト/ビルドを成功させる
- [ ] ドット絵編集のUndo/Redo、消去確認、アクセシビリティ修正
- [ ] 公式SDKのAAR版/API対応検証、実機接続・向き・点灯・切断の検証
- [ ] 非Nothing端末、画面回転、画面サイズ、バックアップ検証

## Phase 2: Glyph Creator
- [ ] アニメーションの複数フレーム・速度・再生
- [ ] PNG/GIF/JSONインポート・エクスポート
- [ ] 反転・回転・取り消し
- [ ] AIでドット絵を生成し手動修正可能にする

## Phase 3: AI Pet
- [ ] 卵、種族、属性、個性、複数体管理
- [ ] Roomデータベース、データ移行
- [ ] AI会話・ローカル記憶・安全なルール
- [ ] 育成イベント・成長分岐・ペットの表情

## Phase 4: 別アプリ Monster Quest
- [ ] Godot 2Dプロジェクト
- [ ] 探索・戦闘・モンスター収集MVP
- [ ] JSON v1インポートとアバター表示
- [ ] クエスト結果イベント、二重適用防止

## Phase 5: 公開
- [ ] 安定化、消費電力・個人情報保護・利用条件確認
- [ ] SDKのEULAに従う配布、署名APKとアップデート手順

別構想：AI App Factoryは将来、Nothing OS Custom Labは低優先の配布用。

## 2026-11-11追加実装（v0.5）
- [x] Phone (4a) ProのAOD Toyのソース/Manifest登録（SDKビルド限定）。実機未検証。
- [x] オフラインの5種族3コマ生成（幼体/成長体/進化体）。
- [x] 20匹までのペット保存、切替、個体別チャット/画像保存と旧単体データ移行。
- [ ] AOD Toy実機検証、Google AI通信実機検証、APK実機テスト。
