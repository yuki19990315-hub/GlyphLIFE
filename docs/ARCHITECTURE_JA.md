# GlyphLIFE / Monster Quest アーキテクチャ v0.10

```text
GitHub repository: GlyphLIFE
 ├─ app/                GlyphLIFE Android APK (Kotlin + Jetpack Compose)
 │   ├─ model/          PetState, PetFactory, PetSpriteGenerator
 │   │                  GlyphFrame/Animation, GlyphMask, GlyphFluid, QuestResult
 │   ├─ data/           PetRepository, ArtworkLibrary
 │   ├─ ui/             Pet, Creator, Gallery, Fluid and Link
 │   ├─ nfc/            RingNfcStore (private UID-hash enrollment)
 │   ├─ ai/             GeminiClient (BYOK, opt-in cloud)
 │   ├─ glyph/          GlyphBridge interface
 │   ├─ src/stub/       No-op Glyph SDK implementation (public debug APK)
 │   └─ src/glyph/      Vendor AAR adapter + AOD Toy service (local SDK build)
 │
 ├─ monsterquest/       Independent Android APK (Kotlin + Jetpack Compose)
 │   ├─ model/          QuestRules, QuestProtocol, WildMonster
 │   ├─ data/           QuestStorage
 │   ├─ ai/             MonsterAi (BYOK, opt-in photo/text input)
 │   └─ ui/             Original deterministic pixel monster portraits
 │
 ├─ protocol/           Pet snapshot JSON Schema v1 + quest result example
 ├─ scripts/            Offline JSON validator, personal Windows SDK APK builder
 ├─ docs/               Specs, roadmap and license/CI/device verification notes
 └─ .github/workflows/  SDK-free APK test/build and temporary vendor SDK type/build check
```

## データの正本と連携
GlyphLIFEはペットのUUID、属性、レベル、経験値などの正本。Monster Questはそのスナップショットを受け取り、ゲーム内の探索を独立して実行する。

### GlyphLIFE → Monster Quest
- `ACTION_SEND`、MIME `application/json`、`EXTRA_TEXT`にスナップショットを入れる。
- 連携ボタンからインストール済みの相手アプリを明示Intentで開く。相手がなければ共有メニューにフォールバックする。
- `schemaVersion=1`、`source=glyph-life`、ペットUUID、属性と169桁Glyphデータを検証する。

### Monster Quest → GlyphLIFE
- 冒険の終了後に`schemaVersion=1`、`source=monster-quest`、`eventId`、`petId`、有界の経験値・幸福度を含む結果JSONを送る。
- GlyphLIFEは対象UUID・報酬値・重複eventIdを検証して保存する。
- イベントIDは同じ冒険に対して固定し、再送による二重報酬を拒否する。
- この方式はローカルの個人用MVPであり、相手アプリの署名検証や暗号署名による改ざん耐性は未実装。競争モードには使用しない。

## 保存方式
- ペット、作品、チャットはSharedPreferences＋JSON。ギャラリーは30件、ペットは20匹まで。
- Monster Questの冒険データも端末内JSONに保存。
- 将来的にRoom等へ移行する際は、スキーマバージョン、端末移行、バックアップ、破損時の回復を追加する。
- NFCリングは生UIDではなくSHA-256化した照合値のみを端末内へ保存する。

## AI境界
- ペットの文章/ドット絵候補、Monster Questのモンスター設定を生成AIが補助。
- 数値ステータス・レアリティ・報酬はゲームロジックが確定させる。AIテキストを権威あるコマンドとして実行しない。
- APIキーはユーザーが手動で設定。端末内保存もGitHub保存もしない。
- Gemini APIの利用条件は変わる可能性があり、リアルAPIアクセスのテストは未実施。

## Glyphデバイス制約
- Phone (4a) Pro: 13×13論理マトリクス、137個のLED、Glyph Touch非対応。
- 前景描画: `setAppMatrixFrame`。AOD Toy: `setMatrixFrame`、約1分ごとのEVENT_AOD。
- Nothing公式SDK 2.0 AARのminSdkは33。SDKなしビルドはminSdk29。
- 今回取得した公式SDKでは`GlyphMatrixFrame.Builder.build(Context)`が必要だった。
- AARはリポジトリで配布しない。実機における表示動作やOSによる優先順位は未検証。

## 開発フロー
1. SDKなしの2アプリの単体テストとAPKビルドを実行。
2. 独立CIで公式AARを一時取得し、SDK対応版のコンパイル/パッケージ生成を行う。SDK入りAPKはアップロードしない。
3. 開発PCでローカルビルドし、実機へ導入して点灯・AOD・NFC・センサー・安定性を検証。
4. プライバシー・ライセンス・署名・データ保護を確認して公開可否を判断。
