# Nothing公式 Glyph Matrix SDK 対応調査（実機未検証）

公式資料: https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit

## v0.8 ソース修正
- GlyphMatrixFrame.Builderの`build()`は公式サンプルで引数なし。
- v0.7以前の`build(context)`を両SDKアダプタから削除。
- 前景: `setAppMatrixFrame(frame.render())`。
- AOD Glyph Toy: `setMatrixFrame(frame.render())`。
- 端末: Phone (4a) Pro / `Glyph.DEVICE_25111p`。
- Phone (4a) ProのAOD Toyは約1分ごとの`EVENT_AOD`に合わせて更新。
- `com.nothing.ketchum.permission.ENABLE`の権限宣言を保持。

## 制約と次の実施
- GitHub ActionsでSDKなしのstub APKはビルドできる。
- Nothingの独自ライセンスのAARは公開リポジトリに登録せず、GitHub配布アーティファクトにも同梱しない。
- **SDK導入ビルドと実機Glyph表示は未検証。**
- 公式AARを開発PCの`app/libs/glyph-matrix-sdk-2.0.aar`に置き、Android Studioからローカルでビルドして初めて型整合性を確認できる。
- 実機でGlyphアプリ接続、Toy表示、画面消灯、反転、終了時クローズを検証する。
- 商用利用はNothingの事前書面許可が必要であり、SDKそのものの再配布は禁止対象。
