# GlyphLIFE 開発・検証状況

更新日: **2026-11-11**。リポジトリ: https://github.com/yuki19990315-hub/GlyphLIFE

## 結論

**GlyphLIFEとMonster QuestのAndroid APKビルドおよび単体テストは成功。**
さらにNothing公式 Glyph Matrix SDK 2.0をダウンロードして一時的に型チェックする独立CIジョブでも、SDK専用コードのコンパイルに成功した。

一方、**Nothing Phone (4a) Proでの実機表示、NFCリング読み取り、傾きセンサーの操作、Gemini APIへの実通信は未検証**。動作確認済みと表現してはならない。

## GlyphLIFE（現在v0.10）

|機能|確認範囲|
|---|---|
|ペット20匹、育成、卵生成、個体別履歴|ソース実装・APKビルド成功|
|種族別3コマ、Lv5/Lv12の成長|モデル単体テスト成功|
|13×13ドット絵エディター、137LEDマスク|単体テスト・APKビルド成功|
|最大64フレームのアニメ、保存、作品ギャラリー|APKビルド成功|
|PNG変換・スプライトシート、JSON入出力|APKビルド成功|
|Glyph Fluid Lab / 傾きセンサー|物理シミュレーションの単体テスト成功、センサー実機未検証|
|NFCリング登録・モード切り替え|SHA-256処理の単体テスト成功、NFC実機未検証|
|Glyph Matrix SDK前景表示・AOD Toy|公式SDK 2.0でコンパイル成功、実機未検証|
|Gemini AI会話・ドット絵生成|プログラムと入力検証実装、API実通信未検証|

## Monster Quest（現在v0.3）

- 独立Android APK。5部屋の探索、通常敵、最終ボス、属性戦闘、回復・防御、捕獲、保存を実装。
- Monster Questの戦闘ロジック、ボス、生成ドット絵、JSON連携の単体テスト成功。
- GlyphLIFEとの双方向連携は明示的なAndroid共有インテントで実装。二重受領判定などはプログラム上の処理。2アプリの実機間連携は未検証。
- Geminiでモンスター名・属性・説明・13×13アートを生成するAPIコードはあるが、APIキー未提供のため実通信未検証。

## SDK検証により判明した事実

1. Nothing公式`glyph-matrix-sdk-2.0.aar`は最低Android API33を要求。SDKなしGlyphLIFEはAPI29以上を維持。
2. 使用したSDKでは`GlyphMatrixFrame.Builder.build(Context)`の呼び出しが必要。公式READMEの一部サンプル表記とは差異があったが、**実際のAARに対するコンパイラの結果を優先**した。
3. GitHub Actionsの`glyph-sdk-compile`ではSDKを公式URLから一時取得し、SDKを含むAPKをアップロードしない。
4. 通常の`build`ジョブはSDKなしのGlyphLIFEとMonster Questの2つのデバッグAPKをアーティファクトとして公開する。

## 次に実機で確かめること
- Windowsローカルスクリプトを用いたGlyph SDK入りのAPK生成と実機インストール。
- Phone (4a) Proで前景フレーム送信・アニメーションの向き・速度・消費電力。
- AOD Glyph Toyのシステム設定への表示と約1分ごとの更新。
- NFCリングの安定UID/読取可否とUI/背面モード切り替え。
- 傾きセンサーの方向・感度調整。
- Gemini APIキーを用いた任意の低回数接続・料金条件・データ取り扱いの確認。

詳細は`docs/WINDOWS_GLYPH_BUILD_JA.md`参照。
