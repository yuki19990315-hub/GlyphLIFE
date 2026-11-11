# GitHub登録と開発手順

リポジトリ: https://github.com/yuki19990315-hub/GlyphLIFE

## 進行状況
Glyph Life MVPのソースと仕様を登録。ビルド・実機動作は未検証。GitHub Actionsが成功しているか確認すること。

## ローカルビルド
Android Studio + JDK17 + SDK35を準備。Gradle 8.14を使用。`gradle :app:assembleDebug` でAPK生成。Gradle Wrapperは未同梱なので、パスにGradleを導入するかWrapperを追加する。

## Nothing SDK実機ビルド
ライセンスを確認しAARを手動配置。バージョン・署名・ハードウェア・ROM差をチェックする。AARやAPIキー、署名鍵は絶対にコミットしない。

## 保守
1. 変更ごとにプロトコルとロジックをテスト。
2. CIが通ったら実機試験。
3. バージョン付きスキーマを維持し、後方互換を壊さない。
4. AI会話・生成機能のデータ送信と料金は実装時に明示。
