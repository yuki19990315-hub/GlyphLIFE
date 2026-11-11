# Dot.調査とGlyphLIFE v0.2の対応

参考： https://github.com/kavastore/nothing-dot
Nothing公式： https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit

2026-11-11現在、Dot.の公開READMEに、13×13対応、ドット絵編集、フレームアニメーション、ライブプレビュー、画像変換、AOD Toy等が記載されている。UI・データ構造はGlyphLIFEで独立設計。Dot.の実装ソースはコピーしない。

|機能|GlyphLIFE v0.2状態|
|---|---|
|複数フレーム|実装（1～64）|
|フレームの長さ|実装（100～2000ms）|
|スマホ画面でループ再生|実装|
|背面Glyphへライブ送信|SDKアダプタを実装、実機未検証（AAR要）|
|ペン・消しゴム・塗りつぶし|実装|
|Undo/Redo|実装（アプリ実行中のみ）|
|左右上下反転・90度回転・明暗反転|実装|
|画像の13×13二値変換|実装（Floyd–Steinberg）|
|PNG保存|実装（MediaStore）|
|アニメーションJSON入出力|実装|
|AOD Glyph Toy|未実装（前景ライブとは別）|
|AIドット絵生成|未実装。文からの生成を偽装しない|
|ペットの卵生成|属性・種族等の手続き生成|
|ペットとの会話|オフライン定型返答。生成AI未接続|
|Monster Questとの交換|ペットJSON v1をエクスポート、結果イベントをインポート。Monster Quest側は未開発|

## Glyph表示の仕様
- 公式SDKの`setAppMatrixFrame`で前景アプリから画像を送信。
- Device IDは`Glyph.DEVICE_25111p`。13×13固定（実際の点灯範囲は円形137LED、四隅32マスは無効）。
- 連続送信は再生ループとフレームのdurationMsに同期。変化が速い連打はUIから75ms程度間引く。
- 未接続中は送信せず、端末画面のプレビューで代替。
- Glyph Toy（特にAOD）はサービス登録とOS側の管理が必要。Phone (4a) ProはGlyph Touch非対応、AOD Toyのみ。
- 背面の実機動作と表示の向きは未検証。

## Dot.比較で今後の拡張候補
- 目に見えるフレームサムネイル、編集ブラシのドラッグ対応
- 複数ドキュメント管理、GIFインポート/エクスポート
- AOD Toyで静的ペット状態表示
- AIドット絵生成（ローカルAIとクラウドAPIの選択）
