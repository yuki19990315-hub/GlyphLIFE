# Nothing Glyph Matrix SDK実装ノート

公式: https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit

- Phone (4a) Proは13×13ドット。Glyph Touch非対応、Glyph ToyはAOD専用。
- SDK側では `GlyphMatrixManager` と `setAppMatrixFrame` を前景描画の候補とする。
- `Glyph.DEVICE_25111p` の対応を使用する。
- 必要なシステムバージョンおよび実際の利用権限を確認する。
- 公式SDKライセンス上の再配布・商用制限を遵守。AARはGitHubへアップロードしない。
- 実機アダプタ: `app/src/glyph/java/dev/glyphlife/app/glyph/GlyphBridgeImpl.kt`
- 非SDKビルド: `app/src/stub/java/dev/glyphlife/app/glyph/GlyphBridgeImpl.kt`

## 実機検証
1. Nothing公式配布元からSDKを入手、規約を確認。
2. `app/libs/glyph-matrix-sdk-2.0.aar` へ手動配置。
3. ビルド成功とSDKのメソッドシグネチャ一致を確認。
4. 実機でGlyph接続、13×13送信、向き、白黒、切断を確認。
5. 画面消灯 / Flip to Glyph / 優先Glyph Toyの干渉を確認。
6. AOD Toyは未実装。公式EVENT_AOD/Manifest要件を確認して追加。

**未検証**: SDK実API、OSバージョン、実機の表示、バックグラウンド描画。
