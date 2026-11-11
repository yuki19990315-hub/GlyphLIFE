# Phone (4a) Pro AOD Glyph Toy 実装ノート

公式一次情報: https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit

## 目的
Glyph Lifeで作った13×13（実LED137点）のアニメーションの各コマを、
Nothing Phone (4a) Proの Flip to Glyph > Always-on Glyph Toy に表示する。

## 実装構成
- 前景アプリ: `GlyphBridgeImpl`, `setAppMatrixFrame`でライブ送信。
- AOD Toy: `PetAodToyService`, `setMatrixFrame`でGlyph Toyサービス内から送信。
- AOD Toyは公式SDK AARをアプリの`app/libs`に配置したビルド時だけ有効。
- SDKなしのGitHub Actions APKではAOD Toyを無効化し、偽装表示を避ける。
- AndroidManifestにToyサービスとメタデータを登録し、AOD許可フラグを指定。
- OSが送る`GlyphToy.EVENT_AOD`イベントに応じて次フレームを1つ表示する。
- 公式文書によるとAODイベントは約1分ごと。通常の速度のアニメーションとは異なる。
- AOD中のバックグラウンド無限ループは利用しない。
- 接続解除時にSDKサービスを`unInit`する。

## 実機導入手順
1. 公式SDKのライセンスを確認し、適合するAARを入手。
2. `app/libs/glyph-matrix-sdk-2.0.aar`に配置してローカルビルド。
3. Phone (4a) Proにインストールし、必要なGlyph権限/OSバージョンを確認。
4. 設定 → Glyph Interface → Flip to Glyph → Always-on Glyph ToyからGlyph Life Petを選ぶ。
5. サービス選択・端末裏返し・1分後のフレーム更新を確認する。
6. 通常の前景表示とGlyph Toyとの競合、電池消費、例外を検証する。

## 未検証
- AAR同梱ビルドのコンパイル、実機接続・レンダリング、実際のAODイベント受信。
- Nothing側のGlyph Toy管理画面でのサービス検出。
- AODのアニメーションは1分に1コマ。画面での100–2000ms再生とは異なる。

## ライセンス
公式SDKは独自EULAでありリポジトリに同梱・再配布しない。
公式の明示許可なくSDKの商用利用を行わない。
